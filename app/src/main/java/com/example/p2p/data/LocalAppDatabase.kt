package com.example.p2p.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Local-First Room Database for serverless app state, user-generated CRDT data,
 * packet logs, and discovered peer topologies.
 */
@Database(
    entities = [
        ContentEntity::class,
        PacketEntity::class,
        PeerEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class LocalAppDatabase : RoomDatabase() {

    abstract fun contentDao(): ContentDao
    abstract fun packetDao(): PacketDao
    abstract fun peerDao(): PeerDao

    companion object {
        @Volatile
        private var INSTANCE: LocalAppDatabase? = null

        fun getInstance(context: Context): LocalAppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LocalAppDatabase::class.java,
                    "noserver_local_app_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
