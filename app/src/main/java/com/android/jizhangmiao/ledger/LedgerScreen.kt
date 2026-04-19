@file:OptIn(ExperimentalMaterial3Api::class)

package com.android.jizhangmiao.ledger

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.android.jizhangmiao.ledger.data.LedgerEntry
import com.android.jizhangmiao.ledger.data.LedgerEntryType
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val IncomeTint = Color(0xFF2E8B57)
private val ExpenseTint = Color(0xFFC76B4B)
private val HeroShape = RoundedCornerShape(32.dp)
private val SectionShape = RoundedCornerShape(28.dp)
private val EntryShape = RoundedCornerShape(24.dp)
private val EntryTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M\u6708d\u65e5 HH:mm", Locale.CHINA)

@Composable
fun LedgerScreen(
    uiState: LedgerUiState,
    onTypeSelected: (LedgerEntryType) -> Unit,
    onAmountChanged: (String) -> Unit,
    onCategoryChanged: (String) -> Unit,
    onNoteChanged: (String) -> Unit,
    onSuggestedCategorySelected: (String) -> Unit,
    onSaveClick: () -> Unit,
    onDeleteClick: (LedgerEntry) -> Unit
) {
    val insight = buildInsight(uiState.entries)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFF7EE),
                        Color(0xFFF4F7F1),
                        Color(0xFFFFFCF8)
                    )
                )
            )
    ) {
        DecorativeBackdrop()

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "\u8bb0\u8d26\u55b5",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "\u628a\u6bcf\u4e00\u7b14\u94b1\u82b1\u5f97\u660e\u767d",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    end = 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 28.dp
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    LedgerHeroCard(
                        summary = uiState.summary,
                        recordCount = uiState.entries.size,
                        insight = insight
                    )
                }
                item {
                    EntryEditorSection(
                        form = uiState.form,
                        onTypeSelected = onTypeSelected,
                        onAmountChanged = onAmountChanged,
                        onCategoryChanged = onCategoryChanged,
                        onNoteChanged = onNoteChanged,
                        onSuggestedCategorySelected = onSuggestedCategorySelected,
                        onSaveClick = onSaveClick
                    )
                }
                item {
                    SectionHeading(
                        title = "\u6700\u8fd1\u8bb0\u5f55",
                        subtitle = "\u5171 ${uiState.entries.size} \u7b14\uff0c\u957f\u6309\u524d\u65e0\u9700\u7f16\u8f91\uff0c\u76f4\u63a5\u5220\u9664\u5373\u53ef"
                    )
                }

                if (uiState.entries.isEmpty()) {
                    item {
                        EmptyLedgerSection()
                    }
                } else {
                    items(
                        items = uiState.entries,
                        key = { entry -> entry.id }
                    ) { entry ->
                        LedgerEntryCard(
                            entry = entry,
                            onDeleteClick = { onDeleteClick(entry) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DecorativeBackdrop() {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset(x = 240.dp, y = (-48).dp)
                .size(220.dp)
                .clip(CircleShape)
                .background(Color(0x332E8B57))
        )
        Box(
            modifier = Modifier
                .offset(x = (-56).dp, y = 320.dp)
                .size(180.dp)
                .clip(CircleShape)
                .background(Color(0x22C76B4B))
        )
    }
}

@Composable
private fun LedgerHeroCard(
    summary: LedgerSummary,
    recordCount: Int,
    insight: LedgerInsight
) {
    val expenseRatio by animateFloatAsState(
        targetValue = spendingRatio(summary),
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 250f),
        label = "expenseRatio"
    )

    Surface(
        color = Color.Transparent,
        shadowElevation = 18.dp,
        shape = HeroShape
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(HeroShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1F4037),
                            Color(0xFF2C5A4B),
                            Color(0xFF875B43)
                        )
                    )
                )
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Surface(
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = "\u8d26\u672c\u603b\u89c8",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "\u5f53\u524d\u7ed3\u4f59",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.82f)
                    )
                    Text(
                        text = formatCurrency(summary.balanceInCents),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = Color.White
                    )
                    Text(
                        text = insight.headline,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HeroMetric(
                        modifier = Modifier.weight(1f),
                        title = "\u6536\u5165",
                        value = formatCurrency(summary.incomeInCents)
                    )
                    HeroMetric(
                        modifier = Modifier.weight(1f),
                        title = "\u652f\u51fa",
                        value = formatCurrency(summary.expenseInCents)
                    )
                    HeroMetric(
                        modifier = Modifier.weight(1f),
                        title = "\u8bb0\u5f55",
                        value = "$recordCount"
                    )
                }

                ElevatedCard(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = Color.White.copy(alpha = 0.14f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "\u652f\u51fa\u5728\u672c\u671f\u6d41\u6c34\u4e2d\u5360 ${(expenseRatio * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White.copy(alpha = 0.18f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(expenseRatio.coerceIn(0f, 1f))
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xFFFFD6C0),
                                                Color(0xFFF8B38B)
                                            )
                                        )
                                    )
                            )
                        }
                        Text(
                            text = insight.detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.86f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroMetric(
    modifier: Modifier = Modifier,
    title: String,
    value: String
) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.12f),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.72f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = Color.White
            )
        }
    }
}

@Composable
private fun EntryEditorSection(
    form: LedgerFormState,
    onTypeSelected: (LedgerEntryType) -> Unit,
    onAmountChanged: (String) -> Unit,
    onCategoryChanged: (String) -> Unit,
    onNoteChanged: (String) -> Unit,
    onSuggestedCategorySelected: (String) -> Unit,
    onSaveClick: () -> Unit
) {
    val accentColor = if (form.type == LedgerEntryType.INCOME) IncomeTint else ExpenseTint

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SectionShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "\u5feb\u901f\u8bb0\u4e00\u7b14",
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        text = "\u5f53\u5929\u7684\u652f\u51fa\u548c\u6536\u5165\uff0c\u5c31\u5728\u8fd9\u91cc\u987a\u624b\u8bb0\u4e0b",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = accentColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = form.type.displayName(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = accentColor
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EntryTypeButton(
                    modifier = Modifier.weight(1f),
                    type = LedgerEntryType.EXPENSE,
                    selected = form.type == LedgerEntryType.EXPENSE,
                    onClick = { onTypeSelected(LedgerEntryType.EXPENSE) }
                )
                EntryTypeButton(
                    modifier = Modifier.weight(1f),
                    type = LedgerEntryType.INCOME,
                    selected = form.type == LedgerEntryType.INCOME,
                    onClick = { onTypeSelected(LedgerEntryType.INCOME) }
                )
            }

            OutlinedTextField(
                value = form.amount,
                onValueChange = onAmountChanged,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("\u91d1\u989d")
                },
                prefix = {
                    Text(
                        text = "\u00a5",
                        color = accentColor
                    )
                },
                textStyle = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = FontFamily.Monospace
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(20.dp)
            )

            OutlinedTextField(
                value = form.category,
                onValueChange = onCategoryChanged,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("\u5206\u7c7b")
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionEyebrow("\u5feb\u6377\u5206\u7c7b")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categorySuggestionsFor(form.type)) { suggestion ->
                        FilterChip(
                            selected = suggestion == form.category,
                            onClick = {
                                onSuggestedCategorySelected(suggestion)
                            },
                            label = {
                                Text(suggestion)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor.copy(alpha = 0.15f),
                                selectedLabelColor = accentColor
                            )
                        )
                    }
                }
            }

            OutlinedTextField(
                value = form.note,
                onValueChange = onNoteChanged,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("\u5907\u6ce8")
                },
                minLines = 2,
                maxLines = 3,
                shape = RoundedCornerShape(20.dp)
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "\u5c0f\u5efa\u8bae\uff1a\u6bcf\u7b14\u6d88\u8d39\u53ea\u5199\u5173\u952e\u4fe1\u606f\uff0c\u4f60\u4e4b\u540e\u56de\u770b\u8d26\u5355\u4f1a\u66f4\u8f7b\u677e\u3002",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = form.errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = form.errorMessage.orEmpty(),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Button(
                onClick = onSaveClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor
                ),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Text(
                    text = "\u4fdd\u5b58\u8fd9\u7b14\u8bb0\u5f55",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun EntryTypeButton(
    modifier: Modifier = Modifier,
    type: LedgerEntryType,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accentColor = if (type == LedgerEntryType.INCOME) IncomeTint else ExpenseTint

    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor
            )
        ) {
            Text(type.displayName())
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(type.displayName())
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionEyebrow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun EmptyLedgerSection() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = EntryShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = CircleShape
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "\u00a5",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = "\u8fd8\u6ca1\u6709\u8d26\u5355\u8bb0\u5f55",
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                text = "\u5148\u8bb0\u4e0b\u4eca\u5929\u7684\u4e00\u7b14\u6536\u652f\uff0c\u4e0b\u9762\u7684\u8d26\u5355\u5217\u8868\u4f1a\u7acb\u523b\u6d3b\u8d77\u6765\u3002",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LedgerEntryCard(
    entry: LedgerEntry,
    onDeleteClick: () -> Unit
) {
    val accentColor = if (entry.type == LedgerEntryType.INCOME) IncomeTint else ExpenseTint

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = EntryShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(84.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accentColor.copy(alpha = 0.88f))
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = entry.category,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Surface(
                            color = accentColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = entry.type.displayName(),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = accentColor
                            )
                        }
                    }
                    Surface(
                        color = accentColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            text = formatSignedCurrency(entry),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = accentColor
                        )
                    }
                }

                if (entry.note.isNotBlank()) {
                    Text(
                        text = entry.note,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatEntryTime(entry.happenedAt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(999.dp)
                            )
                    ) {
                        Text(
                            text = "\u5220\u9664",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private data class LedgerInsight(
    val headline: String,
    val detail: String
)

private fun buildInsight(entries: List<LedgerEntry>): LedgerInsight {
    if (entries.isEmpty()) {
        return LedgerInsight(
            headline = "\u4ece\u7b2c\u4e00\u7b14\u8d26\u5f00\u59cb\uff0c\u628a\u65e5\u5e38\u82b1\u9500\u770b\u6e05\u695a",
            detail = "\u8bb0\u4e0b\u4e00\u7b14\u4ee5\u540e\uff0c\u4f60\u5c31\u80fd\u5f00\u59cb\u770b\u5230\u81ea\u5df1\u7684\u6536\u652f\u8282\u594f\u4e86\u3002"
        )
    }

    val latestEntry = entries.first()
    val topExpense = entries
        .filter { entry -> entry.type == LedgerEntryType.EXPENSE }
        .groupBy { entry -> entry.category }
        .mapValues { (_, groupedEntries) -> groupedEntries.sumOf { entry -> entry.amountInCents } }
        .maxByOrNull { (_, amountInCents) -> amountInCents }

    val headline = topExpense?.let { (category, amountInCents) ->
        "\u6700\u591a\u7684\u652f\u51fa\u843d\u5728 $category\uff0c\u5df2\u7d2f\u8ba1 ${formatCurrency(amountInCents)}"
    } ?: "\u6700\u8fd1\u4e00\u7b14\u662f${latestEntry.type.displayName()}\uff0c\u53ef\u4ee5\u7ee7\u7eed\u4fdd\u6301\u8bb0\u8d26\u8282\u594f"

    val detail = "\u6700\u65b0\u8bb0\u5f55\uff1a${latestEntry.category} ${formatSignedCurrency(latestEntry)}\uff0c${formatEntryTime(latestEntry.happenedAt)}"

    return LedgerInsight(
        headline = headline,
        detail = detail
    )
}

private fun spendingRatio(summary: LedgerSummary): Float {
    val totalFlow = summary.incomeInCents + summary.expenseInCents
    if (totalFlow <= 0L) {
        return 0f
    }
    return summary.expenseInCents.toFloat() / totalFlow.toFloat()
}

private fun formatCurrency(amountInCents: Long): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.CHINA)
    return formatter.format(amountInCents / 100.0)
}

private fun formatSignedCurrency(entry: LedgerEntry): String {
    val prefix = if (entry.type == LedgerEntryType.INCOME) "+" else "-"
    return prefix + formatCurrency(entry.amountInCents)
}

private fun formatEntryTime(timestamp: Long): String {
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(EntryTimeFormatter)
}
