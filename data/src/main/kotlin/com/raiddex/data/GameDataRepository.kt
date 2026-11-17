package com.raiddex.data

import android.content.Context
import com.raiddex.domain.GameData
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Carrega o gamedata.json: primeiro a cópia baixada em filesDir (se válida), senão o asset empacotado.
 * A única chamada de rede do app é [update].
 */
class GameDataRepository(private val context: Context) {
    private val downloaded get() = File(context.filesDir, FILE)

    @Volatile
    var current: GameData = load()
        private set

    /** Usa a cópia baixada só se for mais nova que o asset do APK (versões "AAAAMMDDhhmm-hash" ordenam por data). */
    private fun load(): GameData {
        val bundled = GameData.parse(context.assets.open(FILE).bufferedReader().use { it.readText() })
        if (downloaded.exists()) {
            val fresh = runCatching { GameData.parse(downloaded.readText()) }.getOrNull()
            if (fresh != null && fresh.version > bundled.version) return fresh
            downloaded.delete()
        }
        return bundled
    }

    sealed interface UpdateResult {
        data class Updated(val version: String) : UpdateResult
        data class UpToDate(val version: String) : UpdateResult
        data class Failed(val reason: String) : UpdateResult
    }

    suspend fun update(url: String = UPDATE_URL): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            if (conn.responseCode != 200) return@withContext UpdateResult.Failed("HTTP ${conn.responseCode}")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val parsed = GameData.parse(text) // valida schema antes de gravar
            if (parsed.version <= current.version) return@withContext UpdateResult.UpToDate(parsed.version)
            val tmp = File(context.filesDir, "$FILE.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(downloaded)) return@withContext UpdateResult.Failed("não foi possível gravar")
            current = parsed
            UpdateResult.Updated(parsed.version)
        } catch (e: Exception) {
            UpdateResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    companion object {
        const val FILE = "gamedata.json"
        const val UPDATE_URL = "https://raw.githubusercontent.com/pedrocmpg/RaidDex/main/app/src/main/assets/gamedata.json"
    }
}
