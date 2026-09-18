package io.github.javcinema.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedLruTest {

    @Test
    fun mapEvictsEldestAfterCapacity() {
        val map = BoundedLruMap<Int, String>(80)
        repeat(81) { map[it] = "v$it" }
        assertEquals(80, map.size())
        assertFalse(map.containsKey(0))
        assertTrue(map.containsKey(80))
    }

    @Test
    fun setEvictsEldestAfterCapacity() {
        val set = BoundedLruSet<Int>(80)
        repeat(81) { set.add(it) }
        assertEquals(80, set.size())
        assertFalse(set.contains(0))
        assertTrue(set.contains(80))
    }
}
