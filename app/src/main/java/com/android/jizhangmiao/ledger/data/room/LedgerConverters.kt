package com.android.jizhangmiao.ledger.data.room

import androidx.room.TypeConverter
import com.android.jizhangmiao.ledger.data.LedgerEntryType
import com.android.jizhangmiao.ledger.data.LedgerTemplatePlanType
import com.android.jizhangmiao.ledger.data.LedgerTemplateRecurrence

internal class LedgerConverters {
    @TypeConverter
    fun fromEntryType(value: LedgerEntryType): String = value.name

    @TypeConverter
    fun toEntryType(value: String): LedgerEntryType {
        return runCatching { LedgerEntryType.valueOf(value) }
            .getOrDefault(LedgerEntryType.EXPENSE)
    }

    @TypeConverter
    fun fromTemplateRecurrence(value: LedgerTemplateRecurrence): String = value.name

    @TypeConverter
    fun toTemplateRecurrence(value: String): LedgerTemplateRecurrence {
        return runCatching { LedgerTemplateRecurrence.valueOf(value) }
            .getOrDefault(LedgerTemplateRecurrence.NONE)
    }

    @TypeConverter
    fun fromTemplatePlanType(value: LedgerTemplatePlanType): String = value.name

    @TypeConverter
    fun toTemplatePlanType(value: String): LedgerTemplatePlanType {
        return runCatching { LedgerTemplatePlanType.valueOf(value) }
            .getOrDefault(LedgerTemplatePlanType.STANDARD)
    }
}
