package com.parcelpanel.ui

import android.content.Intent
import android.net.Uri
import com.parcelpanel.BuildConfig
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.parcelpanel.data.CarrierProfileEntity
import com.parcelpanel.model.CarrierMatch
import com.parcelpanel.model.NormalizedStatus
import com.parcelpanel.model.ParcelDetail
import com.parcelpanel.model.ParcelSummary
import com.parcelpanel.model.SyncEntry
import com.parcelpanel.tracking.TrackingSamples
import com.parcelpanel.update.ApkInstaller
import com.parcelpanel.update.AppUpdateState
import com.parcelpanel.update.UpdateStatus
import com.parcelpanel.ui.theme.AmberWarning
import com.parcelpanel.ui.theme.BorderSoft
import com.parcelpanel.ui.theme.CloudText
import com.parcelpanel.ui.theme.InkBlack
import com.parcelpanel.ui.theme.MintSuccess
import com.parcelpanel.ui.theme.MistText
import com.parcelpanel.ui.theme.NightSurfaceAlt
import com.parcelpanel.ui.theme.RoseError
import com.parcelpanel.ui.theme.TealPrimary
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private const val ROUTE_INBOX = "inbox"
private const val ROUTE_ADD = "add"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_DETAIL = "detail"

private data class NavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun ParcelPanelApp(
    viewModel: ParcelViewModel,
    incomingIntent: Intent?,
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val inbox by viewModel.inbox.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val carriers by viewModel.carriers.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val appUpdateState by viewModel.appUpdateState.collectAsStateWithLifecycle()
    val pendingSharedText by viewModel.pendingSharedText.collectAsState()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination
    val scope = rememberCoroutineScope()

    LaunchedEffect(incomingIntent) {
        viewModel.handleIncomingIntent(incomingIntent)
    }
    LaunchedEffect(Unit) {
        viewModel.checkForUpdates(manual = false)
    }

    val bottomNavItems = listOf(
        NavItem(ROUTE_INBOX, "Inbox", Icons.Rounded.Home),
        NavItem(ROUTE_ADD, "Add", Icons.Rounded.Add),
        NavItem(ROUTE_HISTORY, "History", Icons.Rounded.History),
        NavItem(ROUTE_SETTINGS, "Settings", Icons.Rounded.Settings),
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val route = currentDestination?.route
            if (route in setOf(ROUTE_INBOX, ROUTE_ADD, ROUTE_HISTORY, ROUTE_SETTINGS)) {
                NavigationBar(
                    modifier = Modifier.navigationBarsPadding(),
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_INBOX,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(ROUTE_INBOX) {
                InboxScreen(
                    items = inbox,
                    onOpen = { navController.navigate("$ROUTE_DETAIL/$it") },
                    onAdd = { navController.navigate(ROUTE_ADD) },
                    onArchiveSelected = { itemIds ->
                        viewModel.setArchived(itemIds, archived = true) { count ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (count == 1) "1 parcel archived" else "$count parcels archived"
                                )
                            }
                        }
                    },
                    onDeleteSelected = { itemIds ->
                        viewModel.deleteTrackedItems(itemIds) { count ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (count == 1) "1 parcel deleted" else "$count parcels deleted"
                                )
                            }
                        }
                    },
                )
            }
            composable(ROUTE_ADD) {
                AddScreen(
                    prefillTracking = pendingSharedText,
                    detect = viewModel::detectCarriers,
                    onConsumePrefill = viewModel::clearPendingSharedText,
                    onSaved = { tracking, label, notes ->
                        viewModel.addTrackedItem(tracking, label, notes) { itemId ->
                            scope.launch {
                                snackbarHostState.showSnackbar("Parcel saved locally")
                            }
                            navController.navigate("$ROUTE_DETAIL/$itemId") {
                                popUpTo(ROUTE_INBOX)
                            }
                        }
                    }
                )
            }
            composable(ROUTE_HISTORY) {
                HistoryScreen(
                    items = history,
                    onOpen = { navController.navigate("$ROUTE_DETAIL/$it") },
                    onRestoreSelected = { itemIds ->
                        viewModel.setArchived(itemIds, archived = false) { count ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (count == 1) "1 parcel restored" else "$count parcels restored"
                                )
                            }
                        }
                    },
                    onDeleteSelected = { itemIds ->
                        viewModel.deleteTrackedItems(itemIds) { count ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (count == 1) "1 parcel deleted" else "$count parcels deleted"
                                )
                            }
                        }
                    },
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    carriers = carriers,
                    syncIntervalHours = settings.syncIntervalHours,
                    updateState = appUpdateState,
                    onSyncIntervalSelected = viewModel::updateSyncInterval,
                    onAutoCheckUpdatesChanged = viewModel::updateAutoCheckUpdates,
                    onCheckForUpdates = { viewModel.checkForUpdates(manual = true) },
                    onDownloadUpdate = viewModel::downloadLatestUpdate,
                    onInstallUpdate = { apkPath ->
                        val result = ApkInstaller.launchInstall(context, File(apkPath))
                        scope.launch {
                            snackbarHostState.showSnackbar(result.message)
                        }
                    },
                    onOpenReleasePage = {
                        val releasePage = appUpdateState.release?.htmlUrl ?: BuildConfig.UPDATE_RELEASES_PAGE_URL
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releasePage)))
                    },
                )
            }
            composable("$ROUTE_DETAIL/{itemId}") { backStackEntry ->
                val itemId = backStackEntry.arguments?.getString("itemId").orEmpty()
                val detail by viewModel.detail(itemId).collectAsStateWithLifecycle(initialValue = null)
                DetailScreen(
                    detail = detail,
                    onBack = { navController.navigateUp() },
                    onOpenTracker = { url ->
                        if (!url.isNullOrBlank()) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    },
                    onRefresh = {
                        viewModel.refresh(itemId) { syncEntry ->
                            scope.launch {
                                snackbarHostState.showSnackbar(syncEntry.message ?: "Refresh completed")
                            }
                        }
                    },
                    onArchiveToggle = { archived ->
                        viewModel.setArchived(itemId, archived)
                    },
                    onDelete = {
                        viewModel.deleteTrackedItem(itemId) { deleted ->
                            scope.launch {
                                if (deleted) {
                                    navController.navigateUp()
                                }
                                snackbarHostState.showSnackbar(
                                    if (deleted) "Parcel deleted" else "Parcel could not be deleted"
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun InboxScreen(
    items: List<ParcelSummary>,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onArchiveSelected: (List<String>) -> Unit,
    onDeleteSelected: (List<String>) -> Unit,
) {
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var pendingDeleteIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val currentIds = remember(items) { items.map { it.id } }

    LaunchedEffect(currentIds) {
        selectedIds = selectedIds.filter { it in currentIds }
        pendingDeleteIds = pendingDeleteIds.filter { it in currentIds }
        if (currentIds.isEmpty()) {
            selectionMode = false
            selectedIds = emptyList()
        }
    }

    pendingDeleteIds.takeIf { it.isNotEmpty() }?.let { deleteIds ->
        DeleteParcelsDialog(
            count = deleteIds.size,
            onConfirm = {
                onDeleteSelected(deleteIds)
                pendingDeleteIds = emptyList()
                selectedIds = selectedIds.filterNot { it in deleteIds }
                if (selectedIds.isEmpty()) {
                    selectionMode = false
                }
            },
            onDismiss = { pendingDeleteIds = emptyList() },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeroPanel(
                title = "Australian parcel tracking",
                subtitle = "Offline-first history, sane carrier ontology, and live scraping of official tracker pages when direct APIs are not practical."
            )
        }

        if (items.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No active parcels",
                    body = "Add your first tracking number to build a local history and start routing through the right carrier.",
                    actionLabel = "Add parcel",
                    onAction = onAdd,
                )
            }
        } else {
            item {
                SummaryStrip(items)
            }
            item {
                ParcelBulkActionCard(
                    title = "Active parcels",
                    supportingText = "Long press any card or switch into selection mode for bulk archive and delete.",
                    selectionMode = selectionMode,
                    selectedCount = selectedIds.size,
                    totalCount = items.size,
                    primaryActionLabel = "Archive",
                    primaryActionIcon = Icons.Rounded.Archive,
                    onToggleSelectionMode = {
                        selectionMode = !selectionMode
                        if (!selectionMode) {
                            selectedIds = emptyList()
                        }
                    },
                    onSelectAll = {
                        selectedIds = if (selectedIds.size == items.size) emptyList() else items.map { it.id }
                    },
                    onPrimaryAction = {
                        if (selectedIds.isNotEmpty()) {
                            onArchiveSelected(selectedIds)
                            selectionMode = false
                            selectedIds = emptyList()
                        }
                    },
                    onDelete = {
                        if (selectedIds.isNotEmpty()) {
                            pendingDeleteIds = selectedIds
                        }
                    },
                )
            }
            items(items, key = { it.id }) { item ->
                ParcelCard(
                    item = item,
                    onClick = { onOpen(item.id) },
                    selectionMode = selectionMode,
                    selected = item.id in selectedIds,
                    onSelectionToggle = { selected ->
                        selectedIds = selectedIds.withSelection(item.id, selected)
                        if (!selected && selectedIds.isEmpty()) {
                            selectionMode = false
                        }
                    },
                    onLongPress = {
                        selectionMode = true
                        selectedIds = selectedIds.withSelection(item.id, true)
                    },
                )
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    items: List<ParcelSummary>,
    onOpen: (String) -> Unit,
    onRestoreSelected: (List<String>) -> Unit,
    onDeleteSelected: (List<String>) -> Unit,
) {
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var pendingDeleteIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val currentIds = remember(items) { items.map { it.id } }

    LaunchedEffect(currentIds) {
        selectedIds = selectedIds.filter { it in currentIds }
        pendingDeleteIds = pendingDeleteIds.filter { it in currentIds }
        if (currentIds.isEmpty()) {
            selectionMode = false
            selectedIds = emptyList()
        }
    }

    pendingDeleteIds.takeIf { it.isNotEmpty() }?.let { deleteIds ->
        DeleteParcelsDialog(
            count = deleteIds.size,
            onConfirm = {
                onDeleteSelected(deleteIds)
                pendingDeleteIds = emptyList()
                selectedIds = selectedIds.filterNot { it in deleteIds }
                if (selectedIds.isEmpty()) {
                    selectionMode = false
                }
            },
            onDismiss = { pendingDeleteIds = emptyList() },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text = "History",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.statusBarsPadding()
            )
        }
        if (items.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No archived parcels yet",
                    body = "Delivered, returned, or manually archived items will appear here.",
                    actionLabel = "Nothing to do",
                    onAction = {}
                )
            }
        } else {
            item {
                ParcelBulkActionCard(
                    title = "Archived parcels",
                    supportingText = "Restore shipments back into the inbox or remove them permanently in one pass.",
                    selectionMode = selectionMode,
                    selectedCount = selectedIds.size,
                    totalCount = items.size,
                    primaryActionLabel = "Restore",
                    primaryActionIcon = Icons.Rounded.Unarchive,
                    onToggleSelectionMode = {
                        selectionMode = !selectionMode
                        if (!selectionMode) {
                            selectedIds = emptyList()
                        }
                    },
                    onSelectAll = {
                        selectedIds = if (selectedIds.size == items.size) emptyList() else items.map { it.id }
                    },
                    onPrimaryAction = {
                        if (selectedIds.isNotEmpty()) {
                            onRestoreSelected(selectedIds)
                            selectionMode = false
                            selectedIds = emptyList()
                        }
                    },
                    onDelete = {
                        if (selectedIds.isNotEmpty()) {
                            pendingDeleteIds = selectedIds
                        }
                    },
                )
            }
            items(items, key = { it.id }) { item ->
                ParcelCard(
                    item = item,
                    onClick = { onOpen(item.id) },
                    selectionMode = selectionMode,
                    selected = item.id in selectedIds,
                    onSelectionToggle = { selected ->
                        selectedIds = selectedIds.withSelection(item.id, selected)
                        if (!selected && selectedIds.isEmpty()) {
                            selectionMode = false
                        }
                    },
                    onLongPress = {
                        selectionMode = true
                        selectedIds = selectedIds.withSelection(item.id, true)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddScreen(
    prefillTracking: String?,
    detect: (String) -> List<CarrierMatch>,
    onConsumePrefill: () -> Unit,
    onSaved: (tracking: String, label: String, notes: String) -> Unit,
) {
    var trackingNumber by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(prefillTracking) {
        if (!prefillTracking.isNullOrBlank() && trackingNumber.isBlank()) {
            trackingNumber = prefillTracking
            onConsumePrefill()
        }
    }

    val matches = remember(trackingNumber) { detect(trackingNumber) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "Add parcel",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.statusBarsPadding()
            )
        }
        item {
            HeroPanel(
                title = "Simple by default",
                subtitle = "Save the tracking number, keep the history local, and let ParcelPanel refresh against the carrier's official tracking page when possible."
            )
        }
        item {
            OutlinedCard(
                colors = CardDefaults.outlinedCardColors(containerColor = NightSurfaceAlt),
                border = BorderStroke(1.dp, BorderSoft),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Try format samples",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "These example IDs are for carrier detection and UI smoke-testing. They are not guaranteed to resolve as live consignments.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MistText,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TrackingSamples.formatSamples.forEach { sample ->
                            FilterChip(
                                selected = trackingNumber == sample.trackingNumber,
                                onClick = {
                                    trackingNumber = sample.trackingNumber
                                    if (label.isBlank()) {
                                        label = "${sample.label} sample"
                                    }
                                },
                                label = { Text(sample.label) },
                            )
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = trackingNumber,
                    onValueChange = { trackingNumber = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tracking number") },
                    supportingText = { Text("Share text into the app, paste manually, or scan later in a future build.") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Label") },
                    supportingText = { Text("Optional. Example: eBay order, spare parts, laptop return.") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Notes") },
                    supportingText = { Text("Optional local notes. Stored only on device in this build.") },
                )
            }
        }
        item {
            if (matches.isNotEmpty()) {
                OutlinedCard(
                    colors = CardDefaults.outlinedCardColors(containerColor = NightSurfaceAlt),
                    border = BorderStroke(1.dp, BorderSoft),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Detected carriers",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            matches.forEach { match ->
                                AssistChip(
                                    onClick = {},
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (match == matches.first()) TealPrimary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                                        labelColor = CloudText,
                                    ),
                                    label = { Text("${match.displayName} ${match.confidence}%") }
                                )
                            }
                        }
                        Text(
                            text = matches.first().reason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Button(
                onClick = { onSaved(trackingNumber, label, notes) },
                modifier = Modifier.fillMaxWidth(),
                enabled = trackingNumber.trim().length >= 8,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save parcel")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(
    carriers: List<CarrierProfileEntity>,
    syncIntervalHours: Int,
    updateState: AppUpdateState,
    onSyncIntervalSelected: (Int) -> Unit,
    onAutoCheckUpdatesChanged: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: (String) -> Unit,
    onOpenReleasePage: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.statusBarsPadding()
            )
        }
        item {
            UpdateCard(
                state = updateState,
                onAutoCheckUpdatesChanged = onAutoCheckUpdatesChanged,
                onCheckForUpdates = onCheckForUpdates,
                onDownloadUpdate = onDownloadUpdate,
                onInstallUpdate = onInstallUpdate,
                onOpenReleasePage = onOpenReleasePage,
            )
        }
        item {
            OutlinedCard(
                colors = CardDefaults.outlinedCardColors(containerColor = NightSurfaceAlt),
                border = BorderStroke(1.dp, BorderSoft),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Refresh cadence", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Periodic sync uses WorkManager and respects the chosen background interval.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MistText,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(2, 4, 8, 12).forEach { hours ->
                            FilterChip(
                                selected = syncIntervalHours == hours,
                                onClick = { onSyncIntervalSelected(hours) },
                                label = { Text("${hours}h") }
                            )
                        }
                    }
                }
            }
        }
        items(carriers, key = { it.slug }) { carrier ->
            OutlinedCard(
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, BorderSoft),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CarrierBadge(initials = carrier.initials)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(carrier.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Tier ${carrier.supportTier} • ${carrier.authMode.name.replace('_', ' ').lowercase()}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MistText,
                            )
                        }
                    }
                    Text(
                        text = carrier.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UpdateCard(
    state: AppUpdateState,
    onAutoCheckUpdatesChanged: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: (String) -> Unit,
    onOpenReleasePage: () -> Unit,
) {
    val release = state.release
    val actionEnabled = state.status != UpdateStatus.CHECKING && state.status != UpdateStatus.DOWNLOADING

    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(containerColor = NightSurfaceAlt),
        border = BorderStroke(1.dp, BorderSoft),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("App updates", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Signed APKs are fetched from the public GitHub release feed, SHA-256 checked, and matched against the installed ParcelPanel signing certificate before install.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MistText,
                    )
                }
                StatusPill(state = state)
            }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Current build v${state.currentVersionName}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = buildString {
                            append("Last checked ")
                            append(formatTimestamp(state.lastCheckedAt))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MistText,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Automatic update checks", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "Checks the latest public release at launch, throttled to twice daily.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MistText,
                            )
                        }
                        Switch(
                            checked = state.autoCheckEnabled,
                            onCheckedChange = onAutoCheckUpdatesChanged,
                        )
                    }
                }
            }

            Text(
                text = when (state.status) {
                    UpdateStatus.IDLE -> "No release check has run in this session yet."
                    UpdateStatus.CHECKING -> "Checking the latest GitHub release."
                    UpdateStatus.UP_TO_DATE -> "This install is already on the latest published version."
                    UpdateStatus.AVAILABLE -> "A newer signed ParcelPanel APK is available."
                    UpdateStatus.DOWNLOADING -> "Downloading the signed update package."
                    UpdateStatus.READY_TO_INSTALL -> "The downloaded APK passed checksum and signing validation."
                    UpdateStatus.ERROR -> state.errorMessage ?: "Update validation failed."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.status == UpdateStatus.ERROR) RoseError else MistText,
            )

            if (state.status == UpdateStatus.DOWNLOADING) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { (state.downloadProgressPercent ?: 0) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = state.downloadProgressPercent?.let { "Download ${it.coerceIn(0, 100)}%" } ?: "Preparing download",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MistText,
                    )
                }
            }

            release?.let { latest ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Latest release v${latest.versionName}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Published ${formatTimestamp(latest.publishedAt)} • ${formatFileSize(latest.apkAsset.sizeBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MistText,
                        )
                        releaseNotesPreview(latest.body)?.let { notes ->
                            Text(
                                text = notes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onCheckForUpdates,
                    enabled = actionEnabled,
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.status == UpdateStatus.CHECKING) "Checking" else "Check now")
                }
                OutlinedButton(onClick = onOpenReleasePage) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Release page")
                }
                if (state.status == UpdateStatus.AVAILABLE) {
                    Button(onClick = onDownloadUpdate) {
                        Icon(Icons.Rounded.Done, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Download APK")
                    }
                }
                if (state.status == UpdateStatus.READY_TO_INSTALL && state.downloadedApkPath != null) {
                    Button(onClick = { onInstallUpdate(state.downloadedApkPath) }) {
                        Icon(Icons.Rounded.Done, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Install APK")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(state: AppUpdateState) {
    val (label, tint) = when (state.status) {
        UpdateStatus.IDLE -> "Idle" to Color(0xFF7E8A9A)
        UpdateStatus.CHECKING -> "Checking" to TealPrimary
        UpdateStatus.UP_TO_DATE -> "Current" to MintSuccess
        UpdateStatus.AVAILABLE -> "Update" to AmberWarning
        UpdateStatus.DOWNLOADING -> "Downloading" to TealPrimary
        UpdateStatus.READY_TO_INSTALL -> "Ready" to MintSuccess
        UpdateStatus.ERROR -> "Attention" to RoseError
    }
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = tint.copy(alpha = 0.18f),
            labelColor = CloudText,
        ),
        border = null,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreen(
    detail: ParcelDetail?,
    onBack: () -> Unit,
    onOpenTracker: (String?) -> Unit,
    onRefresh: () -> Unit,
    onArchiveToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val currentDetail = detail
    if (currentDetail == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading parcel…", color = MistText)
        }
        return
    }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }

    if (showDeleteConfirmation) {
        DeleteParcelsDialog(
            count = 1,
            onConfirm = {
                showDeleteConfirmation = false
                onDelete()
            },
            onDismiss = { showDeleteConfirmation = false },
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = currentDetail.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { onArchiveToggle(!currentDetail.archived) }) {
                        Icon(
                            if (currentDetail.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                            contentDescription = if (currentDetail.archived) "Restore" else "Archive",
                        )
                    }
                    IconButton(onClick = { showDeleteConfirmation = true }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                    }
                    IconButton(
                        onClick = { onOpenTracker(currentDetail.trackerUrl) },
                        enabled = !currentDetail.trackerUrl.isNullOrBlank(),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Open tracker")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CarrierBadge(initials = currentDetail.carrierInitials)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(currentDetail.carrierDisplayName, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    currentDetail.trackingNumber,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MistText,
                                )
                            }
                        }
                        StatusChip(currentDetail.status)
                        currentDetail.serviceName?.let {
                            Text("Service: $it", color = MistText)
                        }
                        currentDetail.notes?.let {
                            Text(it, color = CloudText, style = MaterialTheme.typography.bodyMedium)
                        }
                        currentDetail.deliveredAt?.let {
                            Text("Delivered ${formatTimestamp(it)}", color = MintSuccess)
                        } ?: currentDetail.etaEnd?.let {
                            Text("ETA ${formatTimestamp(it)}", color = MistText)
                        }
                        Text(
                            text = "Last updated ${formatTimestamp(currentDetail.updatedAt)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MistText,
                        )
                    }
                }
            }
            item {
                Text("Timeline", style = MaterialTheme.typography.titleLarge)
            }
            if (currentDetail.timeline.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No live events yet",
                        body = "ParcelPanel keeps the parcel locally and now tries to scrape the official carrier tracker before falling back to direct hand-off.",
                        actionLabel = "Refresh",
                        onAction = onRefresh,
                    )
                }
            } else {
                items(currentDetail.timeline, key = { it.id }) { event ->
                    EventCard(event)
                }
            }
            item {
                Text("Recent syncs", style = MaterialTheme.typography.titleLarge)
            }
            items(currentDetail.syncEntries) { syncEntry ->
                SyncEntryCard(syncEntry)
            }
        }
    }
}

@Composable
private fun SummaryStrip(items: List<ParcelSummary>) {
    val active = items.count { !it.status.isTerminal }
    val exceptions = items.count { it.status == NormalizedStatus.EXCEPTION || it.status == NormalizedStatus.DELIVERY_ATTEMPTED }
    val outForDelivery = items.count { it.status == NormalizedStatus.OUT_FOR_DELIVERY }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        MetricCard("Active", active.toString(), TealPrimary, Modifier.weight(1f))
        MetricCard("Attention", exceptions.toString(), AmberWarning, Modifier.weight(1f))
        MetricCard("Today", outForDelivery.toString(), MintSuccess, Modifier.weight(1f))
    }
}

@Composable
private fun MetricCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, color = MistText, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, color = accent)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ParcelCard(
    item: ParcelSummary,
    onClick: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelectionToggle: (Boolean) -> Unit = {},
    onLongPress: () -> Unit = {},
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (selectionMode) {
                        onSelectionToggle(!selected)
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    if (selectionMode) {
                        onSelectionToggle(!selected)
                    } else {
                        onLongPress()
                    }
                },
            ),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BorderSoft),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CarrierBadge(initials = item.carrierInitials)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.trackingNumber, style = MaterialTheme.typography.bodyMedium, color = MistText)
                }
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { checked -> onSelectionToggle(checked) },
                    )
                } else {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MistText)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(item.status)
                Text(
                    text = formatTimestamp(item.deliveredAt ?: item.updatedAt),
                    color = MistText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun EventCard(event: com.parcelpanel.model.TimelineEvent) {
    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BorderSoft),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(statusColor(event.status))
                Spacer(Modifier.width(10.dp))
                Text(event.title, style = MaterialTheme.typography.titleMedium)
            }
            event.description?.let {
                Text(it, color = MistText, style = MaterialTheme.typography.bodyMedium)
            }
            event.location?.let {
                Text(it, color = CloudText, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = formatTimestamp(event.occurredAt),
                color = MistText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParcelBulkActionCard(
    title: String,
    supportingText: String,
    selectionMode: Boolean,
    selectedCount: Int,
    totalCount: Int,
    primaryActionLabel: String,
    primaryActionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onToggleSelectionMode: () -> Unit,
    onSelectAll: () -> Unit,
    onPrimaryAction: () -> Unit,
    onDelete: () -> Unit,
) {
    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BorderSoft),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (selectionMode) "$selectedCount selected" else title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (selectionMode) {
                            "Bulk actions apply across the visible list."
                        } else {
                            supportingText
                        },
                        color = MistText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                OutlinedButton(onClick = onToggleSelectionMode) {
                    Text(if (selectionMode) "Done" else "Select")
                }
            }
            if (selectionMode) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(onClick = onSelectAll) {
                        Icon(Icons.Rounded.SelectAll, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (selectedCount == totalCount) "Clear" else "Select all")
                    }
                    OutlinedButton(onClick = onPrimaryAction, enabled = selectedCount > 0) {
                        Icon(primaryActionIcon, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(primaryActionLabel)
                    }
                    OutlinedButton(onClick = onDelete, enabled = selectedCount > 0) {
                        Icon(Icons.Rounded.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteParcelsDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (count == 1) "Delete parcel?" else "Delete $count parcels?")
        },
        text = {
            Text("This removes the parcel, its archived/current state, and the locally stored tracking history for it.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun SyncEntryCard(entry: SyncEntry) {
    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BorderSoft),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = when (entry.result) {
                        com.parcelpanel.model.SyncResult.SUCCESS -> Icons.Rounded.Done
                        com.parcelpanel.model.SyncResult.SEE_EXTERNAL_TRACKER -> Icons.AutoMirrored.Rounded.OpenInNew
                        com.parcelpanel.model.SyncResult.SKIPPED -> Icons.Rounded.History
                        com.parcelpanel.model.SyncResult.ERROR -> Icons.Rounded.Refresh
                    },
                    contentDescription = null,
                    tint = statusColor(
                        when (entry.result) {
                            com.parcelpanel.model.SyncResult.SUCCESS -> NormalizedStatus.ACCEPTED
                            com.parcelpanel.model.SyncResult.SEE_EXTERNAL_TRACKER -> NormalizedStatus.IN_TRANSIT
                            com.parcelpanel.model.SyncResult.SKIPPED -> NormalizedStatus.UNKNOWN
                            com.parcelpanel.model.SyncResult.ERROR -> NormalizedStatus.EXCEPTION
                        }
                    )
                )
                Text(
                    text = "${entry.trigger.name.replace('_', ' ').lowercase()} • ${entry.result.name.replace('_', ' ').lowercase()}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            entry.message?.let {
                Text(it, color = MistText, style = MaterialTheme.typography.bodyMedium)
            }
            Text(formatTimestamp(entry.startedAt), color = MistText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StatusChip(status: NormalizedStatus) {
    AssistChip(
        onClick = {},
        label = { Text(status.label) },
        leadingIcon = {
            Icon(Icons.Rounded.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp))
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = statusColor(status).copy(alpha = 0.18f),
            labelColor = CloudText,
            leadingIconContentColor = statusColor(status),
        ),
        border = null,
    )
}

@Composable
private fun CarrierBadge(initials: String) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(TealPrimary, Color(0xFF248D92))))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = InkBlack,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun HeroPanel(title: String, subtitle: String) {
    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        listOf(
                            TealPrimary.copy(alpha = 0.24f),
                            MaterialTheme.colorScheme.surfaceVariant,
                            InkBlack
                        )
                    )
                )
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MistText)
        }
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BorderSoft),
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, color = MistText, style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = onAction, enabled = actionLabel != "Nothing to do") {
                Text(actionLabel)
            }
        }
    }
}

private fun List<String>.withSelection(itemId: String, selected: Boolean): List<String> =
    if (selected) {
        (this + itemId).distinct()
    } else {
        filterNot { it == itemId }
    }

private fun statusColor(status: NormalizedStatus): Color = when (status) {
    NormalizedStatus.DELIVERED -> MintSuccess
    NormalizedStatus.OUT_FOR_DELIVERY,
    NormalizedStatus.ACCEPTED,
    NormalizedStatus.IN_TRANSIT -> TealPrimary
    NormalizedStatus.CUSTOMS_OR_CLEARANCE,
    NormalizedStatus.AVAILABLE_FOR_COLLECTION,
    NormalizedStatus.DELIVERY_ATTEMPTED -> AmberWarning
    NormalizedStatus.EXCEPTION,
    NormalizedStatus.RETURNED,
    NormalizedStatus.CANCELLED -> RoseError
    NormalizedStatus.UNKNOWN,
    NormalizedStatus.LABEL_CREATED -> Color(0xFF7E8A9A)
}

private fun formatTimestamp(timestamp: Long?): String {
    if (timestamp == null) return "Unknown time"
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
    return formatter.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "Size unknown"
    val kib = 1024.0
    val mib = kib * kib
    return when {
        bytes >= mib -> String.format(Locale.ROOT, "%.1f MB", bytes / mib)
        bytes >= kib -> String.format(Locale.ROOT, "%.1f KB", bytes / kib)
        else -> "$bytes B"
    }
}

private fun releaseNotesPreview(body: String?): String? {
    val cleaned = body
        ?.lineSequence()
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.take(4)
        ?.joinToString("\n")
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return cleaned
}
