package com.android.jizhangmiao.ledger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LedgerAutomationRulesTest {
    @Test
    fun applyAutomationRule_prefersLongerKeyword() {
        val match = applyAutomationRule(
            rules = listOf(
                LedgerAutomationRule(
                    keyword = "star",
                    type = LedgerEntryType.EXPENSE,
                    category = "other",
                    createdAt = 1L
                ),
                LedgerAutomationRule(
                    keyword = "starbucks",
                    type = LedgerEntryType.EXPENSE,
                    category = "coffee",
                    createdAt = 0L
                )
            ),
            type = LedgerEntryType.EXPENSE,
            currentCategory = "misc",
            currentAccount = "wallet",
            matchText = "Starbucks reserve order"
        )

        assertNotNull(match)
        assertEquals("coffee", match?.category)
    }

    @Test
    fun applyAutomationRule_keepsCurrentAccount_whenRuleAccountIsBlank() {
        val match = applyAutomationRule(
            rules = listOf(
                LedgerAutomationRule(
                    keyword = "salary",
                    type = LedgerEntryType.INCOME,
                    category = "salary",
                    account = ""
                )
            ),
            type = LedgerEntryType.INCOME,
            currentCategory = "other",
            currentAccount = "bank",
            matchText = "April salary payout"
        )

        assertNotNull(match)
        assertEquals("salary", match?.category)
        assertEquals("bank", match?.account)
    }
}
