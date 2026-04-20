package com.android.jizhangmiao.ledger

import android.accessibilityservice.AccessibilityService
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.android.jizhangmiao.ledger.data.LedgerStore
import java.util.ArrayDeque
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PaymentAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastImportedSignature: String? = null
    private var lastImportedAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        importScreenIfMatched(
            packageName = rootInActiveWindow?.packageName?.toString().orEmpty(),
            sourceLabel = "\u9875\u9762\u8bc6\u522b",
            dedupeSeed = "connected:${System.currentTimeMillis() / DEDUPE_BUCKET_MILLIS}"
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType !in SupportedEventTypes) {
            return
        }

        val packageName = event.packageName?.toString().orEmpty()
        if (packageName != WeChatPackageName && packageName != AlipayPackageName) {
            return
        }

        importScreenIfMatched(
            packageName = packageName,
            sourceLabel = "\u9875\u9762\u8bc6\u522b",
            dedupeSeed = buildString {
                append(event.className?.toString().orEmpty())
                append(':')
                append(System.currentTimeMillis() / DEDUPE_BUCKET_MILLIS)
            },
            event = event
        )
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun importScreenIfMatched(
        packageName: String,
        sourceLabel: String,
        dedupeSeed: String,
        event: AccessibilityEvent? = null
    ) {
        if (packageName != WeChatPackageName && packageName != AlipayPackageName) {
            return
        }

        val happenedAt = System.currentTimeMillis()
        val mergedText = collectActiveWindowText(event)
        if (mergedText == null) {
            serviceScope.launch {
                LedgerStore.getInstance(applicationContext).recordAutomationTrace(
                    com.android.jizhangmiao.ledger.data.LedgerAutomationTrace(
                        sourceLabel = sourceLabel,
                        summary = "\u5df2\u6536\u5230\u9875\u9762\u4e8b\u4ef6\uff0c\u4f46\u5f53\u524d\u6ca1\u8bfb\u5230\u53ef\u7528\u6587\u5b57",
                        rawText = "",
                        happenedAt = happenedAt
                    )
                )
            }
            return
        }
        val analysis = analyzeAutoImportedEntry(
            packageName = packageName,
            mergedText = mergedText,
            dedupeSeed = dedupeSeed,
            happenedAt = happenedAt,
            sourceLabel = sourceLabel
        )
        val candidate = analysis.candidate ?: run {
            serviceScope.launch {
                LedgerStore.getInstance(applicationContext).recordAutomationTrace(
                    com.android.jizhangmiao.ledger.data.LedgerAutomationTrace(
                        sourceLabel = sourceLabel,
                        summary = analysis.statusSummary,
                        rawText = analysis.mergedText,
                        happenedAt = happenedAt
                    )
                )
            }
            return
        }

        if (
            candidate.signature == lastImportedSignature &&
            happenedAt - lastImportedAt < DEDUPE_BUCKET_MILLIS
        ) {
            return
        }

        lastImportedSignature = candidate.signature
        lastImportedAt = happenedAt
        serviceScope.launch {
            val store = LedgerStore.getInstance(applicationContext)
            val imported = store.importAutoEntry(candidate)
            store.recordAutomationTrace(
                com.android.jizhangmiao.ledger.data.LedgerAutomationTrace(
                    sourceLabel = sourceLabel,
                    summary = if (imported) {
                        "${analysis.statusSummary} / \u5df2\u5165\u8d26"
                    } else {
                        "${analysis.statusSummary} / \u91cd\u590d\u6216\u65e0\u9700\u5165\u8d26"
                    },
                    rawText = analysis.mergedText,
                    happenedAt = happenedAt
                )
            )
        }
    }

    private fun collectActiveWindowText(event: AccessibilityEvent?): String? {
        val rawTexts = mutableListOf<String>()
        event?.text
            ?.map(CharSequence::toString)
            ?.forEach(rawTexts::add)
        event?.contentDescription
            ?.toString()
            ?.let(rawTexts::add)
        traverseNodeTree(event?.source, rawTexts)
        traverseNodeTree(rootInActiveWindow, rawTexts)

        return normalizeCollectedText(rawTexts).takeIf { merged ->
            merged.isNotBlank() && !TextUtils.isDigitsOnly(merged)
        }
    }

    private fun traverseNodeTree(
        rootNode: AccessibilityNodeInfo?,
        rawTexts: MutableList<String>
    ) {
        if (rootNode == null) {
            return
        }

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(rootNode)
        var visitedCount = 0

        while (queue.isNotEmpty() && visitedCount < MAX_NODE_COUNT) {
            val node = queue.removeFirst()
            node.text?.toString()?.let(rawTexts::add)
            node.contentDescription?.toString()?.let(rawTexts::add)

            repeat(node.childCount) { index ->
                node.getChild(index)?.let(queue::addLast)
            }
            visitedCount += 1
        }
    }

    private companion object {
        val SupportedEventTypes = setOf(
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED
        )

        const val MAX_NODE_COUNT = 240
        const val DEDUPE_BUCKET_MILLIS = 15_000L
    }
}
