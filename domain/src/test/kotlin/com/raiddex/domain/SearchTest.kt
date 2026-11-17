package com.raiddex.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchTest {
    private val all = TestData.data.file.species

    @Test
    fun prefixAndAccents() {
        assertEquals("SWAMPERT", Search.species(all, "swamp").first().id)
        assertEquals("Raichu", Search.species(all, "RAICHU").first().name)
    }

    @Test
    fun toleratesTypos() {
        assertEquals("SWAMPERT", Search.species(all, "swampret").first().id)
    }

    @Test
    fun accentInsensitive() {
        val s = all.first { Search.normalize(it.name).contains("a") }
        assertEquals(true, Search.species(all, Search.normalize(s.name).take(4)).isNotEmpty())
    }
}
