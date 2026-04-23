package com.android.jizhangmiao.ledger.data

import android.content.Context
import android.content.SharedPreferences
import com.android.jizhangmiao.ledger.AutoImportedEntry
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class LedgerStore private constructor(
    private val preferences: SharedPreferences
) {
    private val writeLock = Any()

    private val _entries = MutableStateFlow(loadEntries())
    private val _templates = MutableStateFlow(loadTemplates())
    private val _budgetConfig = MutableStateFlow(loadBudgetConfig())
    private val _profileConfig = MutableStateFlow(loadProfileConfig())
    private val _automationTrace = MutableStateFlow(loadAutomationTrace())
    private val _pendingImports = MutableStateFlow(loadPendingImports())
    private val _securityConfig = MutableStateFlow(loadSecurityConfig())

    val entries: StateFlow<List<LedgerEntry>> = _entries.asStateFlow()
    val templates: StateFlow<List<LedgerTemplate>> = _templates.asStateFlow()
    val budgetConfig: StateFlow<LedgerBudgetConfig> = _budgetConfig.asStateFlow()
    val profileConfig: StateFlow<LedgerProfileConfig> = _profileConfig.asStateFlow()
    val automationTrace: StateFlow<LedgerAutomationTrace> = _automationTrace.asStateFlow()
    val pendingImports: StateFlow<List<PendingLedgerImport>> = _pendingImports.asStateFlow()
    val securityConfig: StateFlow<LedgerSecurityConfig> = _securityConfig.asStateFlow()

    suspend fun addEntry(entry: LedgerEntry) {
        updateEntries { current ->
            current + entry.copy(
                id = entry.id.ifBlank { UUID.randomUUID().toString() },
                account = entry.account.ifBlank { defaultLedgerAccount() },
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    suspend fun updateEntry(entry: LedgerEntry) {
        updateEntries { current ->
            current.map { savedEntry ->
                if (savedEntry.id == entry.id) {
                    entry.copy(
                        account = entry.account.ifBlank { defaultLedgerAccount() },
                        updatedAt = System.currentTimeMillis()
                    )
                } else {
                    savedEntry
                }
            }
        }
    }

    suspend fun deleteEntry(entry: LedgerEntry) {
        updateEntries { current ->
            current.filterNot { savedEntry -> savedEntry.id == entry.id }
        }
    }

    internal suspend fun importAutoEntry(entry: AutoImportedEntry): Boolean {
        return withContext(Dispatchers.IO) {
            synchronized(writeLock) {
                val signatures = loadAutoImportHistory().toMutableList()
                val isAlreadyPending = _pendingImports.value.any { pending ->
                    pending.signature == entry.signature
                }
                if (signatures.contains(entry.signature) || isAlreadyPending) {
                    return@withContext false
                }

                val hasLikelyDuplicate = _entries.value.any { savedEntry ->
                    savedEntry.type == entry.type &&
                        savedEntry.amountInCents == entry.amountInCents &&
                        savedEntry.account == entry.account &&
                        savedEntry.happenedAt in (entry.happenedAt - AUTO_IMPORT_WINDOW_MILLIS)..(entry.happenedAt + AUTO_IMPORT_WINDOW_MILLIS)
                }

                signatures += entry.signature
                persistAutoImportHistory(signatures.takeLast(MAX_AUTO_IMPORT_HISTORY))

                if (hasLikelyDuplicate) {
                    return@withContext false
                }

                val updatedPendingImports = normalizePendingImports(
                    _pendingImports.value + PendingLedgerImport(
                        signature = entry.signature,
                        type = entry.type,
                        amountInCents = entry.amountInCents,
                        account = entry.account,
                        category = entry.category,
                        note = entry.note,
                        receiptText = entry.receiptText,
                        happenedAt = entry.happenedAt,
                        sourceLabel = entry.note
                    )
                )
                persistPendingImports(updatedPendingImports)
                _pendingImports.value = updatedPendingImports
                true
            }
        }
    }

    suspend fun approvePendingImport(pendingImport: PendingLedgerImport) {
        withContext(Dispatchers.IO) {
            synchronized(writeLock) {
                val updatedEntries = normalizeEntries(
                    _entries.value + LedgerEntry(
                        type = pendingImport.type,
                        amountInCents = pendingImport.amountInCents,
                        account = pendingImport.account.ifBlank { defaultLedgerAccount() },
                        category = pendingImport.category,
                        note = pendingImport.note,
                        receiptText = pendingImport.receiptText,
                        happenedAt = pendingImport.happenedAt,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                val updatedPendingImports = _pendingImports.value
                    .filterNot { savedImport -> savedImport.id == pendingImport.id }

                persistEntries(updatedEntries)
                persistPendingImports(updatedPendingImports)
                _entries.value = updatedEntries
                _pendingImports.value = updatedPendingImports
            }
        }
    }

    suspend fun ignorePendingImport(pendingImport: PendingLedgerImport) {
        withContext(Dispatchers.IO) {
            synchronized(writeLock) {
                val updatedPendingImports = _pendingImports.value
                    .filterNot { savedImport -> savedImport.id == pendingImport.id }
                persistPendingImports(updatedPendingImports)
                _pendingImports.value = updatedPendingImports
            }
        }
    }

    suspend fun upsertTemplate(template: LedgerTemplate) {
        updateTemplates { current ->
            current.filterNot { savedTemplate -> savedTemplate.id == template.id } +
                template.copy(
                    id = template.id.ifBlank { UUID.randomUUID().toString() },
                    account = template.account.ifBlank { defaultLedgerAccount() },
                    nextDueAt = if (template.recurrence == LedgerTemplateRecurrence.NONE) {
                        null
                    } else {
                        template.nextDueAt
                    }
                )
        }
    }

    suspend fun deleteTemplate(template: LedgerTemplate) {
        updateTemplates { current ->
            current.filterNot { savedTemplate -> savedTemplate.id == template.id }
        }
    }

    suspend fun updateMonthlyBudget(amountInCents: Long?) {
        updateBudgetConfig { current ->
            current.copy(monthlyBudgetInCents = amountInCents)
        }
    }

    suspend fun updateCategoryBudget(category: String, amountInCents: Long?) {
        updateBudgetConfig { current ->
            val updatedBudgets = current.categoryBudgets.toMutableMap()
            if (amountInCents == null) {
                updatedBudgets.remove(category)
            } else {
                updatedBudgets[category] = amountInCents
            }
            current.copy(categoryBudgets = updatedBudgets.toSortedMap())
        }
    }

    suspend fun addAccount(account: String) {
        val normalizedAccount = account.trim().take(12)
        if (normalizedAccount.isBlank()) {
            return
        }

        updateProfileConfig { current ->
            current.copy(
                customAccounts = (current.customAccounts + normalizedAccount)
                    .distinct()
                    .sorted()
            )
        }
    }

    suspend fun addCategory(
        type: LedgerEntryType,
        category: String
    ) {
        val normalizedCategory = category.trim().take(12)
        if (normalizedCategory.isBlank()) {
            return
        }

        updateProfileConfig { current ->
            when (type) {
                LedgerEntryType.EXPENSE -> current.copy(
                    customExpenseCategories = (current.customExpenseCategories + normalizedCategory)
                        .distinct()
                        .sorted()
                )

                LedgerEntryType.INCOME -> current.copy(
                    customIncomeCategories = (current.customIncomeCategories + normalizedCategory)
                        .distinct()
                        .sorted()
                )
            }
        }
    }

    suspend fun renameAccount(
        oldAccount: String,
        newAccount: String
    ) {
        val oldValue = oldAccount.trim()
        val newValue = newAccount.trim().take(12)
        if (oldValue.isBlank() || newValue.isBlank() || oldValue == newValue) {
            return
        }

        withContext(Dispatchers.IO) {
            synchronized(writeLock) {
                val updatedEntries = normalizeEntries(
                    _entries.value.map { entry ->
                        if (entry.account == oldValue) entry.copy(account = newValue) else entry
                    }
                )
                val updatedTemplates = normalizeTemplates(
                    _templates.value.map { template ->
                        if (template.account == oldValue) template.copy(account = newValue) else template
                    }
                )
                val updatedPendingImports = normalizePendingImports(
                    _pendingImports.value.map { pendingImport ->
                        if (pendingImport.account == oldValue) pendingImport.copy(account = newValue) else pendingImport
                    }
                )
                val updatedProfile = _profileConfig.value.copy(
                    customAccounts = (_profileConfig.value.customAccounts
                        .map { account -> if (account == oldValue) newValue else account } + newValue)
                        .distinct()
                        .sorted()
                )

                persistEntries(updatedEntries)
                persistTemplates(updatedTemplates)
                persistPendingImports(updatedPendingImports)
                persistProfileConfig(updatedProfile)
                _entries.value = updatedEntries
                _templates.value = updatedTemplates
                _pendingImports.value = updatedPendingImports
                _profileConfig.value = updatedProfile
            }
        }
    }

    suspend fun renameCategory(
        type: LedgerEntryType,
        oldCategory: String,
        newCategory: String
    ) {
        val oldValue = oldCategory.trim()
        val newValue = newCategory.trim().take(12)
        if (oldValue.isBlank() || newValue.isBlank() || oldValue == newValue) {
            return
        }

        withContext(Dispatchers.IO) {
            synchronized(writeLock) {
                val updatedEntries = normalizeEntries(
                    _entries.value.map { entry ->
                        if (entry.type == type && entry.category == oldValue) {
                            entry.copy(category = newValue)
                        } else {
                            entry
                        }
                    }
                )
                val updatedTemplates = normalizeTemplates(
                    _templates.value.map { template ->
                        if (template.type == type && template.category == oldValue) {
                            template.copy(category = newValue)
                        } else {
                            template
                        }
                    }
                )
                val updatedPendingImports = normalizePendingImports(
                    _pendingImports.value.map { pendingImport ->
                        if (pendingImport.type == type && pendingImport.category == oldValue) {
                            pendingImport.copy(category = newValue)
                        } else {
                            pendingImport
                        }
                    }
                )
                val updatedBudgets = _budgetConfig.value.categoryBudgets
                    .mapKeys { (category, _) ->
                        if (category == oldValue) newValue else category
                    }
                    .toSortedMap()
                val updatedBudgetConfig = _budgetConfig.value.copy(categoryBudgets = updatedBudgets)
                val updatedProfile = when (type) {
                    LedgerEntryType.EXPENSE -> _profileConfig.value.copy(
                        customExpenseCategories = (_profileConfig.value.customExpenseCategories
                            .map { category -> if (category == oldValue) newValue else category } + newValue)
                            .distinct()
                            .sorted()
                    )

                    LedgerEntryType.INCOME -> _profileConfig.value.copy(
                        customIncomeCategories = (_profileConfig.value.customIncomeCategories
                            .map { category -> if (category == oldValue) newValue else category } + newValue)
                            .distinct()
                            .sorted()
                    )
                }

                persistEntries(updatedEntries)
                persistTemplates(updatedTemplates)
                persistPendingImports(updatedPendingImports)
                persistBudgetConfig(updatedBudgetConfig)
                persistProfileConfig(updatedProfile)
                _entries.value = updatedEntries
                _templates.value = updatedTemplates
                _pendingImports.value = updatedPendingImports
                _budgetConfig.value = updatedBudgetConfig
                _profileConfig.value = updatedProfile
            }
        }
    }

    suspend fun updateSecurityConfig(config: LedgerSecurityConfig) {
        withContext(Dispatchers.IO) {
            synchronized(writeLock) {
                persistSecurityConfig(config)
                _securityConfig.value = config
            }
        }
    }

    suspend fun recordAutomationTrace(trace: LedgerAutomationTrace) {
        withContext(Dispatchers.IO) {
            synchronized(writeLock) {
                persistAutomationTrace(trace)
                _automationTrace.value = trace
            }
        }
    }

    fun exportBackupJson(): String {
        return JSONObject().apply {
            put("version", 3)
            put("exportedAt", System.currentTimeMillis())
            put("entries", encodeEntries(_entries.value))
            put("templates", encodeTemplates(_templates.value))
            put("budgetConfig", encodeBudgetConfig(_budgetConfig.value))
            put("profileConfig", encodeProfileConfig(_profileConfig.value))
        }.toString(2)
    }

    fun exportCsv(): String {
        val header = listOf(
            "id",
            "type",
            "amount",
            "account",
            "category",
            "note",
            "happenedAt",
            "updatedAt"
        ).joinToString(",")
        val rows = _entries.value.map { entry ->
            listOf(
                entry.id,
                entry.type.name,
                entry.amountInCents.toAmountInput(),
                entry.account,
                entry.category,
                entry.note,
                entry.happenedAt.toString(),
                entry.updatedAt.toString()
            ).joinToString(",") { value -> csvEscape(value) }
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    suspend fun syncRecurringTemplates(now: Long = System.currentTimeMillis()): Int {
        return withContext(Dispatchers.IO) {
            synchronized(writeLock) {
                val result = syncRecurringTemplates(
                    entries = _entries.value,
                    templates = _templates.value,
                    now = now
                )
                val entriesChanged = result.entries != _entries.value
                val templatesChanged = result.templates != _templates.value
                if (entriesChanged || templatesChanged) {
                    val updatedEntries = normalizeEntries(result.entries)
                    val updatedTemplates = normalizeTemplates(result.templates)
                    persistEntries(updatedEntries)
                    persistTemplates(updatedTemplates)
                    _entries.value = updatedEntries
                    _templates.value = updatedTemplates
                }
                result.generatedCount
            }
        }
    }

    fun previewBackupJson(json: String): BackupPreview? {
        return runCatching {
            val backup = JSONObject(json)
            BackupPreview(
                entriesCount = backup.optJSONArray("entries")?.length() ?: 0,
                templatesCount = backup.optJSONArray("templates")?.length() ?: 0,
                categoryBudgetCount = backup
                    .optJSONObject("budgetConfig")
                    ?.optJSONObject("categoryBudgets")
                    ?.length()
                    ?: 0
            )
        }.getOrNull()
    }

    suspend fun importBackupJson(
        json: String,
        mode: LedgerImportMode = LedgerImportMode.REPLACE
    ): Boolean {
        return withContext(Dispatchers.IO) {
            synchronized(writeLock) {
                runCatching {
                    val backup = JSONObject(json)
                    val importedEntries = decodeEntries(backup.optJSONArray("entries"))
                    val importedTemplates = decodeTemplates(backup.optJSONArray("templates"))
                    val importedBudgetConfig = decodeBudgetConfig(backup.optJSONObject("budgetConfig"))
                    val importedProfileConfig = decodeProfileConfig(backup.optJSONObject("profileConfig"))

                    val updatedEntries = when (mode) {
                        LedgerImportMode.REPLACE -> importedEntries
                        LedgerImportMode.MERGE -> mergeEntries(_entries.value, importedEntries)
                    }
                    val updatedTemplates = when (mode) {
                        LedgerImportMode.REPLACE -> importedTemplates
                        LedgerImportMode.MERGE -> mergeTemplates(_templates.value, importedTemplates)
                    }
                    val updatedBudgetConfig = when (mode) {
                        LedgerImportMode.REPLACE -> importedBudgetConfig
                        LedgerImportMode.MERGE -> _budgetConfig.value.copy(
                            monthlyBudgetInCents = importedBudgetConfig.monthlyBudgetInCents
                                ?: _budgetConfig.value.monthlyBudgetInCents,
                            categoryBudgets = (_budgetConfig.value.categoryBudgets +
                                importedBudgetConfig.categoryBudgets).toSortedMap()
                        )
                    }
                    val updatedProfileConfig = when (mode) {
                        LedgerImportMode.REPLACE -> importedProfileConfig
                        LedgerImportMode.MERGE -> LedgerProfileConfig(
                            customAccounts = (_profileConfig.value.customAccounts +
                                importedProfileConfig.customAccounts).distinct().sorted(),
                            customExpenseCategories = (_profileConfig.value.customExpenseCategories +
                                importedProfileConfig.customExpenseCategories).distinct().sorted(),
                            customIncomeCategories = (_profileConfig.value.customIncomeCategories +
                                importedProfileConfig.customIncomeCategories).distinct().sorted()
                        )
                    }

                    persistEntries(updatedEntries)
                    persistTemplates(updatedTemplates)
                    persistBudgetConfig(updatedBudgetConfig)
                    persistProfileConfig(updatedProfileConfig)

                    _entries.value = updatedEntries
                    _templates.value = updatedTemplates
                    _budgetConfig.value = updatedBudgetConfig
                    _profileConfig.value = updatedProfileConfig
                }.isSuccess
            }
        }
    }

    private suspend fun updateEntries(
        transform: (List<LedgerEntry>) -> List<LedgerEntry>
    ) {
        withContext(Dispatchers.IO) {
            synchronized(writeLock) {
                val updatedEntries = normalizeEntries(transform(_entries.value))
                persistEntries(updatedEntries)
                _entries.value = updatedEntries
            }
        }
    }

    private suspend fun updateTemplates(
        transform: (List<LedgerTemplate>) -> List<LedgerTemplate>
    ) {
        withContext(Dispatchers.IO) {
            synchronized(writeLock) {
                val updatedTemplates = normalizeTemplates(transform(_templates.value))
                persistTemplates(updatedTemplates)
                _templates.value = updatedTemplates
            }
        }
    }

    private suspend fun updateBudgetConfig(
        transform: (LedgerBudgetConfig) -> LedgerBudgetConfig
    ) {
        withContext(Dispatchers.IO) {
            synchronized(writeLock) {
                val updatedConfig = transform(_budgetConfig.value)
                persistBudgetConfig(updatedConfig)
                _budgetConfig.value = updatedConfig
            }
        }
    }

    private suspend fun updateProfileConfig(
        transform: (LedgerProfileConfig) -> LedgerProfileConfig
    ) {
        withContext(Dispatchers.IO) {
            synchronized(writeLock) {
                val updatedConfig = transform(_profileConfig.value)
                persistProfileConfig(updatedConfig)
                _profileConfig.value = updatedConfig
            }
        }
    }

    private fun loadEntries(): List<LedgerEntry> {
        val rawValue = preferences.getString(KEY_ENTRIES, null).orEmpty()
        if (rawValue.isBlank()) {
            return emptyList()
        }

        return runCatching {
            decodeEntries(JSONArray(rawValue))
        }.getOrElse {
            emptyList()
        }
    }

    private fun loadTemplates(): List<LedgerTemplate> {
        val rawValue = preferences.getString(KEY_TEMPLATES, null).orEmpty()
        if (rawValue.isBlank()) {
            return emptyList()
        }

        return runCatching {
            decodeTemplates(JSONArray(rawValue))
        }.getOrElse {
            emptyList()
        }
    }

    private fun loadBudgetConfig(): LedgerBudgetConfig {
        val rawValue = preferences.getString(KEY_BUDGET_CONFIG, null).orEmpty()
        if (rawValue.isBlank()) {
            return LedgerBudgetConfig()
        }

        return runCatching {
            decodeBudgetConfig(JSONObject(rawValue))
        }.getOrElse {
            LedgerBudgetConfig()
        }
    }

    private fun loadProfileConfig(): LedgerProfileConfig {
        val rawValue = preferences.getString(KEY_PROFILE_CONFIG, null).orEmpty()
        if (rawValue.isBlank()) {
            return LedgerProfileConfig()
        }

        return runCatching {
            decodeProfileConfig(JSONObject(rawValue))
        }.getOrElse {
            LedgerProfileConfig()
        }
    }

    private fun loadAutomationTrace(): LedgerAutomationTrace {
        val rawValue = preferences.getString(KEY_AUTOMATION_TRACE, null).orEmpty()
        if (rawValue.isBlank()) {
            return LedgerAutomationTrace()
        }

        return runCatching {
            decodeAutomationTrace(JSONObject(rawValue))
        }.getOrElse {
            LedgerAutomationTrace()
        }
    }

    private fun loadPendingImports(): List<PendingLedgerImport> {
        val rawValue = preferences.getString(KEY_PENDING_IMPORTS, null).orEmpty()
        if (rawValue.isBlank()) {
            return emptyList()
        }

        return runCatching {
            decodePendingImports(JSONArray(rawValue))
        }.getOrElse {
            emptyList()
        }
    }

    private fun loadSecurityConfig(): LedgerSecurityConfig {
        val rawValue = preferences.getString(KEY_SECURITY_CONFIG, null).orEmpty()
        if (rawValue.isBlank()) {
            return LedgerSecurityConfig()
        }

        return runCatching {
            decodeSecurityConfig(JSONObject(rawValue))
        }.getOrElse {
            LedgerSecurityConfig()
        }
    }

    private fun loadAutoImportHistory(): List<String> {
        val rawValue = preferences.getString(KEY_AUTO_IMPORT_HISTORY, null).orEmpty()
        if (rawValue.isBlank()) {
            return emptyList()
        }

        return runCatching {
            val jsonArray = JSONArray(rawValue)
            buildList {
                for (index in 0 until jsonArray.length()) {
                    val value = jsonArray.optString(index)
                    if (value.isNotBlank()) {
                        add(value)
                    }
                }
            }
        }.getOrElse {
            emptyList()
        }
    }

    private fun normalizeEntries(entries: List<LedgerEntry>): List<LedgerEntry> {
        return entries.sortedWith(
            compareByDescending<LedgerEntry> { it.happenedAt }
                .thenByDescending { it.updatedAt }
                .thenByDescending { it.id }
        )
    }

    private fun normalizeTemplates(templates: List<LedgerTemplate>): List<LedgerTemplate> {
        return templates.sortedWith(
            compareByDescending<LedgerTemplate> { it.createdAt }
                .thenByDescending { it.id }
        )
    }

    private fun normalizePendingImports(pendingImports: List<PendingLedgerImport>): List<PendingLedgerImport> {
        return pendingImports.sortedWith(
            compareByDescending<PendingLedgerImport> { it.createdAt }
                .thenByDescending { it.happenedAt }
                .thenByDescending { it.id }
        )
    }

    private fun mergeEntries(
        existingEntries: List<LedgerEntry>,
        importedEntries: List<LedgerEntry>
    ): List<LedgerEntry> {
        val existingIds = existingEntries.map { entry -> entry.id }.toSet()
        val merged = existingEntries + importedEntries.filterNot { entry ->
            entry.id in existingIds || existingEntries.any { savedEntry ->
                savedEntry.type == entry.type &&
                    savedEntry.amountInCents == entry.amountInCents &&
                    savedEntry.account == entry.account &&
                    savedEntry.category == entry.category &&
                    savedEntry.happenedAt == entry.happenedAt
            }
        }
        return normalizeEntries(merged)
    }

    private fun mergeTemplates(
        existingTemplates: List<LedgerTemplate>,
        importedTemplates: List<LedgerTemplate>
    ): List<LedgerTemplate> {
        val existingIds = existingTemplates.map { template -> template.id }.toSet()
        return normalizeTemplates(existingTemplates + importedTemplates.filterNot { template ->
            template.id in existingIds
        })
    }

    private fun persistEntries(entries: List<LedgerEntry>) {
        preferences.edit()
            .putString(KEY_ENTRIES, encodeEntries(entries).toString())
            .apply()
    }

    private fun persistTemplates(templates: List<LedgerTemplate>) {
        preferences.edit()
            .putString(KEY_TEMPLATES, encodeTemplates(templates).toString())
            .apply()
    }

    private fun persistBudgetConfig(config: LedgerBudgetConfig) {
        preferences.edit()
            .putString(KEY_BUDGET_CONFIG, encodeBudgetConfig(config).toString())
            .apply()
    }

    private fun persistProfileConfig(config: LedgerProfileConfig) {
        preferences.edit()
            .putString(KEY_PROFILE_CONFIG, encodeProfileConfig(config).toString())
            .apply()
    }

    private fun persistAutomationTrace(trace: LedgerAutomationTrace) {
        preferences.edit()
            .putString(KEY_AUTOMATION_TRACE, encodeAutomationTrace(trace).toString())
            .apply()
    }

    private fun persistPendingImports(pendingImports: List<PendingLedgerImport>) {
        preferences.edit()
            .putString(KEY_PENDING_IMPORTS, encodePendingImports(pendingImports).toString())
            .apply()
    }

    private fun persistSecurityConfig(config: LedgerSecurityConfig) {
        preferences.edit()
            .putString(KEY_SECURITY_CONFIG, encodeSecurityConfig(config).toString())
            .apply()
    }

    private fun persistAutoImportHistory(signatures: List<String>) {
        preferences.edit()
            .putString(
                KEY_AUTO_IMPORT_HISTORY,
                JSONArray().apply {
                    signatures.forEach(::put)
                }.toString()
            )
            .apply()
    }

    private fun encodeEntries(entries: List<LedgerEntry>): JSONArray {
        return JSONArray().apply {
            entries.forEach { entry ->
                put(
                    JSONObject().apply {
                        put("id", entry.id)
                        put("type", entry.type.name)
                        put("amountInCents", entry.amountInCents)
                        put("account", entry.account)
                        put("category", entry.category)
                        put("note", entry.note)
                        put("receiptText", entry.receiptText)
                        put("happenedAt", entry.happenedAt)
                        put("updatedAt", entry.updatedAt)
                    }
                )
            }
        }
    }

    private fun encodeTemplates(templates: List<LedgerTemplate>): JSONArray {
        return JSONArray().apply {
            templates.forEach { template ->
                put(
                    JSONObject().apply {
                        put("id", template.id)
                        put("title", template.title)
                        put("type", template.type.name)
                        put("amountInCents", template.amountInCents)
                        put("account", template.account)
                        put("category", template.category)
                        put("recurrence", template.recurrence.name)
                        template.nextDueAt?.let { nextDueAt ->
                            put("nextDueAt", nextDueAt)
                        }
                        put("note", template.note)
                        put("createdAt", template.createdAt)
                    }
                )
            }
        }
    }

    private fun encodeBudgetConfig(config: LedgerBudgetConfig): JSONObject {
        return JSONObject().apply {
            config.monthlyBudgetInCents?.let { amount ->
                put("monthlyBudgetInCents", amount)
            }
            put(
                "categoryBudgets",
                JSONObject().apply {
                    config.categoryBudgets.forEach { (category, amount) ->
                        put(category, amount)
                    }
                }
            )
        }
    }

    private fun encodeProfileConfig(config: LedgerProfileConfig): JSONObject {
        return JSONObject().apply {
            put("customAccounts", encodeStringList(config.customAccounts))
            put("customExpenseCategories", encodeStringList(config.customExpenseCategories))
            put("customIncomeCategories", encodeStringList(config.customIncomeCategories))
        }
    }

    private fun encodeAutomationTrace(trace: LedgerAutomationTrace): JSONObject {
        return JSONObject().apply {
            put("sourceLabel", trace.sourceLabel)
            put("summary", trace.summary)
            put("rawText", trace.rawText)
            put("happenedAt", trace.happenedAt)
        }
    }

    private fun encodePendingImports(pendingImports: List<PendingLedgerImport>): JSONArray {
        return JSONArray().apply {
            pendingImports.forEach { pendingImport ->
                put(
                    JSONObject().apply {
                        put("id", pendingImport.id)
                        put("signature", pendingImport.signature)
                        put("type", pendingImport.type.name)
                        put("amountInCents", pendingImport.amountInCents)
                        put("account", pendingImport.account)
                        put("category", pendingImport.category)
                        put("note", pendingImport.note)
                        put("receiptText", pendingImport.receiptText)
                        put("happenedAt", pendingImport.happenedAt)
                        put("sourceLabel", pendingImport.sourceLabel)
                        put("createdAt", pendingImport.createdAt)
                    }
                )
            }
        }
    }

    private fun encodeSecurityConfig(config: LedgerSecurityConfig): JSONObject {
        return JSONObject().apply {
            put("pinHash", config.pinHash)
            put("pinSalt", config.pinSalt)
        }
    }

    private fun encodeStringList(values: List<String>): JSONArray {
        return JSONArray().apply {
            values
                .filter { value -> value.isNotBlank() }
                .distinct()
                .forEach(::put)
        }
    }

    private fun decodeEntries(jsonArray: JSONArray?): List<LedgerEntry> {
        if (jsonArray == null) {
            return emptyList()
        }

        return buildList {
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(index)
                add(
                    LedgerEntry(
                        id = item.optString("id"),
                        type = LedgerEntryType.valueOf(item.getString("type")),
                        amountInCents = item.getLong("amountInCents"),
                        account = item.optString("account").ifBlank { defaultLedgerAccount() },
                        category = item.getString("category"),
                        note = item.optString("note"),
                        receiptText = item.optString("receiptText"),
                        happenedAt = item.optLong("happenedAt", System.currentTimeMillis()),
                        updatedAt = item.optLong("updatedAt", item.optLong("happenedAt", System.currentTimeMillis()))
                    )
                )
            }
        }.let(::normalizeEntries)
    }

    private fun decodeTemplates(jsonArray: JSONArray?): List<LedgerTemplate> {
        if (jsonArray == null) {
            return emptyList()
        }

        return buildList {
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(index)
                add(
                    LedgerTemplate(
                        id = item.optString("id"),
                        title = item.optString("title"),
                        type = LedgerEntryType.valueOf(item.getString("type")),
                        amountInCents = item.getLong("amountInCents"),
                        account = item.optString("account").ifBlank { defaultLedgerAccount() },
                        category = item.getString("category"),
                        recurrence = item.optString("recurrence")
                            .takeIf { value -> value.isNotBlank() }
                            ?.let(LedgerTemplateRecurrence::valueOf)
                            ?: LedgerTemplateRecurrence.NONE,
                        nextDueAt = item.optLong("nextDueAt").takeIf {
                            item.has("nextDueAt")
                        },
                        note = item.optString("note"),
                        createdAt = item.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }.let(::normalizeTemplates)
    }

    private fun decodeBudgetConfig(jsonObject: JSONObject?): LedgerBudgetConfig {
        if (jsonObject == null) {
            return LedgerBudgetConfig()
        }

        val categoryBudgetsObject = jsonObject.optJSONObject("categoryBudgets") ?: JSONObject()
        val categoryBudgets = buildMap {
            val keys = categoryBudgetsObject.keys()
            while (keys.hasNext()) {
                val category = keys.next()
                put(category, categoryBudgetsObject.getLong(category))
            }
        }.toSortedMap()

        return LedgerBudgetConfig(
            monthlyBudgetInCents = jsonObject.optLong("monthlyBudgetInCents").takeIf {
                jsonObject.has("monthlyBudgetInCents")
            },
            categoryBudgets = categoryBudgets
        )
    }

    private fun decodeProfileConfig(jsonObject: JSONObject?): LedgerProfileConfig {
        if (jsonObject == null) {
            return LedgerProfileConfig()
        }

        return LedgerProfileConfig(
            customAccounts = decodeStringList(jsonObject.optJSONArray("customAccounts")),
            customExpenseCategories = decodeStringList(jsonObject.optJSONArray("customExpenseCategories")),
            customIncomeCategories = decodeStringList(jsonObject.optJSONArray("customIncomeCategories"))
        )
    }

    private fun decodeAutomationTrace(jsonObject: JSONObject?): LedgerAutomationTrace {
        if (jsonObject == null) {
            return LedgerAutomationTrace()
        }

        return LedgerAutomationTrace(
            sourceLabel = jsonObject.optString("sourceLabel"),
            summary = jsonObject.optString("summary"),
            rawText = jsonObject.optString("rawText"),
            happenedAt = jsonObject.optLong("happenedAt", 0L)
        )
    }

    private fun decodePendingImports(jsonArray: JSONArray?): List<PendingLedgerImport> {
        if (jsonArray == null) {
            return emptyList()
        }

        return buildList {
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(index)
                add(
                    PendingLedgerImport(
                        id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                        signature = item.optString("signature"),
                        type = LedgerEntryType.valueOf(item.getString("type")),
                        amountInCents = item.getLong("amountInCents"),
                        account = item.optString("account").ifBlank { defaultLedgerAccount() },
                        category = item.getString("category"),
                        note = item.optString("note"),
                        receiptText = item.optString("receiptText"),
                        happenedAt = item.optLong("happenedAt", System.currentTimeMillis()),
                        sourceLabel = item.optString("sourceLabel"),
                        createdAt = item.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }.let(::normalizePendingImports)
    }

    private fun decodeSecurityConfig(jsonObject: JSONObject?): LedgerSecurityConfig {
        if (jsonObject == null) {
            return LedgerSecurityConfig()
        }

        return LedgerSecurityConfig(
            pinHash = jsonObject.optString("pinHash"),
            pinSalt = jsonObject.optString("pinSalt")
        )
    }

    private fun decodeStringList(jsonArray: JSONArray?): List<String> {
        if (jsonArray == null) {
            return emptyList()
        }

        return buildList {
            for (index in 0 until jsonArray.length()) {
                val value = jsonArray.optString(index).trim()
                if (value.isNotBlank()) {
                    add(value)
                }
            }
        }.distinct().sorted()
    }

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { char -> char == ',' || char == '"' || char == '\n' || char == '\r' }) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    companion object {
        private const val PREFS_NAME = "ledger_store"
        private const val KEY_ENTRIES = "entries"
        private const val KEY_TEMPLATES = "templates"
        private const val KEY_BUDGET_CONFIG = "budget_config"
        private const val KEY_PROFILE_CONFIG = "profile_config"
        private const val KEY_AUTOMATION_TRACE = "automation_trace"
        private const val KEY_PENDING_IMPORTS = "pending_imports"
        private const val KEY_SECURITY_CONFIG = "security_config"
        private const val KEY_AUTO_IMPORT_HISTORY = "auto_import_history"
        private const val MAX_AUTO_IMPORT_HISTORY = 80
        private const val AUTO_IMPORT_WINDOW_MILLIS = 2 * 60 * 1000L

        @Volatile
        private var instance: LedgerStore? = null

        fun getInstance(context: Context): LedgerStore {
            return instance ?: synchronized(this) {
                instance ?: LedgerStore(
                    preferences = context.applicationContext.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                    )
                ).also { createdStore ->
                    instance = createdStore
                }
            }
        }
    }
}
