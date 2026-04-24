package com.android.jizhangmiao.ledger.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

enum class LedgerEntryType {
    EXPENSE,
    INCOME
}

enum class LedgerTemplateRecurrence {
    NONE,
    WEEKLY,
    MONTHLY
}

enum class LedgerTemplatePlanType {
    STANDARD,
    SUBSCRIPTION,
    INSTALLMENT
}

val ledgerAccountSuggestions = listOf(
    "\u652f\u4ed8\u5b9d",
    "\u5fae\u4fe1",
    "\u94f6\u884c\u5361",
    "\u73b0\u91d1"
)

fun defaultLedgerAccount(): String = ledgerAccountSuggestions.first()

data class LedgerEntry(
    val id: String = UUID.randomUUID().toString(),
    val type: LedgerEntryType,
    val amountInCents: Long,
    val account: String = defaultLedgerAccount(),
    val category: String,
    val note: String = "",
    val receiptText: String = "",
    val happenedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class LedgerTemplate(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val type: LedgerEntryType,
    val amountInCents: Long,
    val account: String = defaultLedgerAccount(),
    val category: String,
    val recurrence: LedgerTemplateRecurrence = LedgerTemplateRecurrence.NONE,
    val nextDueAt: Long? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val planType: LedgerTemplatePlanType = LedgerTemplatePlanType.STANDARD,
    val installmentTotalPeriods: Int? = null,
    val installmentPaidPeriods: Int = 0
)

data class LedgerBudgetConfig(
    val monthlyBudgetInCents: Long? = null,
    val categoryBudgets: Map<String, Long> = emptyMap()
)

data class LedgerProfileConfig(
    val customAccounts: List<String> = emptyList(),
    val customExpenseCategories: List<String> = emptyList(),
    val customIncomeCategories: List<String> = emptyList()
)

data class LedgerAutomationTrace(
    val sourceLabel: String = "",
    val summary: String = "",
    val rawText: String = "",
    val happenedAt: Long = 0L
) {
    val isAvailable: Boolean
        get() = happenedAt > 0L
}

data class PendingLedgerImport(
    val id: String = UUID.randomUUID().toString(),
    val signature: String,
    val type: LedgerEntryType,
    val amountInCents: Long,
    val account: String = defaultLedgerAccount(),
    val category: String,
    val note: String = "",
    val receiptText: String = "",
    val happenedAt: Long,
    val sourceLabel: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class LedgerSecurityConfig(
    val pinHash: String = "",
    val pinSalt: String = ""
) {
    val isPinEnabled: Boolean
        get() = pinHash.isNotBlank() && pinSalt.isNotBlank()
}

data class BackupPreview(
    val entriesCount: Int,
    val templatesCount: Int,
    val categoryBudgetCount: Int
)

data class LedgerStatementImportResult(
    val totalCount: Int,
    val importedCount: Int,
    val skippedCount: Int
)

enum class LedgerImportMode {
    REPLACE,
    MERGE
}

fun sanitizeAmountInput(value: String): String {
    val filtered = value.filter { it.isDigit() || it == '.' }
    val dotIndex = filtered.indexOf('.')

    if (dotIndex == -1) {
        return filtered
    }

    val integerPart = filtered.substring(0, dotIndex)
    val decimalPart = filtered.substring(dotIndex + 1).replace(".", "").take(2)
    return buildString {
        append(integerPart)
        append('.')
        append(decimalPart)
    }
}

fun String.toAmountInCents(): Long? {
    if (isBlank()) {
        return null
    }

    return runCatching {
        trim().toBigDecimal()
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    }.getOrNull()?.takeIf { it > 0L }
}

fun Long.toAmountInput(): String {
    return BigDecimal.valueOf(this, 2)
        .stripTrailingZeros()
        .toPlainString()
}

data class RecurringSyncResult(
    val entries: List<LedgerEntry>,
    val templates: List<LedgerTemplate>,
    val generatedCount: Int
)

val LedgerTemplate.isInstallmentPlan: Boolean
    get() = planType == LedgerTemplatePlanType.INSTALLMENT && installmentTotalPeriods != null

val LedgerTemplate.isSubscriptionPlan: Boolean
    get() = planType == LedgerTemplatePlanType.SUBSCRIPTION && recurrence != LedgerTemplateRecurrence.NONE

val LedgerTemplate.installmentRemainingPeriods: Int?
    get() = installmentTotalPeriods?.let { total ->
        (total - installmentPaidPeriods.coerceAtLeast(0)).coerceAtLeast(0)
    }

fun nextRecurringDueAt(
    baseTimeMillis: Long,
    recurrence: LedgerTemplateRecurrence
): Long? {
    val zoneId = ZoneId.systemDefault()
    val baseDateTime = Instant.ofEpochMilli(baseTimeMillis).atZone(zoneId)
    return when (recurrence) {
        LedgerTemplateRecurrence.NONE -> null
        LedgerTemplateRecurrence.WEEKLY -> baseDateTime.plusWeeks(1).toInstant().toEpochMilli()
        LedgerTemplateRecurrence.MONTHLY -> baseDateTime.plusMonths(1).toInstant().toEpochMilli()
    }
}

fun initialTemplateNextDueAt(
    fromTimeMillis: Long,
    recurrence: LedgerTemplateRecurrence
): Long? = nextRecurringDueAt(fromTimeMillis, recurrence)

fun syncRecurringTemplates(
    entries: List<LedgerEntry>,
    templates: List<LedgerTemplate>,
    now: Long = System.currentTimeMillis()
): RecurringSyncResult {
    val generatedEntries = mutableListOf<LedgerEntry>()
    val updatedTemplates = templates.map { template ->
        val normalizedTemplate = template.normalizePlan()

        if (normalizedTemplate.recurrence == LedgerTemplateRecurrence.NONE) {
            normalizedTemplate.copy(nextDueAt = null)
        } else {
            var nextDueAt = normalizedTemplate.nextDueAt ?: initialTemplateNextDueAt(now, normalizedTemplate.recurrence)
            var paidPeriods = normalizedTemplate.installmentPaidPeriods
            val totalPeriods = normalizedTemplate.installmentTotalPeriods

            if (normalizedTemplate.isInstallmentPlan && totalPeriods != null && paidPeriods >= totalPeriods) {
                nextDueAt = null
            }

            while (nextDueAt != null && nextDueAt <= now) {
                generatedEntries += LedgerEntry(
                    type = normalizedTemplate.type,
                    amountInCents = normalizedTemplate.amountInCents,
                    account = normalizedTemplate.account.ifBlank { defaultLedgerAccount() },
                    category = normalizedTemplate.category,
                    note = buildGeneratedTemplateNote(
                        template = normalizedTemplate,
                        installmentPeriod = if (normalizedTemplate.isInstallmentPlan) {
                            paidPeriods + 1
                        } else {
                            null
                        }
                    ),
                    happenedAt = nextDueAt,
                    updatedAt = nextDueAt
                )
                if (normalizedTemplate.isInstallmentPlan) {
                    paidPeriods += 1
                }
                nextDueAt = if (
                    normalizedTemplate.isInstallmentPlan &&
                    totalPeriods != null &&
                    paidPeriods >= totalPeriods
                ) {
                    null
                } else {
                    nextRecurringDueAt(nextDueAt, normalizedTemplate.recurrence)
                }
            }

            normalizedTemplate.copy(
                account = normalizedTemplate.account.ifBlank { defaultLedgerAccount() },
                nextDueAt = nextDueAt,
                installmentPaidPeriods = paidPeriods
            )
        }
    }

    return RecurringSyncResult(
        entries = entries + generatedEntries,
        templates = updatedTemplates,
        generatedCount = generatedEntries.size
    )
}

fun normalizeEntries(entries: List<LedgerEntry>): List<LedgerEntry> {
    return entries.sortedWith(
        compareByDescending<LedgerEntry> { it.happenedAt }
            .thenByDescending { it.updatedAt }
            .thenByDescending { it.id }
    )
}

fun normalizeTemplates(templates: List<LedgerTemplate>): List<LedgerTemplate> {
    return templates.sortedWith(
        compareByDescending<LedgerTemplate> { it.createdAt }
            .thenByDescending { it.id }
    )
}

fun normalizePendingImports(pendingImports: List<PendingLedgerImport>): List<PendingLedgerImport> {
    return pendingImports.sortedWith(
        compareByDescending<PendingLedgerImport> { it.createdAt }
            .thenByDescending { it.happenedAt }
            .thenByDescending { it.id }
    )
}

private fun LedgerTemplate.normalizePlan(): LedgerTemplate {
    val normalizedPlanType = when {
        recurrence == LedgerTemplateRecurrence.NONE -> LedgerTemplatePlanType.STANDARD
        planType == LedgerTemplatePlanType.INSTALLMENT && installmentTotalPeriods != null -> {
            LedgerTemplatePlanType.INSTALLMENT
        }

        planType == LedgerTemplatePlanType.SUBSCRIPTION -> LedgerTemplatePlanType.SUBSCRIPTION
        else -> LedgerTemplatePlanType.STANDARD
    }
    val normalizedRecurrence = when (normalizedPlanType) {
        LedgerTemplatePlanType.INSTALLMENT -> LedgerTemplateRecurrence.MONTHLY
        else -> recurrence
    }
    val normalizedTotalPeriods = if (normalizedPlanType == LedgerTemplatePlanType.INSTALLMENT) {
        installmentTotalPeriods?.coerceAtLeast(1)
    } else {
        null
    }
    val normalizedPaidPeriods = if (normalizedTotalPeriods == null) {
        0
    } else {
        installmentPaidPeriods.coerceIn(0, normalizedTotalPeriods)
    }

    return copy(
        recurrence = normalizedRecurrence,
        nextDueAt = if (normalizedRecurrence == LedgerTemplateRecurrence.NONE) null else nextDueAt,
        planType = normalizedPlanType,
        installmentTotalPeriods = normalizedTotalPeriods,
        installmentPaidPeriods = normalizedPaidPeriods
    )
}

private fun buildGeneratedTemplateNote(
    template: LedgerTemplate,
    installmentPeriod: Int?
): String {
    if (!template.isInstallmentPlan || installmentPeriod == null || template.installmentTotalPeriods == null) {
        return template.note
    }

    val baseNote = template.note.trim().ifBlank {
        template.title.trim().ifBlank { template.category }
    }
    return "$baseNote \u7b2c${installmentPeriod}/${template.installmentTotalPeriods}\u671f"
}
