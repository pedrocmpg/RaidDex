package com.raiddex.domain

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

data class Ivs(val atk: Int, val def: Int, val sta: Int) {
    init {
        require(atk in 0..15 && def in 0..15 && sta in 0..15) { "IV fora de 0..15" }
    }

    val percent: Double get() = (atk + def + sta) / 45.0 * 100.0
}

object Formulas {
    fun cp(species: Species, ivs: Ivs, cpm: Double): Int {
        val atk = species.atk + ivs.atk
        val def = species.def + ivs.def
        val sta = species.sta + ivs.sta
        return max(10, floor(atk * sqrt(def.toDouble()) * sqrt(sta.toDouble()) * cpm * cpm / 10.0).toInt())
    }

    fun hp(species: Species, ivs: Ivs, cpm: Double): Int =
        max(10, floor((species.sta + ivs.sta) * cpm).toInt())
}

data class IvCandidate(val level: Double, val ivs: Ivs)

object IvSolver {
    /**
     * Todas as combinações (nível, IVs) que produzem exatamente [cp] e [hp].
     * [knownIvs] (da tela de avaliação) restringe a busca a um único trio de IVs.
     */
    fun solve(
        data: GameData,
        species: Species,
        cp: Int,
        hp: Int,
        knownIvs: Ivs? = null,
        maxLevel: Double = 51.0,
    ): List<IvCandidate> {
        val out = ArrayList<IvCandidate>()
        var level = 1.0
        while (level <= maxLevel) {
            val cpm = data.cpm(level)
            for (s in 0..15) {
                if (knownIvs != null && s != knownIvs.sta) continue
                if (Formulas.hp(species, Ivs(0, 0, s), cpm) != hp) continue
                for (a in 0..15) {
                    if (knownIvs != null && a != knownIvs.atk) continue
                    for (d in 0..15) {
                        if (knownIvs != null && d != knownIvs.def) continue
                        val ivs = Ivs(a, d, s)
                        if (Formulas.cp(species, ivs, cpm) == cp) out.add(IvCandidate(level, ivs))
                    }
                }
            }
            level += 0.5
        }
        return out
    }
}
