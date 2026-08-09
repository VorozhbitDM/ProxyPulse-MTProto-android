package com.proxypulse.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.proxypulse.data.feed.ProxyFeedService
import com.proxypulse.data.health.ProxyHealthService
import com.proxypulse.data.settings.AppSettings
import com.proxypulse.data.settings.SettingsRepository
import com.proxypulse.domain.ProxyEntry
import com.proxypulse.domain.SortMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

enum class Screen { Welcome, Scan, Settings, Help }

data class MainUiState(
    val screen: Screen = Screen.Welcome,
    val isSearching: Boolean = false,
    val searchCancelled: Boolean = false,
    val fetchComplete: Boolean = false,
    val refiningPings: Boolean = false,
    val progressPercent: Int = 0,
    val progressText: String = "",
    val statusText: String = "",
    val activityLine: String = "",
    val foundCount: Int = 0,
    val showFoundCount: Boolean = false,
    val showPullHint: Boolean = false,
    val proxies: List<ProxyEntry> = emptyList(),
    val recheckingKeys: Set<String> = emptySet(),
    val feedError: String? = null,
    val sortMode: SortMode = SortMode.Rating,
    val settingsDraftDark: Boolean = true
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepo = SettingsRepository(application)
    private val feedService = ProxyFeedService()
    private val healthService = ProxyHealthService()
    private val sortedList = SortedProxyList()

    private var searchJob: Job? = null
    private var backgroundRecheckJob: Job? = null
    private var appInForeground = true
    private var collectFound = 0
    private var finalDiscovered = 0
    private var checkedCount = 0
    private var refineTotal = 0
    private var refineDone = 0
    private var lastCollectLog: String? = null
    private var refiningPings = false

    val settings: StateFlow<AppSettings> = settingsRepo.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AppSettings()
    )

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settings.collect { s ->
                _uiState.update {
                    it.copy(
                        settingsDraftDark = s.useDarkTheme,
                        showPullHint = it.showPullHint && !s.pullHintDismissed
                    )
                }
            }
        }
    }

    fun onAppForeground() {
        appInForeground = true
        maybeStartBackgroundRecheck()
    }

    fun onAppBackground() {
        appInForeground = false
        stopBackgroundRecheck()
    }

    fun dismissPullHint() {
        _uiState.update { it.copy(showPullHint = false) }
        viewModelScope.launch { settingsRepo.setPullHintDismissed(true) }
    }

    fun openSettings() {
        stopBackgroundRecheck()
        _uiState.update {
            it.copy(
                screen = Screen.Settings,
                settingsDraftDark = settings.value.useDarkTheme
            )
        }
    }

    fun closeSettings() {
        _uiState.update {
            it.copy(
                screen = if (it.isSearching || it.fetchComplete) Screen.Scan else Screen.Welcome
            )
        }
        maybeStartBackgroundRecheck()
    }

    fun openHelp() {
        stopBackgroundRecheck()
        _uiState.update { it.copy(screen = Screen.Help) }
    }

    fun closeHelp() {
        _uiState.update {
            it.copy(
                screen = when {
                    it.isSearching || it.fetchComplete -> Screen.Scan
                    else -> Screen.Welcome
                }
            )
        }
        maybeStartBackgroundRecheck()
    }

    fun updateSettingsDraftDark(dark: Boolean) {
        _uiState.update { it.copy(settingsDraftDark = dark) }
    }

    fun saveSettings() {
        val draft = _uiState.value
        viewModelScope.launch {
            settingsRepo.update(draft.settingsDraftDark)
            closeSettings()
        }
    }

    fun setSortMode(mode: SortMode) {
        sortedList.setSortMode(mode)
        _uiState.update {
            it.copy(
                sortMode = mode,
                proxies = sortedList.snapshot()
            )
        }
    }

    fun startSearch() {
        stopBackgroundRecheck()
        searchJob?.cancel()
        sortedList.clear()
        collectFound = 0
        checkedCount = 0
        finalDiscovered = 0
        refineTotal = 0
        refineDone = 0
        refiningPings = false
        val sortMode = _uiState.value.sortMode
        sortedList.setSortMode(sortMode)

        _uiState.update {
            MainUiState(
                screen = Screen.Scan,
                isSearching = true,
                showPullHint = false,
                progressText = getApplication<Application>().getString(com.proxypulse.R.string.connecting),
                statusText = getApplication<Application>().getString(com.proxypulse.R.string.connecting),
                activityLine = getApplication<Application>().getString(com.proxypulse.R.string.connecting),
                sortMode = sortMode,
                settingsDraftDark = it.settingsDraftDark
            )
        }

        searchJob = viewModelScope.launch(Dispatchers.IO) {
            val collected = mutableListOf<ProxyEntry>()
            lastCollectLog = null
            try {
                feedService.fetchToList(
                    output = collected,
                    log = { line -> postActivityLine(line) },
                    progress = { p ->
                        collectFound = p.proxiesFound
                        viewModelScope.launch(Dispatchers.Main.immediate) {
                            updateOverallProgress()
                        }
                    }
                )

                finalDiscovered = collected.size
                collectFound = collected.size
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            fetchComplete = true,
                            showFoundCount = collected.isNotEmpty(),
                            activityLine = ""
                        )
                    }
                    updateOverallProgress()
                }

                if (collected.isEmpty()) {
                    val base = getApplication<Application>().getString(com.proxypulse.R.string.feed_failed)
                    val detail = lastCollectLog?.let { "\n\n$it" } ?: ""
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                screen = Screen.Welcome,
                                feedError = base + detail
                            )
                        }
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(activityLine = "") }
                }

                // Phase 1: quick availability → list ASAP
                healthService.scanAvailability(
                    proxies = collected,
                    onProgress = { p ->
                        checkedCount = p.completed
                        viewModelScope.launch(Dispatchers.Main.immediate) {
                            updateOverallProgress()
                        }
                    },
                    onChecked = { result ->
                        if (result.isAvailable) {
                            sortedList.upsertAvailable(result.entry, result.pingMs)
                            viewModelScope.launch(Dispatchers.Main.immediate) {
                                _uiState.update {
                                    it.copy(
                                        proxies = sortedList.snapshot(),
                                        foundCount = sortedList.count,
                                        showFoundCount = true
                                    )
                                }
                            }
                        }
                    }
                )

                if (_uiState.value.searchCancelled) return@launch

                // Phase 2: accurate ping, same as manual refresh (low concurrency)
                val toRefine = sortedList.snapshot()
                if (toRefine.isNotEmpty()) {
                    refineTotal = toRefine.size
                    refineDone = 0
                    refiningPings = true
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(refiningPings = true) }
                        updateOverallProgress()
                    }

                    healthService.refinePings(
                        proxies = toRefine,
                        onProgress = { p ->
                            refineDone = p.completed
                            viewModelScope.launch(Dispatchers.Main.immediate) {
                                updateOverallProgress()
                            }
                        },
                        onChecking = { entry ->
                            viewModelScope.launch(Dispatchers.Main.immediate) {
                                _uiState.update {
                                    it.copy(recheckingKeys = it.recheckingKeys + entry.key)
                                }
                            }
                        },
                        onChecked = { result ->
                            if (result.isAvailable) {
                                sortedList.upsertAvailable(result.entry, result.pingMs)
                            } else {
                                result.entry.isAvailable = false
                                result.entry.pingMs = null
                                sortedList.remove(result.entry.key)
                            }
                            viewModelScope.launch(Dispatchers.Main.immediate) {
                                _uiState.update {
                                    it.copy(
                                        recheckingKeys = it.recheckingKeys - result.entry.key,
                                        proxies = sortedList.snapshot(),
                                        foundCount = sortedList.count,
                                        showFoundCount = sortedList.count > 0
                                    )
                                }
                            }
                        }
                    )
                }

                refiningPings = false
                if (!_uiState.value.searchCancelled) {
                    val showHint = !settings.value.pullHintDismissed && sortedList.count > 0
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                refiningPings = false,
                                progressPercent = 100,
                                activityLine = "",
                                recheckingKeys = emptySet(),
                                showPullHint = showHint,
                                statusText = getApplication<Application>().getString(
                                    com.proxypulse.R.string.search_done,
                                    sortedList.count
                                )
                            )
                        }
                        updateOverallProgress()
                    }
                    maybeStartBackgroundRecheck()
                }
            } catch (_: CancellationException) {
                refiningPings = false
                withContext(Dispatchers.Main) { showPaused() }
            } catch (e: Exception) {
                refiningPings = false
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            refiningPings = false,
                            screen = Screen.Welcome,
                            feedError = e.message ?: e.toString()
                        )
                    }
                }
            } finally {
                refiningPings = false
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            refiningPings = false,
                            recheckingKeys = emptySet()
                        )
                    }
                }
            }
        }
    }

    fun cancelSearch() {
        _uiState.update { it.copy(searchCancelled = true) }
        searchJob?.cancel()
        showPaused()
        maybeStartBackgroundRecheck()
    }

    fun newSearch() {
        if (_uiState.value.showPullHint) dismissPullHint()
        searchJob?.cancel()
        startSearch()
    }

    fun recheckProxy(entry: ProxyEntry) {
        viewModelScope.launch {
            _uiState.update { it.copy(recheckingKeys = it.recheckingKeys + entry.key) }
            val result = withContext(Dispatchers.IO) { healthService.checkOne(entry) }
            if (result.isAvailable) {
                sortedList.upsertAvailable(entry, result.pingMs)
            } else {
                entry.isAvailable = false
                entry.pingMs = null
                sortedList.remove(entry.key)
            }
            _uiState.update {
                it.copy(
                    recheckingKeys = it.recheckingKeys - entry.key,
                    proxies = sortedList.snapshot(),
                    foundCount = sortedList.count,
                    showFoundCount = sortedList.count > 0,
                    activityLine = getApplication<Application>().getString(
                        if (result.isAvailable) {
                            com.proxypulse.R.string.recheck_ok
                        } else {
                            com.proxypulse.R.string.recheck_fail
                        },
                        entry.displayLabel
                    )
                )
            }
        }
    }

    private fun maybeStartBackgroundRecheck() {
        stopBackgroundRecheck()
        val state = _uiState.value
        if (!appInForeground) return
        if (state.isSearching) return
        if (state.screen != Screen.Scan) return
        if (sortedList.count == 0) return

        backgroundRecheckJob = viewModelScope.launch(Dispatchers.IO) {
            while (coroutineContext.isActive) {
                delay(BACKGROUND_RECHECK_INTERVAL_MS)
                if (!appInForeground) continue
                val ui = _uiState.value
                if (ui.isSearching || ui.screen != Screen.Scan) continue

                val top = sortedList.snapshot()
                    .sortedWith(SortedProxyList.comparatorFor(SortMode.Rating))
                    .take(BACKGROUND_RECHECK_TOP_N)
                if (top.isEmpty()) continue

                for (entry in top) {
                    if (!coroutineContext.isActive) break
                    if (!appInForeground || _uiState.value.isSearching) break

                    withContext(Dispatchers.Main.immediate) {
                        _uiState.update { it.copy(recheckingKeys = it.recheckingKeys + entry.key) }
                    }
                    try {
                        val result = healthService.checkLight(entry)
                        if (result.isAvailable) {
                            sortedList.upsertAvailable(entry, result.pingMs)
                        } else {
                            entry.isAvailable = false
                            entry.pingMs = null
                            sortedList.remove(entry.key)
                        }
                        withContext(Dispatchers.Main.immediate) {
                            _uiState.update {
                                it.copy(
                                    proxies = sortedList.snapshot(),
                                    foundCount = sortedList.count,
                                    showFoundCount = sortedList.count > 0
                                )
                            }
                        }
                    } catch (_: CancellationException) {
                        throw CancellationException()
                    } catch (_: Exception) {
                        // skip this proxy on transient errors
                    } finally {
                        withContext(Dispatchers.Main.immediate) {
                            _uiState.update { it.copy(recheckingKeys = it.recheckingKeys - entry.key) }
                        }
                    }
                }
            }
        }
    }

    private fun stopBackgroundRecheck() {
        backgroundRecheckJob?.cancel()
        backgroundRecheckJob = null
    }

    private fun postActivityLine(line: String) {
        if (line.isBlank()) return
        lastCollectLog = line
        viewModelScope.launch(Dispatchers.Main.immediate) {
            _uiState.update {
                it.copy(activityLine = line, statusText = line)
            }
        }
    }

    fun clearFeedError() {
        _uiState.update { it.copy(feedError = null) }
    }

    private fun updateOverallProgress() {
        val state = _uiState.value
        if (state.searchCancelled) return

        val percent = when {
            !state.fetchComplete -> if (collectFound == 0) 5 else 35
            refiningPings -> {
                val total = maxOf(1, refineTotal)
                70 + minOf(30, (30.0 * refineDone / total).toInt())
            }
            else -> {
                val total = maxOf(1, finalDiscovered)
                35 + minOf(35, (35.0 * checkedCount / total).toInt())
            }
        }

        val progressText = formatProgressText(state)
        _uiState.update {
            it.copy(
                progressPercent = percent.coerceIn(0, 100),
                progressText = progressText,
                foundCount = sortedList.count,
                showFoundCount = state.fetchComplete && sortedList.count > 0
            )
        }
    }

    private fun formatProgressText(state: MainUiState): String {
        val app = getApplication<Application>()
        if (state.searchCancelled) {
            return app.getString(com.proxypulse.R.string.search_paused)
        }
        if (!state.fetchComplete) {
            if (collectFound == 0) return app.getString(com.proxypulse.R.string.connecting)
            return app.getString(com.proxypulse.R.string.collect_progress, collectFound)
        }
        if (refiningPings) {
            val total = maxOf(1, refineTotal)
            return if (refineDone >= total) {
                app.getString(com.proxypulse.R.string.refine_done, refineDone, total)
            } else {
                app.getString(com.proxypulse.R.string.refine_progress, refineDone, total)
            }
        }
        val total = maxOf(1, finalDiscovered)
        return if (checkedCount >= total) {
            app.getString(com.proxypulse.R.string.check_done, checkedCount, total)
        } else {
            app.getString(com.proxypulse.R.string.check_progress, checkedCount, total)
        }
    }

    private fun showPaused() {
        val app = getApplication<Application>()
        _uiState.update {
            it.copy(
                isSearching = false,
                refiningPings = false,
                progressPercent = 0,
                progressText = app.getString(com.proxypulse.R.string.search_paused),
                statusText = app.getString(com.proxypulse.R.string.search_paused),
                showFoundCount = sortedList.count > 0,
                recheckingKeys = emptySet(),
                showPullHint = !settings.value.pullHintDismissed && sortedList.count > 0
            )
        }
    }

    companion object {
        private const val BACKGROUND_RECHECK_INTERVAL_MS = 3 * 60_000L
        private const val BACKGROUND_RECHECK_TOP_N = 5
    }
}
