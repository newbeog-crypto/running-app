package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyStepsDao {
    @Query("SELECT * FROM daily_steps WHERE dateKey = :dateKey LIMIT 1")
    fun observeByDate(dateKey: String): Flow<DailyStepsEntity?>

    @Query("SELECT * FROM daily_steps ORDER BY dateKey DESC LIMIT :limit")
    fun observeRecentDays(limit: Int = 7): Flow<List<DailyStepsEntity>>

    @Query("SELECT * FROM daily_steps WHERE dateKey = :dateKey LIMIT 1")
    suspend fun getByDateOnce(dateKey: String): DailyStepsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DailyStepsEntity)
}
