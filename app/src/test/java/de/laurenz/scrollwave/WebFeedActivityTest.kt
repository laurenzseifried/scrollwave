package de.laurenz.scrollwave

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebFeedActivityTest {
    @Test
    fun enhancesOnlyRedditAndRedgifsPages() {
        assertTrue(WebFeedActivity.shouldEnhance("www.reddit.com"))
        assertTrue(WebFeedActivity.shouldEnhance("v3.redgifs.com"))
        assertFalse(WebFeedActivity.shouldEnhance("reddit.com.example.org"))
        assertFalse(WebFeedActivity.shouldEnhance(null))
        assertTrue("scroll-snap-type" in WebFeedActivity.ENHANCEMENT_SCRIPT)
        assertTrue("IntersectionObserver" in WebFeedActivity.ENHANCEMENT_SCRIPT)
        assertTrue("data-scrollwave-media" in WebFeedActivity.ENHANCEMENT_SCRIPT)
        assertTrue("['image', 'gallery', 'video', 'gif']" in WebFeedActivity.ENHANCEMENT_SCRIPT)
        assertTrue("scrollwave-meta" in WebFeedActivity.ENHANCEMENT_SCRIPT)
        assertTrue("pausedByUser" in WebFeedActivity.ENHANCEMENT_SCRIPT)
        assertTrue("managedVideos" in WebFeedActivity.ENHANCEMENT_SCRIPT)
        assertTrue("video.volume = 1" in WebFeedActivity.ENHANCEMENT_SCRIPT)
        assertFalse("setInterval" in WebFeedActivity.ENHANCEMENT_SCRIPT)
    }
}
