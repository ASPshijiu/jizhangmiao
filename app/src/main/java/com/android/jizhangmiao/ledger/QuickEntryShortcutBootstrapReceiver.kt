package com.android.jizhangmiao.ledger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.android.jizhangmiao.ledger.data.LedgerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class QuickEntryShortcutBootstrapReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val pendingResult = goAsync()
        val applicationContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = LedgerStore.getInstance(applicationContext).settings.value
                QuickEntryShortcutController.refresh(
                    context = applicationContext,
                    enabled = settings.quickEntryNotificationEnabled
                )
                QuickEntryWidgetProvider.updateAll(applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
