package com.android.jizhangmiao.ledger

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.android.jizhangmiao.R
import com.android.jizhangmiao.ledger.data.LedgerEntryType

class QuickEntryWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, buildRemoteViews(context))
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        updateAll(context)
    }

    companion object {
        fun updateAll(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, QuickEntryWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            if (ids.isNotEmpty()) {
                ids.forEach { appWidgetId ->
                    appWidgetManager.updateAppWidget(appWidgetId, buildRemoteViews(context))
                }
            }
        }

        private fun buildRemoteViews(context: Context): RemoteViews {
            return RemoteViews(context.packageName, R.layout.widget_quick_entry).apply {
                setOnClickPendingIntent(
                    R.id.widgetOpenApp,
                    PendingIntent.getActivity(
                        context,
                        201,
                        buildQuickEntryIntent(context),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                setOnClickPendingIntent(
                    R.id.widgetExpense,
                    PendingIntent.getActivity(
                        context,
                        202,
                        buildQuickEntryIntent(context, entryType = LedgerEntryType.EXPENSE),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                setOnClickPendingIntent(
                    R.id.widgetIncome,
                    PendingIntent.getActivity(
                        context,
                        203,
                        buildQuickEntryIntent(context, entryType = LedgerEntryType.INCOME),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                setOnClickPendingIntent(
                    R.id.widgetVoice,
                    PendingIntent.getActivity(
                        context,
                        204,
                        buildQuickEntryIntent(context, requestVoice = true),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
        }
    }
}
