package de.laurenz.scrollwave

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("settings", Application.MODE_PRIVATE)
    private val tokenStore = TokenStore(application)
    private val reddit = RedditClient(tokenStore, BuildConfig.REDDIT_CLIENT_ID, MediaResolver())
    private val _state = MutableStateFlow(
        UiState(
            quality = enumPreference("quality", Quality.AUTO),
            resumeMode = enumPreference("resume", ResumeMode.RESUME),
            error = if (BuildConfig.REDDIT_CLIENT_ID.isBlank()) {
                "REDDIT_CLIENT_ID fehlt. Siehe README.md."
            } else null,
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var after: String? = null
    private var pagesScanned = 0
    private val seenMedia = mutableSetOf<String>()
    private val positions = ConcurrentHashMap<String, Long>()

    init {
        if (tokenStore.load() != null) loadSources() else _state.value = _state.value.copy(stage = AppStage.LOGIN)
    }

    fun loginUrl(): Uri? = runCatching { reddit.loginUrl() }
        .onFailure(::showError)
        .getOrNull()

    fun completeLogin(callback: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(stage = AppStage.LOADING_SOURCES, loading = true, error = null)
            runCatching { reddit.completeLogin(callback) }
                .onSuccess { loadSources() }
                .onFailure {
                    tokenStore.clear()
                    _state.value = _state.value.copy(stage = AppStage.LOGIN, loading = false, error = it.message)
                }
        }
    }

    fun loadSources() {
        viewModelScope.launch {
            _state.value = _state.value.copy(stage = AppStage.LOADING_SOURCES, loading = true, error = null)
            runCatching { reddit.loadSources() }
                .onSuccess { sources ->
                    _state.value = _state.value.copy(
                        stage = AppStage.SOURCES,
                        sources = sources,
                        loading = false,
                        error = if (sources.isEmpty()) "Keine eigenen Custom Feeds oder Abonnements gefunden." else null,
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(stage = AppStage.LOGIN, loading = false, error = it.message)
                }
        }
    }

    fun selectSource(source: FeedSource) {
        val stored = preferences.getString("sort:${source.id}", null)?.split('|')
        val sort = stored?.getOrNull(0)?.let { value -> FeedSort.entries.find { it.name == value } } ?: FeedSort.HOT
        val range = stored?.getOrNull(1)?.let { value -> TopRange.entries.find { it.name == value } } ?: TopRange.WEEK
        _state.value = _state.value.copy(
            stage = AppStage.FEED,
            selectedSource = source,
            posts = emptyList(),
            sort = sort,
            topRange = range,
            pausedPosts = emptySet(),
            endReached = false,
            error = null,
        )
        resetFeed()
        loadMore()
    }

    fun showSources() {
        _state.value = _state.value.copy(stage = AppStage.SOURCES, pausedPosts = emptySet())
    }

    fun setSort(sort: FeedSort) {
        if (_state.value.sort == sort) return
        _state.value = _state.value.copy(sort = sort)
        persistSort()
        refresh()
    }

    fun setTopRange(range: TopRange) {
        if (_state.value.topRange == range) return
        _state.value = _state.value.copy(topRange = range)
        persistSort()
        if (_state.value.sort == FeedSort.TOP) refresh()
    }

    fun setQuality(quality: Quality) {
        preferences.edit().putString("quality", quality.name).apply()
        _state.value = _state.value.copy(quality = quality)
    }

    fun setResumeMode(mode: ResumeMode) {
        preferences.edit().putString("resume", mode.name).apply()
        _state.value = _state.value.copy(resumeMode = mode)
    }

    fun refresh() {
        _state.value = _state.value.copy(posts = emptyList(), endReached = false, error = null, pausedPosts = emptySet())
        resetFeed()
        loadMore()
    }

    fun loadMore() {
        val current = _state.value
        val source = current.selectedSource ?: return
        if (current.loading || current.endReached) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val added = mutableListOf<MediaPost>()
            var failure: Throwable? = null
            while (added.size < TARGET_BATCH_SIZE && pagesScanned < MAX_SCAN_PAGES) {
                val page = runCatching {
                    reddit.loadListing(source, _state.value.sort, _state.value.topRange, after)
                }.getOrElse {
                    failure = it
                    break
                }
                pagesScanned++
                after = page.after
                page.posts.forEach { post ->
                    val keys = post.media.map(RemoteMedia::key)
                    if (keys.none(seenMedia::contains)) {
                        seenMedia += keys
                        added += post
                    }
                }
                if (after == null) break
            }
            val all = _state.value.posts + added
            val exhausted = after == null || pagesScanned >= MAX_SCAN_PAGES
            _state.value = _state.value.copy(
                posts = all,
                loading = false,
                endReached = exhausted,
                error = failure?.message ?: if (all.isEmpty() && exhausted) "Keine passenden Medien gefunden." else null,
            )
        }
    }

    fun togglePaused(postId: String) {
        val paused = _state.value.pausedPosts.toMutableSet()
        if (!paused.add(postId)) paused.remove(postId)
        _state.value = _state.value.copy(pausedPosts = paused)
    }

    fun position(key: String): Long = positions[key] ?: 0L
    fun savePosition(key: String, positionMs: Long) { positions[key] = positionMs }

    fun logout() {
        tokenStore.clear()
        preferences.edit().clear().apply()
        positions.clear()
        resetFeed()
        _state.value = UiState(stage = AppStage.LOGIN)
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }

    private fun resetFeed() {
        after = null
        pagesScanned = 0
        seenMedia.clear()
    }

    private fun persistSort() {
        val source = _state.value.selectedSource ?: return
        preferences.edit()
            .putString("sort:${source.id}", "${_state.value.sort.name}|${_state.value.topRange.name}")
            .apply()
    }

    private fun showError(error: Throwable) {
        _state.value = _state.value.copy(error = error.message ?: "Unbekannter Fehler")
    }

    private inline fun <reified T : Enum<T>> enumPreference(key: String, fallback: T): T =
        preferences.getString(key, null)?.let { value -> enumValues<T>().find { it.name == value } } ?: fallback

    private companion object {
        const val TARGET_BATCH_SIZE = 12
        const val MAX_SCAN_PAGES = 5
    }
}
