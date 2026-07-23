package com.example.p2p.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PacketDao {

    @Query("SELECT * FROM stored_packets ORDER BY timestamp DESC")
    fun getAllPackets(): Flow<List<PacketEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPacket(packet: PacketEntity)

    @Query("DELETE FROM stored_packets WHERE id = :id")
    suspend fun deletePacket(id: String)

    @Query("DELETE FROM stored_packets")
    suspend fun clearAll()

    @Query("SELECT SUM(hostedBlockSizeBytes) FROM stored_packets")
    fun getTotalHostedBytes(): Flow<Long?>
}
