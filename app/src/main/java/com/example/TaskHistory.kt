package com.example

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Status values for a tracked agent task run.
 */
object TaskStatus {
    const val RUNNING = "RUNNING"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
    const val STOPPED = "STOPPED"
}

/**
 * One row per autonomous agent run. Created when a goal starts, then updated in place
 * as the run progresses, completes, fails, or is stopped by the user.
 */
@Entity(tableName = "task_history")
data class TaskHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "goal")
    val goal: String,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    @ColumnInfo(name = "ended_at")
    val endedAt: Long? = null,

    @ColumnInfo(name = "step_count")
    val stepCount: Int = 0,

    @ColumnInfo(name = "result_message")
    val resultMessage: String? = null,

    @ColumnInfo(name = "was_simulator")
    val wasSimulator: Boolean = false
)

@Dao
interface TaskHistoryDao {

    @Insert
    suspend fun insert(task: TaskHistoryEntity): Long

    @Update
    suspend fun update(task: TaskHistoryEntity)

    @Query("SELECT * FROM task_history ORDER BY started_at DESC")
    fun getAllTasks(): Flow<List<TaskHistoryEntity>>

    @Query("SELECT * FROM task_history WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Long): TaskHistoryEntity?

    @Query("DELETE FROM task_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM task_history")
    suspend fun deleteAll()
}

@Database(entities = [TaskHistoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskHistoryDao(): TaskHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "horizon_history.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
