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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { Welcome, Scan, Settings, Help }

data class MainUiState(
    val screen: Screen = Screen.Welcome,
    val isSearching: Boolean = false,
    val searchCancelled: Boolean = false,
    val fetchComplete: Boolean = false,
    val progressPercent: Int = 0,
    val progressText: String = "",
    val statusText: String = "",
    val activityLine: String = "",
    val foundCount: Int = 0,
    val showFoundCount: Boolean = false,
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
    private var collectFound = 0
    private var finalDiscovered = 0
    private var checkedCount = 0
    private var lastCollectLog: String? = null

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
                _uiState.update { it.copy(settingsDraftDark = s.useDarkTheme) }
            }
        }
    }

    fun openSettings() {
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
    }

    fun openHelp() {
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
        searchJob?.cancel()
        sortedList.clear()
        collectFound = 0
        checkedCount = 0
        finalDiscovered = 0
        val sortMode = _uiState.value.sortMode
        sortedList.setSortMode(sortMode)

        _uiState.update {
            MainUiState(
                screen = Screen.Scan,
                isSearching = true,
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
                healthService.scan(
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

                if (!_uiState.value.searchCancelled) {
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                progressPercent = 100,
                                activityLine = "",
                                statusText = getApplication<Application>().getString(
                                    com.proxypulse.R.string.search_done,
                                    sortedList.count
                                )
                            )
                        }
                    }
                }
            } catch (_: CancellationException) {
                withContext(Dispatchers.Main) { showPaused() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            screen = Screen.Welcome,
                            feedError = e.message ?: e.toString()
                        )
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isSearching = false) }
                }
            }
        }
    }

    fun cancelSearch() {
        _uiState.update { it.copy(searchCancelled = true) }
        searchJob?.cancel()
        showPaused()
    }

    fun newSearch() {
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

        val percent = if (!state.fetchComplete) {
            if (collectFound == 0) 5 else 40
        } else {
            val total = maxOf(1, finalDiscovered)
            50 + minOf(50, (50.0 * checkedCount / total).toInt())
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
                progressPercent = 0,
                progressText = app.getString(com.proxypulse.R.string.search_paused),
                statusText = app.getString(com.proxypulse.R.string.search_paused),
                showFoundCount = sortedList.count > 0
            )
        }
    }
}
