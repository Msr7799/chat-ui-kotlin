package com.example.chat_ui.data.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.database
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Firebase Manager
 *
 * Centralized Firebase initialization and instance management
 * Thread-safe and null-safe implementation
 */
object FirebaseManager {
    private const val TAG = "FirebaseManager"

    @Volatile
    private var isInitialized = false
    
    private val initLock = Any()

    // Firebase instances - nullable for safety
    private var _auth: FirebaseAuth? = null
    private var _database: FirebaseDatabase? = null
    private var _storage: FirebaseStorage? = null
    private var _firestore: FirebaseFirestore? = null
    
    /**
     * Get Firebase Auth instance
     * @throws IllegalStateException if Firebase is not initialized
     */
    val auth: FirebaseAuth
        get() = _auth ?: throw IllegalStateException("Firebase not initialized. Call init() first.")
    
    /**
     * Get Firebase Database instance
     * @throws IllegalStateException if Firebase is not initialized
     */
    val database: FirebaseDatabase
        get() = _database ?: throw IllegalStateException("Firebase not initialized. Call init() first.")
    
    /**
     * Get Firebase Storage instance
     * @throws IllegalStateException if Firebase is not initialized
     */
    val storage: FirebaseStorage
        get() = _storage ?: throw IllegalStateException("Firebase not initialized. Call init() first.")
    
    /**
     * Get Firebase Firestore instance
     * @throws IllegalStateException if Firebase is not initialized
     */
    val firestore: FirebaseFirestore
        get() = _firestore ?: throw IllegalStateException("Firebase not initialized. Call init() first.")
    
    /**
     * Safe access to auth - returns null if not initialized
     */
    fun getAuthOrNull(): FirebaseAuth? = _auth
    
    /**
     * Safe access to database - returns null if not initialized
     */
    fun getDatabaseOrNull(): FirebaseDatabase? = _database
    
    /**
     * Safe access to storage - returns null if not initialized
     */
    fun getStorageOrNull(): FirebaseStorage? = _storage
    
    /**
     * Safe access to firestore - returns null if not initialized
     */
    fun getFirestoreOrNull(): FirebaseFirestore? = _firestore

    /** 
     * Initialize Firebase (thread-safe)
     * Safe to call multiple times - will only initialize once
     */
    fun init(context: Context) {
        if (isInitialized) return
        
        synchronized(initLock) {
            // Double-check after acquiring lock
            if (isInitialized) return
            
            try {
                // Initialize Firebase App
                FirebaseApp.initializeApp(context)

                // Get Firebase instances
                _auth = Firebase.auth
                _database = Firebase.database
                _storage = Firebase.storage
                _firestore = Firebase.firestore

                // Enable offline persistence for Realtime Database
                try {
                    _database?.setPersistenceEnabled(true)
                } catch (e: Exception) {
                    // Already set, ignore
                    Log.d(TAG, "Persistence already enabled: ${e.message}")
                }

                // Keep synced for conversations and users
                _database?.getReference("conversations")?.keepSynced(true)
                _database?.getReference("users")?.keepSynced(true)

                // Firestore offline persistence is enabled by default in newer versions
                // No need to explicitly set it

                // Initialize Firestore collections
                CoroutineScope(Dispatchers.IO).launch { 
                    try {
                        FirestoreCollections.initializeCollections()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to initialize Firestore collections: ${e.message}", e)
                    }
                }

                isInitialized = true
                Log.i(TAG, "✓ Firebase initialized successfully")

                // Log current auth state
                _auth?.currentUser?.let { user ->
                    Log.i(TAG, "User already signed in: ${user.email}")
                } ?: run {
                    Log.i(TAG, "No user signed in - attempting anonymous sign-in for initial access")
                    try {
                        _auth?.signInAnonymously()?.addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Log.i(TAG, "Anonymous sign-in successful: ${_auth?.currentUser?.uid}")
                            } else {
                                Log.w(TAG, "Anonymous sign-in failed: ${task.exception?.message}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Anonymous sign-in error: ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "✗ Failed to initialize Firebase: ${e.message}", e)
                // Don't set isInitialized to true on failure
            }
        }
    }
    
    /**
     * Cleanup Firebase resources
     * Call this when app is being destroyed
     */
    fun cleanup() {
        synchronized(initLock) {
            try {
                _auth?.signOut()
                _auth = null
                _database = null
                _storage = null
                _firestore = null
                isInitialized = false
                Log.i(TAG, "Firebase cleaned up")
            } catch (e: Exception) {
                Log.e(TAG, "Error during cleanup: ${e.message}", e)
            }
        }
    }

    /** Check if Firebase is initialized */
    fun isInitialized(): Boolean = isInitialized

    /** 
     * Get current user ID (safe)
     * @return user ID or null if not signed in or Firebase not initialized
     */
    fun getCurrentUserId(): String? = _auth?.currentUser?.uid

    /** 
     * Get current user email (safe)
     * @return user email or null if not signed in or Firebase not initialized
     */
    fun getCurrentUserEmail(): String? = _auth?.currentUser?.email

    /** 
     * Check if user is signed in (safe)
     * @return true if signed in, false otherwise
     */
    fun isUserSignedIn(): Boolean = _auth?.currentUser != null

    /** 
     * Sign out current user (safe)
     * Does nothing if Firebase is not initialized
     */
    fun signOut() {
        try {
            _auth?.signOut()
            Log.i(TAG, "User signed out")
        } catch (e: Exception) {
            Log.e(TAG, "Error during sign out: ${e.message}", e)
        }
    }
}
