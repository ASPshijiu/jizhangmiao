package com.android.jizhangmiao.ledger

import com.android.jizhangmiao.ledger.data.LedgerEntry
import com.android.jizhangmiao.ledger.data.LedgerEntryType
import com.android.jizhangmiao.ledger.data.defaultLedgerAccount
import com.android.jizhangmiao.ledger.data.toAmountInCents
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.absoluteValue

data class LedgerStatementParseResult(
    val sourceLabel: String,
    val entries: List<LedgerEntry>,
    val skippedRowCount: Int
)

private data class StatementColumnMapping(
    val timeIndex: Int?,
    val amountIndex: Int?,
    val expenseAmountIndex: Int?,
    val incomeAmountIndex: Int?,
    val typeIndex: Int?,
    val accountIndex: Int?,
    val categoryIndex: Int?,
    val noteIndex: Int?
)

private val StatementDateTimePatterns = listOf(
    "yyyy-MM-dd HH:mm:ss",
    "yyyy/MM/dd HH:mm:ss",
    "yyyy.MM.dd HH:mm:ss",
    "yyyy-MM-dd HH:mm",
    "yyyy/MM/dd HH:mm",
    "yyyy.MM.dd HH:mm",
    "yyyy年M月d日 HH:mm:ss",
    "yyyy年M月d日 HH:mm",
    "M/d/yyyy HH:mm:ss",
    "M/d/yyyy HH:mm",
    "d/M/yyyy HH:mm:ss",
    "d/M/yyyy HH:mm"
).map { pattern ->
    DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
}

private val StatementDatePatterns = listOf(
    "yyyy-MM-dd",
    "yyyy/MM/dd",
    "yyyy.MM.dd",
    "yyyy年M月d日",
    "yyyyMMdd",
    "M/d/yyyy",
    "d/M/yyyy"
).map { pattern ->
    DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
}

fun parseStatementCsv(
    rawText: String,
    now: Long = System.currentTimeMillis()
): LedgerStatementParseResult? {
    val text = rawText
        .replace("\uFEFF", "")
        .trim()
    if (text.isBlank()) {
        return null
    }

    val rows = parseSeparatedRows(text)
        .map { row -> row.map(::normalizeCellContent) }
        .filter { row -> row.any { cell -> cell.isNotBlank() } }
    if (rows.size < 2) {
        return null
    }

    val headerIndex = rows.indexOfFirst { row ->
        scoreHeaderRow(row) >= 2
    }
    if (headerIndex < 0) {
        return null
    }

    val mapping = detectColumnMapping(rows[headerIndex]) ?: return null
    val sourceLabel = detectStatementSource(text, rows[headerIndex])
    val parsedEntries = mutableListOf<LedgerEntry>()
    val seenKeys = linkedSetOf<String>()
    var skippedRowCount = 0

    rows.drop(headerIndex + 1).forEach { row ->
        val rowText = row.joinToString(" ") { cell -> cell.trim() }.trim()
        if (rowText.isBlank() || shouldSkipStatementRow(rowText)) {
            skippedRowCount += 1
            return@forEach
        }

        val entry = parseStatementRow(
            row = row,
            mapping = mapping,
            sourceLabel = sourceLabel,
            now = now
        )
        if (entry == null) {
            skippedRowCount += 1
            return@forEach
        }

        val dedupeKey = listOf(
            entry.type.name,
            entry.amountInCents.toString(),
            entry.account,
            entry.category,
            entry.happenedAt.toString(),
            entry.note
        ).joinToString("|")

        if (!seenKeys.add(dedupeKey)) {
            skippedRowCount += 1
            return@forEach
        }

        parsedEntries += entry
    }

    if (parsedEntries.isEmpty()) {
        return null
    }

    return LedgerStatementParseResult(
        sourceLabel = sourceLabel,
        entries = parsedEntries,
        skippedRowCount = skippedRowCount
    )
}

private fun parseStatementRow(
    row: List<String>,
    mapping: StatementColumnMapping,
    sourceLabel: String,
    now: Long
): LedgerEntry? {
    val rowText = row.joinToString(" ") { cell -> cell.trim() }.trim()
    val parsedAmount = parseStatementAmount(row, mapping, rowText) ?: return null
    if (parsedAmount.second <= 0L) {
        return null
    }

    val type = parsedAmount.first
    val explicitCategory = row.valueAt(mapping.categoryIndex).trim().take(12)
    val explicitAccount = row.valueAt(mapping.accountIndex).trim().take(12)
    val explicitNote = row.valueAt(mapping.noteIndex).trim()
    val happenedAt = parseStatementTime(row.valueAt(mapping.timeIndex))
        ?: parseEpochLikeTime(row.valueAt(mapping.timeIndex))
        ?: now
    val inferredCategory = inferCategoryFromText(type, "$explicitCategory $explicitNote $rowText")
    val inferredAccount = inferAccountFromText("$explicitAccount $explicitNote $rowText", sourceLabel)
    val note = buildStatementNote(
        explicitNote = explicitNote,
        explicitCategory = explicitCategory,
        rowText = rowText
    )

    return LedgerEntry(
        type = type,
        amountInCents = parsedAmount.second,
        account = explicitAccount.ifBlank { inferredAccount }.ifBlank { defaultLedgerAccount() },
        category = explicitCategory.ifBlank { inferredCategory }.ifBlank { fallbackCategory(type) },
        note = note,
        receiptText = "",
        happenedAt = happenedAt,
        updatedAt = now
    )
}

private fun parseStatementAmount(
    row: List<String>,
    mapping: StatementColumnMapping,
    rowText: String
): Pair<LedgerEntryType, Long>? {
    val expenseAmount = row.valueAt(mapping.expenseAmountIndex).toSignedAmountInCents()
        ?.absoluteValue
        ?.takeIf { amount -> amount > 0L }
    val incomeAmount = row.valueAt(mapping.incomeAmountIndex).toSignedAmountInCents()
        ?.absoluteValue
        ?.takeIf { amount -> amount > 0L }

    if (expenseAmount != null && incomeAmount == null) {
        return LedgerEntryType.EXPENSE to expenseAmount
    }
    if (incomeAmount != null && expenseAmount == null) {
        return LedgerEntryType.INCOME to incomeAmount
    }
    if (expenseAmount != null && incomeAmount != null) {
        return if (incomeAmount >= expenseAmount) {
            LedgerEntryType.INCOME to incomeAmount
        } else {
            LedgerEntryType.EXPENSE to expenseAmount
        }
    }

    val rawAmount = row.valueAt(mapping.amountIndex).toSignedAmountInCents() ?: return null
    val rawType = row.valueAt(mapping.typeIndex)
    val inferredType = inferStatementType(rawType, rowText, rawAmount)
    return inferredType to rawAmount.absoluteValue
}

private fun detectStatementSource(
    text: String,
    headerRow: List<String>
): String {
    val headerText = headerRow.joinToString(" ").lowercase(Locale.ROOT)
    val allText = text.lowercase(Locale.ROOT)
    return when {
        "wechat" in allText || "微信" in text -> "微信账单"
        "alipay" in allText || "支付宝" in text -> "支付宝账单"
        "jizhangmiao" in allText || headerText.contains("happenedat") -> "记账喵导出"
        else -> "CSV账单"
    }
}

private fun parseSeparatedRows(text: String): List<List<String>> {
    val candidates = listOf(',', ';', '\t')
    val delimiter = candidates.maxByOrNull { delimiter ->
        scoreDelimiter(text, delimiter)
    } ?: ','
    return splitDelimitedText(text, delimiter)
}

private fun scoreDelimiter(
    text: String,
    delimiter: Char
): Int {
    return splitDelimitedText(text, delimiter)
        .take(6)
        .sumOf { row -> row.count { cell -> cell.isNotBlank() } }
}

private fun splitDelimitedText(
    text: String,
    delimiter: Char
): List<List<String>> {
    val rows = mutableListOf<MutableList<String>>()
    var currentRow = mutableListOf<String>()
    val currentCell = StringBuilder()
    var insideQuote = false
    var index = 0

    while (index < text.length) {
        val char = text[index]
        when {
            char == '"' -> {
                if (insideQuote && index + 1 < text.length && text[index + 1] == '"') {
                    currentCell.append('"')
                    index += 1
                } else {
                    insideQuote = !insideQuote
                }
            }

            char == delimiter && !insideQuote -> {
                currentRow += currentCell.toString()
                currentCell.clear()
            }

            char == '\n' && !insideQuote -> {
                currentRow += currentCell.toString()
                currentCell.clear()
                rows += currentRow
                currentRow = mutableListOf()
            }

            char == '\r' -> Unit
            else -> currentCell.append(char)
        }
        index += 1
    }

    currentRow += currentCell.toString()
    if (currentRow.isNotEmpty()) {
        rows += currentRow
    }
    return rows
}

private fun detectColumnMapping(headerRow: List<String>): StatementColumnMapping? {
    val normalizedHeaders = headerRow.map(::normalizeHeaderKey)
    val mapping = StatementColumnMapping(
        timeIndex = normalizedHeaders.indexOfFirstMatching(
            "交易时间",
            "交易日期",
            "入账时间",
            "记账时间",
            "日期",
            "时间",
            "happenedat",
            "time",
            "date",
            "createdat"
        ),
        amountIndex = normalizedHeaders.indexOfFirstMatching(
            "金额",
            "交易金额",
            "收支金额",
            "实际金额",
            "amount",
            "money",
            "total"
        ),
        expenseAmountIndex = normalizedHeaders.indexOfFirstMatching(
            "支出金额",
            "支出",
            "付款金额",
            "payamount",
            "expenseamount"
        ),
        incomeAmountIndex = normalizedHeaders.indexOfFirstMatching(
            "收入金额",
            "收入",
            "收款金额",
            "incomeamount",
            "receiveamount"
        ),
        typeIndex = normalizedHeaders.indexOfFirstMatching(
            "收支类型",
            "交易类型",
            "类型",
            "收支",
            "type",
            "direction"
        ),
        accountIndex = normalizedHeaders.indexOfFirstMatching(
            "账户",
            "支付方式",
            "付款方式",
            "收付款方式",
            "资金账户",
            "支付账户",
            "account",
            "wallet"
        ),
        categoryIndex = normalizedHeaders.indexOfFirstMatching(
            "分类",
            "商品分类",
            "类目",
            "category"
        ),
        noteIndex = normalizedHeaders.indexOfFirstMatching(
            "备注",
            "说明",
            "摘要",
            "商户",
            "交易对方",
            "商品",
            "商品说明",
            "名称",
            "note",
            "remark",
            "memo",
            "description",
            "title"
        )
    )

    if (
        mapping.amountIndex == null &&
        mapping.expenseAmountIndex == null &&
        mapping.incomeAmountIndex == null
    ) {
        return null
    }
    return mapping
}

private fun scoreHeaderRow(row: List<String>): Int {
    return row.sumOf { cell ->
        val key = normalizeHeaderKey(cell)
        when {
            key.isBlank() -> 0
            key.contains("金额") || key.contains("amount") -> 2
            key.contains("时间") || key.contains("date") || key.contains("time") -> 2
            key.contains("类型") || key.contains("type") || key.contains("收支") -> 2
            key.contains("账户") || key.contains("account") || key.contains("支付方式") -> 1
            key.contains("分类") || key.contains("category") -> 1
            key.contains("备注") || key.contains("remark") || key.contains("note") -> 1
            else -> 0
        }
    }
}

private fun inferStatementType(
    rawType: String,
    rowText: String,
    signedAmount: Long
): LedgerEntryType {
    val normalizedType = rawType.lowercase(Locale.ROOT)
    val normalizedRowText = rowText.lowercase(Locale.ROOT)
    val incomeSignals = listOf("收入", "收款", "到账", "退款", "转入", "income", "credit", "bonus")
    val expenseSignals = listOf("支出", "消费", "付款", "支付", "扣款", "转出", "expense", "debit", "pay")

    return when {
        incomeSignals.any { signal -> signal in normalizedType } -> LedgerEntryType.INCOME
        expenseSignals.any { signal -> signal in normalizedType } -> LedgerEntryType.EXPENSE
        incomeSignals.any { signal -> signal in normalizedRowText } -> LedgerEntryType.INCOME
        expenseSignals.any { signal -> signal in normalizedRowText } -> LedgerEntryType.EXPENSE
        signedAmount < 0L -> LedgerEntryType.EXPENSE
        else -> LedgerEntryType.EXPENSE
    }
}

private fun inferAccountFromText(
    text: String,
    sourceLabel: String
): String {
    val normalizedText = "$sourceLabel $text".lowercase(Locale.ROOT)
    return when {
        "微信" in text || "wechat" in normalizedText -> "微信"
        "支付宝" in text || "alipay" in normalizedText || "花呗" in text || "余额宝" in text -> "支付宝"
        "银行卡" in text || "bank" in normalizedText || "信用卡" in text || "储蓄卡" in text -> "银行卡"
        "现金" in text || "cash" in normalizedText -> "现金"
        else -> defaultLedgerAccount()
    }
}

private fun inferCategoryFromText(
    type: LedgerEntryType,
    text: String
): String {
    val categoryMap = when (type) {
        LedgerEntryType.EXPENSE -> expenseCategoryKeywordMap
        LedgerEntryType.INCOME -> incomeCategoryKeywordMap
    }
    val normalizedText = text.lowercase(Locale.ROOT)
    return categoryMap.entries.firstOrNull { (category, keywords) ->
        category != "其他" && keywords.any { keyword ->
            keyword.lowercase(Locale.ROOT) in normalizedText
        }
    }?.key ?: fallbackCategory(type)
}

private fun fallbackCategory(type: LedgerEntryType): String {
    return when (type) {
        LedgerEntryType.EXPENSE -> "其他"
        LedgerEntryType.INCOME -> "其他"
    }
}

private fun buildStatementNote(
    explicitNote: String,
    explicitCategory: String,
    rowText: String
): String {
    return explicitNote.ifBlank {
        explicitCategory.ifBlank {
            rowText
        }
    }.replace('\n', ' ')
        .replace('\r', ' ')
        .trim()
        .take(80)
}

private fun shouldSkipStatementRow(rowText: String): Boolean {
    val normalizedText = rowText.lowercase(Locale.ROOT)
    val totalKeywords = listOf("总计", "合计", "小计", "共", "统计", "summary", "total")
    return totalKeywords.any { keyword -> keyword.lowercase(Locale.ROOT) in normalizedText } &&
        !normalizedText.contains("退款")
}

private fun parseStatementTime(value: String): Long? {
    val normalizedValue = normalizeCellContent(value)
    if (normalizedValue.isBlank()) {
        return null
    }

    StatementDateTimePatterns.forEach { formatter ->
        runCatching {
            val dateTime = LocalDateTime.parse(normalizedValue, formatter)
            return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    }

    StatementDatePatterns.forEach { formatter ->
        runCatching {
            val date = LocalDate.parse(normalizedValue, formatter)
            return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    }

    return runCatching {
        Instant.parse(normalizedValue).toEpochMilli()
    }.getOrNull()
}

private fun parseEpochLikeTime(value: String): Long? {
    val digits = value.trim()
    return when {
        digits.length == 13 && digits.all(Char::isDigit) -> digits.toLongOrNull()
        digits.length == 10 && digits.all(Char::isDigit) -> digits.toLongOrNull()?.times(1000)
        else -> null
    }
}

private fun String.toSignedAmountInCents(): Long? {
    if (isBlank()) {
        return null
    }

    val normalized = trim()
        .replace("￥", "")
        .replace("¥", "")
        .replace(",", "")
        .replace("，", "")
        .replace("元", "")
        .replace("RMB", "", ignoreCase = true)
        .replace("CNY", "", ignoreCase = true)
        .replace(" ", "")
        .trim()

    val directAmount = normalized.toAmountInCents()
    if (directAmount != null) {
        return if (normalized.startsWith("-")) -directAmount else directAmount
    }

    val regex = Regex("-?\\d+(?:\\.\\d+)?")
    val match = regex.find(normalized) ?: return null
    val rawNumber = match.value
    val isNegative = normalized.startsWith("-") || normalized.contains("(-") ||
        (normalized.startsWith("(") && normalized.endsWith(")"))

    return runCatching {
        BigDecimal(rawNumber)
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    }.getOrNull()?.let { amount ->
        if (isNegative || amount < 0L) amount else amount
    }
}

private fun normalizeHeaderKey(value: String): String {
    return normalizeCellContent(value)
        .lowercase(Locale.ROOT)
        .replace(Regex("[\\s:_\\-()/\\\\【】\\[\\]（）]"), "")
}

private fun normalizeCellContent(value: String): String {
    return value
        .replace("\uFEFF", "")
        .replace("“", "\"")
        .replace("”", "\"")
        .trim()
}

private fun List<String>.valueAt(index: Int?): String {
    return index?.takeIf { it in indices }?.let { this[it] }.orEmpty()
}

private fun List<String>.indexOfFirstMatching(vararg keywords: String): Int? {
    val matchIndex = indexOfFirst { header ->
        keywords.any { keyword -> header == keyword.lowercase(Locale.ROOT) }
    }
    return matchIndex.takeIf { it >= 0 }
}
