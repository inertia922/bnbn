package blbl.cat3399.feature.video

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoCardOwnerNameTest {
    @Test
    fun shortNamesStayUntouched() {
        assertEquals("自由街小王", ellipsizeOwnerName("自由街小王"))
        assertEquals("MrBeast", ellipsizeOwnerName("MrBeast"))
    }

    @Test
    fun sixChineseCharactersStillFit() {
        assertEquals("茶水室电影院", ellipsizeOwnerName("茶水室电影院"))
    }

    @Test
    fun longChineseNameIsCutAfterSixCharacters() {
        assertEquals("茶水室电影院…", ellipsizeOwnerName("茶水室电影院线"))
    }

    @Test
    fun mixedNameKeepsRoomForTheDate() {
        assertEquals("稽木白柏 cyp…", ellipsizeOwnerName("稽木白柏 cypress"))
    }

    @Test
    fun blankNameStaysBlank() {
        assertEquals("", ellipsizeOwnerName("   "))
    }
}
