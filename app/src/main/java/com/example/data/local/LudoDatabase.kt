package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "saved_games")
data class SavedGameEntity(
    @PrimaryKey val id: String = "active_offline_game",
    val gameId: String,
    val mode: String,
    val status: String,
    val currentTurnIndex: Int,
    val diceValue: Int,
    val phase: String,
    val version: Int,
    val boardStateJson: String,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "local_match_history")
data class LocalMatchHistoryEntity(
    @PrimaryKey val matchId: String,
    val gameMode: String,
    val opponentNames: String,
    val winnerName: String,
    val isWin: Boolean,
    val durationSeconds: Int,
    val coinsDelta: Int,
    val xpEarned: Int,
    val completedAtMillis: Long = System.currentTimeMillis()
)

@Dao
interface SavedGameDao {
    @Query("SELECT * FROM saved_games WHERE id = 'active_offline_game' LIMIT 1")
    fun observeSavedOfflineGame(): Flow<SavedGameEntity?>

    @Query("SELECT * FROM saved_games WHERE id = 'active_offline_game' LIMIT 1")
    suspend fun getSavedOfflineGame(): SavedGameEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedGame(game: SavedGameEntity)

    @Query("DELETE FROM saved_games WHERE id = 'active_offline_game'")
    suspend fun clearSavedOfflineGame()
}

@Dao
interface LocalMatchHistoryDao {
    @Query("SELECT * FROM local_match_history ORDER BY completedAtMillis DESC LIMIT 50")
    fun observeLocalHistory(): Flow<List<LocalMatchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatch(match: LocalMatchHistoryEntity)
}

@Database(
    entities = [SavedGameEntity::class, LocalMatchHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class LudoDatabase : RoomDatabase() {
    abstract fun savedGameDao(): SavedGameDao
    abstract fun localMatchHistoryDao(): LocalMatchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: LudoDatabase? = null

        fun getInstance(context: Context): LudoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LudoDatabase::class.java,
                    "royal_dice_ludo.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
