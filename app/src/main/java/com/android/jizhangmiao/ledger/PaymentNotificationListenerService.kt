package com.android.jizhangmiao.ledger

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.android.jizhangmiao.ledger.data.LedgerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PaymentNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        activeNotifications
            ?.forEach(::importNotificationIfMatched)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        importNotificationIfMatched(sbn)
    }

    private fun importNotificationIfMatched(sbn: StatusBarNotification) {
        serviceScope.launch {
            val store = LedgerStore.getInstance(applicationContext)
            val notification = sbn.notification ?: return@launch
            val title = notification.extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)
                ?.toString()
                ?.trim()
                .orEmpty()
            val analysis = analyzeAutoImportedEntry(
                packageName = sbn.packageName,
                mergedText = notification.extras?.let { collectNotificationText(notification) }.orEmpty(),
                dedupeSeed = sbn.key,
                happenedAt = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis(),
                sourceLabel = "\u901a\u77e5",
                title = title
            )
            val candidate = analysis.candidate
            val summary = if (candidate == null) {
                analysis.statusSummary
            } else {
                val imported = store.importAutoEntry(candidate)
                if (imported) {
                    showAutoImportConfirmation(applicationContext)
                    "${analysis.statusSummary} / \u5df2\u8fdb\u5165\u5ba1\u6838\u7bb1"
                } else {
                    "${analysis.statusSummary} / \u91cd\u590d\u6216\u65e0\u9700\u5ba1\u6838"
                }
            }
            store.recordAutomationTrace(
                com.android.jizhangmiao.ledger.data.LedgerAutomationTrace(
                    sourceLabel = "\u901a\u77e5",
                    summary = summary,
                    rawText = analysis.mergedText,
                    happenedAt = System.currentTimeMillis()
                )
            )
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
