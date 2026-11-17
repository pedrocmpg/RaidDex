package com.raiddex.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Move(
    val id: String,
    val name: String,
    val type: String,
    val power: Double,
    val energy: Int,
    val durationMs: Int,
    val fast: Boolean,
)

@Serializable
data class Species(
    val id: String,
    val dex: Int,
    val name: String,
    val types: List<String>,
    val atk: Int,
    val def: Int,
    val sta: Int,
    val fast: List<String>,
    val charged: List<String>,
    val eliteFast: List<String> = emptyList(),
    val eliteCharged: List<String> = emptyList(),
    val mega: Boolean = false,
    val megaOf: String? = null,
    val shadowable: Boolean = false,
)

@Serializable
data class Multipliers(
    val stab: Double,
    val weather: Double,
    val shadowAttack: Double,
    val shadowDefense: Double,
    val friendship: Map<String, Double>,
)

@Serializable
data class BossTier(
    val id: String,
    val label: String,
    val hp: Int,
    val cpm: Double,
    val confirmed: Boolean = false,
    val source: String = "",
)

@Serializable
data class BossParams(
    val bossIv: Int = 15,
    val bossMoveDelaySeconds: Double = 2.0,
)

@Serializable
data class GameDataFile(
    val schemaVersion: Int,
    val version: String,
    val types: List<String>,
    val typeChart: Map<String, Map<String, Double>>,
    val weather: Map<String, List<String>>,
    val multipliers: Multipliers,
    val cpm: List<Double>,
    val moves: List<Move>,
    val species: List<Species>,
    val bosses: List<BossTier>,
    val bossParams: BossParams = BossParams(),
)

/** Dados do jogo carregados do gamedata.json, com índices por id. */
class GameData(val file: GameDataFile) {
    val version: String get() = file.version
    val speciesById: Map<String, Species> = file.species.associateBy { it.id }
    val movesById: Map<String, Move> = file.moves.associateBy { it.id }
    val tiersById: Map<String, BossTier> = file.bosses.associateBy { it.id }
    val megasByBase: Map<String, List<Species>> =
        file.species.filter { it.megaOf != null }.groupBy { it.megaOf!! }

    fun species(id: String): Species = speciesById[id] ?: error("Espécie desconhecida: $id")
    fun move(id: String): Move = movesById[id] ?: error("Golpe desconhecido: $id")

    /** CPM no nível dado (inteiro ou meio nível); meios níveis usam a raiz da média dos quadrados. */
    fun cpm(level: Double): Double {
        val table = file.cpm
        val whole = level.toInt()
        require(level >= 1.0 && level <= table.size.toDouble() && (level * 2) % 1.0 == 0.0) { "Nível inválido: $level" }
        val low = table[whole - 1]
        if (level == whole.toDouble()) return low
        val high = table[whole]
        return Math.sqrt((low * low + high * high) / 2.0)
    }

    fun effectiveness(attackType: String, defenderTypes: List<String>): Double =
        defenderTypes.fold(1.0) { acc, t -> acc * (file.typeChart[attackType]?.get(t) ?: 1.0) }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): GameData {
            val file = json.decodeFromString<GameDataFile>(text)
            require(file.schemaVersion == 1) { "schemaVersion não suportado: ${file.schemaVersion}" }
            require(file.cpm.size >= 51 && file.species.isNotEmpty() && file.moves.isNotEmpty()) { "gamedata incompleto" }
            return GameData(file)
        }
    }
}
