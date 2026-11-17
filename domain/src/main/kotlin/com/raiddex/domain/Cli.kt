package com.raiddex.domain

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CollectionEntry(
    val species: String,
    val level: Double,
    val atk: Int,
    val def: Int,
    val sta: Int,
    val fast: String,
    val charged: String,
    val shadow: Boolean = false,
)

private val json = Json { ignoreUnknownKeys = true }

/**
 * Harness de validação do top 6 (comparar com Pokebattler).
 * Uso: ./gradlew :domain:run --args="<gamedata.json> <colecao.json> <BOSS_ID> <TIER> [megas]"
 */
fun main(args: Array<String>) {
    if (args.size < 4) {
        System.err.println("Uso: <gamedata.json> <colecao.json> <BOSS_ID> <TIER> [megas]")
        return
    }
    val data = GameData.parse(File(args[0]).readText())
    val entries = json.decodeFromString<List<CollectionEntry>>(File(args[1]).readText())
    val attackers = entries.mapIndexed { i, e ->
        Attacker(i.toLong(), e.species, e.level, Ivs(e.atk, e.def, e.sta), e.fast, e.charged, e.shadow)
    }
    val ctx = RaidContext(args[2], args[3], includeMegas = args.getOrNull(4) == "megas")
    println("Dados ${data.version} | chefe ${args[2]} (${args[3]})")
    for (r in RaidEngine(data).rank(attackers, ctx)) {
        val name = data.species(r.speciesId).name
        val warn = r.betterMove?.let { "  ! melhor: ${data.move(it.fastId).name} + ${data.move(it.chargedId).name}" }.orEmpty()
        println("%d. %-22s nota %.0f  DPS %.2f  TDO %.0f%s".format(r.rank, name, r.score, r.dps, r.tdo, warn))
    }
}
