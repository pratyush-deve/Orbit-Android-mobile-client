package com.pratyush.orbitmobile.core.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

sealed interface FirebaseAuthState {
    data object Uninitialized : FirebaseAuthState
    data object Authenticating : FirebaseAuthState
    data class Authenticated(val uid: String) : FirebaseAuthState
    data class Error(val message: String) : FirebaseAuthState
}

/**
 * Manages Firebase Anonymous Authentication and persistent session recovery on Android.
 * Never logs ID tokens or refresh tokens.
 */
class FirebaseIdentityManager(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    companion object {
        private const val TAG = "OrbitFirebaseAuth"
    }

    private val authMutex = Mutex()
    private val _authState = MutableStateFlow<FirebaseAuthState>(
        auth.currentUser?.let { FirebaseAuthState.Authenticated(it.uid) }
            ?: FirebaseAuthState.Uninitialized
    )
    val authState: StateFlow<FirebaseAuthState> = _authState.asStateFlow()

    val currentUid: String?
        get() = auth.currentUser?.uid

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user != null) {
            _authState.value = FirebaseAuthState.Authenticated(user.uid)
        } else if (_authState.value is FirebaseAuthState.Authenticated) {
            Log.w(TAG, "Firebase auth session cleared; will re-authenticate on next check.")
            _authState.value = FirebaseAuthState.Uninitialized
        }
    }

    init {
        auth.addAuthStateListener(authStateListener)
        auth.currentUser?.let {
            Log.i(TAG, "Recovered persisted Firebase anonymous session (uid=${it.uid})")
        }
    }

    /**
     * Ensures an active Firebase anonymous session exists, refreshing or signing in if needed.
     */
    suspend fun ensureSignedIn(): String? = authMutex.withLock {
        val existing: FirebaseUser? = auth.currentUser
        if (existing != null) {
            return@withLock try {
                // Validate/refresh cached token silently without logging the token
                existing.getIdToken(false).await()
                _authState.value = FirebaseAuthState.Authenticated(existing.uid)
                existing.uid
            } catch (e: Exception) {
                Log.w(TAG, "Cached Firebase session validation failed (${e.message}); attempting anonymous re-sign-in.")
                signInAnonymouslyInternal()
            }
        }
        return@withLock signInAnonymouslyInternal()
    }

    suspend fun getValidIdToken(): String? = authMutex.withLock {
        val user = auth.currentUser ?: run {
            signInAnonymouslyInternal()
            auth.currentUser
        } ?: return@withLock null

        return@withLock try {
            user.getIdToken(false).await()?.token
        } catch (e: Exception) {
            Log.w(TAG, "Failed to retrieve Firebase ID token: ${e.message}")
            null
        }
    }

    private suspend fun signInAnonymouslyInternal(): String? {
        _authState.value = FirebaseAuthState.Authenticating
        return try {
            val result = auth.signInAnonymously().await()
            val uid = result.user?.uid
            if (!uid.isNullOrBlank()) {
                Log.i(TAG, "Firebase anonymous authentication succeeded (uid=$uid)")
                _authState.value = FirebaseAuthState.Authenticated(uid)
                uid
            } else {
                val msg = "Firebase anonymous sign-in returned null user"
                Log.e(TAG, msg)
                _authState.value = FirebaseAuthState.Error(msg)
                null
            }
        } catch (e: Exception) {
            val msg = e.message ?: "Firebase anonymous sign-in failed"
            Log.e(TAG, "Firebase anonymous authentication failed: $msg")
            _authState.value = FirebaseAuthState.Error(msg)
            null
        }
    }

    fun dispose() {
        auth.removeAuthStateListener(authStateListener)
    }
}
