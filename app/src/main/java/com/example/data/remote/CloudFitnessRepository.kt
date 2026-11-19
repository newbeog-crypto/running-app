package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.model.WorkoutType
import com.example.util.DateUtils
import com.example.util.MetricUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CloudFitnessRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        if (expectedUserId != null) {
            check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user operation." }
        }
        return uid
    }

    fun observePrivateUser(userId: String): Flow<UserPrivateProfile?> = flow {
        requireUserId(userId)
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        UserPrivateProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    handleFlowError(error, OperationType.GET, docRef.path, userId)
                }
        )
    }

    fun observePublicProfile(userId: String): Flow<PublicRunnerProfile?> = flow {
        requireUserId()
        val docRef = db.collection("public_profiles").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        PublicRunnerProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    handleFlowError(error, OperationType.GET, docRef.path, userId)
                }
        )
    }

    fun observeCloudWorkouts(userId: String): Flow<List<CloudWorkoutSession>> = flow {
        requireUserId(userId)
        val path = "users/$userId/workouts"
        emitAll(
            db.collection("users").document(userId).collection("workouts")
                .orderBy("startedAtMillis", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudWorkoutSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, userId)
                }
        )
    }

    fun observeCloudWorkoutById(userId: String, workoutId: String): Flow<CloudWorkoutSession?> = flow {
        requireUserId(userId)
        val docRef = db.collection("users").document(userId)
            .collection("workouts").document(workoutId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        CloudWorkoutSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    handleFlowError(error, OperationType.GET, docRef.path, userId)
                }
        )
    }

    fun observeCloudDailySteps(userId: String): Flow<List<CloudDailySteps>> = flow {
        requireUserId(userId)
        val path = "users/$userId/dailySteps"
        emitAll(
            db.collection("users").document(userId).collection("dailySteps")
                .orderBy("dateKey", Query.Direction.DESCENDING)
                .limit(366)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudDailySteps::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, userId)
                }
        )
    }

    fun observeLeaderboard(): Flow<List<PublicRunnerProfile>> = flow {
        val uid = requireUserId()
        val path = "public_profiles"
        emitAll(
            db.collection("public_profiles")
                .whereEqualTo("leaderboardVisible", true)
                .limit(50)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PublicRunnerProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, uid)
                }
        )
    }

    fun observeFriendRequests(userId: String): Flow<List<FriendRequestDoc>> = flow {
        requireUserId(userId)
        val path = "friend_requests"
        emitAll(
            db.collection("friend_requests")
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FriendRequestDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, userId)
                }
        )
    }

    suspend fun ensureUserAccountInitialized(userId: String, email: String?): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            requireUserId(userId)
            val docRef = db.collection("users").document(userId)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to userId,
                    "email" to email?.take(200),
                    "dailyStepGoal" to 8000L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun checkUsernameAvailable(rawUsername: String): Result<Boolean> {
        val trimmed = rawUsername.trim()
        if (!MetricUtils.isValidUsernameFormat(trimmed)) {
            return Result.success(false)
        }
        val key = MetricUtils.normalizeUsernameKey(trimmed)
        val docPath = "usernames/$key"
        return try {
            val uid = requireUserId()
            val doc = db.collection("usernames").document(key).get().await()
            val available = !doc.exists() || doc.getString("userId") == uid
            Result.success(available)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            Result.failure(e)
        }
    }

    suspend fun claimOrUpdateUsername(
        userId: String,
        rawUsername: String,
        existingPublicProfile: PublicRunnerProfile?,
        workouts: List<CloudWorkoutSession>,
        dailySteps: List<CloudDailySteps>
    ): Result<Unit> {
        val displayUsername = rawUsername.trim()
        if (!MetricUtils.isValidUsernameFormat(displayUsername)) {
            return Result.failure(
                IllegalArgumentException("Username must be 2–24 characters without spaces or '/'.")
            )
        }
        val newKey = MetricUtils.normalizeUsernameKey(displayUsername)
        val oldKey = existingPublicProfile?.usernameLower?.takeIf { it.isNotBlank() }

        val docPath = "usernames/$newKey"
        return try {
            requireUserId(userId)

            // Check uniqueness before batch commit
            val existingClaim = db.collection("usernames").document(newKey).get().await()
            if (existingClaim.exists() && existingClaim.getString("userId") != userId) {
                return Result.failure(
                    IllegalStateException("That nickname is already taken. Please choose another.")
                )
            }

            // Ensure private user doc exists first
            val userRef = db.collection("users").document(userId)
            val userSnap = userRef.get().await()

            val stats = computeRunnerAggregates(workouts, dailySteps)
            val publicRef = db.collection("public_profiles").document(userId)
            val newUsernameRef = db.collection("usernames").document(newKey)

            db.runBatch { batch ->
                if (oldKey != null && oldKey != newKey) {
                    batch.delete(db.collection("usernames").document(oldKey))
                }
                if (oldKey != newKey || !existingClaim.exists()) {
                    batch.set(
                        newUsernameRef,
                        mapOf(
                            "userId" to userId,
                            "username" to displayUsername,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                if (userSnap.exists()) {
                    batch.update(
                        userRef,
                        mapOf(
                            "username" to displayUsername,
                            "usernameLower" to newKey,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    batch.set(
                        userRef,
                        mapOf(
                            "userId" to userId,
                            "email" to auth.currentUser?.email?.take(200),
                            "username" to displayUsername,
                            "usernameLower" to newKey,
                            "dailyStepGoal" to 8000L,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        ).filterValues { it != null }
                    )
                }

                if (existingPublicProfile == null) {
                    batch.set(
                        publicRef,
                        buildPublicProfileCreateMap(
                            userId = userId,
                            username = displayUsername,
                            usernameLower = newKey,
                            stats = stats
                        )
                    )
                } else {
                    batch.update(
                        publicRef,
                        buildPublicProfileUpdateMap(
                            username = displayUsername,
                            usernameLower = newKey,
                            stats = stats
                        )
                    )
                }
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveCloudWorkout(
        userId: String,
        workoutId: String,
        type: WorkoutType,
        startedAtMillis: Long,
        endedAtMillis: Long,
        movingTimeMillis: Long,
        distanceMeters: Double,
        averagePaceSecPerKm: Int,
        caloriesBurned: Int
    ): Result<String> {
        val docPath = "users/$userId/workouts/$workoutId"
        return try {
            requireUserId(userId)
            val safeId = workoutId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            val safeEnded = maxOf(endedAtMillis, startedAtMillis)
            val docRef = db.collection("users").document(userId).collection("workouts").document(safeId)
            val payload = mapOf(
                "userId" to userId,
                "type" to type.name,
                "startedAtMillis" to startedAtMillis,
                "endedAtMillis" to safeEnded,
                "movingTimeMillis" to movingTimeMillis.coerceAtLeast(0L),
                "distanceMeters" to distanceMeters.coerceIn(0.0, 1_000_000.0),
                "averagePaceSecPerKm" to averagePaceSecPerKm.coerceIn(0, 7200).toLong(),
                "caloriesBurned" to caloriesBurned.coerceIn(0, 50000).toLong(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            refreshPublicProfileStats(userId)
            Result.success(safeId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCloudWorkout(userId: String, workoutId: String): Result<Unit> {
        val docPath = "users/$userId/workouts/$workoutId"
        return try {
            requireUserId(userId)
            require(workoutId.isNotBlank()) { "Workout ID must not be blank" }
            db.collection("users").document(userId)
                .collection("workouts").document(workoutId)
                .delete().await()
            refreshPublicProfileStats(userId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun syncDailySteps(
        userId: String,
        dateKey: String,
        steps: Int,
        goalSteps: Int
    ): Result<Unit> {
        val docPath = "users/$userId/dailySteps/$dateKey"
        return try {
            requireUserId(userId)
            val docRef = db.collection("users").document(userId)
                .collection("dailySteps").document(dateKey)
            val existing = docRef.get().await()
            val clampedSteps = steps.coerceIn(0, 500_000).toLong()
            val clampedGoal = goalSteps.coerceIn(1000, 50_000).toLong()

            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "steps" to clampedSteps,
                        "goalSteps" to clampedGoal,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to userId,
                        "dateKey" to dateKey,
                        "steps" to clampedSteps,
                        "goalSteps" to clampedGoal,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            refreshPublicProfileStats(userId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateCloudDailyGoal(userId: String, goalSteps: Int): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            requireUserId(userId)
            val clampedGoal = goalSteps.coerceIn(1000, 50_000).toLong()
            val docRef = db.collection("users").document(userId)
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "dailyStepGoal" to clampedGoal,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to userId,
                        "email" to auth.currentUser?.email?.take(200),
                        "dailyStepGoal" to clampedGoal,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ).filterValues { it != null }
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun refreshPublicProfileStats(userId: String): Result<Unit> {
        val publicPath = "public_profiles/$userId"
        return try {
            requireUserId(userId)
            val publicRef = db.collection("public_profiles").document(userId)
            val publicSnap = publicRef.get().await()
            val currentPublic = publicSnap.toObject(
                PublicRunnerProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.success(Unit)

            val workouts = db.collection("users").document(userId).collection("workouts")
                .get().await()
                .toObjects(CloudWorkoutSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val stepsList = db.collection("users").document(userId).collection("dailySteps")
                .get().await()
                .toObjects(CloudDailySteps::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

            val stats = computeRunnerAggregates(workouts, stepsList)
            publicRef.update(
                buildPublicProfileUpdateMap(
                    username = currentPublic.username,
                    usernameLower = currentPublic.usernameLower,
                    stats = stats
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, publicPath)
            Result.failure(e)
        }
    }

    suspend fun findRunnerByExactUsername(rawUsername: String): Result<PublicRunnerProfile?> {
        val trimmed = rawUsername.trim()
        if (!MetricUtils.isValidUsernameFormat(trimmed)) {
            return Result.success(null)
        }
        val key = MetricUtils.normalizeUsernameKey(trimmed)
        val path = "usernames/$key"
        return try {
            requireUserId()
            val claim = db.collection("usernames").document(key).get().await()
            val targetUid = claim.getString("userId") ?: return Result.success(null)
            val profile = db.collection("public_profiles").document(targetUid).get().await()
                .toObject(
                    PublicRunnerProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun sendFriendRequest(
        myProfile: PublicRunnerProfile,
        targetProfile: PublicRunnerProfile
    ): Result<Unit> {
        if (myProfile.userId == targetProfile.userId) {
            return Result.failure(IllegalArgumentException("You cannot add yourself as a friend."))
        }
        val reqId = deterministicFriendDocId(myProfile.userId, targetProfile.userId)
        val docPath = "friend_requests/$reqId"
        return try {
            requireUserId(myProfile.userId)
            val docRef = db.collection("friend_requests").document(reqId)
            val existing = docRef.get().await()
            if (existing.exists()) {
                val status = existing.getString("status")
                return if (status == "ACCEPTED") {
                    Result.failure(IllegalStateException("You are already friends with ${targetProfile.username}."))
                } else {
                    Result.failure(IllegalStateException("A friend request with ${targetProfile.username} is already pending."))
                }
            }
            docRef.set(
                mapOf(
                    "fromUid" to myProfile.userId,
                    "fromUsername" to myProfile.username,
                    "toUid" to targetProfile.userId,
                    "toUsername" to targetProfile.username,
                    "status" to "PENDING",
                    "memberUids" to listOf(myProfile.userId, targetProfile.userId),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun acceptFriendRequest(requestId: String): Result<Unit> {
        val docPath = "friend_requests/$requestId"
        return try {
            requireUserId()
            db.collection("friend_requests").document(requestId)
                .update(
                    mapOf(
                        "status" to "ACCEPTED",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteFriendRequestOrFriendship(requestId: String): Result<Unit> {
        val docPath = "friend_requests/$requestId"
        return try {
            requireUserId()
            db.collection("friend_requests").document(requestId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun fetchProfilesByIds(uids: List<String>): Result<List<PublicRunnerProfile>> {
        if (uids.isEmpty()) return Result.success(emptyList())
        return try {
            requireUserId()
            val profiles = uids.distinct().mapNotNull { uid ->
                db.collection("public_profiles").document(uid).get().await()
                    .toObject(
                        PublicRunnerProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
            }
            Result.success(profiles)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "public_profiles")
            Result.failure(e)
        }
    }

    private fun deterministicFriendDocId(uidA: String, uidB: String): String {
        val pair = listOf(uidA, uidB).sorted()
        return "${pair[0]}_${pair[1]}"
    }

    private fun handleFlowError(
        error: Throwable,
        op: OperationType,
        path: String,
        expectedUid: String
    ): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        throw error
    }

    private data class RunnerAggregates(
        val totalDistanceMeters: Double,
        val weeklyDistanceMeters: Double,
        val monthlyDistanceMeters: Double,
        val yearlyDistanceMeters: Double,
        val totalWorkouts: Long,
        val weeklyWorkouts: Long,
        val monthlyWorkouts: Long,
        val yearlyWorkouts: Long,
        val totalMovingTimeMillis: Long,
        val averagePaceSecPerKm: Long,
        val bestPaceSecPerKm: Long,
        val totalSteps: Long,
        val weekKey: String,
        val monthKey: String,
        val yearKey: String
    )

    private fun computeRunnerAggregates(
        workouts: List<CloudWorkoutSession>,
        dailySteps: List<CloudDailySteps>
    ): RunnerAggregates {
        val nowWeek = DateUtils.weekKeyFor()
        val nowMonth = DateUtils.monthKeyFor()
        val nowYear = DateUtils.yearKeyFor()

        val weeklyList = workouts.filter { DateUtils.weekKeyFor(it.startedAtMillis) == nowWeek }
        val monthlyList = workouts.filter { DateUtils.monthKeyFor(it.startedAtMillis) == nowMonth }
        val yearlyList = workouts.filter { DateUtils.yearKeyFor(it.startedAtMillis) == nowYear }

        val totalDist = workouts.sumOf { it.distanceMeters }
        val totalTime = workouts.sumOf { it.movingTimeMillis }
        val avgPace = MetricUtils.calculateAveragePaceSecPerKm(totalDist, totalTime).toLong()
        val bestPace = workouts
            .map { it.averagePaceSecPerKm }
            .filter { it > 0L }
            .minOrNull() ?: 0L

        return RunnerAggregates(
            totalDistanceMeters = totalDist,
            weeklyDistanceMeters = weeklyList.sumOf { it.distanceMeters },
            monthlyDistanceMeters = monthlyList.sumOf { it.distanceMeters },
            yearlyDistanceMeters = yearlyList.sumOf { it.distanceMeters },
            totalWorkouts = workouts.size.toLong(),
            weeklyWorkouts = weeklyList.size.toLong(),
            monthlyWorkouts = monthlyList.size.toLong(),
            yearlyWorkouts = yearlyList.size.toLong(),
            totalMovingTimeMillis = totalTime,
            averagePaceSecPerKm = avgPace,
            bestPaceSecPerKm = bestPace,
            totalSteps = dailySteps.sumOf { it.steps },
            weekKey = nowWeek,
            monthKey = nowMonth,
            yearKey = nowYear
        )
    }

    private fun buildPublicProfileCreateMap(
        userId: String,
        username: String,
        usernameLower: String,
        stats: RunnerAggregates
    ): Map<String, Any> = mapOf(
        "userId" to userId,
        "username" to username,
        "usernameLower" to usernameLower,
        "leaderboardVisible" to true,
        "totalDistanceMeters" to stats.totalDistanceMeters,
        "weeklyDistanceMeters" to stats.weeklyDistanceMeters,
        "monthlyDistanceMeters" to stats.monthlyDistanceMeters,
        "yearlyDistanceMeters" to stats.yearlyDistanceMeters,
        "totalWorkouts" to stats.totalWorkouts,
        "weeklyWorkouts" to stats.weeklyWorkouts,
        "monthlyWorkouts" to stats.monthlyWorkouts,
        "yearlyWorkouts" to stats.yearlyWorkouts,
        "totalMovingTimeMillis" to stats.totalMovingTimeMillis,
        "averagePaceSecPerKm" to stats.averagePaceSecPerKm,
        "bestPaceSecPerKm" to stats.bestPaceSecPerKm,
        "totalSteps" to stats.totalSteps,
        "weekKey" to stats.weekKey,
        "monthKey" to stats.monthKey,
        "yearKey" to stats.yearKey,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun buildPublicProfileUpdateMap(
        username: String,
        usernameLower: String,
        stats: RunnerAggregates
    ): Map<String, Any> = mapOf(
        "username" to username,
        "usernameLower" to usernameLower,
        "leaderboardVisible" to true,
        "totalDistanceMeters" to stats.totalDistanceMeters,
        "weeklyDistanceMeters" to stats.weeklyDistanceMeters,
        "monthlyDistanceMeters" to stats.monthlyDistanceMeters,
        "yearlyDistanceMeters" to stats.yearlyDistanceMeters,
        "totalWorkouts" to stats.totalWorkouts,
        "weeklyWorkouts" to stats.weeklyWorkouts,
        "monthlyWorkouts" to stats.monthlyWorkouts,
        "yearlyWorkouts" to stats.yearlyWorkouts,
        "totalMovingTimeMillis" to stats.totalMovingTimeMillis,
        "averagePaceSecPerKm" to stats.averagePaceSecPerKm,
        "bestPaceSecPerKm" to stats.bestPaceSecPerKm,
        "totalSteps" to stats.totalSteps,
        "weekKey" to stats.weekKey,
        "monthKey" to stats.monthKey,
        "yearKey" to stats.yearKey,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
