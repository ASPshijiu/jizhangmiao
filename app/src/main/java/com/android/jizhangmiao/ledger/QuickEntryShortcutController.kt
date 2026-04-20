package com.android.jizhangmiao.ledger

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.android.jizhangmiao.ledger.data.LedgerEntryType

internal object QuickEntryShortcutController {
    private const val ChannelId = "quick_entry_shortcut"
    private const val ChannelName = "\u5feb\u901f\u8bb0\u8d26"
    private const val NotificationId = 41021

    fun refresh(
        context: Context,
        enabled: Boolean
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!enabled || !manager.areNotificationsEnabled()) {
            manager.cancel(NotificationId)
            return
        }

        createChannel(context)

        val contentIntent = PendingIntent.getActivity(
            context,
            101,
            buildQuickEntryIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val expenseIntent = PendingIntent.getActivity(
            context,
            102,
            buildQuickEntryIntent(context, entryType = LedgerEntryType.EXPENSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val voiceIntent = PendingIntent.getActivity(
            context,
            103,
            buildQuickEntryIntent(context, requestVoice = true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(android.R.drawable.ic_input_add)
            .setContentTitle("\u8bb0\u8d26\u55b5")
            .setContentText("\u4e0b\u62c9\u901a\u77e5\u680f\uff0c\u968f\u65f6\u6253\u5f00\u5feb\u901f\u8bb0\u8d26")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "\u4e0b\u62c9\u901a\u77e5\u680f\uff0c\u53ef\u76f4\u63a5\u6253\u5f00\u5feb\u8bb0\u6216\u8bed\u97f3\u8bb0\u8d26"
                )
            )
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .addAction(0, "\u5feb\u8bb0", contentIntent)
            .addAction(0, "\u652f\u51fa", expenseIntent)
            .addAction(0, "\u8bed\u97f3", voiceIntent)
            .build()

        manager.notify(NotificationId, notification)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val existingChannel = manager.getNotificationChannel(ChannelId)
        if (existingChannel != null) {
            return
        }

        manager.createNotificationChannel(
            NotificationChannel(
                ChannelId,
                ChannelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "\u7528\u4e8e\u901a\u77e5\u680f\u5feb\u901f\u6253\u5f00\u8bb0\u8d26"
                setShowBadge(false)
            }
        )
    }
}
