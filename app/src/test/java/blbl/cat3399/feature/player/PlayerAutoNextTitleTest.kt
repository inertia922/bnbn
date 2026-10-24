package blbl.cat3399.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerAutoNextTitleTest {
    @Test
    fun keepsShortTitlesAfterWhitespaceNormalization() {
        assertEquals("第12话", formatAutoNextHintTitle("  第12话  ", fallbackTitle = "推荐视频"))
    }

    @Test
    fun truncatesTitlesAtTheConfiguredLimitBeforeEllipsis() {
        val maxChars = PlayerActivity.AUTO_NEXT_TITLE_MAX_CHARS
        val raw = "0123456789".repeat((maxChars / 10) + 2)
        assertEquals(raw.take(maxChars) + "...", formatAutoNextHintTitle(raw, fallbackTitle = "推荐视频"))
    }

    @Test
    fun fallsBackWhenTitleIsBlank() {
        assertEquals("推荐视频", formatAutoNextHintTitle("  \n\t  ", fallbackTitle = "推荐视频"))
    }
}
