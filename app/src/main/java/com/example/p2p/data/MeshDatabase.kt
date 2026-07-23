package com.example.p2p.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PacketEntity::class, PeerEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MeshDatabase : RoomDatabase() {

    abstract fun packetDao(): PacketDao
    abstract fun peerDao(): PeerDao

    companion object {
        @Volatile
        private var INSTANCE: MeshDatabase? = null

        fun getInstance(context: Context): MeshDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MeshDatabase::class.java,
                    "living_mesh_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
