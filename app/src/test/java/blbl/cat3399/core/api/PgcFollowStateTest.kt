package blbl.cat3399.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PgcFollowStateTest {
    @Test
    fun acceptsNumericAndBooleanFollowValues() {
        for (follow in listOf("1", "true", " TRUE ")) {
            assertEquals(true, pgcFollowStateOf(follow, null))
        }
        for (follow in listOf("0", "false", " FALSE ")) {
            assertEquals(false, pgcFollowStateOf(follow, null))
        }
    }

    @Test
    fun fallsBackToEveryKnownViewingStatus() {
        assertEquals(false, pgcFollowStateOf(null, 0))
        for (status in 1..3) {
            assertEquals(true, pgcFollowStateOf("", status))
        }
    }

    @Test
    fun acceptsRelationStatusCode() {
        assertEquals(true, pgcFollowStateOf("2", null))
        assertEquals(false, pgcFollowStateOf("0", null))
        assertNull(pgcFollowStateOf("-1", null))
    }

    @Test
    fun explicitUnfollowOverridesViewingStatus() {
        assertEquals(false, pgcFollowStateOf("0", 2))
        assertEquals(true, pgcFollowStateOf("1", 0))
    }

    @Test
    fun missingOrMalformedStateMustNotBecomeUnfollowed() {
        assertNull(pgcFollowStateOf(null, null))
        assertNull(pgcFollowStateOf("", -1))
        assertNull(pgcFollowStateOf("null", 99))
    }
}
