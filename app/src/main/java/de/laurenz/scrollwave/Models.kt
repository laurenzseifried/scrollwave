package de.laurenz.scrollwave

enum class SourceKind { CUSTOM_FEED, SUBREDDIT }

data class FeedSource(
    val id: String,
    val label: String,
    val path: String,
    val kind: SourceKind,
)

enum class FeedSort(val apiName: String, val label: String) {
    HOT("hot", "Hot"),
    NEW("new", "New"),
    TOP("top", "Top"),
}

enum class TopRange(val apiName: String, val label: String) {
    HOUR("hour", "Stunde"),
    DAY("day", "Tag"),
    WEEK("week", "Woche"),
    MONTH("month", "Monat"),
    YEAR("year", "Jahr"),
    ALL("all", "Gesamt"),
}

enum class MediaKind { IMAGE, VIDEO }

data class RemoteMedia(
    val key: String,
    val url: String,
    val kind: MediaKind,
    val hasAudio: Boolean = true,
)

data class MediaPost(
    val id: String,
    val author: String,
    val createdUtc: Long,
    val media: List<RemoteMedia>,
)

data class ListingPage(
    val posts: List<MediaPost>,
    val after: String?,
)

enum class Quality(val label: String) { AUTO("Auto"), HIGH("Hoch") }
enum class ResumeMode(val label: String) { RESUME("Fortsetzen"), RESTART("Neu starten") }

enum class AppStage { LOGIN, LOADING_SOURCES, SOURCES, FEED }

data class UiState(
    val stage: AppStage = AppStage.LOGIN,
    val sources: List<FeedSource> = emptyList(),
    val selectedSource: FeedSource? = null,
    val posts: List<MediaPost> = emptyList(),
    val sort: FeedSort = FeedSort.HOT,
    val topRange: TopRange = TopRange.WEEK,
    val quality: Quality = Quality.AUTO,
    val resumeMode: ResumeMode = ResumeMode.RESUME,
    val pausedPosts: Set<String> = emptySet(),
    val loading: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
)
