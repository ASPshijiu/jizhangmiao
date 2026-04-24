package com.android.jizhangmiao.ledger

import com.android.jizhangmiao.ledger.data.LedgerEntryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerStatementImporterTest {
    @Test
    fun parseStatementCsv_supportsGenericChineseHeaders() {
        val result = parseStatementCsv(
            """
            交易时间,收支类型,金额,支付方式,交易对方,分类
            2026-04-24 21:30:00,支出,18.80,微信,便利店,日用
            2026-04-24 22:10:00,收入,88.00,支付宝,退款,退款
            """.trimIndent(),
            now = 1_710_000_000_000L
        )

        assertNotNull(result)
        assertEquals("微信账单", result?.sourceLabel)
        assertEquals(2, result?.entries?.size)
        assertEquals(LedgerEntryType.EXPENSE, result?.entries?.get(0)?.type)
        assertEquals(1_880L, result?.entries?.get(0)?.amountInCents)
        assertEquals("微信", result?.entries?.get(0)?.account)
        assertEquals("日用", result?.entries?.get(0)?.category)
        assertEquals(LedgerEntryType.INCOME, result?.entries?.get(1)?.type)
        assertEquals(8_800L, result?.entries?.get(1)?.amountInCents)
        assertEquals("支付宝", result?.entries?.get(1)?.account)
    }

    @Test
    fun parseStatementCsv_supportsJizhangmiaoExportCsv() {
        val result = parseStatementCsv(
            """
            id,type,amount,account,category,note,happenedAt,updatedAt
            abc123,EXPENSE,12.34,银行卡,交通,地铁,1710000000000,1710000000000
            """.trimIndent(),
            now = 1_720_000_000_000L
        )

        assertNotNull(result)
        assertEquals("记账喵导出", result?.sourceLabel)
        assertEquals(1, result?.entries?.size)
        assertEquals(LedgerEntryType.EXPENSE, result?.entries?.first()?.type)
        assertEquals(1_234L, result?.entries?.first()?.amountInCents)
        assertEquals("银行卡", result?.entries?.first()?.account)
        assertEquals("交通", result?.entries?.first()?.category)
        assertEquals("地铁", result?.entries?.first()?.note)
    }

    @Test
    fun parseStatementCsv_skipsFooterAndDuplicateRows() {
        val result = parseStatementCsv(
            """
            时间,类型,金额,账户,备注
            2026/04/24 08:00,支出,20.00,微信,早餐
            2026/04/24 08:00,支出,20.00,微信,早餐
            合计, ,20.00, ,
            """.trimIndent()
        )

        assertNotNull(result)
        assertEquals(1, result?.entries?.size)
        assertTrue((result?.skippedRowCount ?: 0) >= 2)
    }
}
