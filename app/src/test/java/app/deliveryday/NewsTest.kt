package app.deliveryday

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsTest {
    @Test fun highlightEndsAfter24HoursOrWhenSeen() {
        val at = 1_000L
        assertTrue(News.highlighted(at, 0, at))
        assertTrue(News.highlighted(at, 0, at + 86_399_999L))
        assertFalse(News.highlighted(at, 0, at + 86_400_000L))
        assertFalse(News.highlighted(at, at, at + 1))
        assertTrue(News.highlighted(at + 2, at, at + 3))
        assertFalse(News.highlighted(0, 0, at))
        assertFalse(News.highlighted(at, 0, at - 1))
    }
}
