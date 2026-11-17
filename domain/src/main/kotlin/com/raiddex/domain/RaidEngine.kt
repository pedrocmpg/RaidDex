package com.raiddex.domain

import kotlin.math.ceil
import kotlin.math.floor

/** Pokémon da coleção, já reduzido ao que o motor precisa. */
data class Attacker(
    val ref: Long,
    val speciesId: String,
    val level: Double,
    val ivs: Ivs,
    val fastId: String,
    val chargedId: String,
    val shadow: Boolean = false,
)

enum class Weather { CLEAR, FOG, OVERCAST, PARTLY_CLOUDY, RAINY, SNOW, WINDY }

data class RaidContext(
    val bossSpeciesId: String,
    val tierId: String,
    val weather: Weather? = null,
    val friendshipLevel: Int = 0,
    val includeMegas: Boolean = false,
    val maxMegas: Int = 1,
    val teamSize: Int = 6,
    /** Só avisa se outro golpe render pelo menos esta razão da nota atual. */
    val betterMoveRatio: Double = 1.2,
)

data class MoveSuggestion(val fastId: String, val chargedId: String, val score: Double)

data class Score(val dps: Double, val tdo: Double) {
    val value: Double get() = dps * dps * dps * tdo
}

data class RankedAttacker(
    val rank: Int,
    val attacker: Attacker,
    /** Espécie efetivamente usada (a mega, quando for o caso). */
    val speciesId: String,
    val mega: Boolean,
    val score: Double,
    val dps: Double,
    val tdo: Double,
    val betterMove: MoveSuggestion? = null,
)

class RaidEngine(private val data: GameData) {
    private val m = data.file.multipliers
    private val params = data.file.bossParams

    private class Boss(val species: Species, val atk: Double, val def: Double, val hp: Int, val movesets: List<Pair<Move, Move>>)

    private fun boss(ctx: RaidContext): Boss {
        val sp = data.species(ctx.bossSpeciesId)
        val tier = data.tiersById[ctx.tierId] ?: error("Nível de raid desconhecido: ${ctx.tierId}")
        val iv = params.bossIv
        val sets = sp.fast.flatMap { f -> sp.charged.map { c -> data.move(f) to data.move(c) } }
        require(sets.isNotEmpty()) { "Chefe sem golpes: ${sp.id}" }
        return Boss(sp, (sp.atk + iv) * tier.cpm, (sp.def + iv) * tier.cpm, tier.hp, sets)
    }

    private fun weatherBoosts(ctx: RaidContext, type: String): Boolean =
        ctx.weather != null && data.file.weather[ctx.weather.name]?.contains(type) == true

    private fun damage(power: Double, atk: Double, def: Double, multiplier: Double): Int =
        floor(0.5 * power * atk / def * multiplier).toInt() + 1

    /** Dano por segundo e dano por ciclo (rápido × n + carregado), sem simular a batalha. */
    private fun cycleDps(fastDamage: Int, fast: Move, chargedDamage: Int, charged: Move, delaySeconds: Double): Double {
        val n = ceil(charged.energy.toDouble() / fast.energy).toInt()
        val damage = n * fastDamage + chargedDamage
        val time = n * (fast.durationMs / 1000.0 + delaySeconds) + (charged.durationMs / 1000.0 + delaySeconds)
        return damage / time
    }

    /** Dano por segundo que o chefe causa ao defensor, média sobre todos os conjuntos de golpes do chefe. */
    private fun bossDps(ctx: RaidContext, boss: Boss, defenderTypes: List<String>, defenderDef: Double): Double {
        var total = 0.0
        for ((f, c) in boss.movesets) {
            fun dmg(mv: Move): Int {
                var mult = data.effectiveness(mv.type, defenderTypes)
                if (mv.type in boss.species.types) mult *= m.stab
                if (weatherBoosts(ctx, mv.type)) mult *= m.weather
                return damage(mv.power, boss.atk, defenderDef, mult)
            }
            total += cycleDps(dmg(f), f, dmg(c), c, params.bossMoveDelaySeconds)
        }
        return total / boss.movesets.size
    }

    private fun score(ctx: RaidContext, boss: Boss, sp: Species, a: Attacker, fast: Move, charged: Move): Score {
        val cpm = data.cpm(a.level)
        val atk = (sp.atk + a.ivs.atk) * cpm * (if (a.shadow) m.shadowAttack else 1.0)
        val def = (sp.def + a.ivs.def) * cpm * (if (a.shadow) m.shadowDefense else 1.0)
        val friend = m.friendship[ctx.friendshipLevel.toString()] ?: 1.0

        fun dmg(mv: Move): Int {
            var mult = data.effectiveness(mv.type, boss.species.types) * friend
            if (mv.type in sp.types) mult *= m.stab
            if (weatherBoosts(ctx, mv.type)) mult *= m.weather
            return damage(mv.power, atk, boss.def, mult)
        }

        // Atacante não espera entre golpes: duração do golpe é o ciclo.
        val dps = cycleDps(dmg(fast), fast, dmg(charged), charged, 0.0)
        val health = Formulas.hp(sp, a.ivs, cpm)
        val taken = bossDps(ctx, boss, sp.types, def)
        val tdo = dps * (health / taken)
        return Score(dps, tdo)
    }

    private class Candidate(val attacker: Attacker, val species: Species, val score: Score, val better: MoveSuggestion?)

    fun rank(collection: List<Attacker>, ctx: RaidContext): List<RankedAttacker> {
        val boss = boss(ctx)
        val candidates = ArrayList<Candidate>()
        for (a in collection) {
            val base = data.species(a.speciesId)
            val forms = buildList {
                add(base)
                if (ctx.includeMegas) addAll(data.megasByBase[base.id].orEmpty())
            }
            for (sp in forms) {
                val current = score(ctx, boss, sp, a, data.move(a.fastId), data.move(a.chargedId))
                candidates.add(Candidate(a, sp, current, betterMove(ctx, boss, sp, a, current)))
            }
        }
        candidates.sortByDescending { it.score.value }

        val team = ArrayList<RankedAttacker>()
        val used = HashSet<Long>()
        var megas = 0
        for (c in candidates) {
            if (team.size == ctx.teamSize) break
            if (c.attacker.ref in used) continue
            if (c.species.mega && megas >= ctx.maxMegas) continue
            used.add(c.attacker.ref)
            if (c.species.mega) megas++
            team.add(
                RankedAttacker(
                    rank = team.size + 1, attacker = c.attacker, speciesId = c.species.id, mega = c.species.mega,
                    score = c.score.value, dps = c.score.dps, tdo = c.score.tdo, betterMove = c.better,
                ),
            )
        }
        return team
    }

    /** Melhor par de golpes do movepool completo (inclui elite), se superar o atual pela razão configurada. */
    private fun betterMove(ctx: RaidContext, boss: Boss, sp: Species, a: Attacker, current: Score): MoveSuggestion? {
        var best: MoveSuggestion? = null
        for (f in sp.fast + sp.eliteFast) for (c in sp.charged + sp.eliteCharged) {
            if (f == a.fastId && c == a.chargedId) continue
            val s = score(ctx, boss, sp, a, data.move(f), data.move(c)).value
            if (best == null || s > best.score) best = MoveSuggestion(f, c, s)
        }
        return best?.takeIf { it.score >= current.value * ctx.betterMoveRatio }
    }
}
