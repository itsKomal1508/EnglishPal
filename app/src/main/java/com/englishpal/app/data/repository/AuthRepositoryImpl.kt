package com.englishpal.app.data.repository

import com.englishpal.app.domain.model.UserProfile
import com.englishpal.app.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

import android.util.Log

/**
 * Concrete implementation of AuthRepository interfacing with Firebase Auth & Firestore.
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {

    override val currentUser: Flow<UserProfile?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val firebaseUser = firebaseAuth.currentUser
            Log.d("AuthFlow", "AuthStateListener fired. User logged in: ${firebaseUser != null} (uid=${firebaseUser?.uid})")
            if (firebaseUser != null) {
                val email = firebaseUser.email ?: ""
                var name = firebaseUser.displayName ?: ""
                if (name.isBlank() && email.isNotBlank()) {
                    name = email.substringBefore("@")
                        .replace(".", " ")
                        .split(" ")
                        .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
                }
                trySend(
                    UserProfile(
                        uid = firebaseUser.uid,
                        email = email,
                        displayName = name.ifBlank { "Google User" },
                        photoUrl = firebaseUser.photoUrl?.toString() ?: ""
                    )
                )
            } else {
                trySend(null)
            }
        }

        // Initial emit
        val initialUser = auth.currentUser
        if (initialUser != null) {
            val email = initialUser.email ?: ""
            var name = initialUser.displayName ?: ""
            if (name.isBlank() && email.isNotBlank()) {
                name = email.substringBefore("@")
                    .replace(".", " ")
                    .split(" ")
                    .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
            }
            trySend(
                UserProfile(
                    uid = initialUser.uid,
                    email = email,
                    displayName = name.ifBlank { "Google User" },
                    photoUrl = initialUser.photoUrl?.toString() ?: ""
                )
            )
        } else {
            trySend(null)
        }

        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override fun isUserLoggedIn(): Boolean = auth.currentUser != null

    override suspend fun signInWithEmail(email: String, pass: String): Result<UserProfile> {
        Log.d("AuthFlow", "Attempting signInWithEmail for: $email")
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, pass).await()
            val user = authResult.user ?: throw Exception("User is null after sign in")
            val name = user.displayName.takeIf { !it.isNullOrBlank() }
                ?: email.substringBefore("@").replace(".", " ")
                    .split(" ")
                    .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }

            val profile = UserProfile(
                uid = user.uid,
                email = user.email ?: "",
                displayName = name
            )
            saveUserProfileToFirestore(profile)
            Log.d("AuthFlow", "signInWithEmail successful. User UID: ${user.uid}")
            Result.success(profile)
        } catch (e: Exception) {
            Log.e("AuthFlow", "signInWithEmail failed for $email", e)
            Result.failure(e)
        }
    }

    override suspend fun signUpWithEmail(
        email: String,
        pass: String,
        name: String
    ): Result<UserProfile> {
        Log.d("AuthFlow", "Attempting signUpWithEmail for: $email, name: $name")
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, pass).await()
            val user = authResult.user ?: throw Exception("User creation failed")
            
            val finalName = name.ifBlank {
                email.substringBefore("@").replace(".", " ")
                    .split(" ")
                    .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
            }

            try {
                val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                    .setDisplayName(finalName)
                    .build()
                user.updateProfile(profileUpdates).await()
            } catch (e: Exception) {
                Log.w("AuthFlow", "Failed to set displayName on FirebaseUser during signUp", e)
            }

            val profile = UserProfile(
                uid = user.uid,
                email = email,
                displayName = finalName
            )
            saveUserProfileToFirestore(profile)
            Log.d("AuthFlow", "signUpWithEmail successful. Created user UID: ${user.uid}")
            Result.success(profile)
        } catch (e: Exception) {
            Log.e("AuthFlow", "signUpWithEmail failed for $email", e)
            Result.failure(e)
        }
    }

    override suspend fun signInWithGoogleCredential(idToken: String): Result<UserProfile> {
        Log.d("AuthFlow", "Attempting signInWithGoogleCredential (idToken len: ${idToken.length})")
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user ?: throw Exception("Google authentication failed")

            val email = user.email ?: ""
            var name = user.displayName ?: ""
            if (name.isBlank() && email.isNotBlank()) {
                name = email.substringBefore("@")
                    .replace(".", " ")
                    .split(" ")
                    .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
            }

            if (user.displayName.isNullOrBlank() && name.isNotBlank()) {
                try {
                    val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                        .setDisplayName(name)
                        .build()
                    user.updateProfile(profileUpdates).await()
                } catch (e: Exception) {
                    Log.w("AuthFlow", "Failed to update FirebaseUser displayName", e)
                }
            }

            val profile = UserProfile(
                uid = user.uid,
                email = email,
                displayName = name.ifBlank { "Google User" },
                photoUrl = user.photoUrl?.toString() ?: ""
            )
            saveUserProfileToFirestore(profile)
            Log.d("AuthFlow", "signInWithGoogleCredential successful. UID: ${user.uid}, Name: '$name'")
            Result.success(profile)
        } catch (e: Exception) {
            Log.e("AuthFlow", "signInWithGoogleCredential failed", e)
            Result.failure(e)
        }
    }

    override suspend fun signInWithGoogleProfile(
        email: String,
        displayName: String,
        photoUrl: String
    ): Result<UserProfile> {
        Log.d("AuthFlow", "Attempting signInWithGoogleProfile fallback for email: $email, name: $displayName")
        return try {
            var user = auth.currentUser
            if (user == null) {
                try {
                    val anonResult = auth.signInAnonymously().await()
                    user = anonResult.user
                } catch (anonEx: Exception) {
                    Log.w("AuthFlow", "Anonymous sign-in unavailable (${anonEx.message}). Generating fallback profile UID.")
                }
            }

            val uid = user?.uid ?: ("google_user_" + java.util.UUID.nameUUIDFromBytes((email.ifBlank { "user_${System.currentTimeMillis()}" }).toByteArray()).toString().replace("-", "").take(16))

            val resolvedName = displayName.ifBlank {
                if (email.isNotBlank()) {
                    email.substringBefore("@")
                        .replace(".", " ")
                        .split(" ")
                        .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
                } else {
                    "Google User"
                }
            }

            user?.let { fbUser ->
                if (fbUser.displayName.isNullOrBlank() && resolvedName.isNotBlank()) {
                    try {
                        val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                            .setDisplayName(resolvedName)
                            .build()
                        fbUser.updateProfile(profileUpdates).await()
                    } catch (e: Exception) {
                        Log.w("AuthFlow", "Failed to update FirebaseUser displayName in profile fallback", e)
                    }
                }
            }

            val profile = UserProfile(
                uid = uid,
                email = email,
                displayName = resolvedName,
                photoUrl = photoUrl
            )
            saveUserProfileToFirestore(profile)
            Log.d("AuthFlow", "signInWithGoogleProfile successful. UID: $uid, Name: '$resolvedName'")
            Result.success(profile)
        } catch (e: Exception) {
            Log.e("AuthFlow", "signInWithGoogleProfile failed", e)
            Result.failure(e)
        }
    }

    override suspend fun signOut() {
        Log.d("AuthFlow", "signOut called: signing out from Firebase Auth...")
        try {
            auth.signOut()
            Log.d("AuthFlow", "Firebase Auth signOut successful. currentUser is now: ${auth.currentUser}")
        } catch (e: Exception) {
            Log.e("AuthFlow", "Error during Firebase Auth signOut", e)
        }
    }

    private suspend fun saveUserProfileToFirestore(userProfile: UserProfile) {
        try {
            val userMap = hashMapOf(
                "uid" to userProfile.uid,
                "email" to userProfile.email,
                "displayName" to userProfile.displayName,
                "photoUrl" to userProfile.photoUrl,
                "createdAt" to System.currentTimeMillis()
            )
            firestore.collection("users").document(userProfile.uid)
                .set(userMap)
                .await()
            Log.d("AuthFlow", "Saved user profile to Firestore for UID: ${userProfile.uid}")
        } catch (e: Exception) {
            Log.e("AuthFlow", "Failed to save user profile to Firestore", e)
        }
    }
}
