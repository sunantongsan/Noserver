package com.example.p2p.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for local CRDT content items.
 */
@Dao
interface ContentDao {

    @Query("SELECT * FROM crdt_content_items WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllActiveContent(): Flow<List<ContentEntity>>

    @Query("SELECT * FROM crdt_content_items ORDER BY timestamp DESC")
    fun getAllContentWithTombstones(): Flow<List<ContentEntity>>

    @Query("SELECT * FROM crdt_content_items WHERE `key` = :key LIMIT 1")
    suspend fun getContentByKey(key: String): ContentEntity?

    @Query("SELECT * FROM crdt_content_items WHERE timestamp > :sinceTimestamp")
    suspend fun getContentModifiedSince(sinceTimestamp: Long): List<ContentEntity>

    @Query("SELECT COUNT(*) FROM crdt_content_items WHERE isDeleted = 0")
    fun getActiveContentCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContent(content: ContentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(contents: List<ContentEntity>)

    @Query("DELETE FROM crdt_content_items WHERE `key` = :key")
    suspend fun hardDeleteByKey(key: String)

    @Query("DELETE FROM crdt_content_items")
    suspend fun clearAll()
}
