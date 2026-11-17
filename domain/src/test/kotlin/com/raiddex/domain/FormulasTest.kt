package com.raiddex.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FormulasTest {
    private val data = TestData.data

    @Test
    fun swampertMaxIvsGives2974() {
        val swampert = data.species("SWAMPERT")
        assertEquals(Triple(208, 175, 225), Triple(swampert.atk, swampert.def, swampert.sta))
        assertEquals(2974, Formulas.cp(swampert, Ivs(15, 15, 15), 0.7903))
    }

    @Test
    fun level40CpmFromTable() {
        assertEquals(0.7903, data.cpm(40.0), 1e-9)
    }

    @Test
    fun halfLevelCpmMatchesKnownValue() {
        // Valores conhecidos do jogo: nível 1.5 e 40.5 (0.7928; o 41 inteiro é 0.7953).
        assertEquals(0.1351374318, data.cpm(1.5), 1e-6)
        assertEquals(0.79280001, data.cpm(40.5), 1e-5)
    }

    @Test
    fun hpUsesFloorAndMinimum() {
        val swampert = data.species("SWAMPERT")
        assertEquals(189, Formulas.hp(swampert, Ivs(15, 15, 15), 0.7903)) // floor(240 * 0.7903)
        assertEquals(10, Formulas.hp(swampert, Ivs(0, 0, 0), 0.01))
    }

    @Test
    fun solverRecoversKnownPokemon() {
        val swampert = data.species("SWAMPERT")
        val found = IvSolver.solve(data, swampert, cp = 2974, hp = 189)
        assertTrue(found.any { it.level == 40.0 && it.ivs == Ivs(15, 15, 15) })
        val exact = IvSolver.solve(data, swampert, 2974, 189, knownIvs = Ivs(15, 15, 15))
        assertTrue(exact.all { it.ivs == Ivs(15, 15, 15) } && exact.any { it.level == 40.0 })
    }

    @Test
    fun solverReturnsEmptyWhenInconsistent() {
        assertTrue(IvSolver.solve(data, data.species("SWAMPERT"), cp = 2974, hp = 50).isEmpty())
    }

    @Test
    fun typeChartOrderingWaterVsFire() {
        assertEquals(1.6, data.effectiveness("WATER", listOf("FIRE")))
        assertEquals(1.6 * 1.6, data.effectiveness("WATER", listOf("FIRE", "GROUND")), 1e-9)
    }
}
