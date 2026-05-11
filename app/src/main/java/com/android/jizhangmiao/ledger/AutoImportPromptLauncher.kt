package com.android.jizhangmiao.ledger

import android.content.Context
import android.content.Intent

internal fun showAutoImportConfirmation(context: Context) {
    val launchIntent = context.packageManager
        .getLaunchIntentForPackage(context.packageName)
        ?: return

    launchIntent
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

    runCatching {
        context.startActivity(launchIntent)
    }
}
