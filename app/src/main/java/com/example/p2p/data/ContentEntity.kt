package com.example.p2p.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONObject

/**
 * Supported CRDT Types for local-first data convergence.
 */
enum class CrdtType {
    LWW_SET,      // Last-Write-Wins Element Set with Tombstone support
    PN_COUNTER,   // Positive-Negative Counter for distributed increment/decrement
    MV_REGISTER   // Multi-Value Register
}

/**
 * Room Database entity representing a local-first CRDT content entry.
 * Designed for offline-first replication, cryptographic zero-knowledge verification,
 * and deterministic state convergence.
 *
 * @property id Unique entry identifier (UUID or composite key).
 * @property key Logical domain key or item name (e.g. "note_123" or "reputation_user_456").
 * @property value Encrypted or plain text payload content.
 * @property crdtType Type of CRDT logic applied during merging.
 * @property timestamp Epoch millisecond timestamp for Last-Write-Wins ordering.
 * @property authorNodeId Public key fingerprint / Node ID of the entry creator or updater.
 * @property pIncrement Positive counter component for PN-Counter.
 * @property nDecrement Negative counter component for PN-Counter.
 * @property isDeleted Tombstone flag for LWW-Element-Set removals.
 * @property version Monotonically increasing logical clock version.
 * @property signature Cryptographic signature signed by authorNodeId.
 */
@Entity(tableName = "crdt_content_items")
data class ContentEntity(
    @PrimaryKey val id: String,
    val key: String,
    val value: String,
    val crdtType: String = CrdtType.LWW_SET.name,
    val timestamp: Long = System.currentTimeMillis(),
    val authorNodeId: String,
    val pIncrement: Long = 0L,
    val nDecrement: Long = 0L,
    val isDeleted: Boolean = false,
    val version: Int = 1,
    val signature: String = ""
) {
    /**
     * Calculates current net counter value if this entity represents a PN_COUNTER CRDT.
     */
    fun getCounterValue(): Long = pIncrement - nDecrement

    /**
     * Converts canonical fields to JSON representation for network synchronization wire format.
     */
    fun toJson(): String {
        val json = JSONObject()
        json.put("id", id)
        json.put("key", key)
        json.put("value", value)
        json.put("crdtType", crdtType)
        json.put("timestamp", timestamp)
        json.put("authorNodeId", authorNodeId)
        json.put("pIncrement", pIncrement)
        json.put("nDecrement", nDecrement)
        json.put("isDeleted", isDeleted)
        json.put("version", version)
        json.put("signature", signature)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): ContentEntity? {
            return try {
                val json = JSONObject(jsonStr)
                ContentEntity(
                    id = json.getString("id"),
                    key = json.getString("key"),
                    value = json.getString("value"),
                    crdtType = json.optString("crdtType", CrdtType.LWW_SET.name),
                    timestamp = json.getLong("timestamp"),
                    authorNodeId = json.getString("authorNodeId"),
                    pIncrement = json.optLong("pIncrement", 0L),
                    nDecrement = json.optLong("nDecrement", 0L),
                    isDeleted = json.optBoolean("isDeleted", false),
                    version = json.optInt("version", 1),
                    signature = json.optString("signature", "")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
