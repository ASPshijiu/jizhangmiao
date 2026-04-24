@file:OptIn(ExperimentalMaterial3Api::class)

package com.android.jizhangmiao.ledger

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.android.jizhangmiao.ledger.data.LedgerAutomationRule
import com.android.jizhangmiao.ledger.data.LedgerAutomationTrace
import com.android.jizhangmiao.ledger.data.LedgerBudgetConfig
import com.android.jizhangmiao.ledger.data.LedgerEntry
import com.android.jizhangmiao.ledger.data.LedgerEntryType
import com.android.jizhangmiao.ledger.data.LedgerImportMode
import com.android.jizhangmiao.ledger.data.LedgerProfileConfig
import com.android.jizhangmiao.ledger.data.LedgerSecurityConfig
import com.android.jizhangmiao.ledger.data.LedgerTemplate
import com.android.jizhangmiao.ledger.data.LedgerTemplateRecurrence
import com.android.jizhangmiao.ledger.data.PendingLedgerImport
import com.android.jizhangmiao.ledger.data.toAmountInCents
import com.android.jizhangmiao.ledger.data.toAmountInput
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private val IncomeTint = Color(0xFF2E8B57)
private val ExpenseTint = Color(0xFFC76B4B)
private val ChartPalette = listOf(
    Color(0xFFC76B4B),
    Color(0xFF2E8B57),
    Color(0xFF5B8DEF),
    Color(0xFFF2A93B),
    Color(0xFF8E6CCF)
)
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
    onAccountChanged: (String) -> Unit,
    onCategoryChanged: (String) -> Unit,
    onNoteChanged: (String) -> Unit,
    onSuggestedAccountSelected: (String) -> Unit,
    onSuggestedCategorySelected: (String) -> Unit,
    onTemplateRecurrenceSelected: (LedgerTemplateRecurrence) -> Unit,
    onSaveClick: () -> Unit,
    onCancelEditClick: () -> Unit,
    onDeleteClick: (LedgerEntry) -> Unit,
    onEditClick: (LedgerEntry) -> Unit,
    onSaveTemplateClick: () -> Unit,
    onApplyTemplateClick: (LedgerTemplate) -> Unit,
    onDeleteTemplateClick: (LedgerTemplate) -> Unit,
    onSaveBudgetClick: (String, String, String) -> Unit,
    onExportBackup: (Uri) -> Unit,
    onExportCsv: (Uri) -> Unit,
    onPreviewBackup: (Uri) -> Unit,
    onImportBackup: (Uri, LedgerImportMode) -> Unit,
    onImportStatement: (Uri) -> Unit,
    onScanReceipt: (Uri) -> Unit,
    onApprovePendingImport: (PendingLedgerImport) -> Unit,
    onIgnorePendingImport: (PendingLedgerImport) -> Unit,
    onAddAccount: (String) -> Unit,
    onAddCategory: (LedgerEntryType, String) -> Unit,
    onAddAutomationRule: (String, LedgerEntryType, String, String) -> Unit,
    onRenameAccount: (String, String) -> Unit,
    onRenameCategory: (LedgerEntryType, String, String) -> Unit,
    onDeleteAutomationRule: (LedgerAutomationRule) -> Unit,
    onUnlockPin: (String) -> Unit,
    onSetPin: (String) -> Unit,
    onDisablePin: () -> Unit,
    onLockNow: () -> Unit,
    onDismissStatusMessage: () -> Unit
) {
    var periodFilterName by rememberSaveable { mutableStateOf(LedgerPeriodFilter.THIS_MONTH.name) }
    var typeFilterName by rememberSaveable { mutableStateOf(LedgerEntryFilterType.ALL.name) }
    var accountFilter by rememberSaveable { mutableStateOf("") }
    var categoryFilter by rememberSaveable { mutableStateOf("") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var minAmountText by rememberSaveable { mutableStateOf("") }
    var maxAmountText by rememberSaveable { mutableStateOf("") }
    var budgetCategory by rememberSaveable { mutableStateOf(defaultCategoryFor(LedgerEntryType.EXPENSE)) }
    var trendGranularityName by rememberSaveable { mutableStateOf(LedgerTrendGranularity.MONTH.name) }
    var chartDetailCategory by rememberSaveable { mutableStateOf("") }
    var chartDetailTypeName by rememberSaveable { mutableStateOf("") }

    var monthlyBudgetText by remember(uiState.budgetConfig.monthlyBudgetInCents) {
        mutableStateOf(uiState.budgetConfig.monthlyBudgetInCents?.toAmountInput().orEmpty())
    }
    var categoryBudgetText by remember(uiState.budgetConfig.categoryBudgets, budgetCategory) {
        mutableStateOf(uiState.budgetConfig.categoryBudgets[budgetCategory]?.toAmountInput().orEmpty())
    }

    val periodFilter = remember(periodFilterName) { LedgerPeriodFilter.valueOf(periodFilterName) }
    val typeFilter = remember(typeFilterName) { LedgerEntryFilterType.valueOf(typeFilterName) }
    val trendGranularity = remember(trendGranularityName) {
        LedgerTrendGranularity.valueOf(trendGranularityName)
    }
    val boards = remember {
        listOf(
            LedgerBoard.ENTRY,
            LedgerBoard.DASHBOARD,
            LedgerBoard.LEDGER,
            LedgerBoard.CALENDAR,
            LedgerBoard.STATS,
            LedgerBoard.BUDGET,
            LedgerBoard.SETTINGS
        )
    }
    val pagerState = rememberPagerState(pageCount = { boards.size })
    val coroutineScope = rememberCoroutineScope()
    val boardTabState = rememberLazyListState()

    val filteredEntries = remember(
        uiState.entries,
        periodFilter,
        typeFilter,
        accountFilter,
        categoryFilter,
        searchQuery,
        minAmountText,
        maxAmountText
    ) {
        filterEntries(
            entries = uiState.entries,
            periodFilter = periodFilter,
            typeFilter = typeFilter,
            account = accountFilter.ifBlank { null },
            category = categoryFilter.ifBlank { null },
            query = searchQuery,
            minAmountInCents = minAmountText.toAmountFilterCents(),
            maxAmountInCents = maxAmountText.toAmountFilterCents()
        )
    }
    val filteredSummary = remember(filteredEntries) {
        LedgerSummaryCalculator.calculate(filteredEntries)
    }
    val dashboardSummary = remember(uiState.entries) {
        LedgerSummaryCalculator.calculate(uiState.entries)
    }
    val currentMonthEntries = remember(uiState.entries) {
        filterEntries(
            entries = uiState.entries,
            periodFilter = LedgerPeriodFilter.THIS_MONTH,
            typeFilter = LedgerEntryFilterType.ALL,
            account = null,
            category = null,
            query = "",
            minAmountInCents = null,
            maxAmountInCents = null
        )
    }
    val currentMonthSummary = remember(currentMonthEntries) {
        LedgerSummaryCalculator.calculate(currentMonthEntries)
    }
    val currentMonthTopCategories = remember(currentMonthEntries) {
        buildCategoryBreakdown(currentMonthEntries, LedgerEntryType.EXPENSE)
    }
    val trendPoints = remember(filteredEntries, trendGranularity) {
        buildTrendPoints(filteredEntries, trendGranularity)
    }
    val availableCategories = remember(uiState.entries, uiState.templates, uiState.profileConfig) {
        buildAvailableCategories(uiState.entries, uiState.templates, uiState.profileConfig)
    }
    val availableAccounts = remember(uiState.entries, uiState.templates, uiState.profileConfig) {
        buildAvailableAccounts(uiState.entries, uiState.templates, uiState.profileConfig)
    }
    val recentEntries = remember(uiState.entries) {
        uiState.entries.take(4)
    }
    val accountSnapshots = remember(uiState.entries) {
        buildAccountSnapshots(uiState.entries)
    }
    val budgetCategories = remember(availableCategories) {
        if (availableCategories.isEmpty()) {
            categorySuggestionsFor(LedgerEntryType.EXPENSE)
        } else {
            availableCategories
        }
    }
    val selectedStatsFocus = remember(chartDetailTypeName, chartDetailCategory, filteredEntries) {
        val selectedType = runCatching {
            LedgerEntryType.valueOf(chartDetailTypeName)
        }.getOrNull()

        if (
            selectedType != null &&
            chartDetailCategory.isNotBlank() &&
            filteredEntries.any { entry ->
                entry.type == selectedType && entry.category == chartDetailCategory
            }
        ) {
            ChartFocus(type = selectedType, category = chartDetailCategory)
        } else {
            null
        }
    }
    val statsDetailEntries = remember(filteredEntries, selectedStatsFocus) {
        if (selectedStatsFocus == null) {
            filteredEntries
        } else {
            filteredEntries.filter { entry ->
                entry.type == selectedStatsFocus.type &&
                    entry.category == selectedStatsFocus.category
            }
        }
    }
    val currentBoard = boards[pagerState.currentPage.coerceIn(0, boards.lastIndex)]

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let(onExportBackup)
    }
    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let(onExportCsv)
    }
    val previewBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(onPreviewBackup)
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { selectedUri ->
            onImportBackup(selectedUri, LedgerImportMode.REPLACE)
        }
    }
    val mergeImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { selectedUri ->
            onImportBackup(selectedUri, LedgerImportMode.MERGE)
        }
    }
    val statementImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(onImportStatement)
    }
    val receiptLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let(onScanReceipt)
    }
    val openBoard: (LedgerBoard) -> Unit = { board ->
        val targetPage = boards.indexOf(board)
        if (targetPage >= 0) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(targetPage)
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        boardTabState.animateScrollToItem(pagerState.currentPage)
    }

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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                    text = currentBoard.subtitle(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                    LazyRow(
                        state = boardTabState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(boards) { board ->
                            FilterChip(
                                selected = board == currentBoard,
                                onClick = {
                                    openBoard(board)
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                label = {
                                    Text(board.displayName())
                                }
                            )
                        }
                    }
                    BoardPaginationIndicator(
                        boards = boards,
                        pagerState = pagerState,
                        onBoardSelected = openBoard
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = innerPadding.calculateTopPadding(),
                        bottom = innerPadding.calculateBottomPadding()
                    )
            ) {
                AnimatedVisibility(visible = uiState.statusMessage != null) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        StatusBanner(
                            message = uiState.statusMessage.orEmpty(),
                            onDismiss = onDismissStatusMessage
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { page ->
                    BoardPageContainer(
                        page = page,
                        pagerState = pagerState
                    ) {
                        when (boards[page]) {
                            LedgerBoard.ENTRY -> EntryBoard(
                                accountOptions = availableAccounts,
                                categoryOptions = availableCategories,
                                recentEntries = recentEntries,
                                form = uiState.form,
                                isReceiptScanning = uiState.isReceiptScanning,
                                onTypeSelected = onTypeSelected,
                                onAmountChanged = onAmountChanged,
                                onAccountChanged = onAccountChanged,
                                onCategoryChanged = onCategoryChanged,
                                onNoteChanged = onNoteChanged,
                                onSuggestedAccountSelected = onSuggestedAccountSelected,
                                onSuggestedCategorySelected = onSuggestedCategorySelected,
                                onTemplateRecurrenceSelected = onTemplateRecurrenceSelected,
                                onSaveClick = onSaveClick,
                                onCancelEditClick = onCancelEditClick,
                                onSaveTemplateClick = onSaveTemplateClick,
                                onScanReceiptClick = {
                                    receiptLauncher.launch("image/*")
                                },
                                onEditClick = onEditClick,
                                onDeleteClick = onDeleteClick,
                                onViewAllEntriesClick = {
                                    periodFilterName = LedgerPeriodFilter.ALL.name
                                    typeFilterName = LedgerEntryFilterType.ALL.name
                                    accountFilter = ""
                                    categoryFilter = ""
                                    searchQuery = ""
                                    minAmountText = ""
                                    maxAmountText = ""
                                    chartDetailCategory = ""
                                    chartDetailTypeName = ""
                                    openBoard(LedgerBoard.LEDGER)
                                }
                            )

                            LedgerBoard.DASHBOARD -> DashboardOverviewBoard(
                                summary = dashboardSummary,
                                budgetConfig = uiState.budgetConfig,
                                currentMonthSummary = currentMonthSummary,
                                currentMonthEntries = currentMonthEntries,
                                allEntries = uiState.entries,
                                topCategories = currentMonthTopCategories,
                                accountSnapshots = accountSnapshots,
                                totalRecordCount = uiState.entries.size,
                                onOpenEntryClick = {
                                    openBoard(LedgerBoard.ENTRY)
                                },
                                onOpenLedgerClick = {
                                    openBoard(LedgerBoard.LEDGER)
                                }
                            )

                            LedgerBoard.CALENDAR -> CalendarBoard(
                                entries = uiState.entries,
                                onEditClick = { entry ->
                                    onEditClick(entry)
                                    openBoard(LedgerBoard.ENTRY)
                                },
                                onDeleteClick = onDeleteClick
                            )

                            LedgerBoard.STATS -> StatisticsBoard(
                                periodFilter = periodFilter,
                                typeFilter = typeFilter,
                                accountFilter = accountFilter.ifBlank { null },
                                categoryFilter = categoryFilter.ifBlank { null },
                                searchQuery = searchQuery,
                                minAmountText = minAmountText,
                                maxAmountText = maxAmountText,
                                accounts = availableAccounts,
                                categories = availableCategories,
                                filteredEntries = filteredEntries,
                                filteredSummary = filteredSummary,
                                trendPoints = trendPoints,
                                trendGranularity = trendGranularity,
                                selectedFocus = selectedStatsFocus,
                                detailEntries = statsDetailEntries,
                                onPeriodSelected = { selectedFilter ->
                                    periodFilterName = selectedFilter.name
                                },
                                onTypeSelected = { selectedFilter ->
                                    typeFilterName = selectedFilter.name
                                },
                                onAccountSelected = { selectedAccount ->
                                    accountFilter = selectedAccount.orEmpty()
                                },
                                onCategorySelected = { selectedCategory ->
                                    categoryFilter = selectedCategory.orEmpty()
                                },
                                onSearchQueryChanged = { value ->
                                    searchQuery = value.take(40)
                                },
                                onMinAmountChanged = { value ->
                                    minAmountText = value.filter { char -> char.isDigit() || char == '.' }
                                },
                                onMaxAmountChanged = { value ->
                                    maxAmountText = value.filter { char -> char.isDigit() || char == '.' }
                                },
                                onClearAdvancedFilters = {
                                    searchQuery = ""
                                    minAmountText = ""
                                    maxAmountText = ""
                                },
                                onTrendGranularitySelected = { selectedGranularity ->
                                    trendGranularityName = selectedGranularity.name
                                },
                                onChartCategorySelected = { selectedType, selectedCategory ->
                                    if (
                                        chartDetailTypeName == selectedType.name &&
                                        chartDetailCategory == selectedCategory
                                    ) {
                                        chartDetailTypeName = ""
                                        chartDetailCategory = ""
                                    } else {
                                        chartDetailTypeName = selectedType.name
                                        chartDetailCategory = selectedCategory
                                    }
                                },
                                onClearChartSelection = {
                                    chartDetailTypeName = ""
                                    chartDetailCategory = ""
                                },
                                onEditClick = { entry ->
                                    onEditClick(entry)
                                    openBoard(LedgerBoard.ENTRY)
                                },
                                onDeleteClick = onDeleteClick
                            )

                            LedgerBoard.BUDGET -> BudgetBoard(
                                budgetConfig = uiState.budgetConfig,
                                currentMonthEntries = currentMonthEntries,
                                budgetCategories = budgetCategories,
                                selectedCategory = budgetCategory,
                                monthlyBudgetText = monthlyBudgetText,
                                categoryBudgetText = categoryBudgetText,
                                onMonthlyBudgetChanged = { value ->
                                    monthlyBudgetText = value.filter { it.isDigit() || it == '.' }
                                },
                                onCategorySelected = { category ->
                                    budgetCategory = category
                                    categoryBudgetText = uiState.budgetConfig.categoryBudgets[category]?.toAmountInput().orEmpty()
                                },
                                onCategoryBudgetChanged = { value ->
                                    categoryBudgetText = value.filter { it.isDigit() || it == '.' }
                                },
                                onSaveBudgetClick = {
                                    onSaveBudgetClick(monthlyBudgetText, budgetCategory, categoryBudgetText)
                                },
                                templates = uiState.templates,
                                onApplyTemplateClick = { template ->
                                    onApplyTemplateClick(template)
                                    openBoard(LedgerBoard.ENTRY)
                                },
                                onDeleteTemplateClick = onDeleteTemplateClick
                            )

                            LedgerBoard.SETTINGS -> SettingsBoard(
                                profileConfig = uiState.profileConfig,
                                automationRules = uiState.automationRules,
                                automationTrace = uiState.automationTrace,
                                pendingImports = uiState.pendingImports,
                                securityConfig = uiState.securityConfig,
                                isLocked = uiState.isLocked,
                                accounts = availableAccounts,
                                expenseCategories = (categorySuggestionsFor(LedgerEntryType.EXPENSE) +
                                    uiState.profileConfig.customExpenseCategories).distinct(),
                                incomeCategories = (categorySuggestionsFor(LedgerEntryType.INCOME) +
                                    uiState.profileConfig.customIncomeCategories).distinct(),
                                onApprovePendingImport = onApprovePendingImport,
                                onIgnorePendingImport = onIgnorePendingImport,
                                onAddAccount = onAddAccount,
                                onAddCategory = onAddCategory,
                                onAddAutomationRule = onAddAutomationRule,
                                onRenameAccount = onRenameAccount,
                                onRenameCategory = onRenameCategory,
                                onDeleteAutomationRule = onDeleteAutomationRule,
                                onUnlockPin = onUnlockPin,
                                onSetPin = onSetPin,
                                onDisablePin = onDisablePin,
                                onLockNow = onLockNow,
                                onExportClick = {
                                    exportLauncher.launch("jizhangmiao-backup-${LocalDate.now()}.json")
                                },
                                onExportCsvClick = {
                                    exportCsvLauncher.launch("jizhangmiao-ledger-${LocalDate.now()}.csv")
                                },
                                onPreviewBackupClick = {
                                    previewBackupLauncher.launch(arrayOf("application/json", "text/plain"))
                                },
                                onImportStatementClick = {
                                    statementImportLauncher.launch(
                                        arrayOf(
                                            "text/csv",
                                            "text/comma-separated-values",
                                            "application/csv",
                                            "application/vnd.ms-excel",
                                            "text/plain"
                                        )
                                    )
                                },
                                onImportClick = {
                                    importLauncher.launch(arrayOf("application/json", "text/plain"))
                                },
                                onMergeImportClick = {
                                    mergeImportLauncher.launch(arrayOf("application/json", "text/plain"))
                                }
                            )

                            LedgerBoard.LEDGER -> LedgerBoardPage(
                                periodFilter = periodFilter,
                                typeFilter = typeFilter,
                                accountFilter = accountFilter.ifBlank { null },
                                categoryFilter = categoryFilter.ifBlank { null },
                                searchQuery = searchQuery,
                                minAmountText = minAmountText,
                                maxAmountText = maxAmountText,
                                accounts = availableAccounts,
                                categories = availableCategories,
                                filteredEntries = filteredEntries,
                                onPeriodSelected = { selectedFilter ->
                                    periodFilterName = selectedFilter.name
                                },
                                onTypeSelected = { selectedFilter ->
                                    typeFilterName = selectedFilter.name
                                },
                                onAccountSelected = { selectedAccount ->
                                    accountFilter = selectedAccount.orEmpty()
                                },
                                onCategorySelected = { selectedCategory ->
                                    categoryFilter = selectedCategory.orEmpty()
                                },
                                onSearchQueryChanged = { value ->
                                    searchQuery = value.take(40)
                                },
                                onMinAmountChanged = { value ->
                                    minAmountText = value.filter { char -> char.isDigit() || char == '.' }
                                },
                                onMaxAmountChanged = { value ->
                                    maxAmountText = value.filter { char -> char.isDigit() || char == '.' }
                                },
                                onClearAdvancedFilters = {
                                    searchQuery = ""
                                    minAmountText = ""
                                    maxAmountText = ""
                                },
                                onEditClick = { entry ->
                                    onEditClick(entry)
                                    openBoard(LedgerBoard.ENTRY)
                                },
                                onDeleteClick = onDeleteClick
                            )
                        }
                    }
                }
            }
        }

        if (uiState.isLocked) {
            PrivacyLockOverlay(
                onUnlockPin = onUnlockPin
            )
        }
    }
}

@Composable
private fun EntryBoard(
    accountOptions: List<String>,
    categoryOptions: List<String>,
    recentEntries: List<LedgerEntry>,
    form: LedgerFormState,
    isReceiptScanning: Boolean,
    onTypeSelected: (LedgerEntryType) -> Unit,
    onAmountChanged: (String) -> Unit,
    onAccountChanged: (String) -> Unit,
    onCategoryChanged: (String) -> Unit,
    onNoteChanged: (String) -> Unit,
    onSuggestedAccountSelected: (String) -> Unit,
    onSuggestedCategorySelected: (String) -> Unit,
    onTemplateRecurrenceSelected: (LedgerTemplateRecurrence) -> Unit,
    onSaveClick: () -> Unit,
    onCancelEditClick: () -> Unit,
    onSaveTemplateClick: () -> Unit,
    onScanReceiptClick: () -> Unit,
    onEditClick: (LedgerEntry) -> Unit,
    onDeleteClick: (LedgerEntry) -> Unit,
    onViewAllEntriesClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            EntryEditorSection(
                accountOptions = accountOptions,
                categoryOptions = categoryOptions,
                form = form,
                onTypeSelected = onTypeSelected,
                onAmountChanged = onAmountChanged,
                onAccountChanged = onAccountChanged,
                onCategoryChanged = onCategoryChanged,
                onNoteChanged = onNoteChanged,
                onSuggestedAccountSelected = onSuggestedAccountSelected,
                onSuggestedCategorySelected = onSuggestedCategorySelected,
                onTemplateRecurrenceSelected = onTemplateRecurrenceSelected,
                onSaveClick = onSaveClick,
                onCancelEditClick = onCancelEditClick,
                onSaveTemplateClick = onSaveTemplateClick,
                onScanReceiptClick = onScanReceiptClick,
                isReceiptScanning = isReceiptScanning
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeading(
                    title = "\u6700\u8fd1\u8d26\u5355",
                    subtitle = if (recentEntries.isEmpty()) {
                        "\u8fd8\u6ca1\u6709\u8bb0\u5f55\uff0c\u53ef\u4ee5\u5148\u5728\u4e0a\u65b9\u8bb0\u4e00\u7b14"
                    } else {
                        "\u6700\u8fd1 ${recentEntries.size} \u7b14\u8bb0\u5f55\uff0c\u53ef\u4ee5\u76f4\u63a5\u7f16\u8f91\u6216\u5220\u9664"
                    }
                )
                if (recentEntries.isNotEmpty()) {
                    TextButton(onClick = onViewAllEntriesClick) {
                        Text("\u67e5\u770b\u66f4\u591a")
                    }
                }
            }
        }

        if (recentEntries.isEmpty()) {
            item {
                EmptyLedgerSection(
                    title = "\u9996\u9875\u8fd8\u6ca1\u6709\u7b14\u8bb0\u5f55",
                    subtitle = "\u5148\u4ece\u624b\u52a8\u8bb0\u8d26\u5f00\u59cb\uff0c\u4e4b\u540e\u8fd9\u91cc\u4f1a\u51fa\u73b0\u6700\u65b0\u8d26\u5355"
                )
            }
        } else {
            items(
                items = recentEntries,
                key = { entry -> entry.id }
            ) { entry ->
                LedgerEntryCard(
                    entry = entry,
                    onEditClick = { onEditClick(entry) },
                    onDeleteClick = { onDeleteClick(entry) }
                )
            }
        }
    }
}

@Composable
private fun DashboardOverviewBoard(
    summary: LedgerSummary,
    budgetConfig: LedgerBudgetConfig,
    currentMonthSummary: LedgerSummary,
    currentMonthEntries: List<LedgerEntry>,
    allEntries: List<LedgerEntry>,
    topCategories: List<CategorySpend>,
    accountSnapshots: List<AccountSnapshot>,
    totalRecordCount: Int,
    onOpenEntryClick: () -> Unit,
    onOpenLedgerClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            LedgerHeroCard(
                summary = summary,
                budgetConfig = budgetConfig,
                currentMonthSummary = currentMonthSummary,
                recordCount = totalRecordCount,
                topCategories = topCategories
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenEntryClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("\u53bb\u8bb0\u8d26")
                }
                OutlinedButton(
                    onClick = onOpenLedgerClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("\u770b\u8d26\u672c")
                }
            }
        }

        item {
            AccountOverviewSection(accountSnapshots = accountSnapshots)
        }

        item {
            BudgetAlertSection(
                budgetConfig = budgetConfig,
                currentMonthEntries = currentMonthEntries
            )
        }

        item {
            MonthlyReportSection(
                currentMonthEntries = currentMonthEntries,
                allEntries = allEntries
            )
        }
    }
}

@Composable
private fun MonthlyReportSection(
    currentMonthEntries: List<LedgerEntry>,
    allEntries: List<LedgerEntry>
) {
    val zoneId = ZoneId.systemDefault()
    val currentMonth = YearMonth.now(zoneId)
    val lastMonth = currentMonth.minusMonths(1)
    val lastMonthEntries = remember(allEntries) {
        allEntries.filter { entry ->
            val month = Instant.ofEpochMilli(entry.happenedAt)
                .atZone(zoneId)
                .toLocalDate()
                .let(YearMonth::from)
            month == lastMonth
        }
    }
    val currentSummary = remember(currentMonthEntries) {
        LedgerSummaryCalculator.calculate(currentMonthEntries)
    }
    val lastSummary = remember(lastMonthEntries) {
        LedgerSummaryCalculator.calculate(lastMonthEntries)
    }
    val topExpense = remember(currentMonthEntries) {
        buildCategoryBreakdown(currentMonthEntries, LedgerEntryType.EXPENSE).firstOrNull()
    }
    val expenseDelta = currentSummary.expenseInCents - lastSummary.expenseInCents

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SectionShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeading(
                title = "\u6708\u5ea6\u62a5\u544a",
                subtitle = "\u81ea\u52a8\u6c47\u603b\u672c\u6708\u82b1\u8d39\u91cd\u70b9\u548c\u4e0a\u6708\u5bf9\u6bd4"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    modifier = Modifier.weight(1f),
                    title = "\u672c\u6708\u652f\u51fa",
                    value = formatCurrency(currentSummary.expenseInCents),
                    accentColor = ExpenseTint
                )
                StatTile(
                    modifier = Modifier.weight(1f),
                    title = "\u672c\u6708\u6536\u5165",
                    value = formatCurrency(currentSummary.incomeInCents),
                    accentColor = IncomeTint
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = buildMonthlyInsightText(topExpense, expenseDelta),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BudgetAlertSection(
    budgetConfig: LedgerBudgetConfig,
    currentMonthEntries: List<LedgerEntry>
) {
    val alerts = remember(budgetConfig, currentMonthEntries) {
        buildBudgetAlerts(budgetConfig, currentMonthEntries)
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SectionShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeading(
                title = "\u9884\u7b97\u63d0\u9192",
                subtitle = if (alerts.isEmpty()) {
                    "\u76ee\u524d\u6ca1\u6709\u8fbe\u5230 80% \u7684\u9884\u7b97\u98ce\u9669"
                } else {
                    "\u6709 ${alerts.size} \u4e2a\u9884\u7b97\u9700\u8981\u7559\u610f"
                }
            )
            if (alerts.isEmpty()) {
                Text(
                    text = "\u7ee7\u7eed\u4fdd\u6301\uff0c\u5f53\u6708\u9884\u7b97\u6216\u5206\u7c7b\u9884\u7b97\u8d85\u8fc7 80% \u65f6\u4f1a\u5728\u8fd9\u91cc\u51fa\u73b0\u63d0\u9192\u3002",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                alerts.forEach { alert ->
                    Surface(
                        color = ExpenseTint.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            text = alert,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarBoard(
    entries: List<LedgerEntry>,
    onEditClick: (LedgerEntry) -> Unit,
    onDeleteClick: (LedgerEntry) -> Unit
) {
    val zoneId = ZoneId.systemDefault()
    val currentMonth = YearMonth.now(zoneId)
    val dailySummaries = remember(entries) {
        buildDailySummaries(entries, currentMonth)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeading(
                title = "\u8d26\u5355\u65e5\u5386",
                subtitle = "${currentMonth.monthValue}\u6708\u6bcf\u5929\u7684\u6536\u652f\u8282\u594f\uff0c\u53ef\u4ee5\u5feb\u901f\u56de\u770b\u5f53\u5929\u660e\u7ec6"
            )
        }
        if (dailySummaries.isEmpty()) {
            item {
                EmptyLedgerSection(
                    title = "\u672c\u6708\u8fd8\u6ca1\u6709\u8bb0\u5f55",
                    subtitle = "\u5f53\u6709\u8bb0\u5f55\u540e\uff0c\u65e5\u5386\u4f1a\u6309\u5929\u5c55\u793a\u6536\u5165\u3001\u652f\u51fa\u548c\u7ed3\u4f59\u3002"
                )
            }
        } else {
            dailySummaries.forEach { dailySummary ->
                item(key = dailySummary.date.toString()) {
                    DailySummaryCard(
                        dailySummary = dailySummary,
                        onEditClick = onEditClick,
                        onDeleteClick = onDeleteClick
                    )
                }
            }
        }
    }
}

@Composable
private fun DailySummaryCard(
    dailySummary: DailySummary,
    onEditClick: (LedgerEntry) -> Unit,
    onDeleteClick: (LedgerEntry) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = EntryShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${dailySummary.date.monthValue}\u6708${dailySummary.date.dayOfMonth}\u65e5",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "\u6536 ${formatCurrency(dailySummary.incomeInCents)} / \u652f ${formatCurrency(dailySummary.expenseInCents)}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            dailySummary.entries.take(3).forEach { entry ->
                LedgerEntryCard(
                    entry = entry,
                    onEditClick = { onEditClick(entry) },
                    onDeleteClick = { onDeleteClick(entry) }
                )
            }
            if (dailySummary.entries.size > 3) {
                Text(
                    text = "\u8fd8\u6709 ${dailySummary.entries.size - 3} \u7b14\u8bb0\u5f55\uff0c\u53ef\u5728\u8d26\u672c\u9875\u67e5\u770b\u5168\u90e8\u3002",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BoardPaginationIndicator(
    boards: List<LedgerBoard>,
    pagerState: PagerState,
    onBoardSelected: (LedgerBoard) -> Unit
) {
    val pageProgress = pagerState.currentPage + pagerState.currentPageOffsetFraction

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = Color.White.copy(alpha = 0.62f),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "\u677f\u5757\u5bfc\u822a",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${pagerState.currentPage + 1}/${boards.size} ${boards[pagerState.currentPage].displayName()}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                boards.forEachIndexed { index, board ->
                    val emphasis = (1f - (pageProgress - index).absoluteValue.coerceIn(0f, 1f))
                    val indicatorColor by animateColorAsState(
                        targetValue = if (emphasis > 0.5f) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                        },
                        label = "boardIndicatorColor$index"
                    )

                    Box(
                        modifier = Modifier
                            .width(12.dp + 22.dp * emphasis)
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(indicatorColor)
                            .clickable {
                                onBoardSelected(board)
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun BoardPageContainer(
    page: Int,
    pagerState: PagerState,
    content: @Composable () -> Unit
) {
    val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
        .absoluteValue
        .coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = 1f - (pageOffset * 0.18f)
                scaleX = 1f - (pageOffset * 0.06f)
                scaleY = 1f - (pageOffset * 0.06f)
                translationX = pageOffset * 48f
            }
    ) {
        content()
    }
}

@Composable
private fun LedgerBoardPage(
    periodFilter: LedgerPeriodFilter,
    typeFilter: LedgerEntryFilterType,
    accountFilter: String?,
    categoryFilter: String?,
    searchQuery: String,
    minAmountText: String,
    maxAmountText: String,
    accounts: List<String>,
    categories: List<String>,
    filteredEntries: List<LedgerEntry>,
    onPeriodSelected: (LedgerPeriodFilter) -> Unit,
    onTypeSelected: (LedgerEntryFilterType) -> Unit,
    onAccountSelected: (String?) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onMinAmountChanged: (String) -> Unit,
    onMaxAmountChanged: (String) -> Unit,
    onClearAdvancedFilters: () -> Unit,
    onEditClick: (LedgerEntry) -> Unit,
    onDeleteClick: (LedgerEntry) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            FilterSection(
                periodFilter = periodFilter,
                typeFilter = typeFilter,
                accountFilter = accountFilter,
                categoryFilter = categoryFilter,
                searchQuery = searchQuery,
                minAmountText = minAmountText,
                maxAmountText = maxAmountText,
                accounts = accounts,
                categories = categories,
                onPeriodSelected = onPeriodSelected,
                onTypeSelected = onTypeSelected,
                onAccountSelected = onAccountSelected,
                onCategorySelected = onCategorySelected,
                onSearchQueryChanged = onSearchQueryChanged,
                onMinAmountChanged = onMinAmountChanged,
                onMaxAmountChanged = onMaxAmountChanged,
                onClearAdvancedFilters = onClearAdvancedFilters
            )
        }

        item {
            SectionHeading(
                title = "\u5b8c\u6574\u8d26\u5355",
                subtitle = if (filteredEntries.isEmpty()) {
                    "\u5f53\u524d\u7b5b\u9009\u4e0b\u6682\u65f6\u6ca1\u6709\u8bb0\u5f55"
                } else {
                    "\u5f53\u524d\u7b5b\u9009\u4e0b\u5171 ${filteredEntries.size} \u7b14\uff0c\u53ef\u4ee5\u7f16\u8f91\u3001\u5220\u9664\u6216\u56de\u770b\u8be6\u60c5"
                }
            )
        }

        if (filteredEntries.isEmpty()) {
            item {
                EmptyLedgerSection()
            }
        } else {
            items(
                items = filteredEntries,
                key = { entry -> entry.id }
            ) { entry ->
                LedgerEntryCard(
                    entry = entry,
                    onEditClick = { onEditClick(entry) },
                    onDeleteClick = { onDeleteClick(entry) }
                )
            }
        }
    }
}

@Composable
private fun StatisticsBoard(
    periodFilter: LedgerPeriodFilter,
    typeFilter: LedgerEntryFilterType,
    accountFilter: String?,
    categoryFilter: String?,
    searchQuery: String,
    minAmountText: String,
    maxAmountText: String,
    accounts: List<String>,
    categories: List<String>,
    filteredEntries: List<LedgerEntry>,
    filteredSummary: LedgerSummary,
    trendPoints: List<TrendPoint>,
    trendGranularity: LedgerTrendGranularity,
    selectedFocus: ChartFocus?,
    detailEntries: List<LedgerEntry>,
    onPeriodSelected: (LedgerPeriodFilter) -> Unit,
    onTypeSelected: (LedgerEntryFilterType) -> Unit,
    onAccountSelected: (String?) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onMinAmountChanged: (String) -> Unit,
    onMaxAmountChanged: (String) -> Unit,
    onClearAdvancedFilters: () -> Unit,
    onTrendGranularitySelected: (LedgerTrendGranularity) -> Unit,
    onChartCategorySelected: (LedgerEntryType, String) -> Unit,
    onClearChartSelection: () -> Unit,
    onEditClick: (LedgerEntry) -> Unit,
    onDeleteClick: (LedgerEntry) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            FilterSection(
                periodFilter = periodFilter,
                typeFilter = typeFilter,
                accountFilter = accountFilter,
                categoryFilter = categoryFilter,
                searchQuery = searchQuery,
                minAmountText = minAmountText,
                maxAmountText = maxAmountText,
                accounts = accounts,
                categories = categories,
                onPeriodSelected = onPeriodSelected,
                onTypeSelected = onTypeSelected,
                onAccountSelected = onAccountSelected,
                onCategorySelected = onCategorySelected,
                onSearchQueryChanged = onSearchQueryChanged,
                onMinAmountChanged = onMinAmountChanged,
                onMaxAmountChanged = onMaxAmountChanged,
                onClearAdvancedFilters = onClearAdvancedFilters
            )
        }

        item {
            StatsSection(
                summary = filteredSummary,
                filteredEntries = filteredEntries,
                trendPoints = trendPoints,
                typeFilter = typeFilter,
                trendGranularity = trendGranularity,
                selectedFocus = selectedFocus,
                onTrendGranularitySelected = onTrendGranularitySelected,
                onCategorySelected = onChartCategorySelected
            )
        }

        item {
            StatsDetailHeader(
                selectedFocus = selectedFocus,
                entryCount = detailEntries.size,
                onClearSelection = onClearChartSelection
            )
        }

        if (detailEntries.isEmpty()) {
            item {
                EmptyLedgerSection()
            }
        } else {
            items(
                items = detailEntries,
                key = { entry -> entry.id }
            ) { entry ->
                LedgerEntryCard(
                    entry = entry,
                    onEditClick = { onEditClick(entry) },
                    onDeleteClick = { onDeleteClick(entry) }
                )
            }
        }
    }
}

@Composable
private fun BudgetBoard(
    budgetConfig: LedgerBudgetConfig,
    currentMonthEntries: List<LedgerEntry>,
    budgetCategories: List<String>,
    selectedCategory: String,
    monthlyBudgetText: String,
    categoryBudgetText: String,
    onMonthlyBudgetChanged: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    onCategoryBudgetChanged: (String) -> Unit,
    onSaveBudgetClick: () -> Unit,
    templates: List<LedgerTemplate>,
    onApplyTemplateClick: (LedgerTemplate) -> Unit,
    onDeleteTemplateClick: (LedgerTemplate) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            BudgetSection(
                budgetConfig = budgetConfig,
                currentMonthEntries = currentMonthEntries,
                budgetCategories = budgetCategories,
                selectedCategory = selectedCategory,
                monthlyBudgetText = monthlyBudgetText,
                categoryBudgetText = categoryBudgetText,
                onMonthlyBudgetChanged = onMonthlyBudgetChanged,
                onCategorySelected = onCategorySelected,
                onCategoryBudgetChanged = onCategoryBudgetChanged,
                onSaveBudgetClick = onSaveBudgetClick
            )
        }

        item {
            TemplateSection(
                templates = templates,
                onApplyTemplateClick = onApplyTemplateClick,
                onDeleteTemplateClick = onDeleteTemplateClick
            )
        }
    }
}

@Composable
private fun SettingsBoard(
    profileConfig: LedgerProfileConfig,
    automationRules: List<LedgerAutomationRule>,
    automationTrace: LedgerAutomationTrace,
    pendingImports: List<PendingLedgerImport>,
    securityConfig: LedgerSecurityConfig,
    isLocked: Boolean,
    accounts: List<String>,
    expenseCategories: List<String>,
    incomeCategories: List<String>,
    onApprovePendingImport: (PendingLedgerImport) -> Unit,
    onIgnorePendingImport: (PendingLedgerImport) -> Unit,
    onAddAccount: (String) -> Unit,
    onAddCategory: (LedgerEntryType, String) -> Unit,
    onAddAutomationRule: (String, LedgerEntryType, String, String) -> Unit,
    onRenameAccount: (String, String) -> Unit,
    onRenameCategory: (LedgerEntryType, String, String) -> Unit,
    onDeleteAutomationRule: (LedgerAutomationRule) -> Unit,
    onUnlockPin: (String) -> Unit,
    onSetPin: (String) -> Unit,
    onDisablePin: () -> Unit,
    onLockNow: () -> Unit,
    onExportClick: () -> Unit,
    onExportCsvClick: () -> Unit,
    onPreviewBackupClick: () -> Unit,
    onImportStatementClick: () -> Unit,
    onImportClick: () -> Unit,
    onMergeImportClick: () -> Unit
) {
    val context = LocalContext.current
    val automationStatus = rememberNotificationAutomationStatus()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            AutomationSection(
                status = automationStatus,
                onOpenNotificationAccess = {
                    openNotificationAutomationSettings(context)
                },
                onOpenAccessibilityAccess = {
                    openAccessibilityAutomationSettings(context)
                },
                onOpenWeChat = {
                    launchExternalPaymentApp(context, WeChatPackageName)
                },
                onOpenAlipay = {
                    launchExternalPaymentApp(context, AlipayPackageName)
                }
            )
        }

        item {
            AutomationTraceSection(trace = automationTrace)
        }

        item {
            PendingImportReviewSection(
                pendingImports = pendingImports,
                onApprovePendingImport = onApprovePendingImport,
                onIgnorePendingImport = onIgnorePendingImport
            )
        }

        item {
            ProfileManagementSection(
                profileConfig = profileConfig,
                accounts = accounts,
                expenseCategories = expenseCategories,
                incomeCategories = incomeCategories,
                onAddAccount = onAddAccount,
                onAddCategory = onAddCategory,
                onRenameAccount = onRenameAccount,
                onRenameCategory = onRenameCategory
            )
        }

        item {
            AutomationRuleSection(
                rules = automationRules,
                accounts = accounts,
                expenseCategories = expenseCategories,
                incomeCategories = incomeCategories,
                onAddAutomationRule = onAddAutomationRule,
                onDeleteAutomationRule = onDeleteAutomationRule
            )
        }

        item {
            SecuritySection(
                securityConfig = securityConfig,
                isLocked = isLocked,
                onUnlockPin = onUnlockPin,
                onSetPin = onSetPin,
                onDisablePin = onDisablePin,
                onLockNow = onLockNow
            )
        }

        item {
            ToolSection(
                onExportClick = onExportClick,
                onExportCsvClick = onExportCsvClick,
                onPreviewBackupClick = onPreviewBackupClick,
                onImportStatementClick = onImportStatementClick,
                onImportClick = onImportClick,
                onMergeImportClick = onMergeImportClick
            )
        }
    }
}

@Composable
private fun rememberNotificationAutomationStatus(): NotificationAutomationStatus {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var status by remember(context) {
        mutableStateOf(queryNotificationAutomationStatus(context))
    }

    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                status = queryNotificationAutomationStatus(context)
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return status
}

@Composable
private fun DecorativeBackdrop() {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset(x = 120.dp, y = (-84).dp)
                .size(width = 260.dp, height = 180.dp)
                .clip(RoundedCornerShape(90.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0x22C76B4B),
                            Color(0x112E8B57)
                        )
                    )
                )
        )
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
        Box(
            modifier = Modifier
                .offset(x = 170.dp, y = 620.dp)
                .size(160.dp)
                .clip(CircleShape)
                .background(Color(0x145B8DEF))
        )
    }
}

@Composable
private fun StatusBanner(
    message: String,
    onDismiss: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            TextButton(onClick = onDismiss) {
                Text("\u77e5\u9053\u4e86")
            }
        }
    }
}

@Composable
private fun LedgerHeroCard(
    summary: LedgerSummary,
    budgetConfig: LedgerBudgetConfig,
    currentMonthSummary: LedgerSummary,
    recordCount: Int,
    topCategories: List<CategorySpend>
) {
    val budgetRatio by animateFloatAsState(
        targetValue = budgetRatio(
            spentInCents = currentMonthSummary.expenseInCents,
            budgetInCents = budgetConfig.monthlyBudgetInCents
        ),
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 260f),
        label = "budgetRatio"
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = HeroShape,
        colors = CardDefaults.elevatedCardColors(containerColor = Color.Transparent)
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
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = "\u8d26\u672c\u4eea\u8868\u76d8",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }

                Text(
                    text = formatCurrency(summary.balanceInCents),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 34.sp,
                        lineHeight = 38.sp
                    ),
                    color = Color.White
                )

                Text(
                    text = "\u5f53\u524d\u7b5b\u9009\u4e0b\u5171 $recordCount \u7b14\u8bb0\u5f55\uff0c\u6536\u5165 ${formatCurrency(summary.incomeInCents)}\uff0c\u652f\u51fa ${formatCurrency(summary.expenseInCents)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroMetric(
                        modifier = Modifier.weight(1f),
                        title = "\u672c\u6708\u9884\u7b97",
                        value = budgetConfig.monthlyBudgetInCents?.let(::formatCurrency) ?: "\u672a\u8bbe\u7f6e"
                    )
                    HeroMetric(
                        modifier = Modifier.weight(1f),
                        title = "\u672c\u6708\u5df2\u82b1",
                        value = formatCurrency(currentMonthSummary.expenseInCents)
                    )
                    HeroMetric(
                        modifier = Modifier.weight(1f),
                        title = "\u6700\u70ed\u5206\u7c7b",
                        value = topCategories.firstOrNull()?.category ?: "\u6682\u65e0"
                    )
                }

                Surface(
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "\u672c\u6708\u9884\u7b97\u4f7f\u7528 ${(budgetRatio * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                        LinearProgressIndicator(
                            progress = { budgetRatio.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = Color(0xFFFFD7B7),
                            trackColor = Color.White.copy(alpha = 0.16f)
                        )
                        Text(
                            text = budgetInsightText(budgetConfig, currentMonthSummary),
                            style = MaterialTheme.typography.labelLarge,
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
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.72f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = Color.White
            )
        }
    }
}

@Composable
private fun AccountOverviewSection(accountSnapshots: List<AccountSnapshot>) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SectionShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeading(
                title = "\u8d26\u6237\u770b\u677f",
                subtitle = if (accountSnapshots.isEmpty()) {
                    "\u8fd8\u6ca1\u6709\u53ef\u89c2\u5bdf\u7684\u8d26\u6237\u6570\u636e"
                } else {
                    "\u770b\u6e05\u6bcf\u4e2a\u8d26\u6237\u7684\u6d41\u5165\u3001\u6d41\u51fa\u548c\u5f53\u524d\u7ed3\u4f59"
                }
            )

            if (accountSnapshots.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Text(
                        text = "\u4f60\u53ef\u4ee5\u5728\u8bb0\u8d26\u65f6\u533a\u5206\u5fae\u4fe1\u3001\u652f\u4ed8\u5b9d\u3001\u94f6\u884c\u5361\u6216\u73b0\u91d1\uff0c\u540e\u9762\u8fd9\u91cc\u5c31\u4f1a\u5f62\u6210\u8d26\u6237\u89c6\u56fe\u3002",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(accountSnapshots, key = { snapshot -> snapshot.account }) { snapshot ->
                        AccountSnapshotCard(snapshot = snapshot)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountSnapshotCard(snapshot: AccountSnapshot) {
    val accentColor = if (snapshot.balanceInCents >= 0L) IncomeTint else ExpenseTint

    Surface(
        modifier = Modifier.width(200.dp),
        color = accentColor.copy(alpha = 0.08f),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = snapshot.account,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = formatCurrency(snapshot.balanceInCents),
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                color = accentColor
            )
            Text(
                text = "\u6536\u5165 ${formatCurrency(snapshot.incomeInCents)} / \u652f\u51fa ${formatCurrency(snapshot.expenseInCents)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FilterSection(
    periodFilter: LedgerPeriodFilter,
    typeFilter: LedgerEntryFilterType,
    accountFilter: String?,
    categoryFilter: String?,
    searchQuery: String,
    minAmountText: String,
    maxAmountText: String,
    accounts: List<String>,
    categories: List<String>,
    onPeriodSelected: (LedgerPeriodFilter) -> Unit,
    onTypeSelected: (LedgerEntryFilterType) -> Unit,
    onAccountSelected: (String?) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onMinAmountChanged: (String) -> Unit,
    onMaxAmountChanged: (String) -> Unit,
    onClearAdvancedFilters: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SectionShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionHeading(
                title = "\u7b5b\u9009\u548c\u89c2\u5bdf",
                subtitle = "\u6309\u65f6\u95f4\u3001\u8d26\u6237\u3001\u6536\u652f\u7c7b\u578b\u548c\u5206\u7c7b\u770b\u4f60\u7684\u8d26\u672c"
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionEyebrow("\u65f6\u95f4")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(LedgerPeriodFilter.entries.toList()) { filter ->
                        FilterChip(
                            selected = filter == periodFilter,
                            onClick = { onPeriodSelected(filter) },
                            label = {
                                Text(filter.displayName())
                            }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionEyebrow("\u7c7b\u578b")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(LedgerEntryFilterType.entries.toList()) { filter ->
                        FilterChip(
                            selected = filter == typeFilter,
                            onClick = { onTypeSelected(filter) },
                            label = {
                                Text(filter.displayName())
                            }
                        )
                    }
                }
            }

            if (accounts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionEyebrow("\u8d26\u6237")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = accountFilter == null,
                                onClick = { onAccountSelected(null) },
                                label = {
                                    Text("\u5168\u90e8")
                                }
                            )
                        }
                        items(accounts) { account ->
                            FilterChip(
                                selected = account == accountFilter,
                                onClick = { onAccountSelected(account) },
                                label = {
                                    Text(account)
                                }
                            )
                        }
                    }
                }
            }

            if (categories.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionEyebrow("\u5206\u7c7b")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = categoryFilter == null,
                                onClick = { onCategorySelected(null) },
                                label = {
                                    Text("\u5168\u90e8")
                                }
                            )
                        }
                        items(categories) { category ->
                            FilterChip(
                                selected = category == categoryFilter,
                                onClick = { onCategorySelected(category) },
                                label = {
                                    Text(category)
                                }
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionEyebrow("\u641c\u7d22\u548c\u91d1\u989d")
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("\u641c\u7d22\u5907\u6ce8\u3001\u8d26\u6237\u3001\u5206\u7c7b\u6216\u5c0f\u7968\u6587\u672c")
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = minAmountText,
                        onValueChange = onMinAmountChanged,
                        modifier = Modifier.weight(1f),
                        label = {
                            Text("\u6700\u5c0f\u91d1\u989d")
                        },
                        prefix = {
                            Text("\u00a5")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp)
                    )
                    OutlinedTextField(
                        value = maxAmountText,
                        onValueChange = onMaxAmountChanged,
                        modifier = Modifier.weight(1f),
                        label = {
                            Text("\u6700\u5927\u91d1\u989d")
                        },
                        prefix = {
                            Text("\u00a5")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp)
                    )
                }
                if (searchQuery.isNotBlank() || minAmountText.isNotBlank() || maxAmountText.isNotBlank()) {
                    TextButton(onClick = onClearAdvancedFilters) {
                        Text("\u6e05\u7a7a\u641c\u7d22\u548c\u91d1\u989d\u6761\u4ef6")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsSection(
    summary: LedgerSummary,
    filteredEntries: List<LedgerEntry>,
    trendPoints: List<TrendPoint>,
    typeFilter: LedgerEntryFilterType,
    trendGranularity: LedgerTrendGranularity,
    selectedFocus: ChartFocus?,
    onTrendGranularitySelected: (LedgerTrendGranularity) -> Unit,
    onCategorySelected: (LedgerEntryType, String) -> Unit
) {
    val expenseCategories = remember(filteredEntries) {
        buildCategoryBreakdown(filteredEntries, LedgerEntryType.EXPENSE)
    }
    val incomeCategories = remember(filteredEntries) {
        buildCategoryBreakdown(filteredEntries, LedgerEntryType.INCOME)
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SectionShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SectionHeading(
                title = "\u7edf\u8ba1\u89c6\u56fe",
                subtitle = "\u7528\u56fe\u8868\u5bf9\u6bd4\u8d26\u76ee\u91cd\u5fc3\uff0c\u8fd8\u53ef\u4ee5\u70b9\u51fb\u805a\u7126\u660e\u7ec6"
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    modifier = Modifier.weight(1f),
                    title = "\u7ed3\u4f59",
                    value = formatCurrency(summary.balanceInCents),
                    accentColor = MaterialTheme.colorScheme.primary
                )
                StatTile(
                    modifier = Modifier.weight(1f),
                    title = "\u8bb0\u5f55",
                    value = filteredEntries.size.toString(),
                    accentColor = MaterialTheme.colorScheme.secondary
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "\u7edf\u8ba1\u56fe\u5df2\u62c6\u6210\u6536\u5165\u548c\u652f\u51fa\u4e24\u7ec4\uff0c\u70b9\u51fb\u4efb\u610f\u5206\u7c7b\u540e\uff0c\u4e0b\u65b9\u660e\u7ec6\u4f1a\u6309\u7c7b\u578b\u548c\u5206\u7c7b\u4e00\u8d77\u805a\u7126\u3002",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (typeFilter != LedgerEntryFilterType.INCOME) {
                CategoryChartGroup(
                    title = "\u652f\u51fa\u5206\u5e03",
                    subtitle = "\u89c2\u5bdf\u54ea\u4e9b\u652f\u51fa\u5206\u7c7b\u6700\u5403\u9884\u7b97",
                    entryType = LedgerEntryType.EXPENSE,
                    topCategories = expenseCategories,
                    selectedFocus = selectedFocus,
                    onCategorySelected = onCategorySelected
                )
            }

            if (typeFilter != LedgerEntryFilterType.EXPENSE) {
                CategoryChartGroup(
                    title = "\u6536\u5165\u5206\u5e03",
                    subtitle = "\u5355\u72ec\u770b\u6536\u5165\u6765\u6e90\uff0c\u533a\u5206\u5de5\u8d44\u3001\u5956\u91d1\u548c\u5176\u4ed6\u6765\u6e90",
                    entryType = LedgerEntryType.INCOME,
                    topCategories = incomeCategories,
                    selectedFocus = selectedFocus,
                    onCategorySelected = onCategorySelected
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionEyebrow("\u6536\u652f\u8d8b\u52bf")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(LedgerTrendGranularity.entries.toList()) { granularity ->
                        FilterChip(
                            selected = granularity == trendGranularity,
                            onClick = {
                                onTrendGranularitySelected(granularity)
                            },
                            label = {
                                Text(granularity.displayName())
                            }
                        )
                    }
                }
                trendPoints.forEach { point ->
                    TrendRow(point = point)
                }
            }
        }
    }
}

@Composable
private fun CategoryChartGroup(
    title: String,
    subtitle: String,
    entryType: LedgerEntryType,
    topCategories: List<CategorySpend>,
    selectedFocus: ChartFocus?,
    onCategorySelected: (LedgerEntryType, String) -> Unit
) {
    val accentColor = if (entryType == LedgerEntryType.INCOME) IncomeTint else ExpenseTint
    val selectedCategory = selectedFocus
        ?.takeIf { focus -> focus.type == entryType }
        ?.category

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeading(
            title = title,
            subtitle = subtitle
        )
        if (topCategories.isEmpty()) {
            Text(
                text = if (entryType == LedgerEntryType.INCOME) {
                    "\u5f53\u524d\u7b5b\u9009\u4e0b\u8fd8\u6ca1\u6709\u8db3\u591f\u7684\u6536\u5165\u6570\u636e"
                } else {
                    "\u5f53\u524d\u7b5b\u9009\u4e0b\u8fd8\u6ca1\u6709\u8db3\u591f\u7684\u652f\u51fa\u6570\u636e"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            CategoryPieChart(
                topCategories = topCategories,
                selectedCategory = selectedCategory,
                accentColor = accentColor,
                centerLabel = if (entryType == LedgerEntryType.INCOME) {
                    "\u6536\u5165\u603b\u989d"
                } else {
                    "\u652f\u51fa\u603b\u989d"
                },
                onCategorySelected = { category ->
                    onCategorySelected(entryType, category)
                }
            )
            CategoryBarChart(
                topCategories = topCategories,
                selectedCategory = selectedCategory,
                accentColor = accentColor,
                onCategorySelected = { category ->
                    onCategorySelected(entryType, category)
                }
            )
            topCategories.forEach { categorySpend ->
                CategoryBreakdownRow(
                    categorySpend = categorySpend,
                    topAmount = topCategories.first().amountInCents,
                    accentColor = accentColor,
                    selected = categorySpend.category == selectedCategory,
                    onClick = {
                        onCategorySelected(entryType, categorySpend.category)
                    }
                )
            }
        }
    }
}

@Composable
private fun CategoryPieChart(
    topCategories: List<CategorySpend>,
    selectedCategory: String?,
    accentColor: Color,
    centerLabel: String,
    onCategorySelected: (String) -> Unit
) {
    val totalAmount = remember(topCategories) {
        topCategories.sumOf { categorySpend -> categorySpend.amountInCents }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.size(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = size.minDimension * 0.18f
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset(
                        x = (size.width - diameter) / 2f,
                        y = (size.height - diameter) / 2f
                    )
                    val arcSize = Size(diameter, diameter)
                    var startAngle = -90f

                    topCategories.forEachIndexed { index, categorySpend ->
                        val sweepAngle = if (totalAmount <= 0L) {
                            0f
                        } else {
                            categorySpend.amountInCents.toFloat() / totalAmount.toFloat() * 360f
                        }

                        drawArc(
                            color = ChartPalette[index % ChartPalette.size],
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth)
                        )
                        startAngle += sweepAngle
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = centerLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatCurrency(totalAmount),
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                        color = accentColor
                    )
                    Text(
                        text = "\u524d ${topCategories.size} \u4e2a\u5206\u7c7b",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(topCategories) { categorySpend ->
                val percent = if (totalAmount <= 0L) {
                    0
                } else {
                    (categorySpend.amountInCents * 100 / totalAmount).toInt()
                }
                FilterChip(
                    selected = categorySpend.category == selectedCategory,
                    onClick = {
                        onCategorySelected(categorySpend.category)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accentColor.copy(alpha = 0.14f),
                        selectedLabelColor = accentColor
                    ),
                    label = {
                        Text("${categorySpend.category} $percent%")
                    }
                )
            }
        }
    }
}

@Composable
private fun CategoryBarChart(
    topCategories: List<CategorySpend>,
    selectedCategory: String?,
    accentColor: Color,
    onCategorySelected: (String) -> Unit
) {
    val topAmount = remember(topCategories) {
        topCategories.maxOfOrNull { categorySpend -> categorySpend.amountInCents } ?: 0L
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            topCategories.forEachIndexed { index, categorySpend ->
                val isSelected = categorySpend.category == selectedCategory
                val ratio by animateFloatAsState(
                    targetValue = if (topAmount <= 0L) {
                        0f
                    } else {
                        categorySpend.amountInCents.toFloat() / topAmount.toFloat()
                    },
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 280f),
                    label = "categoryBar${categorySpend.category}"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            onCategorySelected(categorySpend.category)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatCurrency(categorySpend.amountInCents),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                        color = accentColor,
                        textAlign = TextAlign.Center
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((128.dp * ratio.coerceAtLeast(0.16f)).coerceAtLeast(24.dp))
                            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
                            .background(
                                ChartPalette[index % ChartPalette.size].copy(
                                    alpha = if (isSelected) 1f else 0.72f
                                )
                            )
                    )
                    Text(
                        text = categorySpend.category,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) {
                            accentColor
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun StatTile(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    accentColor: Color
) {
    Surface(
        modifier = modifier,
        color = accentColor.copy(alpha = 0.1f),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = accentColor
            )
        }
    }
}

@Composable
private fun StatsDetailHeader(
    selectedFocus: ChartFocus?,
    entryCount: Int,
    onClearSelection: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionHeading(
            title = if (selectedFocus == null) {
                "\u7b5b\u9009\u660e\u7ec6"
            } else {
                "${selectedFocus.type.displayName()} \u00b7 ${selectedFocus.category}"
            },
            subtitle = if (selectedFocus == null) {
                "\u5f53\u524d\u7b5b\u9009\u4e0b\u5171 $entryCount \u7b14\uff0c\u70b9\u51fb\u56fe\u8868\u540e\u53ef\u6309\u5206\u7c7b\u805a\u7126"
            } else {
                "\u5f53\u524d\u805a\u7126 ${selectedFocus.type.displayName()} / ${selectedFocus.category}\uff0c\u5171 $entryCount \u7b14\uff0c\u53ef\u4ee5\u70b9\u51fb\u53f3\u4fa7\u6062\u590d\u5168\u90e8"
            }
        )
        if (selectedFocus != null) {
            TextButton(onClick = onClearSelection) {
                Text("\u67e5\u770b\u5168\u90e8")
            }
        }
    }
}

@Composable
private fun CategoryBreakdownRow(
    categorySpend: CategorySpend,
    topAmount: Long,
    accentColor: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) {
            accentColor.copy(alpha = 0.08f)
        } else {
            Color.Transparent
        },
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = categorySpend.category,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = formatCurrency(categorySpend.amountInCents),
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    color = accentColor
                )
            }
            LinearProgressIndicator(
                progress = {
                    if (topAmount <= 0L) 0f else categorySpend.amountInCents.toFloat() / topAmount.toFloat()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = accentColor,
                trackColor = accentColor.copy(alpha = 0.12f)
            )
        }
    }
}

@Composable
private fun TrendRow(point: TrendPoint) {
    val accentColor = if (point.netInCents >= 0L) IncomeTint else ExpenseTint
    val barRatio = remember(point.maxAbsoluteValue, point.netInCents) {
        if (point.maxAbsoluteValue <= 0L) {
            0f
        } else {
            point.netInCents.absoluteValue.toFloat() / point.maxAbsoluteValue.toFloat()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = point.label,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = if (point.netInCents >= 0L) "+${formatCurrency(point.netInCents)}" else "-${formatCurrency(point.netInCents.absoluteValue)}",
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                color = accentColor
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(accentColor.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(barRatio)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accentColor)
            )
        }
    }
}

@Composable
private fun BudgetSection(
    budgetConfig: LedgerBudgetConfig,
    currentMonthEntries: List<LedgerEntry>,
    budgetCategories: List<String>,
    selectedCategory: String,
    monthlyBudgetText: String,
    categoryBudgetText: String,
    onMonthlyBudgetChanged: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    onCategoryBudgetChanged: (String) -> Unit,
    onSaveBudgetClick: () -> Unit
) {
    val monthExpense = currentMonthEntries
        .filter { entry -> entry.type == LedgerEntryType.EXPENSE }
        .sumOf { entry -> entry.amountInCents }
    val categoryExpense = currentMonthEntries
        .filter { entry -> entry.type == LedgerEntryType.EXPENSE && entry.category == selectedCategory }
        .sumOf { entry -> entry.amountInCents }
    val monthlyBudget = budgetConfig.monthlyBudgetInCents
    val categoryBudget = budgetConfig.categoryBudgets[selectedCategory]

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SectionShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SectionHeading(
                title = "\u9884\u7b97\u7ba1\u7406",
                subtitle = "\u53ef\u4ee5\u540c\u65f6\u8bbe\u7f6e\u6708\u603b\u9884\u7b97\u548c\u5206\u7c7b\u9884\u7b97"
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BudgetProgressCard(
                    modifier = Modifier.weight(1f),
                    title = "\u672c\u6708\u603b\u9884\u7b97",
                    spentInCents = monthExpense,
                    budgetInCents = monthlyBudget,
                    accentColor = ExpenseTint
                )
                BudgetProgressCard(
                    modifier = Modifier.weight(1f),
                    title = "$selectedCategory \u9884\u7b97",
                    spentInCents = categoryExpense,
                    budgetInCents = categoryBudget,
                    accentColor = MaterialTheme.colorScheme.primary
                )
            }

            OutlinedTextField(
                value = monthlyBudgetText,
                onValueChange = onMonthlyBudgetChanged,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("\u6708\u5ea6\u603b\u9884\u7b97")
                },
                prefix = {
                    Text("\u00a5")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(20.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionEyebrow("\u5206\u7c7b\u9884\u7b97")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(budgetCategories) { category ->
                        FilterChip(
                            selected = category == selectedCategory,
                            onClick = { onCategorySelected(category) },
                            label = {
                                Text(category)
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = categoryBudgetText,
                onValueChange = onCategoryBudgetChanged,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("$selectedCategory \u9884\u7b97")
                },
                prefix = {
                    Text("\u00a5")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(20.dp)
            )

            Button(
                onClick = onSaveBudgetClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("\u4fdd\u5b58\u9884\u7b97")
            }
        }
    }
}

@Composable
private fun BudgetProgressCard(
    modifier: Modifier = Modifier,
    title: String,
    spentInCents: Long,
    budgetInCents: Long?,
    accentColor: Color
) {
    val ratio = budgetRatio(spentInCents, budgetInCents)

    Surface(
        modifier = modifier,
        color = accentColor.copy(alpha = 0.08f),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = budgetInCents?.let(::formatCurrency) ?: "\u672a\u8bbe\u7f6e",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                color = accentColor
            )
            LinearProgressIndicator(
                progress = { ratio.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = accentColor,
                trackColor = accentColor.copy(alpha = 0.12f)
            )
            Text(
                text = if (budgetInCents == null) {
                    "\u5df2\u82b1 ${formatCurrency(spentInCents)}"
                } else {
                    "\u5df2\u82b1 ${formatCurrency(spentInCents)} / \u5269\u4f59 ${formatCurrency((budgetInCents - spentInCents).coerceAtLeast(0L))}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TemplateSection(
    templates: List<LedgerTemplate>,
    onApplyTemplateClick: (LedgerTemplate) -> Unit,
    onDeleteTemplateClick: (LedgerTemplate) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SectionShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionHeading(
                title = "\u5feb\u6377\u6a21\u677f",
                subtitle = if (templates.isEmpty()) {
                    "\u53ef\u4ee5\u5148\u4ece\u5f55\u5165\u533a\u4fdd\u5b58\u6a21\u677f\uff0c\u9009\u4e86\u6bcf\u5468/\u6bcf\u6708\u540e\u5c31\u4f1a\u53d8\u6210\u5468\u671f\u6a21\u677f"
                } else {
                    "\u666e\u901a\u6a21\u677f\u53ef\u4ee5\u4e00\u952e\u5957\u7528\uff0c\u5468\u671f\u6a21\u677f\u4f1a\u5728\u5230\u671f\u65f6\u81ea\u52a8\u8865\u8d26"
                }
            )

            if (templates.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Text(
                        text = "\u6ca1\u6709\u6a21\u677f\u65f6\uff0c\u4f60\u6bcf\u6b21\u90fd\u9700\u8981\u624b\u52a8\u8f93\u5165\uff1b\u6709\u4e86\u6a21\u677f\uff0c\u5e38\u7528\u6d88\u8d39\u53ef\u4ee5\u4e00\u952e\u590d\u7528\u3002",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(templates, key = { template -> template.id }) { template ->
                        TemplateCard(
                            template = template,
                            onApplyClick = { onApplyTemplateClick(template) },
                            onDeleteClick = { onDeleteTemplateClick(template) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(
    template: LedgerTemplate,
    onApplyClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val accentColor = if (template.type == LedgerEntryType.INCOME) IncomeTint else ExpenseTint

    Surface(
        modifier = Modifier.width(220.dp),
        shape = RoundedCornerShape(24.dp),
        color = accentColor.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = template.title,
                style = MaterialTheme.typography.titleLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = accentColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = template.account,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor
                    )
                }
                if (template.recurrence != LedgerTemplateRecurrence.NONE) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = template.recurrence.displayName(),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Text(
                text = "${template.category} ${formatCurrency(template.amountInCents)}",
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                color = accentColor
            )
            if (template.nextDueAt != null && template.recurrence != LedgerTemplateRecurrence.NONE) {
                Text(
                    text = "\u4e0b\u6b21\u81ea\u52a8\u8bb0\u8d26 ${formatTemplateDueTime(template.nextDueAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (template.note.isNotBlank()) {
                Text(
                    text = template.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onApplyClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text("\u5957\u7528")
                }
                OutlinedButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("\u5220\u9664")
                }
            }
        }
    }
}

@Composable
private fun EntryEditorSection(
    accountOptions: List<String>,
    categoryOptions: List<String>,
    form: LedgerFormState,
    onTypeSelected: (LedgerEntryType) -> Unit,
    onAmountChanged: (String) -> Unit,
    onAccountChanged: (String) -> Unit,
    onCategoryChanged: (String) -> Unit,
    onNoteChanged: (String) -> Unit,
    onSuggestedAccountSelected: (String) -> Unit,
    onSuggestedCategorySelected: (String) -> Unit,
    onTemplateRecurrenceSelected: (LedgerTemplateRecurrence) -> Unit,
    onSaveClick: () -> Unit,
    onCancelEditClick: () -> Unit,
    onSaveTemplateClick: () -> Unit,
    onScanReceiptClick: () -> Unit,
    isReceiptScanning: Boolean
) {
    val accentColor = if (form.type == LedgerEntryType.INCOME) IncomeTint else ExpenseTint
    val accountSelectionOptions = remember(accountOptions, form.account) {
        (accountSuggestions() + accountOptions + listOf(form.account))
            .filter { option -> option.isNotBlank() }
            .distinct()
    }
    val categorySelectionOptions = remember(categoryOptions, form.type, form.category) {
        (categorySuggestionsFor(form.type) + categoryOptions + listOf(form.category))
            .filter { option -> option.isNotBlank() }
            .distinct()
    }
    val advancedByDefault = form.isEditing ||
        form.note.isNotBlank() ||
        form.templateRecurrence != LedgerTemplateRecurrence.NONE ||
        form.receiptText.isNotBlank()
    var showAdvanced by rememberSaveable(
        form.isEditing,
        form.note,
        form.templateRecurrence,
        form.receiptText
    ) {
        mutableStateOf(advancedByDefault)
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SectionShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (form.isEditing) "\u7f16\u8f91\u8bb0\u5f55" else "\u8bb0\u4e00\u7b14",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (form.isEditing) {
                            "\u4fee\u6539\u91d1\u989d\u3001\u8d26\u6237\u3001\u5206\u7c7b"
                        } else {
                            "\u5148\u586b\u91d1\u989d\u548c\u5206\u7c7b\uff0c\u8be6\u7ec6\u9879\u53ef\u4ee5\u5c55\u5f00"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = accentColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = form.type.displayName(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor
                    )
                }
            }

            AnimatedVisibility(visible = form.isEditing) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = "\u5f53\u524d\u662f\u7f16\u8f91\u6a21\u5f0f\uff0c\u4fdd\u5b58\u540e\u4f1a\u76f4\u63a5\u8986\u76d6\u539f\u8bb0\u5f55\u3002",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = false,
                        onClick = onScanReceiptClick,
                        label = {
                            Text(
                                if (isReceiptScanning) "\u8bc6\u522b\u4e2d..." else "\u626b\u5c0f\u7968"
                            )
                        }
                    )
                }
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
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Monospace
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(18.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SelectionField(
                    value = form.account,
                    label = "\u8d26\u6237",
                    options = accountSelectionOptions,
                    modifier = Modifier.weight(1f),
                    onOptionSelected = { selectedAccount ->
                        onSuggestedAccountSelected(selectedAccount)
                    },
                    onCustomValueAdded = { customAccount ->
                        onAccountChanged(customAccount)
                    }
                )
                SelectionField(
                    value = form.category,
                    label = "\u5206\u7c7b",
                    options = categorySelectionOptions,
                    modifier = Modifier.weight(1f),
                    onOptionSelected = { selectedCategory ->
                        onSuggestedCategorySelected(selectedCategory)
                    },
                    onCustomValueAdded = { customCategory ->
                        onCategoryChanged(customCategory)
                    }
                )
            }

            AnimatedVisibility(visible = form.errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = form.errorMessage.orEmpty(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (form.isEditing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSaveClick,
                        modifier = Modifier.weight(1.15f),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Text("\u4fdd\u5b58\u4fee\u6539")
                    }
                    OutlinedButton(
                        onClick = onCancelEditClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("\u53d6\u6d88\u7f16\u8f91")
                    }
                }
            } else {
                Button(
                    onClick = onSaveClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text("\u4fdd\u5b58\u8bb0\u5f55")
                }
            }

            TextButton(
                onClick = { showAdvanced = !showAdvanced },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    if (showAdvanced) "\u6536\u8d77\u8be6\u7ec6\u9879" else "\u5c55\u5f00\u8be6\u7ec6\u9879"
                )
            }

            AnimatedVisibility(visible = showAdvanced) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = form.note,
                        onValueChange = onNoteChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text("\u5907\u6ce8")
                        },
                        minLines = 1,
                        maxLines = 2,
                        shape = RoundedCornerShape(18.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionEyebrow("\u6a21\u677f")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(LedgerTemplateRecurrence.entries.toList()) { recurrence ->
                                FilterChip(
                                    selected = recurrence == form.templateRecurrence,
                                    onClick = {
                                        onTemplateRecurrenceSelected(recurrence)
                                    },
                                    label = {
                                        Text(recurrence.displayName())
                                    }
                                )
                            }
                        }
                    }

                    if (form.receiptText.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "\u6700\u8fd1\u4e00\u6b21\u8bc6\u522b\u7ed3\u679c",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = form.receiptText.take(120),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = if (form.templateRecurrence == LedgerTemplateRecurrence.NONE) {
                                "\u8be6\u7ec6\u9879\u91cc\u53ef\u4ee5\u7edf\u4e00\u8bbe\u5907\u6ce8\u3001\u5b58\u6210\u6a21\u677f\uff0c\u6216\u8005\u76f4\u63a5\u626b\u5c0f\u7968\u586b\u5145\u4fe1\u606f\u3002"
                            } else {
                                "\u5f53\u524d\u6a21\u677f\u4f1a\u6309${form.templateRecurrence.displayName()}\u81ea\u52a8\u8865\u8d26\u3002"
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onScanReceiptClick,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                if (isReceiptScanning) "\u8bc6\u522b\u4e2d..." else "\u8bc6\u522b\u5c0f\u7968"
                            )
                        }
                        OutlinedButton(
                            onClick = onSaveTemplateClick,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                if (form.templateRecurrence == LedgerTemplateRecurrence.NONE) {
                                    "\u5b58\u4e3a\u6a21\u677f"
                                } else {
                                    "\u5b58\u4e3a\u5468\u671f"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionField(
    value: String,
    label: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    onOptionSelected: (String) -> Unit,
    onCustomValueAdded: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showCustomDialog by rememberSaveable { mutableStateOf(false) }
    var customValue by rememberSaveable(label, value) { mutableStateOf(value) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            modifier = Modifier
                .menuAnchor(
                    type = MenuAnchorType.PrimaryNotEditable,
                    enabled = true
                )
                .fillMaxWidth(),
            readOnly = true,
            singleLine = true,
            label = {
                Text(label)
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            shape = RoundedCornerShape(18.dp)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(option)
                    },
                    onClick = {
                        expanded = false
                        onOptionSelected(option)
                    }
                )
            }
            DropdownMenuItem(
                text = {
                    Text("\u81ea\u5b9a\u4e49\u6dfb\u52a0")
                },
                onClick = {
                    expanded = false
                    customValue = value
                    showCustomDialog = true
                }
            )
        }
    }

    if (showCustomDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showCustomDialog = false
            },
            title = {
                Text("\u81ea\u5b9a\u4e49$label")
            },
            text = {
                OutlinedTextField(
                    value = customValue,
                    onValueChange = { input ->
                        customValue = input.take(12)
                    },
                    singleLine = true,
                    label = {
                        Text(label)
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val normalized = customValue.trim()
                        if (normalized.isNotBlank()) {
                            onCustomValueAdded(normalized)
                            showCustomDialog = false
                        }
                    }
                ) {
                    Text("\u786e\u5b9a")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCustomDialog = false
                    }
                ) {
                    Text("\u53d6\u6d88")
                }
            }
        )
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
            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(type.displayName())
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(18.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(type.displayName())
        }
    }
}

@Composable
private fun AutomationSection(
    status: NotificationAutomationStatus,
    onOpenNotificationAccess: () -> Unit,
    onOpenAccessibilityAccess: () -> Unit,
    onOpenWeChat: () -> Unit,
    onOpenAlipay: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SectionShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionHeading(
                title = "\u81ea\u52a8\u8bb0\u8d26",
                subtitle = "\u901a\u77e5\u8bbf\u95ee\u548c\u65e0\u969c\u788d\u8bfb\u5c4f\u90fd\u53ef\u4ee5\u81ea\u52a8\u5bfc\u5165\uff0c\u652f\u4ed8\u6210\u529f\u9875\u3001\u6536\u6b3e\u6210\u529f\u9875\u548c\u901a\u77e5\u90fd\u4f1a\u88ab\u5c1d\u8bd5\u8bc6\u522b"
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    AutomationStatusPill(
                        label = if (status.notificationAccessEnabled) "\u901a\u77e5\u5df2\u5f00"
                        else "\u901a\u77e5\u672a\u5f00",
                        active = status.notificationAccessEnabled
                    )
                }
                item {
                    AutomationStatusPill(
                        label = if (status.accessibilityAccessEnabled) "\u65e0\u969c\u788d\u5df2\u5f00"
                        else "\u65e0\u969c\u788d\u672a\u5f00",
                        active = status.accessibilityAccessEnabled
                    )
                }
                item {
                    AutomationStatusPill(
                        label = if (status.isWeChatInstalled) "\u5fae\u4fe1\u5df2\u5b89\u88c5"
                        else "\u5fae\u4fe1\u672a\u5b89\u88c5",
                        active = status.isWeChatInstalled
                    )
                }
                item {
                    AutomationStatusPill(
                        label = if (status.isAlipayInstalled) "\u652f\u4ed8\u5b9d\u5df2\u5b89\u88c5"
                        else "\u652f\u4ed8\u5b9d\u672a\u5b89\u88c5",
                        active = status.isAlipayInstalled
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = if (status.notificationAccessEnabled && status.accessibilityAccessEnabled) {
                        "\u5f53\u524d\u5df2\u540c\u65f6\u6253\u5f00\u901a\u77e5\u8bbf\u95ee\u548c\u65e0\u969c\u788d\u8bfb\u5c4f\u3002\u80cc\u666f\u901a\u77e5\u3001\u652f\u4ed8\u7ed3\u679c\u9875\u3001\u6536\u6b3e\u9875\u90fd\u4f1a\u88ab\u5c1d\u8bd5\u8bc6\u522b\u3002"
                    } else if (status.notificationAccessEnabled || status.accessibilityAccessEnabled) {
                        "\u81ea\u52a8\u8bb0\u8d26\u5df2\u5f00\u542f\u4e00\u90e8\u5206\u80fd\u529b\uff0c\u4f46\u8fd8\u6ca1\u5168\u90e8\u6253\u901a\u3002\u5efa\u8bae\u5c06\u901a\u77e5\u8bbf\u95ee\u548c\u65e0\u969c\u788d\u90fd\u6253\u5f00\uff0c\u8ba9\u901a\u77e5\u548c\u652f\u4ed8\u6210\u529f\u9875\u90fd\u80fd\u8fdb\u5165\u89e3\u6790\u3002"
                    } else {
                        "\u8981\u8ba9\u81ea\u52a8\u8bb0\u8d26\u751f\u6548\uff0c\u8bf7\u5148\u5728\u7cfb\u7edf\u8bbe\u7f6e\u91cc\u6253\u5f00\u901a\u77e5\u8bbf\u95ee\u548c\u65e0\u969c\u788d\u670d\u52a1\u3002\u8fd9\u6837\u4e0d\u9700\u8981\u5fae\u4fe1/\u652f\u4ed8\u5b9d\u79c1\u6709\u63a5\u53e3\u4e5f\u80fd\u62ff\u5230\u4ea4\u6613\u7ed3\u679c\u3002"
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "\u53d7\u7b2c\u4e09\u65b9 App \u9650\u5236\uff0c\u4e0d\u4fdd\u8bc1\u80fd\u8df3\u5230\u6307\u5b9a\u4ed8\u6b3e\u7801\u6216\u6536\u6b3e\u7801\u9875\u3002\u4f46\u53ea\u8981\u4ea4\u6613\u540e\u663e\u793a\u6210\u529f\u9875\u6216\u53d1\u51fa\u901a\u77e5\uff0c\u5c31\u4f1a\u5c1d\u8bd5\u81ea\u52a8\u5165\u8d26\u3002",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onOpenNotificationAccess,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(if (status.notificationAccessEnabled) "\u901a\u77e5\u6743\u9650" else "\u6253\u5f00\u901a\u77e5")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onOpenAccessibilityAccess,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(if (status.accessibilityAccessEnabled) "\u65e0\u969c\u788d\u6743\u9650" else "\u6253\u5f00\u65e0\u969c\u788d")
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = "\u53ef\u5728\u4e0b\u65b9\u201c\u6700\u8fd1\u81ea\u52a8\u8bb0\u8d26\u72b6\u6001\u201d\u91cc\u770b\u6700\u8fd1\u4e00\u6b21\u8bc6\u522b\u5230\u7684\u6587\u672c",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onOpenWeChat,
                    modifier = Modifier.weight(1f),
                    enabled = status.isWeChatInstalled,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("\u6253\u5f00\u5fae\u4fe1")
                }
                OutlinedButton(
                    onClick = onOpenAlipay,
                    modifier = Modifier.weight(1f),
                    enabled = status.isAlipayInstalled,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("\u6253\u5f00\u652f\u4ed8\u5b9d")
                }
            }
        }
    }
}

@Composable
private fun AutomationStatusPill(
    label: String,
    active: Boolean
) {
    Surface(
        color = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun AutomationTraceSection(trace: LedgerAutomationTrace) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SectionShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeading(
                title = "\u6700\u8fd1\u81ea\u52a8\u8bb0\u8d26\u72b6\u6001",
                subtitle = "\u8fd9\u91cc\u4f1a\u663e\u793a\u6700\u8fd1\u4e00\u6b21\u6293\u5230\u7684\u901a\u77e5/\u9875\u9762\u6587\u5b57\uff0c\u7528\u6765\u5224\u65ad\u4e3a\u4ec0\u4e48\u6ca1\u5165\u8d26"
            )

            if (!trace.isAvailable) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Text(
                        text = "\u6682\u65f6\u8fd8\u6ca1\u6293\u5230\u5fae\u4fe1/\u652f\u4ed8\u5b9d\u7684\u4ea4\u6613\u7ed3\u679c\u3002\u5b8c\u6210\u4e00\u7b14\u4ed8\u6b3e\u6216\u6536\u6b3e\u540e\uff0c\u56de\u6765\u8fd9\u91cc\u770b\u6700\u8fd1\u8bb0\u5f55\u3002",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = trace.summary,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "\u6765\u6e90\uff1a${trace.sourceLabel}  ${formatEntryTime(trace.happenedAt)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (trace.rawText.isNotBlank()) {
                            Surface(
                                color = Color.White.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Text(
                                    text = trace.rawText.take(220),
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolSection(
    onExportClick: () -> Unit,
    onExportCsvClick: () -> Unit,
    onPreviewBackupClick: () -> Unit,
    onImportStatementClick: () -> Unit,
    onImportClick: () -> Unit,
    onMergeImportClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SectionShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionHeading(
                title = "\u5907\u4efd\u548c\u8fc1\u79fb",
                subtitle = "\u652f\u6301 JSON \u5907\u4efd\u3001CSV \u5bfc\u51fa\u3001\u8d26\u5355 CSV \u5bfc\u5165\u3001\u5bfc\u5165\u9884\u89c8\u3001\u5408\u5e76\u6216\u8986\u76d6\u5bfc\u5165"
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onExportClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("\u5bfc\u51fa JSON")
                }
                OutlinedButton(
                    onClick = onExportCsvClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("\u5bfc\u51fa CSV")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onPreviewBackupClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("\u9884\u89c8\u5907\u4efd")
                }
                OutlinedButton(
                    onClick = onMergeImportClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("\u5408\u5e76\u5bfc\u5165")
                }
            }

            OutlinedButton(
                onClick = onImportStatementClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("\u5bfc\u5165\u8d26\u5355 CSV\uff08\u5408\u5e76\u8fdb\u73b0\u6709\u8d26\u672c\uff09")
            }

            OutlinedButton(
                onClick = onImportClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("\u8986\u76d6\u5bfc\u5165\uff08\u4f1a\u66ff\u6362\u73b0\u6709\u8d26\u672c\uff09")
            }
        }
    }
}

@Composable
private fun PendingImportReviewSection(
    pendingImports: List<PendingLedgerImport>,
    onApprovePendingImport: (PendingLedgerImport) -> Unit,
    onIgnorePendingImport: (PendingLedgerImport) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SectionShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionHeading(
                title = "\u81ea\u52a8\u5bfc\u5165\u5ba1\u6838\u7bb1",
                subtitle = if (pendingImports.isEmpty()) {
                    "\u5fae\u4fe1/\u652f\u4ed8\u5b9d\u8bc6\u522b\u5230\u7684\u8d26\u5355\u4f1a\u5148\u653e\u5728\u8fd9\u91cc\uff0c\u786e\u8ba4\u540e\u518d\u5165\u8d26"
                } else {
                    "\u6709 ${pendingImports.size} \u7b14\u5019\u9009\u8bb0\u5f55\u7b49\u5f85\u786e\u8ba4\uff0c\u53ef\u4ee5\u907f\u514d\u8bef\u5165\u8d26"
                }
            )

            if (pendingImports.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Text(
                        text = "\u5f53\u81ea\u52a8\u8bb0\u8d26\u8bc6\u522b\u5230\u4ea4\u6613\u540e\uff0c\u4e0d\u4f1a\u76f4\u63a5\u5199\u5165\u8d26\u672c\uff0c\u4f60\u53ef\u4ee5\u5728\u8fd9\u91cc\u6838\u5bf9\u91d1\u989d\u3001\u8d26\u6237\u548c\u5206\u7c7b\u3002",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    pendingImports.take(6).forEach { pendingImport ->
                        PendingImportCard(
                            pendingImport = pendingImport,
                            onApprove = { onApprovePendingImport(pendingImport) },
                            onIgnore = { onIgnorePendingImport(pendingImport) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingImportCard(
    pendingImport: PendingLedgerImport,
    onApprove: () -> Unit,
    onIgnore: () -> Unit
) {
    val accentColor = if (pendingImport.type == LedgerEntryType.INCOME) IncomeTint else ExpenseTint
    Surface(
        color = accentColor.copy(alpha = 0.08f),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${pendingImport.account} / ${pendingImport.category}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = formatCurrency(pendingImport.amountInCents),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    color = accentColor
                )
            }
            Text(
                text = pendingImport.note.ifBlank { pendingImport.receiptText.take(60) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatEntryTime(pendingImport.happenedAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("\u786e\u8ba4\u5165\u8d26")
                }
                OutlinedButton(
                    onClick = onIgnore,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("\u5ffd\u7565")
                }
            }
        }
    }
}

@Composable
private fun ProfileManagementSection(
    profileConfig: LedgerProfileConfig,
    accounts: List<String>,
    expenseCategories: List<String>,
    incomeCategories: List<String>,
    onAddAccount: (String) -> Unit,
    onAddCategory: (LedgerEntryType, String) -> Unit,
    onRenameAccount: (String, String) -> Unit,
    onRenameCategory: (LedgerEntryType, String, String) -> Unit
) {
    var accountInput by rememberSaveable { mutableStateOf("") }
    var expenseCategoryInput by rememberSaveable { mutableStateOf("") }
    var incomeCategoryInput by rememberSaveable { mutableStateOf("") }
    var renameSource by rememberSaveable { mutableStateOf("") }
    var renameTarget by rememberSaveable { mutableStateOf("") }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SectionShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionHeading(
                title = "\u5206\u7c7b\u548c\u8d26\u6237\u7ba1\u7406",
                subtitle = "\u53ef\u4ee5\u589e\u52a0\u5e38\u7528\u8d26\u6237/\u5206\u7c7b\uff0c\u4e5f\u53ef\u4ee5\u5c06\u65e7\u540d\u79f0\u6279\u91cf\u91cd\u547d\u540d"
            )

            ProfileInputRow(
                value = accountInput,
                onValueChange = { accountInput = it.take(12) },
                label = "\u65b0\u8d26\u6237",
                buttonText = "\u6dfb\u52a0\u8d26\u6237",
                onSubmit = {
                    onAddAccount(accountInput)
                    accountInput = ""
                }
            )
            ProfileInputRow(
                value = expenseCategoryInput,
                onValueChange = { expenseCategoryInput = it.take(12) },
                label = "\u65b0\u652f\u51fa\u5206\u7c7b",
                buttonText = "\u6dfb\u52a0\u652f\u51fa",
                onSubmit = {
                    onAddCategory(LedgerEntryType.EXPENSE, expenseCategoryInput)
                    expenseCategoryInput = ""
                }
            )
            ProfileInputRow(
                value = incomeCategoryInput,
                onValueChange = { incomeCategoryInput = it.take(12) },
                label = "\u65b0\u6536\u5165\u5206\u7c7b",
                buttonText = "\u6dfb\u52a0\u6536\u5165",
                onSubmit = {
                    onAddCategory(LedgerEntryType.INCOME, incomeCategoryInput)
                    incomeCategoryInput = ""
                }
            )

            SectionEyebrow("\u5df2\u6536\u85cf")
            Text(
                text = "\u81ea\u5b9a\u4e49\u8d26\u6237 ${profileConfig.customAccounts.size} \u4e2a / \u652f\u51fa\u5206\u7c7b ${profileConfig.customExpenseCategories.size} \u4e2a / \u6536\u5165\u5206\u7c7b ${profileConfig.customIncomeCategories.size} \u4e2a",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(accounts.take(12)) { account ->
                    FilterChip(
                        selected = renameSource == account,
                        onClick = { renameSource = account },
                        label = { Text(account) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = renameSource,
                    onValueChange = { renameSource = it.take(12) },
                    modifier = Modifier.weight(1f),
                    label = { Text("\u65e7\u540d\u79f0") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )
                OutlinedTextField(
                    value = renameTarget,
                    onValueChange = { renameTarget = it.take(12) },
                    modifier = Modifier.weight(1f),
                    label = { Text("\u65b0\u540d\u79f0") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        onRenameAccount(renameSource, renameTarget)
                        renameSource = ""
                        renameTarget = ""
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("\u91cd\u547d\u540d\u8d26\u6237")
                }
                OutlinedButton(
                    onClick = {
                        onRenameCategory(LedgerEntryType.EXPENSE, renameSource, renameTarget)
                        renameSource = ""
                        renameTarget = ""
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("\u91cd\u547d\u540d\u652f\u51fa")
                }
            }
            OutlinedButton(
                onClick = {
                    onRenameCategory(LedgerEntryType.INCOME, renameSource, renameTarget)
                    renameSource = ""
                    renameTarget = ""
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("\u91cd\u547d\u540d\u6536\u5165\u5206\u7c7b")
            }
        }
    }
}

@Composable
private fun ProfileInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    buttonText: String,
    onSubmit: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            label = { Text(label) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp)
        )
        Button(
            onClick = onSubmit,
            enabled = value.isNotBlank(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(buttonText)
        }
    }
}

@Composable
private fun AutomationRuleSection(
    rules: List<LedgerAutomationRule>,
    accounts: List<String>,
    expenseCategories: List<String>,
    incomeCategories: List<String>,
    onAddAutomationRule: (String, LedgerEntryType, String, String) -> Unit,
    onDeleteAutomationRule: (LedgerAutomationRule) -> Unit
) {
    var keywordInput by rememberSaveable { mutableStateOf("") }
    var categoryInput by rememberSaveable { mutableStateOf("") }
    var accountInput by rememberSaveable { mutableStateOf("") }
    var ruleTypeName by rememberSaveable { mutableStateOf(LedgerEntryType.EXPENSE.name) }

    val ruleType = remember(ruleTypeName) { LedgerEntryType.valueOf(ruleTypeName) }
    val categoryOptions = remember(ruleType, expenseCategories, incomeCategories) {
        when (ruleType) {
            LedgerEntryType.EXPENSE -> expenseCategories
            LedgerEntryType.INCOME -> incomeCategories
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SectionShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionHeading(
                title = "\u81ea\u52a8\u5206\u7c7b\u89c4\u5219",
                subtitle = "\u5bfc\u5165\u8d26\u5355\u6216\u81ea\u52a8\u8bb0\u8d26\u547d\u4e2d\u5173\u952e\u8bcd\u540e\uff0c\u4f1a\u76f4\u63a5\u5957\u7528\u4f60\u6307\u5b9a\u7684\u5206\u7c7b\u548c\u8d26\u6237"
            )

            OutlinedTextField(
                value = keywordInput,
                onValueChange = { keywordInput = it.take(20) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("\u5339\u914d\u5173\u952e\u8bcd") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )

            SectionEyebrow("\u89c4\u5219\u7c7b\u578b")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LedgerEntryType.entries.toList()) { type ->
                    FilterChip(
                        selected = type == ruleType,
                        onClick = { ruleTypeName = type.name },
                        label = { Text(type.displayName()) }
                    )
                }
            }

            OutlinedTextField(
                value = categoryInput,
                onValueChange = { categoryInput = it.take(12) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("\u81ea\u52a8\u5f52\u7c7b\u5230") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )

            OutlinedTextField(
                value = accountInput,
                onValueChange = { accountInput = it.take(12) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("\u81ea\u52a8\u5207\u6362\u8d26\u6237\uff08\u53ef\u9009\uff09") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )

            if (categoryOptions.isNotEmpty()) {
                SectionEyebrow("\u5feb\u901f\u9009\u5206\u7c7b")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categoryOptions.take(12)) { category ->
                        FilterChip(
                            selected = categoryInput == category,
                            onClick = { categoryInput = category },
                            label = { Text(category) }
                        )
                    }
                }
            }

            if (accounts.isNotEmpty()) {
                SectionEyebrow("\u5feb\u901f\u9009\u8d26\u6237")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(accounts.take(12)) { account ->
                        FilterChip(
                            selected = accountInput == account,
                            onClick = { accountInput = account },
                            label = { Text(account) }
                        )
                    }
                }
            }

            Button(
                onClick = {
                    onAddAutomationRule(keywordInput, ruleType, categoryInput, accountInput)
                    keywordInput = ""
                    categoryInput = ""
                    accountInput = ""
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = keywordInput.isNotBlank() && categoryInput.isNotBlank(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("\u4fdd\u5b58\u89c4\u5219")
            }

            SectionEyebrow("\u89c4\u5219\u8bf4\u660e")
            Text(
                text = "\u540c\u4e00\u7c7b\u578b\u4e0b\u4f1a\u4f18\u5148\u5339\u914d\u66f4\u957f\u7684\u5173\u952e\u8bcd\uff0c\u5982\u679c\u5173\u952e\u8bcd\u957f\u5ea6\u4e00\u6837\uff0c\u5219\u4f18\u5148\u4f7f\u7528\u66f4\u65b0\u4fdd\u5b58\u7684\u89c4\u5219\u3002"
                    + if (rules.isEmpty()) {
                        "\u73b0\u5728\u8fd8\u6ca1\u6709\u89c4\u5219\uff0c\u53ef\u4ee5\u5148\u4e3a\u5e38\u89c1\u5546\u6237\u914d\u4e00\u6279\u3002"
                    } else {
                        ""
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (rules.isEmpty()) {
                EmptyLedgerSection(
                    title = "\u8fd8\u6ca1\u6709\u81ea\u52a8\u5206\u7c7b\u89c4\u5219",
                    subtitle = "\u6bd4\u5982\u53ef\u4ee5\u8bbe\u7f6e\u300c\u7f8e\u56e2\u300d\u81ea\u52a8\u5f52\u5230\u9910\u996e\uff0c\u6216\u628a\u300c\u5de5\u8d44\u300d\u81ea\u52a8\u5207\u5230\u94f6\u884c\u5361\u3002"
                )
            } else {
                SectionEyebrow("\u5df2\u4fdd\u5b58 ${rules.size} \u6761")
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rules.forEach { rule ->
                        AutomationRuleCard(
                            rule = rule,
                            onDeleteAutomationRule = { onDeleteAutomationRule(rule) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationRuleCard(
    rule: LedgerAutomationRule,
    onDeleteAutomationRule: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "\u5173\u952e\u8bcd\uff1a${rule.keyword}",
                    style = MaterialTheme.typography.titleMedium
                )
                Surface(
                    color = if (rule.type == LedgerEntryType.INCOME) {
                        IncomeTint.copy(alpha = 0.12f)
                    } else {
                        ExpenseTint.copy(alpha = 0.12f)
                    },
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = rule.type.displayName(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (rule.type == LedgerEntryType.INCOME) IncomeTint else ExpenseTint
                    )
                }
            }

            Text(
                text = if (rule.account.isBlank()) {
                    "\u547d\u4e2d\u540e\u81ea\u52a8\u5f52\u5230 ${rule.category}\uff0c\u4fdd\u7559\u539f\u8d26\u6237"
                } else {
                    "\u547d\u4e2d\u540e\u81ea\u52a8\u5f52\u5230 ${rule.category}\uff0c\u5e76\u5207\u6362\u5230 ${rule.account}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TextButton(
                onClick = onDeleteAutomationRule,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("\u5220\u9664")
            }
        }
    }
}

@Composable
private fun SecuritySection(
    securityConfig: LedgerSecurityConfig,
    isLocked: Boolean,
    onUnlockPin: (String) -> Unit,
    onSetPin: (String) -> Unit,
    onDisablePin: () -> Unit,
    onLockNow: () -> Unit
) {
    var pinInput by rememberSaveable { mutableStateOf("") }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = SectionShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionHeading(
                title = "\u9690\u79c1\u9501",
                subtitle = if (securityConfig.isPinEnabled) {
                    "\u5df2\u5f00\u542f PIN \u9501\uff0c\u53ef\u4ee5\u624b\u52a8\u9501\u5b9a\u6216\u5173\u95ed"
                } else {
                    "\u8bbe\u7f6e 4 \u4f4d\u4ee5\u4e0a PIN\uff0c\u6253\u5f00 App \u540e\u9700\u5148\u89e3\u9501"
                }
            )
            OutlinedTextField(
                value = pinInput,
                onValueChange = { pinInput = it.filter(Char::isDigit).take(12) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        onSetPin(pinInput)
                        pinInput = ""
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(if (securityConfig.isPinEnabled) "\u66f4\u65b0 PIN" else "\u5f00\u542f")
                }
                OutlinedButton(
                    onClick = {
                        onUnlockPin(pinInput)
                        pinInput = ""
                    },
                    modifier = Modifier.weight(1f),
                    enabled = securityConfig.isPinEnabled && isLocked,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("\u89e3\u9501")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onLockNow,
                    modifier = Modifier.weight(1f),
                    enabled = securityConfig.isPinEnabled && !isLocked,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("\u7acb\u5373\u9501\u5b9a")
                }
                OutlinedButton(
                    onClick = onDisablePin,
                    modifier = Modifier.weight(1f),
                    enabled = securityConfig.isPinEnabled,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("\u5173\u95ed\u9690\u79c1\u9501")
                }
            }
        }
    }
}

@Composable
private fun PrivacyLockOverlay(onUnlockPin: (String) -> Unit) {
    var pinInput by rememberSaveable { mutableStateOf("") }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFFFF7EE),
                        Color(0xFFF4F7F1)
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(shape = SectionShape) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("\u8bb0\u8d26\u55b5\u5df2\u9501\u5b9a", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "\u8bf7\u8f93\u5165 PIN \u67e5\u770b\u8d26\u672c",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { pinInput = it.filter(Char::isDigit).take(12) },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )
                Button(
                    onClick = {
                        onUnlockPin(pinInput)
                        pinInput = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("\u89e3\u9501")
                }
            }
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
private fun EmptyLedgerSection(
    title: String = "\u8fd8\u6ca1\u6709\u7b26\u5408\u7b5b\u9009\u6761\u4ef6\u7684\u8bb0\u5f55",
    subtitle: String = "\u4f60\u53ef\u4ee5\u653e\u5bbd\u7b5b\u9009\uff0c\u6216\u8005\u5148\u65b0\u589e\u4e00\u7b14\u6536\u652f\u3002"
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = EntryShape
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
                    modifier = Modifier.size(52.dp),
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
                text = title,
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LedgerEntryCard(
    entry: LedgerEntry,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val accentColor = if (entry.type == LedgerEntryType.INCOME) IncomeTint else ExpenseTint
    var showDeleteConfirm by rememberSaveable(entry.id) { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = EntryShape
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = entry.account,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = onEditClick,
                            modifier = Modifier
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(999.dp)
                                )
                        ) {
                            Text("\u7f16\u8f91")
                        }
                        TextButton(
                            onClick = {
                                showDeleteConfirm = true
                            },
                            modifier = Modifier
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(999.dp)
                                )
                        ) {
                            Text("\u5220\u9664")
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showDeleteConfirm = false
            },
            title = {
                Text("\u786e\u8ba4\u5220\u9664")
            },
            text = {
                Text(
                    "\u786e\u5b9a\u5220\u9664\u8fd9\u7b14${entry.category} ${formatSignedCurrency(entry)}\u5417\uff1f\u5220\u9664\u540e\u4e0d\u53ef\u6062\u590d\u3002"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteClick()
                    }
                ) {
                    Text("\u5220\u9664")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                    }
                ) {
                    Text("\u53d6\u6d88")
                }
            }
        )
    }
}

private data class CategorySpend(
    val category: String,
    val amountInCents: Long
)

private data class TrendPoint(
    val label: String,
    val netInCents: Long,
    val maxAbsoluteValue: Long
)

private data class AccountSnapshot(
    val account: String,
    val incomeInCents: Long,
    val expenseInCents: Long
) {
    val balanceInCents: Long
        get() = incomeInCents - expenseInCents

    val turnoverInCents: Long
        get() = incomeInCents + expenseInCents
}

private data class ChartFocus(
    val type: LedgerEntryType,
    val category: String
)

private data class DailySummary(
    val date: LocalDate,
    val entries: List<LedgerEntry>,
    val incomeInCents: Long,
    val expenseInCents: Long
)

enum class LedgerBoard {
    ENTRY,
    DASHBOARD,
    CALENDAR,
    STATS,
    BUDGET,
    SETTINGS,
    LEDGER
}

private enum class LedgerTrendGranularity {
    WEEK,
    MONTH
}

private fun LedgerBoard.displayName(): String {
    return when (this) {
        LedgerBoard.ENTRY -> "\u8bb0\u8d26"
        LedgerBoard.DASHBOARD -> "\u4eea\u8868\u76d8"
        LedgerBoard.CALENDAR -> "\u65e5\u5386"
        LedgerBoard.STATS -> "\u5206\u6790"
        LedgerBoard.BUDGET -> "\u89c4\u5212"
        LedgerBoard.SETTINGS -> "\u8bbe\u7f6e"
        LedgerBoard.LEDGER -> "\u8d26\u672c"
    }
}

private fun LedgerBoard.subtitle(): String {
    return when (this) {
        LedgerBoard.ENTRY -> "\u65b0\u589e\u8bb0\u5f55\u548c\u6700\u8fd1\u8d26\u5355"
        LedgerBoard.DASHBOARD -> "\u4f59\u989d\u603b\u89c8\u3001\u8d26\u6237\u770b\u677f\u548c\u6838\u5fc3\u6307\u6807"
        LedgerBoard.CALENDAR -> "\u6309\u5929\u56de\u770b\u672c\u6708\u6536\u652f"
        LedgerBoard.STATS -> "\u6536\u652f\u56fe\u8868\u3001\u8d8b\u52bf\u548c\u5206\u7c7b\u660e\u7ec6"
        LedgerBoard.BUDGET -> "\u9884\u7b97\u3001\u5e38\u7528\u6a21\u677f\u548c\u5468\u671f\u6a21\u677f\u90fd\u5728\u8fd9\u91cc"
        LedgerBoard.SETTINGS -> "\u81ea\u52a8\u8bb0\u8d26\u3001\u5907\u4efd\u548c\u8fc1\u79fb"
        LedgerBoard.LEDGER -> "\u7b5b\u9009\u5e76\u7ba1\u7406\u5168\u90e8\u8d26\u76ee"
    }
}

private fun LedgerTrendGranularity.displayName(): String {
    return when (this) {
        LedgerTrendGranularity.WEEK -> "\u6309\u5468"
        LedgerTrendGranularity.MONTH -> "\u6309\u6708"
    }
}

private fun buildAvailableCategories(
    entries: List<LedgerEntry>,
    templates: List<LedgerTemplate>,
    profileConfig: LedgerProfileConfig
): List<String> {
    return (
        entries.map { entry -> entry.category } +
            templates.map { template -> template.category } +
            profileConfig.customExpenseCategories +
            profileConfig.customIncomeCategories
        )
        .filter { category -> category.isNotBlank() }
        .distinct()
        .sorted()
}

private fun buildAvailableAccounts(
    entries: List<LedgerEntry>,
    templates: List<LedgerTemplate>,
    profileConfig: LedgerProfileConfig
): List<String> {
    return (entries.map { entry -> entry.account } + templates.map { template -> template.account } +
        profileConfig.customAccounts)
        .filter { account -> account.isNotBlank() }
        .distinct()
        .sorted()
}

private fun buildAccountSnapshots(entries: List<LedgerEntry>): List<AccountSnapshot> {
    return entries
        .groupBy { entry -> entry.account }
        .map { (account, accountEntries) ->
            AccountSnapshot(
                account = account,
                incomeInCents = accountEntries
                    .filter { entry -> entry.type == LedgerEntryType.INCOME }
                    .sumOf { entry -> entry.amountInCents },
                expenseInCents = accountEntries
                    .filter { entry -> entry.type == LedgerEntryType.EXPENSE }
                    .sumOf { entry -> entry.amountInCents }
            )
        }
        .sortedWith(
            compareByDescending<AccountSnapshot> { it.turnoverInCents }
                .thenByDescending { it.balanceInCents }
                .thenBy { it.account }
        )
        .take(6)
}

private fun buildCategoryBreakdown(
    entries: List<LedgerEntry>,
    entryType: LedgerEntryType
): List<CategorySpend> {
    return entries
        .filter { entry -> entry.type == entryType }
        .groupBy { entry -> entry.category }
        .map { (category, categoryEntries) ->
            CategorySpend(
                category = category,
                amountInCents = categoryEntries.sumOf { entry -> entry.amountInCents }
            )
        }
        .sortedByDescending { categorySpend -> categorySpend.amountInCents }
        .take(4)
}

private fun buildTrendPoints(
    entries: List<LedgerEntry>,
    granularity: LedgerTrendGranularity
): List<TrendPoint> {
    val zoneId = ZoneId.systemDefault()

    val points = when (granularity) {
        LedgerTrendGranularity.MONTH -> {
            val months = (0..5).map { offset ->
                YearMonth.now(zoneId).minusMonths(offset.toLong())
            }.reversed()

            months.map { month ->
                val monthEntries = entries.filter { entry ->
                    val entryMonth = Instant.ofEpochMilli(entry.happenedAt)
                        .atZone(zoneId)
                        .toLocalDate()
                        .let(YearMonth::from)
                    entryMonth == month
                }
                val summary = LedgerSummaryCalculator.calculate(monthEntries)
                TrendPoint(
                    label = "${month.monthValue}\u6708",
                    netInCents = summary.balanceInCents,
                    maxAbsoluteValue = 0L
                )
            }
        }

        LedgerTrendGranularity.WEEK -> {
            val today = LocalDate.now(zoneId)
            val currentWeekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
            val weeks = (0..7).map { offset ->
                currentWeekStart.minusWeeks(offset.toLong())
            }.reversed()

            weeks.map { weekStart ->
                val weekEnd = weekStart.plusDays(6)
                val weekEntries = entries.filter { entry ->
                    val entryDate = Instant.ofEpochMilli(entry.happenedAt)
                        .atZone(zoneId)
                        .toLocalDate()
                    !entryDate.isBefore(weekStart) && !entryDate.isAfter(weekEnd)
                }
                val summary = LedgerSummaryCalculator.calculate(weekEntries)
                TrendPoint(
                    label = "${weekStart.monthValue}/${weekStart.dayOfMonth}",
                    netInCents = summary.balanceInCents,
                    maxAbsoluteValue = 0L
                )
            }
        }
    }

    val maxAbsolute = points.maxOfOrNull { point -> point.netInCents.absoluteValue } ?: 0L
    return points.map { point ->
        point.copy(maxAbsoluteValue = maxAbsolute)
    }
}

private fun buildDailySummaries(
    entries: List<LedgerEntry>,
    month: YearMonth
): List<DailySummary> {
    val zoneId = ZoneId.systemDefault()
    return entries
        .filter { entry ->
            val entryMonth = Instant.ofEpochMilli(entry.happenedAt)
                .atZone(zoneId)
                .toLocalDate()
                .let(YearMonth::from)
            entryMonth == month
        }
        .groupBy { entry ->
            Instant.ofEpochMilli(entry.happenedAt)
                .atZone(zoneId)
                .toLocalDate()
        }
        .map { (date, dayEntries) ->
            DailySummary(
                date = date,
                entries = dayEntries.sortedByDescending { entry -> entry.happenedAt },
                incomeInCents = dayEntries
                    .filter { entry -> entry.type == LedgerEntryType.INCOME }
                    .sumOf { entry -> entry.amountInCents },
                expenseInCents = dayEntries
                    .filter { entry -> entry.type == LedgerEntryType.EXPENSE }
                    .sumOf { entry -> entry.amountInCents }
            )
        }
        .sortedByDescending { dailySummary -> dailySummary.date }
}

private fun buildBudgetAlerts(
    budgetConfig: LedgerBudgetConfig,
    currentMonthEntries: List<LedgerEntry>
): List<String> {
    val expenseEntries = currentMonthEntries.filter { entry -> entry.type == LedgerEntryType.EXPENSE }
    val alerts = mutableListOf<String>()
    val totalSpent = expenseEntries.sumOf { entry -> entry.amountInCents }
    budgetConfig.monthlyBudgetInCents?.let { monthlyBudget ->
        val ratio = budgetRatio(totalSpent, monthlyBudget)
        if (ratio >= 0.8f) {
            alerts += "\u672c\u6708\u603b\u9884\u7b97\u5df2\u4f7f\u7528 ${(ratio * 100).roundToInt()}%\uff0c\u5df2\u82b1 ${formatCurrency(totalSpent)} / ${formatCurrency(monthlyBudget)}"
        }
    }
    budgetConfig.categoryBudgets.forEach { (category, budget) ->
        val spent = expenseEntries
            .filter { entry -> entry.category == category }
            .sumOf { entry -> entry.amountInCents }
        val ratio = budgetRatio(spent, budget)
        if (ratio >= 0.8f) {
            alerts += "$category \u9884\u7b97\u5df2\u4f7f\u7528 ${(ratio * 100).roundToInt()}%\uff0c\u5df2\u82b1 ${formatCurrency(spent)} / ${formatCurrency(budget)}"
        }
    }
    return alerts
}

private fun buildMonthlyInsightText(
    topExpense: CategorySpend?,
    expenseDelta: Long
): String {
    val topText = topExpense?.let { categorySpend ->
        "\u672c\u6708\u652f\u51fa\u6700\u9ad8\u7684\u5206\u7c7b\u662f ${categorySpend.category}\uff0c\u5171 ${formatCurrency(categorySpend.amountInCents)}\u3002"
    } ?: "\u672c\u6708\u8fd8\u6ca1\u6709\u652f\u51fa\u8bb0\u5f55\u3002"
    val deltaText = when {
        expenseDelta > 0L -> "\u6bd4\u4e0a\u6708\u591a\u82b1 ${formatCurrency(expenseDelta)}\uff0c\u53ef\u4ee5\u5173\u6ce8\u6700\u5927\u652f\u51fa\u5206\u7c7b\u3002"
        expenseDelta < 0L -> "\u6bd4\u4e0a\u6708\u5c11\u82b1 ${formatCurrency(expenseDelta.absoluteValue)}\uff0c\u8282\u594f\u6bd4\u4e0a\u6708\u66f4\u7a33\u3002"
        else -> "\u4e0e\u4e0a\u6708\u652f\u51fa\u6301\u5e73\u3002"
    }
    return "$topText $deltaText"
}

private fun filterEntries(
    entries: List<LedgerEntry>,
    periodFilter: LedgerPeriodFilter,
    typeFilter: LedgerEntryFilterType,
    account: String?,
    category: String?,
    query: String,
    minAmountInCents: Long?,
    maxAmountInCents: Long?
): List<LedgerEntry> {
    val zoneId = ZoneId.systemDefault()
    val now = Instant.now().atZone(zoneId)
    val periodStart = when (periodFilter) {
        LedgerPeriodFilter.THIS_MONTH -> YearMonth.from(now).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        LedgerPeriodFilter.LAST_90_DAYS -> now.minusDays(89).toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()
        LedgerPeriodFilter.ALL -> null
    }

    return entries.filter { entry ->
        val matchesPeriod = periodStart == null || entry.happenedAt >= periodStart
        val matchesType = when (typeFilter) {
            LedgerEntryFilterType.ALL -> true
            LedgerEntryFilterType.EXPENSE -> entry.type == LedgerEntryType.EXPENSE
            LedgerEntryFilterType.INCOME -> entry.type == LedgerEntryType.INCOME
        }
        val matchesAccount = account == null || entry.account == account
        val matchesCategory = category == null || entry.category == category
        val normalizedQuery = query.trim()
        val matchesQuery = normalizedQuery.isBlank() ||
            entry.account.contains(normalizedQuery, ignoreCase = true) ||
            entry.category.contains(normalizedQuery, ignoreCase = true) ||
            entry.note.contains(normalizedQuery, ignoreCase = true) ||
            entry.receiptText.contains(normalizedQuery, ignoreCase = true)
        val matchesMinAmount = minAmountInCents == null || entry.amountInCents >= minAmountInCents
        val matchesMaxAmount = maxAmountInCents == null || entry.amountInCents <= maxAmountInCents

        matchesPeriod &&
            matchesType &&
            matchesAccount &&
            matchesCategory &&
            matchesQuery &&
            matchesMinAmount &&
            matchesMaxAmount
    }
}

private fun String.toAmountFilterCents(): Long? {
    return trim().takeIf { value -> value.isNotBlank() }?.toAmountInCents()
}

private fun budgetRatio(
    spentInCents: Long,
    budgetInCents: Long?
): Float {
    if (budgetInCents == null || budgetInCents <= 0L) {
        return 0f
    }
    return (spentInCents.toFloat() / budgetInCents.toFloat()).coerceAtLeast(0f)
}

private fun budgetInsightText(
    budgetConfig: LedgerBudgetConfig,
    currentMonthSummary: LedgerSummary
): String {
    val monthlyBudget = budgetConfig.monthlyBudgetInCents
    if (monthlyBudget == null) {
        return "\u4f60\u8fd8\u6ca1\u8bbe\u7f6e\u672c\u6708\u603b\u9884\u7b97\uff0c\u53ef\u4ee5\u5728\u4e0b\u65b9\u5148\u8bbe\u5b9a\u4e00\u4e2a\u76ee\u6807\u989d\u5ea6\u3002"
    }

    val remaining = monthlyBudget - currentMonthSummary.expenseInCents
    return if (remaining >= 0L) {
        "\u8ddd\u79bb\u672c\u6708\u9884\u7b97\u8fd8\u6709 ${formatCurrency(remaining)} \u4f59\u91cf\uff0c\u53ef\u4ee5\u7ee7\u7eed\u7a33\u4f4f\u8282\u594f\u3002"
    } else {
        "\u672c\u6708\u5df2\u8d85\u51fa\u9884\u7b97 ${formatCurrency(remaining.absoluteValue)}\uff0c\u53ef\u4ee5\u4f18\u5148\u68c0\u67e5\u6392\u540d\u6700\u9ad8\u7684\u652f\u51fa\u5206\u7c7b\u3002"
    }
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

private fun formatTemplateDueTime(timestamp: Long): String {
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M\u6708d\u65e5", Locale.CHINA))
}

private val Long.absoluteValue: Long
    get() = if (this < 0) -this else this
