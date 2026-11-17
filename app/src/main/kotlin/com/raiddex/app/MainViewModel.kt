package com.raiddex.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raiddex.data.AppDb
import com.raiddex.data.GameDataRepository
import com.raiddex.data.PokemonEntity
import com.raiddex.data.Settings
import com.raiddex.domain.GameData
import com.raiddex.domain.RaidContext
import com.raiddex.domain.RaidEngine
import com.raiddex.domain.RankedAttacker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Collection : Screen
    data class Edit(val id: Long?) : Screen
    data object Team : Screen
    data object Settings : Screen
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = GameDataRepository(app)
    private val dao = AppDb.create(app).pokemon()
    val settings = Settings(app)

    var screen: Screen by mutableStateOf(Screen.Collection)
    var data: GameData by mutableStateOf(repo.current)
        private set
    var updateStatus by mutableStateOf("")
        private set
    var trainerLevel by mutableStateOf(settings.trainerLevel)
        private set

    val collection: StateFlow<List<PokemonEntity>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun load(id: Long): PokemonEntity? = dao.get(id)

    fun save(p: PokemonEntity) = viewModelScope.launch { dao.upsert(p); screen = Screen.Collection }
    fun delete(p: PokemonEntity) = viewModelScope.launch { dao.delete(p); screen = Screen.Collection }
    fun toggleFavorite(p: PokemonEntity) = viewModelScope.launch { dao.update(p.copy(favorite = !p.favorite)) }

    fun changeTrainerLevel(level: Int) {
        settings.trainerLevel = level
        trainerLevel = settings.trainerLevel
    }

    fun updateData() = viewModelScope.launch {
        updateStatus = "Baixando…"
        updateStatus = when (val r = repo.update()) {
            is GameDataRepository.UpdateResult.Updated -> { data = repo.current; "Atualizado para ${r.version}" }
            is GameDataRepository.UpdateResult.UpToDate -> "Já está na versão mais recente (${r.version})"
            is GameDataRepository.UpdateResult.Failed -> "Falhou: ${r.reason}"
        }
    }

    suspend fun rank(ctx: RaidContext): List<RankedAttacker> = withContext(Dispatchers.Default) {
        RaidEngine(data).rank(dao.getAll().map { it.toAttacker() }, ctx)
    }
}
