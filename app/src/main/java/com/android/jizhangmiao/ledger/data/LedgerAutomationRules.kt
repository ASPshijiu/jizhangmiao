package com.android.jizhangmiao.ledger.data

import java.util.UUID

data class LedgerAutomationRule(
    val id: String = UUID.randomUUID().toString(),
    val keyword: String,
    val type: LedgerEntryType,
    val category: String,
    val account: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class LedgerAutomationRuleMatch(
    val rule: LedgerAutomationRule,
    val category: String,
    val account: String
)

fun applyAutomationRule(
    rules: List<LedgerAutomationRule>,
    type: LedgerEntryType,
    currentCategory: String,
    currentAccount: String,
    matchText: String
): LedgerAutomationRuleMatch? {
    val normalizedText = matchText.lowercase()
    if (normalizedText.isBlank()) {
        return null
    }

    val matchedRule = rules
        .asSequence()
        .filter { rule ->
            rule.type == type && rule.keyword.trim().isNotBlank()
        }
        .sortedWith(
            compareByDescending<LedgerAutomationRule> { rule -> rule.keyword.trim().length }
                .thenByDescending { rule -> rule.createdAt }
        )
        .firstOrNull { rule ->
            normalizedText.contains(rule.keyword.trim().lowercase())
        }
        ?: return null

    return LedgerAutomationRuleMatch(
        rule = matchedRule,
        category = matchedRule.category.trim().ifBlank { currentCategory },
        account = matchedRule.account.trim().ifBlank { currentAccount }
    )
}
