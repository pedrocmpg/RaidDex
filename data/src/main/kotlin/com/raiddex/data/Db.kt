package com.raiddex.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.raiddex.domain.Attacker
import com.raiddex.domain.Ivs
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "pokemon")
data class PokemonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val speciesId: String,
    val nickname: String = "",
    val cp: Int,
    val hp: Int,
    val level: Double,
    val atkIv: Int,
    val defIv: Int,
    val staIv: Int,
    val fastId: String,
    val chargedId: String,
    val shadow: Boolean = false,
    val favorite: Boolean = false,
    /** Fase 2: IV só estimado (sem a tela de avaliação). */
    val ivEstimated: Boolean = false,
) {
    fun toAttacker() = Attacker(id, speciesId, level, Ivs(atkIv, defIv, staIv), fastId, chargedId, shadow)
}

@Dao
interface PokemonDao {
    @Query("SELECT * FROM pokemon ORDER BY favorite DESC, cp DESC")
    fun observeAll(): Flow<List<PokemonEntity>>

    @Query("SELECT * FROM pokemon")
    suspend fun getAll(): List<PokemonEntity>

    @Query("SELECT * FROM pokemon WHERE id = :id")
    suspend fun get(id: Long): PokemonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: PokemonEntity): Long

    @Update
    suspend fun update(p: PokemonEntity)

    @Delete
    suspend fun delete(p: PokemonEntity)
}

@Database(entities = [PokemonEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun pokemon(): PokemonDao

    companion object {
        fun create(context: Context): AppDb =
            Room.databaseBuilder(context.applicationContext, AppDb::class.java, "raiddex.db").build()
    }
}
