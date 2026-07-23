package com.example.p2p.data

import android.util.Log
import com.example.p2p.DataPacket
import com.example.p2p.NodeIdentityManager
import com.example.p2p.PacketType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.UUID

/**
 * Result of merging an incoming remote CRDT item into local database.
 */
enum class SyncMergeResult {
    INSERTED,      // Item was missing locally and inserted
    UPDATED,       // Remote item was newer (LWW) and replaced local
    COUNTER_MERGED,// PN-Counter positive/negative vector state merged
    IGNORED,       // Local item was newer or tie-breaker favored local
    INVALID_SIG    // Cryptographic signature check failed
}

/**
 * State metrics snapshot emitted by CRDT Sync Engine for UI monitoring.
 */
data class CrdtSyncStats(
    val activeItemsCount: Int = 0,
    val totalMergedEvents: Int = 0,
    val conflictsResolvedCount: Int = 0,
    val lastSyncTime: Long = 0L,
    val isSyncing: Boolean = false
)

/**
 * Decentralized local-first CRDT Sync Engine.
 * Resolves offline-online state divergence using Last-Write-Wins (LWW-Element-Set)
 * and Positive-Negative Counter (PN-Counter) algorithms without central server authority.
 */
class CrdtSyncEngine(
    private val contentDao: ContentDao,
    private val identityManager: NodeIdentityManager
) {

    private val tag = "CrdtSyncEngine"
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _syncStats = MutableStateFlow(CrdtSyncStats())
    val syncStats: StateFlow<CrdtSyncStats> = _syncStats.asStateFlow()

    init {
        // Observe active content count reactively from Room
        scope.launch {
            contentDao.getActiveContentCount().collect { count ->
                _syncStats.value = _syncStats.value.copy(activeItemsCount = count)
            }
        }
    }

    /**
     * Creates or updates a local LWW-Element-Set content entry signed by this node.
     */
    suspend fun upsertLocalContent(
        key: String,
        value: String,
        crdtType: CrdtType = CrdtType.LWW_SET
    ): ContentEntity = withContext(Dispatchers.IO) {
        val existing = contentDao.getContentByKey(key)
        val now = System.currentTimeMillis()
        val version = (existing?.version ?: 0) + 1
        val id = existing?.id ?: UUID.randomUUID().toString()

        val unsigned = ContentEntity(
            id = id,
            key = key,
            value = value,
            crdtType = crdtType.name,
            timestamp = now,
            authorNodeId = identityManager.nodeId,
            pIncrement = existing?.pIncrement ?: 0L,
            nDecrement = existing?.nDecrement ?: 0L,
            isDeleted = false,
            version = version,
            signature = ""
        )

        val signed = signContentEntity(unsigned)
        contentDao.upsertContent(signed)
        Log.d(tag, "Local content upserted: key=$key, version=$version")
        signed
    }

    /**
     * Performs a Tombstone soft-deletion for LWW-Element-Set.
     */
    suspend fun deleteLocalContent(key: String): ContentEntity? = withContext(Dispatchers.IO) {
        val existing = contentDao.getContentByKey(key) ?: return@withContext null
        val now = System.currentTimeMillis()

        val unsignedTombstone = existing.copy(
            isDeleted = true,
            timestamp = now,
            version = existing.version + 1,
            signature = ""
        )

        val signedTombstone = signContentEntity(unsignedTombstone)
        contentDao.upsertContent(signedTombstone)
        Log.d(tag, "Local content tombstone inserted for key: $key")
        signedTombstone
    }

    /**
     * Adjusts a PN-Counter (Positive-Negative Counter) locally by a specified delta (positive or negative).
     */
    suspend fun applyPnCounterDelta(key: String, delta: Long): ContentEntity = withContext(Dispatchers.IO) {
        val existing = contentDao.getContentByKey(key)
        val now = System.currentTimeMillis()
        val currentP = existing?.pIncrement ?: 0L
        val currentN = existing?.nDecrement ?: 0L

        val newP = if (delta > 0) currentP + delta else currentP
        val newN = if (delta < 0) currentN + kotlin.math.abs(delta) else currentN
        val version = (existing?.version ?: 0) + 1
        val id = existing?.id ?: UUID.randomUUID().toString()

        val unsigned = ContentEntity(
            id = id,
            key = key,
            value = "${newP - newN}",
            crdtType = CrdtType.PN_COUNTER.name,
            timestamp = now,
            authorNodeId = identityManager.nodeId,
            pIncrement = newP,
            nDecrement = newN,
            isDeleted = false,
            version = version,
            signature = ""
        )

        val signed = signContentEntity(unsigned)
        contentDao.upsertContent(signed)
        Log.d(tag, "PN-Counter delta applied to $key: delta=$delta, newNet=${newP - newN}")
        signed
    }

    /**
     * Main CRDT State Convergence Algorithm.
     * Merges an incoming remote ContentEntity received from a peer node into local storage.
     */
    suspend fun mergeIncomingContent(remoteEntity: ContentEntity): SyncMergeResult = withContext(Dispatchers.IO) {
        // 1. Zero-Trust Cryptographic Signature Check (if signature provided)
        if (remoteEntity.signature.isNotBlank()) {
            val isSigValid = verifyContentSignature(remoteEntity)
            if (!isSigValid) {
                Log.w(tag, "Rejected incoming CRDT entity for key=${remoteEntity.key}: Invalid Signature")
                return@withContext SyncMergeResult.INVALID_SIG
            }
        }

        val localEntity = contentDao.getContentByKey(remoteEntity.key)

        // 2. Case A: Missing locally -> Insert remote directly
        if (localEntity == null) {
            contentDao.upsertContent(remoteEntity)
            recordMergeSuccess(isConflictResolved = false)
            Log.d(tag, "Inserted new remote CRDT entity for key=${remoteEntity.key}")
            return@withContext SyncMergeResult.INSERTED
        }

        // 3. Case B: PN-Counter Merge Logic (Commutative Vector Max)
        if (remoteEntity.crdtType == CrdtType.PN_COUNTER.name || localEntity.crdtType == CrdtType.PN_COUNTER.name) {
            val mergedP = maxOf(localEntity.pIncrement, remoteEntity.pIncrement)
            val mergedN = maxOf(localEntity.nDecrement, remoteEntity.nDecrement)
            val netValue = mergedP - mergedN
            val maxTimestamp = maxOf(localEntity.timestamp, remoteEntity.timestamp)
            val maxVersion = maxOf(localEntity.version, remoteEntity.version) + 1

            val mergedEntity = localEntity.copy(
                value = "$netValue",
                pIncrement = mergedP,
                nDecrement = mergedN,
                timestamp = maxTimestamp,
                version = maxVersion
            )

            val signedMerged = signContentEntity(mergedEntity)
            contentDao.upsertContent(signedMerged)
            recordMergeSuccess(isConflictResolved = true)
            Log.i(tag, "PN-Counter state merged for key=${remoteEntity.key}: Net value=$netValue")
            return@withContext SyncMergeResult.COUNTER_MERGED
        }

        // 4. Case C: LWW-Element-Set Conflict Resolution (Last-Write-Wins with Deterministic Tie-Breaker)
        val remoteTs = remoteEntity.timestamp
        val localTs = localEntity.timestamp

        return@withContext when {
            // Remote is strictly newer -> Remote Wins
            remoteTs > localTs -> {
                contentDao.upsertContent(remoteEntity)
                recordMergeSuccess(isConflictResolved = true)
                Log.i(tag, "LWW Conflict Resolved: Remote won for key=${remoteEntity.key} (remoteTs=$remoteTs > localTs=$localTs)")
                SyncMergeResult.UPDATED
            }
            // Local is strictly newer -> Local Wins (Ignore remote)
            remoteTs < localTs -> {
                Log.d(tag, "LWW Conflict Ignored: Local is newer for key=${remoteEntity.key} (localTs=$localTs > remoteTs=$remoteTs)")
                SyncMergeResult.IGNORED
            }
            // Timestamp Exact Tie -> Deterministic Tie-Breaker (Compare authorNodeId lexicographically)
            else -> {
                val tieBreakerRemoteWins = remoteEntity.authorNodeId.compareTo(localEntity.authorNodeId) > 0
                if (tieBreakerRemoteWins) {
                    contentDao.upsertContent(remoteEntity)
                    recordMergeSuccess(isConflictResolved = true)
                    Log.i(tag, "LWW Tie-Breaker: Remote won tie for key=${remoteEntity.key}")
                    SyncMergeResult.UPDATED
                } else {
                    Log.d(tag, "LWW Tie-Breaker: Local won tie for key=${remoteEntity.key}")
                    SyncMergeResult.IGNORED
                }
            }
        }
    }

    /**
     * Processes a batch of CRDT entities received in a DataPacket wire delta from a peer.
     */
    suspend fun processSyncDeltaPacket(packet: DataPacket): Int = withContext(Dispatchers.IO) {
        _syncStats.value = _syncStats.value.copy(isSyncing = true)
        var appliedCount = 0

        try {
            val jsonArray = JSONArray(packet.payload)
            for (i in 0 until jsonArray.length()) {
                val rawJsonStr = jsonArray.getJSONObject(i).toString()
                val entity = ContentEntity.fromJson(rawJsonStr) ?: continue
                val result = mergeIncomingContent(entity)
                if (result != SyncMergeResult.IGNORED && result != SyncMergeResult.INVALID_SIG) {
                    appliedCount++
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing CRDT delta packet payload: ${e.message}")
        } finally {
            _syncStats.value = _syncStats.value.copy(
                isSyncing = false,
                lastSyncTime = System.currentTimeMillis()
            )
        }
        appliedCount
    }

    /**
     * Generates a sync delta payload containing all content modified locally since lastSyncTimestamp.
     */
    suspend fun generateSyncDelta(lastSyncTimestamp: Long): List<ContentEntity> = withContext(Dispatchers.IO) {
        contentDao.getContentModifiedSince(lastSyncTimestamp)
    }

    /**
     * Converts a list of ContentEntities into a DataPacket ready to broadcast to mesh network.
     */
    fun createDeltaPacket(deltaEntities: List<ContentEntity>, targetNodeId: String = "BROADCAST"): DataPacket {
        val jsonArray = JSONArray()
        deltaEntities.forEach { jsonArray.put(org.json.JSONObject(it.toJson())) }

        return DataPacket(
            senderNodeId = identityManager.nodeId,
            targetNodeId = targetNodeId,
            payload = jsonArray.toString(),
            packetType = PacketType.CRDT_DELTA
        ).signPacket(identityManager)
    }

    private fun recordMergeSuccess(isConflictResolved: Boolean) {
        val current = _syncStats.value
        _syncStats.value = current.copy(
            totalMergedEvents = current.totalMergedEvents + 1,
            conflictsResolvedCount = if (isConflictResolved) current.conflictsResolvedCount + 1 else current.conflictsResolvedCount,
            lastSyncTime = System.currentTimeMillis()
        )
    }

    private fun signContentEntity(entity: ContentEntity): ContentEntity {
        val canonicalToSign = "${entity.id}|${entity.key}|${entity.value}|${entity.crdtType}|${entity.timestamp}|${entity.authorNodeId}|${entity.pIncrement}|${entity.nDecrement}|${entity.isDeleted}|${entity.version}"
        val sig = identityManager.signData(canonicalToSign.toByteArray(Charsets.UTF_8))
        return entity.copy(signature = sig)
    }

    private fun verifyContentSignature(entity: ContentEntity): Boolean {
        val canonicalToVerify = "${entity.id}|${entity.key}|${entity.value}|${entity.crdtType}|${entity.timestamp}|${entity.authorNodeId}|${entity.pIncrement}|${entity.nDecrement}|${entity.isDeleted}|${entity.version}"
        return NodeIdentityManager.verifyDataSignature(
            publicKeyHex = entity.authorNodeId,
            data = canonicalToVerify.toByteArray(Charsets.UTF_8),
            signatureBase64 = entity.signature
        )
    }
}
