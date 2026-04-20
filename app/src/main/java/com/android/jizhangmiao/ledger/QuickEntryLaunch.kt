package com.android.jizhangmiao.ledger

import android.content.Context
import android.content.Intent
import com.android.jizhangmiao.MainActivity
import com.android.jizhangmiao.ledger.data.LedgerEntryType

private const val ExtraTargetBoard = "ledger_target_board"
private const val ExtraEntryType = "ledger_entry_type"
private const val ExtraRequestVoice = "ledger_request_voice"

data class LedgerLaunchRequest(
    val board: LedgerBoard = LedgerBoard.QUICK_ENTRY,
    val entryType: LedgerEntryType? = null,
    val requestVoice: Boolean = false,
    val nonce: Long = System.currentTimeMillis()
)

internal fun buildQuickEntryIntent(
    context: Context,
    entryType: LedgerEntryType? = null,
    requestVoice: Boolean = false,
    board: LedgerBoard = LedgerBoard.QUICK_ENTRY
): Intent {
    return Intent(context, MainActivity::class.java).apply {
        putExtra(ExtraTargetBoard, board.name)
        putExtra(ExtraRequestVoice, requestVoice)
        entryType?.let { putExtra(ExtraEntryType, it.name) }
        addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
    }
}

internal fun parseLaunchRequest(intent: Intent?): LedgerLaunchRequest? {
    val extras = intent?.extras ?: return null
    if (!extras.containsKey(ExtraTargetBoard) && !extras.containsKey(ExtraRequestVoice)) {
        return null
    }

    val board = extras.getString(ExtraTargetBoard)
        ?.let { boardName -> runCatching { LedgerBoard.valueOf(boardName) }.getOrNull() }
        ?: LedgerBoard.QUICK_ENTRY
    val entryType = extras.getString(ExtraEntryType)
        ?.let { typeName -> runCatching { LedgerEntryType.valueOf(typeName) }.getOrNull() }

    return LedgerLaunchRequest(
        board = board,
        entryType = entryType,
        requestVoice = extras.getBoolean(ExtraRequestVoice, false)
    )
}
