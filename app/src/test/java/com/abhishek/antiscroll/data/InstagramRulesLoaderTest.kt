package com.abhishek.antiscroll.data

import com.abhishek.antiscroll.rules.profile.Surface
import java.io.File
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InstagramRulesLoaderTest {

    /** The file that ships in the app, so a typo there fails the build's tests. */
    private val shippedJson = File("src/main/assets/${InstagramRulesLoader.FILE_NAME}").readText()

    @Test
    fun `shipped config parses`() {
        val rules = InstagramRulesLoader.parse(shippedJson)
        assertEquals(
            listOf(Surface.FEED, Surface.REELS, Surface.DMS, Surface.EXPLORE, Surface.PROFILE),
            rules.tabs.map { it.surface },
        )
        assertEquals("clips_tab", rules.tabs[1].id)
        assertEquals("Reels", rules.tabs[1].label)
        assertEquals("swipeable_tab_view_pager", rules.tabPagerId)
        assertEquals("clips_viewer_view_pager", rules.reelsPagerId)
        assertEquals(listOf("clips_carousel_viewpager", "clips_carousel_image_media_content"), rules.photoPostInReelsViewerIds)
        assertEquals(listOf("message_list"), rules.dmThreadIds)
        assertEquals(listOf("reel_viewer_root"), rules.storyViewerIds)
    }

    @Test
    fun `a config without photo-post IDs still loads`() {
        val old = shippedJson.replace(Regex("""\s*"photoPostInReelsViewerIds": \[[^]]*],"""), "")
        assertEquals(emptyList<String>(), InstagramRulesLoader.parse(old).photoPostInReelsViewerIds)
    }

    @Test
    fun `unknown surface name is rejected`() {
        val bad = shippedJson.replace("\"REELS\"", "\"SHORTS\"")
        assertThrows(IllegalArgumentException::class.java) { InstagramRulesLoader.parse(bad) }
    }

    @Test
    fun `missing field is rejected`() {
        val bad = shippedJson.replace("\"reelsPagerId\"", "\"renamed\"")
        assertThrows(JSONException::class.java) { InstagramRulesLoader.parse(bad) }
    }
}
