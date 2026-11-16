package com.example.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.R
import com.example.data.local.StorageQuotaManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

object FirestoreClientProvider {

    private val configuredDatabaseIds = mutableSetOf<String>()

    /**
     * Resolves the custom provisioned Firestore instance using [R.string.firestore_database_id]
     * and configures persistent local disk caching (up to 100 GB) for offline data entry and automatic sync.
     */
    fun getInstance(context: Context): FirebaseFirestore {
        val appContext = context.applicationContext
        val databaseId = appContext.getString(R.string.firestore_database_id)
        val firestore = FirebaseFirestore.getInstance(databaseId)
        val configuredBytes = StorageQuotaManager.getConfiguredLimitBytes(appContext)
        configurePersistentCacheIfNeeded(firestore, databaseId, configuredBytes)
        return firestore
    }

    internal fun buildPersistentCacheSettings(
        cacheSizeBytes: Long = StorageQuotaManager.DEFAULT_STORAGE_LIMIT_BYTES
    ): com.google.firebase.firestore.FirebaseFirestoreSettings {
        val clampedBytes = cacheSizeBytes.coerceIn(
            StorageQuotaManager.MIN_STORAGE_LIMIT_BYTES,
            StorageQuotaManager.MAX_STORAGE_LIMIT_BYTES
        )
        return firestoreSettings {
            setLocalCacheSettings(
                persistentCacheSettings {
                    setSizeBytes(clampedBytes)
                }
            )
        }
    }

    fun configurePersistentCacheIfNeeded(
        firestore: FirebaseFirestore,
        databaseId: String = "custom",
        cacheSizeBytes: Long = StorageQuotaManager.DEFAULT_STORAGE_LIMIT_BYTES
    ) {
        synchronized(configuredDatabaseIds) {
            if (databaseId in configuredDatabaseIds) return
            try {
                firestore.firestoreSettings = buildPersistentCacheSettings(cacheSizeBytes)
            } catch (_: IllegalStateException) {
                // Firestore instance was already started; persistence settings already active
            }
            configuredDatabaseIds.add(databaseId)
        }
    }

    /**
     * Persists the new storage limit (up to 100 GB) and applies it immediately if Firestore
     * has not yet been started in this process. Returns true if applied to Firestore immediately,
     * or false if saved for local archives now and Firestore cache limit will take effect on next restart.
     */
    fun updateStorageLimit(context: Context, requestedBytes: Long): Boolean {
        val appContext = context.applicationContext
        val clamped = StorageQuotaManager.setConfiguredLimitBytes(appContext, requestedBytes)
        val databaseId = appContext.getString(R.string.firestore_database_id)
        return synchronized(configuredDatabaseIds) {
            if (databaseId in configuredDatabaseIds) {
                false
            } else {
                try {
                    val firestore = FirebaseFirestore.getInstance(databaseId)
                    firestore.firestoreSettings = buildPersistentCacheSettings(clamped)
                    configuredDatabaseIds.add(databaseId)
                    true
                } catch (_: IllegalStateException) {
                    configuredDatabaseIds.add(databaseId)
                    false
                }
            }
        }
    }

    fun isNetworkOnline(context: Context): Boolean {
        return try {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    fun observeConnectivity(context: Context): Flow<Boolean> = callbackFlow {
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            trySend(true)
            awaitClose { }
            return@callbackFlow
        }

        trySend(isNetworkOnline(appContext))

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(isNetworkOnline(appContext))
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                trySend(hasInternet)
            }
        }

        val registered = try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, callback)
            true
        } catch (_: Exception) {
            false
        }

        awaitClose {
            if (registered) {
                try {
                    cm.unregisterNetworkCallback(callback)
                } catch (_: Exception) {
                }
            }
        }
    }.distinctUntilChanged()
}
