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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import com.proxypulse.AppLinks
import com.proxypulse.BuildConfig
import com.proxypulse.domain.FeedSourceMode
import com.proxypulse.R
import com.proxypulse.data.settings.SettingsRepository
import com.proxypulse.domain.ProxyEntry
import com.proxypulse.ui.theme.pingAccentColor
import com.proxypulse.ui.theme.pingTextColor
import com.proxypulse.ui.theme.ProxyPulseTheme
import kotlin.math.roundToInt

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
                            onOpenProxy = { entry ->
                                TelegramLauncher.openProxy(context, entry)
                            },
                            onRecheck = { viewModel.recheckProxy(it) }
                        )
                    }
                    Screen.Settings -> SettingsScreen(
                        maxProxies = uiState.settingsDraftMax,
                        darkTheme = uiState.settingsDraftDark,
                        feedSource = uiState.settingsDraftFeedSource,
                        tgStatStatus = uiState.tgStatStatus,
                        tgStatBusy = uiState.tgStatBusy,
                        tgStatHasSession = uiState.tgStatHasSession,
                        tgStatCanOpenBot = uiState.tgStatAuthKey != null,
                        onMaxChange = { viewModel.updateSettingsDraftMax(it) },
                        onDarkChange = { viewModel.updateSettingsDraftDark(it) },
                        onFeedSourceChange = { viewModel.updateSettingsDraftFeedSource(it) },
                        onTgStatLogin = { viewModel.startTgStatTelegramLogin() },
                        onTgStatOpenBot = { viewModel.openTgStatTelegramBot() },
                        onTgStatVerify = { viewModel.verifyTgStatSession() },
                        onTgStatClear = { viewModel.clearTgStatSession() },
                        onSave = { viewModel.saveSettings() }
                    )
                    Screen.Help -> HelpScreen()
                }
            }
        }

        uiState.feedError?.let { msg ->
            AlertDialog(
                onDismissRequest = { viewModel.clearFeedError() },
                title = { Text("ProxyPulse") },
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
    onOpenProxy: (ProxyEntry) -> Unit,
    onRecheck: (ProxyEntry) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (state.isSearching || state.fetchComplete || state.activityLine.isNotEmpty()) {
            LinearProgressIndicator(
                progress = { state.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
            Text(
                text = state.progressText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
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
            if (state.showFoundCount) {
                Text(
                    stringResource(R.string.found_count, state.foundCount),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.isSearching) {
                    OutlinedButton(onClick = onCancel) {
                        Text(stringResource(R.string.cancel_search))
                    }
                } else {
                    Button(onClick = onNewSearch) {
                        Text(stringResource(R.string.new_search))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(
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
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = !isRechecking) { onOpen(entry) }
                ) {
                    Text(entry.displayLabel, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (isRechecking) {
                            stringResource(R.string.rechecking)
                        } else {
                            entry.pingDisplay
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = entry.pingTextColor(isRechecking)
                    )
                    if (!isRechecking) {
                        entry.publishedCaption?.let { caption ->
                            Text(
                                text = caption,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
                if (!isRechecking) {
                    Text(
                        text = stringResource(R.string.connect_hint),
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clickable { onOpen(entry) },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (isRechecking) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(28.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    OutlinedButton(
                        onClick = { onRecheck(entry) },
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(stringResource(R.string.recheck), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    maxProxies: Int,
    darkTheme: Boolean,
    feedSource: FeedSourceMode,
    tgStatStatus: String,
    tgStatBusy: Boolean,
    tgStatHasSession: Boolean,
    tgStatCanOpenBot: Boolean,
    onMaxChange: (Int) -> Unit,
    onDarkChange: (Boolean) -> Unit,
    onFeedSourceChange: (FeedSourceMode) -> Unit,
    onTgStatLogin: () -> Unit,
    onTgStatOpenBot: () -> Unit,
    onTgStatVerify: () -> Unit,
    onTgStatClear: () -> Unit,
    onSave: () -> Unit
) {
    val min = SettingsRepository.MIN_MAX
    val max = SettingsRepository.MAX_MAX
    val scroll = rememberScrollState()
    val showTgStat = feedSource == FeedSourceMode.Bypass
    val highlightLogin = showTgStat && !tgStatHasSession && !tgStatCanOpenBot
    val highlightVerify = showTgStat && (tgStatHasSession || tgStatCanOpenBot)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.max_proxies), style = MaterialTheme.typography.titleMedium)
        Text("$maxProxies", style = MaterialTheme.typography.headlineSmall)
        Slider(
            value = maxProxies.toFloat(),
            onValueChange = { raw ->
                onMaxChange(SettingsRepository.snapProxyLimit(raw.roundToInt()))
            },
            valueRange = min.toFloat()..max.toFloat()
        )
        Text(
            text = stringResource(R.string.max_proxies_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Text(stringResource(R.string.feed_source), style = MaterialTheme.typography.titleMedium)
        FeedSourceRow(
            label = stringResource(R.string.feed_source_direct),
            selected = feedSource == FeedSourceMode.Direct,
            onClick = { onFeedSourceChange(FeedSourceMode.Direct) }
        )
        FeedSourceRow(
            label = stringResource(R.string.feed_source_bypass),
            selected = feedSource == FeedSourceMode.Bypass,
            onClick = { onFeedSourceChange(FeedSourceMode.Bypass) }
        )
        Text(
            text = if (feedSource == FeedSourceMode.Direct) {
                stringResource(R.string.feed_hint_direct)
            } else {
                stringResource(R.string.feed_hint_bypass)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        )

        if (showTgStat) {
            Text(stringResource(R.string.tgstat_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.tgstat_step1), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.tgstat_step2), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.tgstat_step3), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.tgstat_step4), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.tgstat_step5), style = MaterialTheme.typography.bodySmall)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (highlightLogin) {
                    Button(onClick = onTgStatLogin, enabled = !tgStatBusy) {
                        Text(stringResource(R.string.tgstat_login_telegram))
                    }
                } else {
                    OutlinedButton(onClick = onTgStatLogin, enabled = !tgStatBusy) {
                        Text(stringResource(R.string.tgstat_login_telegram))
                    }
                }
                if (tgStatCanOpenBot) {
                    Button(onClick = onTgStatOpenBot, enabled = !tgStatBusy) {
                        Text(stringResource(R.string.tgstat_open_telegram))
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (highlightVerify) {
                    Button(onClick = onTgStatVerify, enabled = !tgStatBusy) {
                        Text(stringResource(R.string.tgstat_verify))
                    }
                } else {
                    OutlinedButton(onClick = onTgStatVerify, enabled = !tgStatBusy) {
                        Text(stringResource(R.string.tgstat_verify))
                    }
                }
                OutlinedButton(onClick = onTgStatClear, enabled = !tgStatBusy) {
                    Text(stringResource(R.string.tgstat_clear))
                }
            }

            if (tgStatBusy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.tgstat_connecting), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (tgStatStatus.isNotEmpty()) {
                Text(
                    tgStatStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

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
private fun FeedSourceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 4.dp))
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
