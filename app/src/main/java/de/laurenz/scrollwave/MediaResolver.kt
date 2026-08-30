package de.laurenz.scrollwave

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

class MediaResolver(private val http: OkHttpClient = OkHttpClient()) {
    private var redgifsToken: String? = null

    suspend fun resolve(post: JSONObject): MediaPost? = withContext(Dispatchers.IO) {
        val source = post.optJSONArray("crosspost_parent_list")
            ?.optJSONObject(0)
            ?: post
        val media = when {
            source.optJSONObject("gallery_data") != null -> redditGallery(source)
            redditVideo(source) != null -> listOfNotNull(redditVideo(source))
            redgifsId(source.optString("url_overridden_by_dest", source.optString("url"))) != null -> {
                resolveRedgifs(redgifsId(source.optString("url_overridden_by_dest", source.optString("url")))!!)
            }
            else -> listOfNotNull(directOrPreview(source))
        }.distinctBy { it.key }
        if (media.isEmpty()) return@withContext null
        MediaPost(
            id = post.optString("name", post.optString("id")),
            author = post.optString("author", "[gelöscht]"),
            createdUtc = post.optLong("created_utc"),
            media = media,
        )
    }

    private fun redditGallery(post: JSONObject): List<RemoteMedia> {
        val order = post.optJSONObject("gallery_data")?.optJSONArray("items") ?: return emptyList()
        val metadata = post.optJSONObject("media_metadata") ?: return emptyList()
        return buildList {
            for (index in 0 until order.length()) {
                val id = order.optJSONObject(index)?.optString("media_id").orEmpty()
                val item = metadata.optJSONObject(id) ?: continue
                galleryItem(id, item)?.let(::add)
            }
        }
    }

    private fun galleryItem(id: String, item: JSONObject): RemoteMedia? {
        val source = item.optJSONObject("s") ?: return null
        val hls = source.optString("hlsUrl").unescaped().takeIf(String::isNotBlank)
        val dash = source.optString("dashUrl").unescaped().takeIf(String::isNotBlank)
        val mp4 = source.optString("mp4").unescaped().takeIf(String::isNotBlank)
        val gif = source.optString("gif").unescaped().takeIf(String::isNotBlank)
        val image = source.optString("u").unescaped().takeIf(String::isNotBlank)
        val url = hls ?: dash ?: mp4 ?: gif ?: image ?: return null
        val isVideo = hls != null || dash != null || mp4 != null || item.optString("e") == "RedditVideo"
        return RemoteMedia(
            key = "reddit-gallery:$id",
            url = url,
            kind = if (isVideo) MediaKind.VIDEO else MediaKind.IMAGE,
            hasAudio = hls != null || dash != null,
        )
    }

    private fun redditVideo(post: JSONObject): RemoteMedia? {
        val media = post.optJSONObject("secure_media") ?: post.optJSONObject("media")
        val video = media?.optJSONObject("reddit_video")
            ?: post.optJSONObject("preview")?.optJSONObject("reddit_video_preview")
            ?: return null
        val hls = video.optString("hls_url").unescaped().takeIf(String::isNotBlank)
        val dash = video.optString("dash_url").unescaped().takeIf(String::isNotBlank)
        val fallback = video.optString("fallback_url").unescaped().takeIf(String::isNotBlank)
        val url = hls ?: dash ?: fallback ?: return null
        return RemoteMedia(
            key = "reddit-video:${post.optString("id", url)}",
            url = url,
            kind = MediaKind.VIDEO,
            hasAudio = !video.optBoolean("is_gif") && (hls != null || dash != null),
        )
    }

    private fun directOrPreview(post: JSONObject): RemoteMedia? {
        val direct = post.optString("url_overridden_by_dest", post.optString("url")).unescaped()
        directKind(direct)?.let {
            return RemoteMedia("direct:${normalized(direct)}", direct, it, it == MediaKind.VIDEO)
        }
        val image = post.optJSONObject("preview")
            ?.optJSONArray("images")
            ?.optJSONObject(0)
        val variants = image?.optJSONObject("variants")
        val mp4 = variants?.optJSONObject("mp4")?.optJSONObject("source")?.optString("url")
            ?.unescaped()?.takeIf(String::isNotBlank)
        if (mp4 != null) return RemoteMedia("preview:${normalized(mp4)}", mp4, MediaKind.VIDEO, false)
        val gif = variants?.optJSONObject("gif")?.optJSONObject("source")?.optString("url")
            ?.unescaped()?.takeIf(String::isNotBlank)
        if (gif != null) return RemoteMedia("preview:${normalized(gif)}", gif, MediaKind.IMAGE, false)
        val preview = image?.optJSONObject("source")?.optString("url")
            ?.unescaped()?.takeIf(String::isNotBlank)
        return preview?.let { RemoteMedia("preview:${normalized(it)}", it, MediaKind.IMAGE, false) }
    }

    private fun directKind(url: String): MediaKind? {
        val path = runCatching { URI(url).path.lowercase() }.getOrDefault("")
        return when {
            IMAGE_EXTENSIONS.any(path::endsWith) -> MediaKind.IMAGE
            VIDEO_EXTENSIONS.any(path::endsWith) -> MediaKind.VIDEO
            else -> null
        }
    }

    private fun redgifsId(url: String): String? = REDGIFS_REGEX.find(url)?.groupValues?.get(1)?.lowercase()

    private fun resolveRedgifs(id: String): List<RemoteMedia> {
        var response = redgifsGet("https://api.redgifs.com/v2/gifs/$id")
        if (response.first == 401) {
            redgifsToken = null
            response = redgifsGet("https://api.redgifs.com/v2/gifs/$id")
        }
        if (response.first !in 200..299) return emptyList()
        val gif = JSONObject(response.second).optJSONObject("gif") ?: return emptyList()
        val gallery = gif.optString("gallery")
            .takeIf { it.length > 3 && it != "true" && it != "false" && it != "null" }
        if (gallery != null) {
            val galleryResponse = redgifsGet("https://api.redgifs.com/v2/gallery/$gallery")
            if (galleryResponse.first in 200..299) {
                return redgifsItems(JSONObject(galleryResponse.second).optJSONArray("gifs"))
            }
        }
        return listOfNotNull(redgifsItem(gif))
    }

    private fun redgifsItems(items: JSONArray?): List<RemoteMedia> = buildList {
        if (items == null) return@buildList
        for (index in 0 until items.length()) items.optJSONObject(index)?.let(::redgifsItem)?.let(::add)
    }

    private fun redgifsItem(item: JSONObject): RemoteMedia? {
        val urls = item.optJSONObject("urls") ?: return null
        val url = urls.optString("hd").takeIf(String::isNotBlank)
            ?: urls.optString("sd").takeIf(String::isNotBlank)
            ?: return null
        val id = item.optString("id", normalized(url)).lowercase()
        val isImage = item.optInt("type", 1) == 2 || directKind(url) == MediaKind.IMAGE
        return RemoteMedia(
            key = "redgifs:$id",
            url = url,
            kind = if (isImage) MediaKind.IMAGE else MediaKind.VIDEO,
            hasAudio = item.optBoolean("hasAudio", true),
        )
    }

    private fun redgifsGet(url: String): Pair<Int, String> {
        if (redgifsToken == null) {
            val tokenRequest = Request.Builder()
                .url("https://api.redgifs.com/v2/auth/temporary")
                .header("User-Agent", USER_AGENT)
                .build()
            redgifsToken = http.newCall(tokenRequest).execute().use {
                if (!it.isSuccessful) return it.code to ""
                JSONObject(it.body?.string().orEmpty()).optString("token").takeIf(String::isNotBlank)
            }
        }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${redgifsToken.orEmpty()}")
            .header("Origin", "https://www.redgifs.com")
            .header("Referer", "https://www.redgifs.com/")
            .header("User-Agent", USER_AGENT)
            .build()
        return http.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
    }

    private fun String.unescaped() = replace("&amp;", "&")
    private fun normalized(url: String) = url.substringBefore('?').lowercase()

    private companion object {
        val REDGIFS_REGEX = Regex("(?:www\\.)?redgifs\\.com/(?:watch|ifr)/([a-z0-9]+)", RegexOption.IGNORE_CASE)
        val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp", ".avif", ".gif")
        val VIDEO_EXTENSIONS = listOf(".mp4", ".m3u8", ".mpd")
        const val USER_AGENT = "Scrollwave/0.1.0"
    }
}
