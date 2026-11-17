package com.raiddex.domain

import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RaidEngineTest {
    private val data = TestData.data
    private val engine = RaidEngine(data)
    private var next = 0L

    private fun atk(species: String, fast: String, charged: String, level: Double = 40.0, ivs: Ivs = Ivs(15, 15, 15), shadow: Boolean = false) =
        Attacker(next++, species, level, ivs, fast, charged, shadow)

    private val ctx = RaidContext("GROUDON", "T5")

    @Test
    fun superEffectiveBeatsNeutralAgainstGroudon() {
        val team = engine.rank(
            listOf(
                atk("SWAMPERT", "WATER_GUN_FAST", "HYDRO_CANNON"),
                atk("RATTATA", "TACKLE_FAST", "BODY_SLAM"),
            ),
            ctx,
        )
        assertEquals("SWAMPERT", team.first().speciesId)
        assertEquals(listOf(1, 2), team.map { it.rank })
    }

    @Test
    fun higherIvsRankHigher() {
        val strong = atk("SWAMPERT", "WATER_GUN_FAST", "HYDRO_CANNON", ivs = Ivs(15, 15, 15))
        val weak = atk("SWAMPERT", "WATER_GUN_FAST", "HYDRO_CANNON", ivs = Ivs(0, 0, 0))
        val team = engine.rank(listOf(weak, strong), ctx)
        assertEquals(strong.ref, team.first().attacker.ref)
    }

    @Test
    fun duplicatesOfSameSpeciesAllowed() {
        val list = List(3) { atk("SWAMPERT", "WATER_GUN_FAST", "HYDRO_CANNON") }
        assertEquals(3, engine.rank(list, ctx).size)
    }

    @Test
    fun atMostOneMega() {
        val list = List(4) { atk("SWAMPERT", "WATER_GUN_FAST", "HYDRO_CANNON") }
        val team = engine.rank(list, ctx.copy(includeMegas = true))
        assertEquals(1, team.count { it.mega })
        assertEquals(4, team.size)
    }

    @Test
    fun teamCappedAtSix() {
        val list = List(10) { atk("SWAMPERT", "WATER_GUN_FAST", "HYDRO_CANNON") }
        assertEquals(6, engine.rank(list, ctx).size)
    }

    @Test
    fun warnsWhenBetterMoveExistsIncludingElite() {
        val team = engine.rank(listOf(atk("SWAMPERT", "WATER_GUN_FAST", "SLUDGE")), ctx)
        val better = team.single().betterMove
        assertTrue(better != null, "esperava aviso de golpe melhor")
        assertEquals("HYDRO_CANNON", better.chargedId)
    }

    @Test
    fun noWarningWhenAlreadyBest() {
        val team = engine.rank(listOf(atk("SWAMPERT", "WATER_GUN_FAST", "HYDRO_CANNON")), ctx)
        assertEquals(null, team.single().betterMove)
    }

    @Test
    fun twentyPokemonRankUnderOneSecond() {
        val species = data.file.species.filter { !it.mega && it.fast.isNotEmpty() && it.charged.isNotEmpty() }.take(20)
        val list = species.map { atk(it.id, it.fast.first(), it.charged.first()) }
        engine.rank(list, ctx) // aquecimento da JVM
        val ms = measureTimeMillis { engine.rank(list, ctx) }
        assertTrue(ms < 1000, "demorou ${ms}ms")
    }
}
