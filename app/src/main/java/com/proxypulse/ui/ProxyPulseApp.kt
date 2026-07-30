package com.proxypulse.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.proxypulse.AppLinks
import com.proxypulse.BuildConfig
import com.proxypulse.R
import com.proxypulse.domain.ProxyEntry
import com.proxypulse.domain.SortMode
import com.proxypulse.ui.theme.PingColor
import com.proxypulse.ui.theme.pingAccentColor
import com.proxypulse.ui.theme.pingTextColor
import com.proxypulse.ui.theme.ProxyPulseTheme

@Composable
fun ProxyPulseApp(viewModel: MainViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val useDarkTheme = when (uiState.screen) {
        Screen.Settings -> uiState.settingsDraftDark
        else -> settings.useDarkTheme
    }

    ProxyPulseTheme(useDarkTheme = useDarkTheme) {
        Scaffold(
            topBar = {
                AppTopBar(
                    screen = uiState.screen,
                    onSettings = { viewModel.openSettings() },
                    onHelp = { viewModel.openHelp() },
                    onBack = {
                        when (uiState.screen) {
                            Screen.Settings -> viewModel.closeSettings()
                            Screen.Help -> viewModel.closeHelp()
                            else -> Unit
                        }
                    }
                )
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (uiState.screen) {
                    Screen.Welcome -> WelcomeScreen(onStart = { viewModel.startSearch() })
                    Screen.Scan -> {
                        val context = LocalContext.current
                        ScanScreen(
                            state = uiState,
                            onCancel = { viewModel.cancelSearch() },
                            onNewSearch = { viewModel.newSearch() },
                            onSortMode = { viewModel.setSortMode(it) },
                            onOpenProxy = { entry ->
                                TelegramLauncher.openProxy(context, entry)
                            },
                            onRecheck = { viewModel.recheckProxy(it) }
                        )
                    }
                    Screen.Settings -> SettingsScreen(
                        darkTheme = uiState.settingsDraftDark,
                        onDarkChange = { viewModel.updateSettingsDraftDark(it) },
                        onSave = { viewModel.saveSettings() }
                    )
                    Screen.Help -> HelpScreen()
                }
            }
        }

        uiState.feedError?.let { msg ->
            AlertDialog(
                onDismissRequest = { viewModel.clearFeedError() },
                title = { Text("ProxyPulse Lite") },
                text = { Text(msg) },
                confirmButton = {
                    Button(onClick = { viewModel.clearFeedError() }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    screen: Screen,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onBack: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.version_label, appVersionShort()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
        },
        navigationIcon = {
            if (screen == Screen.Settings || screen == Screen.Help) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back)
                    )
                }
            }
        },
        actions = {
            if (screen != Screen.Settings && screen != Screen.Help) {
                TextButton(onClick = onHelp) {
                    Text(stringResource(R.string.help), fontSize = 13.sp)
                }
                TextButton(onClick = onSettings) {
                    Text(stringResource(R.string.settings), fontSize = 13.sp)
                }
            }
        }
    )
}

@Composable
private fun WelcomeScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth(0.85f)) {
            Text(stringResource(R.string.start_search))
        }
    }
}

@Composable
private fun ScanScreen(
    state: MainUiState,
    onCancel: () -> Unit,
    onNewSearch: () -> Unit,
    onSortMode: (SortMode) -> Unit,
    onOpenProxy: (ProxyEntry) -> Unit,
    onRecheck: (ProxyEntry) -> Unit
) {
    val listState = rememberLazyListState()
    var lastSortMode by remember { mutableStateOf(state.sortMode) }
    LaunchedEffect(state.sortMode) {
        if (state.sortMode != lastSortMode) {
            lastSortMode = state.sortMode
            if (state.proxies.isNotEmpty()) {
                listState.scrollToItem(0)
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (state.isSearching || state.fetchComplete || state.activityLine.isNotEmpty()) {
            LinearProgressIndicator(
                progress = { state.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.progressText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (state.showFoundCount) {
                    Text(
                        text = stringResource(R.string.found_count, state.foundCount),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            val showActivityLog = state.isSearching && !state.fetchComplete
            if (showActivityLog && state.activityLine.isNotEmpty() && state.activityLine != state.progressText) {
                Text(
                    text = state.activityLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))
            if (state.isSearching) {
                OutlinedButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel_search))
                }
            } else {
                Button(onClick = onNewSearch) {
                    Text(stringResource(R.string.new_search))
                }
            }
            if (state.proxies.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SortBar(
                    sortMode = state.sortMode,
                    onSortMode = onSortMode
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.proxies, key = { it.key }) { entry ->
                ProxyCard(
                    entry = entry,
                    isRechecking = entry.key in state.recheckingKeys,
                    onOpen = onOpenProxy,
                    onRecheck = onRecheck
                )
            }
        }
    }
}

@Composable
private fun SortBar(
    sortMode: SortMode,
    onSortMode: (SortMode) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SortChip(
                label = stringResource(R.string.sort_rating),
                selected = sortMode == SortMode.Rating,
                onClick = { onSortMode(SortMode.Rating) }
            )
            SortChip(
                label = stringResource(R.string.sort_ping),
                selected = sortMode == SortMode.Ping,
                onClick = { onSortMode(SortMode.Ping) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.height(32.dp),
        label = {
            Text(
                text = label,
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        },
        colors = FilterChipDefaults.filterChipColors()
    )
}

@Composable
private fun ProxyCard(
    entry: ProxyEntry,
    isRechecking: Boolean,
    onOpen: (ProxyEntry) -> Unit,
    onRecheck: (ProxyEntry) -> Unit
) {
    val accentColor = entry.pingAccentColor()
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
            ) {
                Text(
                    text = entry.displayLabel,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!isRechecking && entry.isAvailable) {
                        Text(
                            text = entry.ratingDisplay,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = PingColor.StarGold
                        )
                    }
                    Text(
                        text = if (isRechecking) {
                            stringResource(R.string.rechecking)
                        } else {
                            entry.pingDisplay
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = entry.pingTextColor(isRechecking)
                    )
                }
                if (!isRechecking) {
                    entry.publishedCaption?.let { caption ->
                        Text(
                            text = caption,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
            if (isRechecking) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(28.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Button(
                    onClick = { onOpen(entry) },
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text(stringResource(R.string.connect), fontSize = 12.sp, maxLines = 1)
                }
                IconButton(
                    onClick = { onRecheck(entry) },
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.recheck),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    darkTheme: Boolean,
    onDarkChange: (Boolean) -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.dark_theme))
            Switch(checked = darkTheme, onCheckedChange = onDarkChange)
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.save))
        }
    }
}

@Composable
private fun HelpScreen() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.version_label, appVersionShort()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = stringResource(R.string.help_desc),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { openUrl(context, AppLinks.GITHUB_PROJECT) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.help_github))
        }
        OutlinedButton(
            onClick = { openUrl(context, AppLinks.YOO_MONEY) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.help_yoomoney))
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        Toast.makeText(context, R.string.link_open_failed, Toast.LENGTH_LONG).show()
    }
}

private fun appVersionShort(): String = BuildConfig.VERSION_LABEL
