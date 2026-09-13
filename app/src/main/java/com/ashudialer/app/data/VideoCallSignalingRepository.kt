package com.ashudialer.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await


data class SignalingIceCandidate(
    val sdpMid: String,
    val sdpMLineIndex: Int,
    val candidate: String
)

data class SignalingSession(
    val callId: String,
    val callerUid: String,
    val calleeNumber: String,
    val callerCarrierId: String?,
    val offerSdp: String? = null,
    val answerSdp: String? = null,
    val status: String = STATUS_RINGING
) {
    companion object {
        const val STATUS_RINGING = "ringing"
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_DECLINED = "declined"
        const val STATUS_ENDED = "ended"
    }
}


class VideoCallSignalingRepository {

    private val db: FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (e: IllegalStateException) {
        android.util.Log.w("VideoCallSignaling", "Firebase not configured — video calling disabled.", e)
        null
    }

    private fun callDoc(callId: String) = db?.collection("video_calls")?.document(callId)
    private fun candidatesCollection(callId: String, ownerUid: String) =
        callDoc(callId)?.collection("candidates_$ownerUid")


    suspend fun publishPhoneDirectoryEntry(uid: String, phoneNumber: String): Boolean {
        val normalized = normalizePhoneForLookup(phoneNumber)
        if (normalized.isBlank()) return false
        val doc = db?.collection("phone_directory")?.document(normalized) ?: return false
        return try {
            doc.set(mapOf("uid" to uid, "updatedAtMillis" to System.currentTimeMillis())).await()
            true
        } catch (e: Exception) {
            android.util.Log.w("VideoCallSignaling", "publishPhoneDirectoryEntry failed", e)
            false
        }
    }


    suspend fun resolveUidForNumber(phoneNumber: String): String? {
        val normalized = normalizePhoneForLookup(phoneNumber)
        if (normalized.isBlank()) return null
        val doc = db?.collection("phone_directory")?.document(normalized) ?: return null
        return try {
            doc.get().await().getString("uid")
        } catch (e: Exception) {
            android.util.Log.w("VideoCallSignaling", "resolveUidForNumber failed", e)
            null
        }
    }


    private fun normalizePhoneForLookup(raw: String): String = raw.filter { it.isDigit() }.takeLast(10)


    suspend fun createCall(
        callId: String,
        callerUid: String,
        calleeNumber: String,
        callerCarrierId: String?,
        offerSdp: String
    ): Boolean {
        val doc = callDoc(callId) ?: return false
        return try {
            doc.set(
                mapOf(
                    "callerUid" to callerUid,
                    "calleeNumber" to calleeNumber,
                    "callerCarrierId" to callerCarrierId,
                    "offerSdp" to offerSdp,
                    "status" to SignalingSession.STATUS_RINGING,
                    "createdAtMillis" to System.currentTimeMillis()
                )
            ).await()
            true
        } catch (e: Exception) {
            android.util.Log.w("VideoCallSignaling", "createCall failed", e)
            false
        }
    }

    suspend fun submitAnswer(callId: String, answerSdp: String): Boolean {
        val doc = callDoc(callId) ?: return false
        return try {
            doc.update(mapOf("answerSdp" to answerSdp, "status" to SignalingSession.STATUS_ACCEPTED)).await()
            true
        } catch (e: Exception) {
            android.util.Log.w("VideoCallSignaling", "submitAnswer failed", e)
            false
        }
    }

    suspend fun updateStatus(callId: String, status: String): Boolean {
        val doc = callDoc(callId) ?: return false
        return try {
            doc.update("status", status).await()
            true
        } catch (e: Exception) {
            android.util.Log.w("VideoCallSignaling", "updateStatus failed", e)
            false
        }
    }

    suspend fun addIceCandidate(callId: String, ownerUid: String, candidate: SignalingIceCandidate): Boolean {
        val collection = candidatesCollection(callId, ownerUid) ?: return false
        return try {
            collection.add(
                mapOf(
                    "sdpMid" to candidate.sdpMid,
                    "sdpMLineIndex" to candidate.sdpMLineIndex,
                    "candidate" to candidate.candidate
                )
            ).await()
            true
        } catch (e: Exception) {
            android.util.Log.w("VideoCallSignaling", "addIceCandidate failed", e)
            false
        }
    }


    fun observeCall(callId: String): Flow<SignalingSession?> = callbackFlow {
        val doc = callDoc(callId)
        if (doc == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val registration: ListenerRegistration = doc.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                trySend(null)
                return@addSnapshotListener
            }
            trySend(
                SignalingSession(
                    callId = callId,
                    callerUid = snapshot.getString("callerUid") ?: "",
                    calleeNumber = snapshot.getString("calleeNumber") ?: "",
                    callerCarrierId = snapshot.getString("callerCarrierId"),
                    offerSdp = snapshot.getString("offerSdp"),
                    answerSdp = snapshot.getString("answerSdp"),
                    status = snapshot.getString("status") ?: SignalingSession.STATUS_RINGING
                )
            )
        }
        awaitClose { registration.remove() }
    }


    fun observeIncomingCalls(myNumber: String): Flow<SignalingSession> = callbackFlow {
        val normalized = normalizePhoneForLookup(myNumber)
        val collection = db?.collection("video_calls")
        if (collection == null || normalized.isBlank()) {
            close()
            return@callbackFlow
        }
        val registration = collection
            .whereEqualTo("calleeNumber", myNumber)
            .whereEqualTo("status", SignalingSession.STATUS_RINGING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                for (change in snapshot.documentChanges) {
                    if (change.type.name != "ADDED") continue
                    val d = change.document
                    trySend(
                        SignalingSession(
                            callId = d.id,
                            callerUid = d.getString("callerUid") ?: continue,
                            calleeNumber = d.getString("calleeNumber") ?: myNumber,
                            callerCarrierId = d.getString("callerCarrierId"),
                            offerSdp = d.getString("offerSdp"),
                            status = d.getString("status") ?: SignalingSession.STATUS_RINGING
                        )
                    )
                }
            }
        awaitClose { registration.remove() }
    }


    fun observeRemoteIceCandidates(callId: String, remoteOwnerUid: String): Flow<SignalingIceCandidate> = callbackFlow {
        val collection = candidatesCollection(callId, remoteOwnerUid)
        if (collection == null) {
            close()
            return@callbackFlow
        }
        val registration = collection.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            for (change in snapshot.documentChanges) {
                if (change.type.name != "ADDED") continue
                val d = change.document
                val sdpMid = d.getString("sdpMid") ?: continue
                val sdpMLineIndex = (d.getLong("sdpMLineIndex") ?: continue).toInt()
                val candidate = d.getString("candidate") ?: continue
                trySend(SignalingIceCandidate(sdpMid, sdpMLineIndex, candidate))
            }
        }
        awaitClose { registration.remove() }
    }


    suspend fun teardown(callId: String, ownerUid: String) {
        try {
            candidatesCollection(callId, ownerUid)?.get()?.await()?.documents?.forEach { it.reference.delete() }
            callDoc(callId)?.delete()?.await()
        } catch (_: Exception) {
        }
    }
}
