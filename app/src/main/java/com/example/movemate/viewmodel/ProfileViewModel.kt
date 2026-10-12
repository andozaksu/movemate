package com.example.movemate.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.movemate.data.ActivityRepository
import com.example.movemate.data.local.entity.FoodRecord
import com.example.movemate.data.local.entity.SleepRecord
import com.example.movemate.data.local.entity.UserProfile
import com.example.movemate.data.local.entity.WeightRecord
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

class ProfileViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ActivityRepository(app)

    val userProfile: LiveData<UserProfile?> = repo.userProfile

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> get() = _message

    /** Call after the message has been shown so it isn't re-delivered on re-subscribe. */
    fun clearMessage() { _message.value = null }

    private val _isLoggedIn = MutableLiveData(false)
    val isLoggedIn: LiveData<Boolean> get() = _isLoggedIn

    val currentUserEmail: String?
        get() = tryGetAuth()?.currentUser?.email

    private fun tryGetAuth(): FirebaseAuth? = try {
        if (FirebaseApp.getApps(getApplication()).isEmpty()) null
        else FirebaseAuth.getInstance()
    } catch (e: Exception) {
        Log.w("ProfileViewModel", "Firebase not initialised: ${e.message}")
        null
    }

    private fun tryGetFirestore(): FirebaseFirestore? = try {
        if (FirebaseApp.getApps(getApplication()).isEmpty()) null
        else FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        Log.w("ProfileViewModel", "Firestore not available: ${e.message}")
        null
    }

    init {
        _isLoggedIn.value = tryGetAuth()?.currentUser != null
    }

    fun saveProfile(profile: UserProfile) {
        viewModelScope.launch {
            repo.saveUserProfile(profile)
            _message.value = "Profile saved"
        }
    }

    // ── Firebase Authentication ────────────────────────────────────────────────

    fun signUp(email: String, password: String) {
        if (email.isBlank() || password.length < 6) {
            _message.value = "Enter a valid email and a password of at least 6 characters"
            return
        }
        val auth = tryGetAuth() ?: run {
            _message.value = "Firebase is not configured. Please add google-services.json."
            return
        }
        _message.value = "Creating account…"
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener {
                _isLoggedIn.value = true
                // New account — wipe any leftover local data so the slate is clean
                viewModelScope.launch {
                    repo.clearAllLocalData()
                    clearStepBaseline()
                }
                _message.value = "Account created — you're starting fresh!"
            }
            .addOnFailureListener { e ->
                Log.e("ProfileViewModel", "signUp failed", e)
                _message.value = friendlyAuthError(e)
            }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _message.value = "Please enter email and password"
            return
        }
        val auth = tryGetAuth() ?: run {
            _message.value = "Firebase is not configured. Please add google-services.json."
            return
        }
        _message.value = "Signing in…"
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                _isLoggedIn.value = true
                _message.value = "Signed in — loading your data…"
                // Automatically load this account's data from Firestore
                val uid = result.user?.uid ?: return@addOnSuccessListener
                autoRestoreAfterSignIn(uid)
            }
            .addOnFailureListener { e ->
                Log.e("ProfileViewModel", "signIn failed", e)
                _message.value = friendlyAuthError(e)
            }
    }

    fun signOut() {
        tryGetAuth()?.signOut()
        _isLoggedIn.value = false
        // Wipe all local data and reset the step baseline so the next user starts clean
        viewModelScope.launch {
            repo.clearAllLocalData()
            clearStepBaseline()
        }
        _message.value = "Signed out — local data cleared"
    }

    // ── Auto-restore on sign-in ────────────────────────────────────────────────

    /**
     * Called immediately after a successful sign-in.
     * Clears local data, then loads the signed-in user's Firestore document.
     * If no document exists (brand-new account signed in from another device),
     * we simply leave the app in its clean state — no error shown.
     */
    private fun autoRestoreAfterSignIn(userId: String) {
        val db = tryGetFirestore() ?: run {
            _message.value = "Signed in, but Firestore is unavailable — data not loaded"
            return
        }
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    // Brand-new account or no backup yet — start with a clean local state
                    viewModelScope.launch {
                        repo.clearAllLocalData()
                        clearStepBaseline()
                    }
                    _message.value = "Welcome! No previous data found — starting fresh."
                    return@addOnSuccessListener
                }
                viewModelScope.launch {
                    try {
                        repo.clearAllLocalData()
                        clearStepBaseline()
                        restoreDocumentToLocal(doc)
                        _message.value = "Welcome back! Your data has been loaded."
                    } catch (e: Exception) {
                        Log.e("ProfileViewModel", "autoRestore failed", e)
                        _message.value = "Signed in, but data load failed: ${e.message}"
                    }
                }
            }
            .addOnFailureListener { e ->
                // Don't block the user — they're signed in; just surface a soft warning
                _message.value = "Signed in, but couldn't load data: ${friendlyFirestoreError(e)}"
            }
    }

    // ── Firebase Firestore — Manual Backup / Restore ───────────────────────────

    /**
     * Backs up the full profile AND all activity history to Firestore.
     * Records are serialised as lists of maps inside a single user document.
     */
    fun backupToCloud() {
        val auth = tryGetAuth() ?: run {
            _message.value = "Firebase is not configured. Please add google-services.json."
            return
        }
        val user = auth.currentUser ?: run {
            _message.value = "Please sign in before backing up"
            return
        }
        val db = tryGetFirestore() ?: run {
            _message.value = "Firestore is not available. Check Firebase console."
            return
        }

        _message.value = "Backing up…"
        viewModelScope.launch {
            try {
                val profile = repo.getUserProfileOnce()
                val steps   = repo.getAllStepRecordsOnce()
                val sleeps  = repo.getAllSleepRecordsOnce()
                val foods   = repo.getAllFoodRecordsOnce()
                val weights = repo.getAllWeightRecordsOnce()

                val data = mapOf(
                    "name"             to profile.name,
                    "age"              to profile.age,
                    "heightCm"         to profile.heightCm,
                    "weightKg"         to profile.weightKg,
                    "gender"           to profile.gender,
                    "stepGoal"         to profile.stepGoal,
                    "calorieGoal"      to profile.calorieGoal,
                    "sleepGoalMinutes" to profile.sleepGoalMinutes,
                    "backupTimestamp"  to System.currentTimeMillis(),
                    "stepRecords"   to steps.map  { r -> mapOf("date" to r.date.toString(), "steps" to r.steps) },
                    "sleepRecords"  to sleeps.map { r -> mapOf(
                        "startTime"       to r.startTime.toString(),
                        "endTime"         to r.endTime.toString(),
                        "durationMinutes" to r.durationMinutes
                    )},
                    "foodRecords"   to foods.map  { r -> mapOf(
                        "date"      to r.date.toString(),
                        "foodName"  to r.foodName,
                        "calories"  to r.calories,
                        "protein"   to r.protein,
                        "carbs"     to r.carbs,
                        "fat"       to r.fat,
                        "timestamp" to r.timestamp.toString()
                    )},
                    "weightRecords" to weights.map { r -> mapOf(
                        "date"     to r.date.toString(),
                        "weightKg" to r.weightKg,
                        "bmi"      to r.bmi
                    )}
                )

                db.collection("users").document(user.uid)
                    .set(data)
                    .addOnSuccessListener {
                        _message.value = "Backup complete — " +
                            "${steps.size} step days, ${sleeps.size} sleep sessions, " +
                            "${foods.size} food entries, ${weights.size} weight records"
                    }
                    .addOnFailureListener { e -> _message.value = friendlyFirestoreError(e) }

            } catch (e: Exception) {
                _message.value = "Backup failed: ${e.message}"
            }
        }
    }

    /**
     * Manual restore — same as sign-in auto-restore but invoked by the user button.
     */
    fun restoreFromCloud() {
        val auth = tryGetAuth() ?: run {
            _message.value = "Firebase is not configured. Please add google-services.json."
            return
        }
        val user = auth.currentUser ?: run {
            _message.value = "Please sign in before restoring"
            return
        }
        val db = tryGetFirestore() ?: run {
            _message.value = "Firestore is not available. Check Firebase console."
            return
        }

        _message.value = "Restoring…"
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    _message.value = "No backup found in cloud"
                    return@addOnSuccessListener
                }
                viewModelScope.launch {
                    try {
                        repo.clearAllLocalData()
                        clearStepBaseline()
                        restoreDocumentToLocal(doc)
                        _message.value = "Restore complete"
                    } catch (e: Exception) {
                        _message.value = "Restore failed: ${e.message}"
                    }
                }
            }
            .addOnFailureListener { e -> _message.value = friendlyFirestoreError(e) }
    }

    // ── Shared restore logic ───────────────────────────────────────────────────

    /**
     * Parses a Firestore [DocumentSnapshot] and writes all fields to the local Room database.
     * Must be called from a coroutine. Assumes local data has already been cleared.
     */
    private suspend fun restoreDocumentToLocal(doc: DocumentSnapshot) {
        // Profile
        val profile = UserProfile(
            id               = 1,
            name             = doc.getString("name") ?: "",
            age              = (doc.getLong("age") ?: 25).toInt(),
            heightCm         = (doc.getDouble("heightCm") ?: 170.0).toFloat(),
            weightKg         = (doc.getDouble("weightKg") ?: 70.0).toFloat(),
            gender           = doc.getString("gender") ?: "Not specified",
            stepGoal         = (doc.getLong("stepGoal") ?: 10000).toInt(),
            calorieGoal      = (doc.getLong("calorieGoal") ?: 2000).toInt(),
            sleepGoalMinutes = (doc.getLong("sleepGoalMinutes") ?: 480).toInt()
        )
        repo.saveUserProfile(profile)

        // Steps
        @Suppress("UNCHECKED_CAST")
        val stepMaps = doc.get("stepRecords") as? List<Map<String, Any>> ?: emptyList()
        stepMaps.forEach { m ->
            val date  = (m["date"]  as? String)?.let { LocalDate.parse(it) }   ?: return@forEach
            val steps = (m["steps"] as? Long)?.toInt()                          ?: return@forEach
            repo.updateStepsForToday(steps, date)
        }

        // Sleep
        @Suppress("UNCHECKED_CAST")
        val sleepMaps = doc.get("sleepRecords") as? List<Map<String, Any>> ?: emptyList()
        sleepMaps.forEach { m ->
            val start = (m["startTime"] as? String)?.let { LocalDateTime.parse(it) } ?: return@forEach
            val end   = (m["endTime"]   as? String)?.let { LocalDateTime.parse(it) } ?: return@forEach
            val dur   = (m["durationMinutes"] as? Long)?.toInt()                      ?: return@forEach
            repo.insertSleepRecord(SleepRecord(startTime = start, endTime = end, durationMinutes = dur))
        }

        // Food
        @Suppress("UNCHECKED_CAST")
        val foodMaps = doc.get("foodRecords") as? List<Map<String, Any>> ?: emptyList()
        foodMaps.forEach { m ->
            val date  = (m["date"]     as? String)?.let { LocalDate.parse(it) }      ?: return@forEach
            val name  = m["foodName"]  as? String                                     ?: return@forEach
            val cal   = (m["calories"] as? Long)?.toInt()                             ?: 0
            val prot  = (m["protein"]  as? Number)?.toFloat()                         ?: 0f
            val carbs = (m["carbs"]    as? Number)?.toFloat()                         ?: 0f
            val fat   = (m["fat"]      as? Number)?.toFloat()                         ?: 0f
            val ts    = (m["timestamp"] as? String)?.let { LocalDateTime.parse(it) }  ?: LocalDateTime.now()
            repo.insertFoodRecord(FoodRecord(date = date, foodName = name, calories = cal, protein = prot, carbs = carbs, fat = fat, timestamp = ts))
        }

        // Weight
        @Suppress("UNCHECKED_CAST")
        val weightMaps = doc.get("weightRecords") as? List<Map<String, Any>> ?: emptyList()
        weightMaps.forEach { m ->
            val date   = (m["date"]     as? String)?.let { LocalDate.parse(it) } ?: return@forEach
            val weight = (m["weightKg"] as? Number)?.toFloat()                   ?: return@forEach
            val bmi    = (m["bmi"]      as? Number)?.toFloat()                   ?: 0f
            repo.insertWeightRecord(WeightRecord(date = date, weightKg = weight, bmi = bmi))
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Clears the persisted step-counter baseline so the next user's day starts at zero.
     */
    private fun clearStepBaseline() {
        getApplication<Application>()
            .getSharedPreferences("steps_prefs", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    // ── Human-readable error messages ─────────────────────────────────────────

    private fun friendlyAuthError(e: Exception): String {
        if (e is FirebaseAuthException) {
            Log.e("ProfileViewModel", "FirebaseAuthException code: ${e.errorCode}")
            return when (e.errorCode) {
                "ERROR_EMAIL_ALREADY_IN_USE",
                "email-already-in-use"          -> "This email is already registered"
                "ERROR_WRONG_PASSWORD",
                "wrong-password",
                "ERROR_INVALID_CREDENTIAL",
                "invalid-credential"            -> "Incorrect email or password"
                "ERROR_USER_NOT_FOUND",
                "user-not-found"                -> "No account found for this email"
                "ERROR_INVALID_EMAIL",
                "invalid-email"                 -> "Invalid email address"
                "ERROR_WEAK_PASSWORD",
                "weak-password"                 -> "Password is too weak (min 6 characters)"
                "ERROR_NETWORK_REQUEST_FAILED",
                "network-request-failed"        -> "Network error — check your connection"
                "ERROR_TOO_MANY_REQUESTS",
                "too-many-requests"             -> "Too many attempts. Please try again later"
                "ERROR_OPERATION_NOT_ALLOWED",
                "operation-not-allowed"         ->
                    "Email/Password sign-in is not enabled. " +
                    "Go to Firebase Console → Authentication → Sign-in method → enable Email/Password"
                "ERROR_INTERNAL_ERROR",
                "internal-error",
                "INTERNAL"                      ->
                    "Firebase internal error. Most likely cause: Email/Password is not enabled in " +
                    "Firebase Console → Authentication → Sign-in method. " +
                    "(Code: ${e.errorCode})"
                else -> "Auth error [${e.errorCode}]: ${e.message}"
            }
        }
        return "Sign-in failed: ${e::class.simpleName}: ${e.message}"
    }

    private fun friendlyFirestoreError(e: Exception): String {
        if (e is FirebaseFirestoreException) {
            return when (e.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "Permission denied — check Firestore security rules in Firebase console"
                FirebaseFirestoreException.Code.UNAVAILABLE ->
                    "Firestore unavailable — check your internet connection"
                FirebaseFirestoreException.Code.NOT_FOUND ->
                    "No backup found"
                FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                    "Not authenticated — please sign in again"
                else -> "Firestore error (${e.code}): ${e.message}"
            }
        }
        return "Cloud error: ${e.message}"
    }
}
