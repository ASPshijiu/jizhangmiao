package com.android.jizhangmiao.ledger

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.jizhangmiao.ledger.data.LedgerAutomationRule
import com.android.jizhangmiao.ledger.data.LedgerEntry
import com.android.jizhangmiao.ledger.data.LedgerEntryType
import com.android.jizhangmiao.ledger.data.LedgerImportMode
import com.android.jizhangmiao.ledger.data.LedgerSecurityConfig
import com.android.jizhangmiao.ledger.data.LedgerStore
import com.android.jizhangmiao.ledger.data.LedgerTemplate
import com.android.jizhangmiao.ledger.data.LedgerTemplateRecurrence
import com.android.jizhangmiao.ledger.data.PendingLedgerImport
import com.android.jizhangmiao.ledger.data.defaultLedgerAccount
import com.android.jizhangmiao.ledger.data.initialTemplateNextDueAt
import com.android.jizhangmiao.ledger.data.sanitizeAmountInput
import com.android.jizhangmiao.ledger.data.toAmountInCents
import com.android.jizhangmiao.ledger.data.toAmountInput
import java.nio.charset.Charset
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LedgerViewModel(
    private val appContext: Context,
    private val ledgerStore: LedgerStore
) : ViewModel() {
    private data class UiSeed(
        val entries: List<LedgerEntry>,
        val templates: List<LedgerTemplate>,
        val budgetConfig: com.android.jizhangmiao.ledger.data.LedgerBudgetConfig,
        val profileConfig: com.android.jizhangmiao.ledger.data.LedgerProfileConfig,
        val automationRules: List<LedgerAutomationRule>,
        val automationTrace: com.android.jizhangmiao.ledger.data.LedgerAutomationTrace,
        val pendingImports: List<PendingLedgerImport>,
        val securityConfig: LedgerSecurityConfig
    )

    private data class UiMeta(
        val automationRules: List<LedgerAutomationRule>,
        val automationTrace: com.android.jizhangmiao.ledger.data.LedgerAutomationTrace,
        val pendingImports: List<PendingLedgerImport>,
        val securityConfig: LedgerSecurityConfig
    )

    private val formState = MutableStateFlow(LedgerFormState())
    private val statusMessage = MutableStateFlow<String?>(null)
    private val scanningState = MutableStateFlow(false)
    private val lockState = MutableStateFlow(ledgerStore.securityConfig.value.isPinEnabled)

    init {
        viewModelScope.launch {
            val generatedCount = ledgerStore.syncRecurringTemplates()
            if (generatedCount > 0) {
                statusMessage.value = "\u5df2\u81ea\u52a8\u8865\u5165 $generatedCount \u7b14\u5230\u671f\u7684\u5468\u671f\u8d26\u5355"
            }
        }
    }

    val uiState: StateFlow<LedgerUiState> = combine(
        combine(
            ledgerStore.entries,
            ledgerStore.templates,
            ledgerStore.budgetConfig,
            ledgerStore.profileConfig
        ) { entries, templates, budgetConfig, profileConfig ->
            UiSeed(
                entries = entries,
                templates = templates,
                budgetConfig = budgetConfig,
                profileConfig = profileConfig,
                automationRules = ledgerStore.automationRules.value,
                automationTrace = ledgerStore.automationTrace.value,
                pendingImports = ledgerStore.pendingImports.value,
                securityConfig = ledgerStore.securityConfig.value
            )
        },
        combine(
            ledgerStore.automationRules,
            ledgerStore.automationTrace,
            ledgerStore.pendingImports,
            ledgerStore.securityConfig
        ) { automationRules, automationTrace, pendingImports, securityConfig ->
            UiMeta(
                automationRules = automationRules,
                automationTrace = automationTrace,
                pendingImports = pendingImports,
                securityConfig = securityConfig
            )
        }
    ) { seed, meta ->
        seed.copy(
            automationRules = meta.automationRules,
            automationTrace = meta.automationTrace,
            pendingImports = meta.pendingImports,
            securityConfig = meta.securityConfig
        )
    }.let { seedFlow ->
        combine(
            seedFlow,
            formState,
            statusMessage,
            scanningState,
            lockState
        ) { seed, form, message, isScanning, isLocked ->
            LedgerUiState(
                entries = seed.entries,
                templates = seed.templates,
                budgetConfig = seed.budgetConfig,
                profileConfig = seed.profileConfig,
                automationRules = seed.automationRules,
                automationTrace = seed.automationTrace,
                pendingImports = seed.pendingImports,
                securityConfig = seed.securityConfig,
                isLocked = seed.securityConfig.isPinEnabled && isLocked,
                form = form,
                statusMessage = message,
                isReceiptScanning = isScanning
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LedgerUiState()
    )

    fun onTypeSelected(type: LedgerEntryType) {
        formState.update { current ->
            val shouldReplaceCategory = current.category.isBlank() ||
                current.category in categorySuggestionsFor(current.type)

            current.copy(
                type = type,
                category = if (shouldReplaceCategory) defaultCategoryFor(type) else current.category,
                errorMessage = null
            )
        }
    }

    fun onAmountChanged(value: String) {
        formState.update { current ->
            current.copy(
                amount = sanitizeAmountInput(value),
                errorMessage = null
            )
        }
    }

    fun onCategoryChanged(value: String) {
        formState.update { current ->
            current.copy(
                category = value.take(12),
                errorMessage = null
            )
        }
    }

    fun onAccountChanged(value: String) {
        formState.update { current ->
            current.copy(
                account = value.take(12),
                errorMessage = null
            )
        }
    }

    fun onNoteChanged(value: String) {
        formState.update { current ->
            current.copy(
                note = value.take(80),
                errorMessage = null
            )
        }
    }

    fun onSuggestedCategorySelected(category: String) {
        formState.update { current ->
            current.copy(
                category = category,
                errorMessage = null
            )
        }
    }

    fun onSuggestedAccountSelected(account: String) {
        formState.update { current ->
            current.copy(
                account = account,
                errorMessage = null
            )
        }
    }

    fun onTemplateRecurrenceSelected(recurrence: LedgerTemplateRecurrence) {
        formState.update { current ->
            current.copy(
                templateRecurrence = recurrence,
                errorMessage = null
            )
        }
    }

    fun startEditing(entry: LedgerEntry) {
        formState.value = LedgerFormState(
            editingEntryId = entry.id,
            type = entry.type,
            amount = entry.amountInCents.toAmountInput(),
            account = entry.account,
            category = entry.category,
            note = entry.note,
            receiptText = entry.receiptText
        )
        statusMessage.value = "\u5df2\u8f7d\u5165\u8fd9\u7b14\u8bb0\u5f55\uff0c\u53ef\u4ee5\u76f4\u63a5\u4fee\u6539"
    }

    fun cancelEditing() {
        resetForm()
    }

    fun saveEntry() {
        val snapshot = formState.value
        val amountInCents = snapshot.amount.toAmountInCents()
        val account = snapshot.account.trim()
        val category = snapshot.category.trim()

        when {
            amountInCents == null -> {
                showFormError("\u8bf7\u8f93\u5165\u5927\u4e8e 0 \u7684\u91d1\u989d")
                return
            }

            account.isBlank() -> {
                showFormError("\u8bf7\u8f93\u5165\u8d26\u6237")
                return
            }

            category.isBlank() -> {
                showFormError("\u8bf7\u8f93\u5165\u5206\u7c7b")
                return
            }
        }

        viewModelScope.launch {
            val existingEntry = snapshot.editingEntryId?.let { editingId ->
                ledgerStore.entries.value.firstOrNull { entry -> entry.id == editingId }
            }

            if (existingEntry == null) {
                ledgerStore.addEntry(
                    LedgerEntry(
                        type = snapshot.type,
                        amountInCents = amountInCents,
                        account = account,
                        category = category,
                        note = snapshot.note.trim(),
                        receiptText = snapshot.receiptText.trim()
                    )
                )
                statusMessage.value = "\u5df2\u4fdd\u5b58\u8fd9\u7b14\u8bb0\u5f55"
            } else {
                ledgerStore.updateEntry(
                    existingEntry.copy(
                        type = snapshot.type,
                        amountInCents = amountInCents,
                        account = account,
                        category = category,
                        note = snapshot.note.trim(),
                        receiptText = snapshot.receiptText.trim()
                    )
                )
                statusMessage.value = "\u8bb0\u5f55\u5df2\u66f4\u65b0"
            }

            resetForm(type = snapshot.type)
        }
    }

    fun deleteEntry(entry: LedgerEntry) {
        viewModelScope.launch {
            ledgerStore.deleteEntry(entry)
            if (formState.value.editingEntryId == entry.id) {
                resetForm()
            }
            statusMessage.value = "\u8fd9\u7b14\u8bb0\u5f55\u5df2\u5220\u9664"
        }
    }

    fun saveCurrentAsTemplate() {
        val snapshot = formState.value
        val amountInCents = snapshot.amount.toAmountInCents()
        val account = snapshot.account.trim()
        val category = snapshot.category.trim()

        when {
            amountInCents == null -> {
                showFormError("\u5148\u586b\u597d\u91d1\u989d\uff0c\u518d\u4fdd\u5b58\u4e3a\u6a21\u677f")
                return
            }

            account.isBlank() -> {
                showFormError("\u5148\u9009\u597d\u8d26\u6237\uff0c\u518d\u4fdd\u5b58\u4e3a\u6a21\u677f")
                return
            }

            category.isBlank() -> {
                showFormError("\u5148\u586b\u597d\u5206\u7c7b\uff0c\u518d\u4fdd\u5b58\u4e3a\u6a21\u677f")
                return
            }
        }

        viewModelScope.launch {
            val title = snapshot.note.trim().ifBlank { category }
            ledgerStore.upsertTemplate(
                LedgerTemplate(
                    title = title,
                    type = snapshot.type,
                    amountInCents = amountInCents,
                    account = account,
                    category = category,
                    recurrence = snapshot.templateRecurrence,
                    nextDueAt = initialTemplateNextDueAt(
                        fromTimeMillis = System.currentTimeMillis(),
                        recurrence = snapshot.templateRecurrence
                    ),
                    note = snapshot.note.trim()
                )
            )
            statusMessage.value = if (snapshot.templateRecurrence == LedgerTemplateRecurrence.NONE) {
                "\u5df2\u4fdd\u5b58\u4e3a\u5feb\u6377\u6a21\u677f"
            } else {
                "\u5df2\u4fdd\u5b58\u4e3a${snapshot.templateRecurrence.displayName()}\u5468\u671f\u6a21\u677f"
            }
        }
    }

    fun applyTemplate(template: LedgerTemplate) {
        formState.value = LedgerFormState(
            type = template.type,
            amount = template.amountInCents.toAmountInput(),
            account = template.account,
            category = template.category,
            note = template.note,
            templateRecurrence = template.recurrence
        )
        statusMessage.value = "\u5df2\u5e94\u7528\u6a21\u677f\uff0c\u53ef\u4ee5\u76f4\u63a5\u4fdd\u5b58\u6216\u518d\u5fae\u8c03"
    }

    fun deleteTemplate(template: LedgerTemplate) {
        viewModelScope.launch {
            ledgerStore.deleteTemplate(template)
            statusMessage.value = "\u5df2\u5220\u9664\u8fd9\u4e2a\u6a21\u677f"
        }
    }

    fun approvePendingImport(pendingImport: PendingLedgerImport) {
        viewModelScope.launch {
            ledgerStore.approvePendingImport(pendingImport)
            statusMessage.value = "\u5df2\u786e\u8ba4\u8fd9\u7b14\u81ea\u52a8\u8bb0\u8d26"
        }
    }

    fun ignorePendingImport(pendingImport: PendingLedgerImport) {
        viewModelScope.launch {
            ledgerStore.ignorePendingImport(pendingImport)
            statusMessage.value = "\u5df2\u5ffd\u7565\u8fd9\u6761\u81ea\u52a8\u8bb0\u8d26\u5019\u9009"
        }
    }

    fun addAccount(account: String) {
        viewModelScope.launch {
            ledgerStore.addAccount(account)
            statusMessage.value = "\u8d26\u6237\u5df2\u52a0\u5165\u5e38\u7528\u5217\u8868"
        }
    }

    fun addCategory(
        type: LedgerEntryType,
        category: String
    ) {
        viewModelScope.launch {
            ledgerStore.addCategory(type, category)
            statusMessage.value = "\u5206\u7c7b\u5df2\u52a0\u5165\u5e38\u7528\u5217\u8868"
        }
    }

    fun addAutomationRule(
        keyword: String,
        type: LedgerEntryType,
        category: String,
        account: String
    ) {
        val normalizedKeyword = keyword.trim().take(20)
        val normalizedCategory = category.trim().take(12)
        val normalizedAccount = account.trim().take(12)

        when {
            normalizedKeyword.isBlank() -> {
                statusMessage.value = "\u8bf7\u8f93\u5165\u5339\u914d\u5173\u952e\u8bcd"
                return
            }

            normalizedCategory.isBlank() -> {
                statusMessage.value = "\u8bf7\u8f93\u5165\u8981\u81ea\u52a8\u5f52\u7c7b\u7684\u5206\u7c7b"
                return
            }
        }

        viewModelScope.launch {
            ledgerStore.addAutomationRule(
                LedgerAutomationRule(
                    keyword = normalizedKeyword,
                    type = type,
                    category = normalizedCategory,
                    account = normalizedAccount
                )
            )
            ledgerStore.addCategory(type, normalizedCategory)
            if (normalizedAccount.isNotBlank()) {
                ledgerStore.addAccount(normalizedAccount)
            }
            statusMessage.value = "\u81ea\u52a8\u5206\u7c7b\u89c4\u5219\u5df2\u4fdd\u5b58"
        }
    }

    fun deleteAutomationRule(rule: LedgerAutomationRule) {
        viewModelScope.launch {
            ledgerStore.deleteAutomationRule(rule.id)
            statusMessage.value = "\u81ea\u52a8\u5206\u7c7b\u89c4\u5219\u5df2\u5220\u9664"
        }
    }

    fun renameAccount(
        oldAccount: String,
        newAccount: String
    ) {
        viewModelScope.launch {
            ledgerStore.renameAccount(oldAccount, newAccount)
            statusMessage.value = "\u8d26\u6237\u5df2\u91cd\u547d\u540d"
        }
    }

    fun renameCategory(
        type: LedgerEntryType,
        oldCategory: String,
        newCategory: String
    ) {
        viewModelScope.launch {
            ledgerStore.renameCategory(type, oldCategory, newCategory)
            statusMessage.value = "\u5206\u7c7b\u5df2\u91cd\u547d\u540d"
        }
    }

    fun saveBudget(
        monthlyBudgetInput: String,
        category: String,
        categoryBudgetInput: String
    ) {
        val normalizedMonthlyBudget = monthlyBudgetInput.trim()
        val normalizedCategoryBudget = categoryBudgetInput.trim()

        if (normalizedMonthlyBudget.isNotBlank() && normalizedMonthlyBudget.toAmountInCents() == null) {
            statusMessage.value = "\u6708\u5ea6\u9884\u7b97\u683c\u5f0f\u4e0d\u5bf9"
            return
        }

        if (normalizedCategoryBudget.isNotBlank() && normalizedCategoryBudget.toAmountInCents() == null) {
            statusMessage.value = "\u5206\u7c7b\u9884\u7b97\u683c\u5f0f\u4e0d\u5bf9"
            return
        }

        viewModelScope.launch {
            ledgerStore.updateMonthlyBudget(normalizedMonthlyBudget.toAmountInCents())
            ledgerStore.updateCategoryBudget(category, normalizedCategoryBudget.toAmountInCents())
            statusMessage.value = "\u9884\u7b97\u5df2\u4fdd\u5b58"
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                appContext.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(ledgerStore.exportBackupJson())
                } ?: error("Unable to open output stream")
            }.onSuccess {
                statusMessage.value = "\u5907\u4efd\u5df2\u5bfc\u51fa"
            }.onFailure {
                statusMessage.value = "\u5bfc\u51fa\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5"
            }
        }
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                appContext.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(ledgerStore.exportCsv())
                } ?: error("Unable to open output stream")
            }.onSuccess {
                statusMessage.value = "CSV \u5df2\u5bfc\u51fa"
            }.onFailure {
                statusMessage.value = "CSV \u5bfc\u51fa\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5"
            }
        }
    }

    fun previewBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val backupJson = appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    reader.readText()
                } ?: error("Unable to open input stream")
                ledgerStore.previewBackupJson(backupJson)
            }.onSuccess { preview ->
                statusMessage.value = if (preview == null) {
                    "\u5907\u4efd\u683c\u5f0f\u4e0d\u6b63\u786e"
                } else {
                    "\u5907\u4efd\u9884\u89c8\uff1a${preview.entriesCount} \u7b14\u8bb0\u5f55\u3001${preview.templatesCount} \u4e2a\u6a21\u677f\u3001${preview.categoryBudgetCount} \u4e2a\u5206\u7c7b\u9884\u7b97"
                }
            }.onFailure {
                statusMessage.value = "\u9884\u89c8\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u6587\u4ef6"
            }
        }
    }

    fun importBackup(
        uri: Uri,
        mode: LedgerImportMode = LedgerImportMode.REPLACE
    ) {
        viewModelScope.launch {
            runCatching {
                val backupJson = appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    reader.readText()
                } ?: error("Unable to open input stream")

                ledgerStore.importBackupJson(backupJson, mode)
            }.onSuccess { imported ->
                if (imported) {
                    val generatedCount = ledgerStore.syncRecurringTemplates()
                    resetForm()
                    val actionText = if (mode == LedgerImportMode.MERGE) "\u5df2\u5408\u5e76\u5bfc\u5165" else "\u5df2\u8986\u76d6\u5bfc\u5165"
                    statusMessage.value = if (generatedCount > 0) {
                        "$actionText\uff0c\u5e76\u8865\u5165 $generatedCount \u7b14\u5468\u671f\u8d26\u5355"
                    } else {
                        actionText
                    }
                } else {
                    statusMessage.value = "\u5907\u4efd\u683c\u5f0f\u4e0d\u6b63\u786e"
                }
            }.onFailure {
                statusMessage.value = "\u5bfc\u5165\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u6587\u4ef6"
            }
        }
    }

    fun importStatement(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val statementText = readTextFromUri(uri)
                val parsedResult = parseStatementCsv(statementText)
                    ?: error("Unsupported statement csv")
                val importedResult = ledgerStore.importStatementEntries(parsedResult.entries)
                parsedResult to importedResult
            }.onSuccess { (parsedResult, importedResult) ->
                val skippedCount = importedResult.skippedCount + parsedResult.skippedRowCount
                statusMessage.value = if (importedResult.importedCount > 0) {
                    buildString {
                        append(parsedResult.sourceLabel)
                        append("导入完成：新增 ")
                        append(importedResult.importedCount)
                        append(" 笔")
                        if (skippedCount > 0) {
                            append("，跳过 ")
                            append(skippedCount)
                            append(" 行")
                        }
                    }
                } else {
                    buildString {
                        append(parsedResult.sourceLabel)
                        append("里没有新增记录")
                        if (skippedCount > 0) {
                            append("，已跳过 ")
                            append(skippedCount)
                            append(" 行重复或无效数据")
                        }
                    }
                }
            }.onFailure {
                statusMessage.value = "账单导入失败，当前仅支持带时间和金额列的 CSV 文件"
            }
        }
    }

    fun unlockWithPin(pin: String) {
        val securityConfig = ledgerStore.securityConfig.value
        if (!securityConfig.isPinEnabled) {
            lockState.value = false
            return
        }

        if (hashPin(pin, securityConfig.pinSalt) == securityConfig.pinHash) {
            lockState.value = false
            statusMessage.value = "\u5df2\u89e3\u9501"
        } else {
            statusMessage.value = "PIN \u4e0d\u6b63\u786e"
        }
    }

    fun lockNow() {
        if (ledgerStore.securityConfig.value.isPinEnabled) {
            lockState.value = true
            statusMessage.value = "\u5df2\u9501\u5b9a"
        }
    }

    fun setPin(pin: String) {
        val normalizedPin = pin.trim()
        if (normalizedPin.length < 4) {
            statusMessage.value = "PIN \u81f3\u5c11\u9700\u8981 4 \u4f4d"
            return
        }

        viewModelScope.launch {
            val salt = generateSalt()
            ledgerStore.updateSecurityConfig(
                LedgerSecurityConfig(
                    pinHash = hashPin(normalizedPin, salt),
                    pinSalt = salt
                )
            )
            lockState.value = false
            statusMessage.value = "\u9690\u79c1\u9501\u5df2\u5f00\u542f"
        }
    }

    fun disablePin() {
        viewModelScope.launch {
            ledgerStore.updateSecurityConfig(LedgerSecurityConfig())
            lockState.value = false
            statusMessage.value = "\u9690\u79c1\u9501\u5df2\u5173\u95ed"
        }
    }

    fun scanReceipt(uri: Uri) {
        viewModelScope.launch {
            scanningState.value = true
            runCatching {
                recognizeReceipt(appContext, uri)
            }.onSuccess { result ->
                if (result == null || result.rawText.isBlank()) {
                    statusMessage.value = "\u672a\u8bc6\u522b\u5230\u53ef\u7528\u6587\u5b57"
                } else {
                    formState.update { current ->
                        current.copy(
                            editingEntryId = null,
                            type = result.type ?: current.type,
                            amount = result.amountInput ?: current.amount,
                            account = result.account ?: current.account.ifBlank { defaultLedgerAccount() },
                            category = result.category ?: current.category,
                            note = if (current.note.isBlank()) result.suggestedNote.take(80) else current.note,
                            receiptText = result.rawText,
                            errorMessage = null
                        )
                    }
                    statusMessage.value = "\u5df2\u4ece\u5c0f\u7968\u63d0\u53d6\u5185\u5bb9\uff0c\u8bf7\u518d\u6838\u5bf9\u91d1\u989d"
                }
            }.onFailure {
                statusMessage.value = "\u5c0f\u7968\u8bc6\u522b\u5931\u8d25\uff0c\u53ef\u4ee5\u6539\u4e3a\u624b\u52a8\u5f55\u5165"
            }
            scanningState.value = false
        }
    }

    fun dismissStatusMessage() {
        statusMessage.value = null
    }

    private fun showFormError(message: String) {
        formState.update { current ->
            current.copy(errorMessage = message)
        }
    }

    private fun resetForm(type: LedgerEntryType = LedgerEntryType.EXPENSE) {
        formState.value = LedgerFormState(
            type = type,
            account = defaultLedgerAccount(),
            category = defaultCategoryFor(type)
        )
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun readTextFromUri(uri: Uri): String {
        val bytes = appContext.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes()
        } ?: error("Unable to open input stream")

        val candidateCharsets = listOf(
            Charsets.UTF_8,
            Charset.forName("GB18030"),
            Charsets.UTF_16LE,
            Charsets.UTF_16BE
        )
        return candidateCharsets
            .map { charset -> charset to bytes.toString(charset) }
            .maxByOrNull { (_, text) ->
                scoreDecodedText(text)
            }
            ?.second
            ?.replace("\uFEFF", "")
            ?: error("Unable to decode statement file")
    }

    private fun scoreDecodedText(text: String): Int {
        return text.count { char -> char == '\n' || char == ',' || char == ';' || char == '\t' } * 2 -
            text.count { char -> char == '\uFFFD' } * 5
    }

    private fun hashPin(
        pin: String,
        salt: String
    ): String {
        return MessageDigest.getInstance("SHA-256")
            .digest("$salt:$pin".toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    companion object {
        fun factory(
            appContext: Context,
            ledgerStore: LedgerStore
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LedgerViewModel(appContext.applicationContext, ledgerStore)
            }
        }
    }
}
