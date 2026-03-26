package com.parcelpanel.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material3.Text
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
import com.parcelpanel.ui.theme.AmberWarning
import com.parcelpanel.ui.theme.BorderSoft
import com.parcelpanel.ui.theme.CloudText
import com.parcelpanel.ui.theme.InkBlack
import com.parcelpanel.ui.theme.MintSuccess
import com.parcelpanel.ui.theme.MistText
import com.parcelpanel.ui.theme.NightSurfaceAlt
import com.parcelpanel.ui.theme.RoseError
import com.parcelpanel.ui.theme.TealPrimary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    val pendingSharedText by viewModel.pendingSharedText.collectAsState()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination
    val scope = rememberCoroutineScope()

    LaunchedEffect(incomingIntent) {
        viewModel.handleIncomingIntent(incomingIntent)
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
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    carriers = carriers,
                    syncIntervalHours = settings.syncIntervalHours,
                    onSyncIntervalSelected = viewModel::updateSyncInterval,
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
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeroPanel(
                title = "Australian parcel tracking",
                subtitle = "Offline-first history, sane carrier ontology, and quick hand-off to the right official tracker."
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
            items(items, key = { it.id }) { item ->
                ParcelCard(item = item, onClick = { onOpen(item.id) })
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    items: List<ParcelSummary>,
    onOpen: (String) -> Unit,
) {
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
            items(items, key = { it.id }) { item ->
                ParcelCard(item = item, onClick = { onOpen(item.id) })
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
                subtitle = "Save the tracking number, keep the history local, and let ParcelPanel suggest the most likely Australian carrier."
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
    onSyncIntervalSelected: (Int) -> Unit,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreen(
    detail: ParcelDetail?,
    onBack: () -> Unit,
    onOpenTracker: (String?) -> Unit,
    onRefresh: () -> Unit,
    onArchiveToggle: (Boolean) -> Unit,
) {
    val currentDetail = detail
    if (currentDetail == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading parcel…", color = MistText)
        }
        return
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
                        Icon(Icons.Rounded.Archive, contentDescription = "Archive")
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
                        body = "This v1 build keeps the parcel locally and hands off to the official tracker when direct polling is not safe to do on-device.",
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

@Composable
private fun ParcelCard(item: ParcelSummary, onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MistText)
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
            Text(
                text = formatTimestamp(event.occurredAt),
                color = MistText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
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
