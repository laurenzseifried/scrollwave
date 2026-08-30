package de.laurenz.scrollwave

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaResolverTest {
    private val resolver = MediaResolver()

    @Test
    fun resolvesDirectImage() = runBlocking {
        val post = resolver.resolve(
            JSONObject(
                """
                {
                  "name": "t3_image",
                  "author": "alice",
                  "created_utc": 123,
                  "url_overridden_by_dest": "https://i.redd.it/example.webp?width=1080"
                }
                """.trimIndent(),
            ),
        )

        assertNotNull(post)
        assertEquals("alice", post?.author)
        assertEquals(MediaKind.IMAGE, post?.media?.single()?.kind)
    }

    @Test
    fun prefersHlsForRedditVideoWithAudio() = runBlocking {
        val post = resolver.resolve(
            JSONObject(
                """
                {
                  "name": "t3_video",
                  "author": "bob",
                  "created_utc": 456,
                  "secure_media": {
                    "reddit_video": {
                      "hls_url": "https://v.redd.it/abc/HLSPlaylist.m3u8?a=1&amp;b=2",
                      "fallback_url": "https://v.redd.it/abc/DASH_720.mp4",
                      "is_gif": false
                    }
                  }
                }
                """.trimIndent(),
            ),
        )

        val media = post?.media?.single()
        assertEquals("https://v.redd.it/abc/HLSPlaylist.m3u8?a=1&b=2", media?.url)
        assertEquals(MediaKind.VIDEO, media?.kind)
        assertTrue(media?.hasAudio == true)
    }

    @Test
    fun keepsGalleryOrderAndMediaTypes() = runBlocking {
        val post = resolver.resolve(
            JSONObject(
                """
                {
                  "name": "t3_gallery",
                  "author": "carol",
                  "created_utc": 789,
                  "gallery_data": {"items": [{"media_id": "one"}, {"media_id": "two"}]},
                  "media_metadata": {
                    "one": {"e": "Image", "s": {"u": "https://i.redd.it/one.jpg"}},
                    "two": {"e": "AnimatedImage", "s": {"mp4": "https://i.redd.it/two.mp4"}}
                  }
                }
                """.trimIndent(),
            ),
        )

        assertEquals(listOf(MediaKind.IMAGE, MediaKind.VIDEO), post?.media?.map(RemoteMedia::kind))
        assertFalse(post?.media?.last()?.hasAudio ?: true)
    }

    @Test
    fun resolvesCrosspostMediaButKeepsOuterAuthorAndTime() = runBlocking {
        val post = resolver.resolve(
            JSONObject(
                """
                {
                  "name": "t3_crosspost",
                  "author": "crossposter",
                  "created_utc": 999,
                  "crosspost_parent_list": [{
                    "name": "t3_original",
                    "author": "original",
                    "created_utc": 100,
                    "url_overridden_by_dest": "https://i.redd.it/crosspost.png"
                  }]
                }
                """.trimIndent(),
            ),
        )

        assertEquals("crossposter", post?.author)
        assertEquals(999L, post?.createdUtc)
        assertEquals(MediaKind.IMAGE, post?.media?.single()?.kind)
    }
}
