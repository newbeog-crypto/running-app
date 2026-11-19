package com.example.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.remote.CloudDailySteps
import com.example.data.remote.CloudFitnessRepository
import com.example.data.remote.CloudWorkoutSession
import com.example.data.remote.FriendRequestDoc
import com.example.data.remote.PublicRunnerProfile
import com.example.data.remote.UiState
import com.example.data.remote.UserPrivateProfile
import com.example.util.DateUtils
import com.example.util.MetricUtils
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RankingPeriod(val label: String) {
    ALL_TIME("All-Time"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly")
}

data class RankedRunner(
    val rank: Int,
    val profile: PublicRunnerProfile,
    val distanceMeters: Double,
    val workoutsCount: Long,
    val isCurrentUser: Boolean
)

data class FriendComparisonItem(
    val requestId: String,
    val profile: PublicRunnerProfile
)

data class SocialDashboardData(
    val privateUser: UserPrivateProfile? = null,
    val myPublicProfile: PublicRunnerProfile? = null,
    val selectedRankingPeriod: RankingPeriod = RankingPeriod.WEEKLY,
    val rankedRunners: List<RankedRunner> = emptyList(),
    val myRank: Int? = null,
    val incomingRequests: List<FriendRequestDoc> = emptyList(),
    val outgoingRequests: List<FriendRequestDoc> = emptyList(),
    val friends: List<FriendComparisonItem> = emptyList(),
    val workoutsCache: List<CloudWorkoutSession> = emptyList(),
    val dailyStepsCache: List<CloudDailySteps> = emptyList(),
    val isSavingUsername: Boolean = false,
    val usernameAvailability: Boolean? = null,
    val usernameMessage: String? = null,
    val friendSearchQuery: String = "",
    val isSearchingFriend: Boolean = false,
    val searchedRunner: PublicRunnerProfile? = null,
    val searchFeedback: String? = null,
    val actionFeedback: String? = null
)

private data class SocialLocalInteractionState(
    val rankingPeriod: RankingPeriod = RankingPeriod.WEEKLY,
    val isSavingUsername: Boolean = false,
    val usernameAvailability: Boolean? = null,
    val usernameMessage: String? = null,
    val friendSearchQuery: String = "",
    val isSearchingFriend: Boolean = false,
    val searchedRunner: PublicRunnerProfile? = null,
    val searchFeedback: String? = null,
    val actionFeedback: String? = null,
    val friendProfilesByUid: Map<String, PublicRunnerProfile> = emptyMap()
)

class SocialViewModel(
    private val cloudRepository: CloudFitnessRepository,
    private val currentUserId: String,
    private val currentUserEmail: String?
) : ViewModel() {

    private val _localState = MutableStateFlow(SocialLocalInteractionState())

    val uiState: StateFlow<UiState<SocialDashboardData>> = combine(
        cloudRepository.observePrivateUser(currentUserId),
        cloudRepository.observePublicProfile(currentUserId),
        cloudRepository.observeLeaderboard(),
        cloudRepository.observeFriendRequests(currentUserId),
        cloudRepository.observeCloudWorkouts(currentUserId),
        cloudRepository.observeCloudDailySteps(currentUserId),
        _localState
    ) { values ->
        val privateUser = values[0] as UserPrivateProfile?
        val myPublic = values[1] as PublicRunnerProfile?
        @Suppress("UNCHECKED_CAST")
        val leaderboard = values[2] as List<PublicRunnerProfile>
        @Suppress("UNCHECKED_CAST")
        val friendRequests = values[3] as List<FriendRequestDoc>
        @Suppress("UNCHECKED_CAST")
        val workouts = values[4] as List<CloudWorkoutSession>
        @Suppress("UNCHECKED_CAST")
        val steps = values[5] as List<CloudDailySteps>
        val local = values[6] as SocialLocalInteractionState

        val nowWeek = DateUtils.weekKeyFor()
        val nowMonth = DateUtils.monthKeyFor()
        val nowYear = DateUtils.yearKeyFor()

        val sorted = leaderboard.map { p ->
            val dist = when (local.rankingPeriod) {
                RankingPeriod.ALL_TIME -> p.totalDistanceMeters
                RankingPeriod.WEEKLY -> if (p.weekKey == nowWeek) p.weeklyDistanceMeters else 0.0
                RankingPeriod.MONTHLY -> if (p.monthKey == nowMonth) p.monthlyDistanceMeters else 0.0
                RankingPeriod.YEARLY -> if (p.yearKey == nowYear) p.yearlyDistanceMeters else 0.0
            }
            val count = when (local.rankingPeriod) {
                RankingPeriod.ALL_TIME -> p.totalWorkouts
                RankingPeriod.WEEKLY -> if (p.weekKey == nowWeek) p.weeklyWorkouts else 0L
                RankingPeriod.MONTHLY -> if (p.monthKey == nowMonth) p.monthlyWorkouts else 0L
                RankingPeriod.YEARLY -> if (p.yearKey == nowYear) p.yearlyWorkouts else 0L
            }
            Triple(p, dist, count)
        }.sortedWith(
            compareByDescending<Triple<PublicRunnerProfile, Double, Long>> { it.second }
                .thenByDescending { it.third }
                .thenBy { it.first.usernameLower }
        )

        val ranked = sorted.mapIndexed { idx, (profile, dist, count) ->
            RankedRunner(
                rank = idx + 1,
                profile = profile,
                distanceMeters = dist,
                workoutsCount = count,
                isCurrentUser = profile.userId == currentUserId
            )
        }
        val myRank = ranked.firstOrNull { it.isCurrentUser }?.rank

        val incoming = friendRequests.filter { it.status == "PENDING" && it.toUid == currentUserId }
        val outgoing = friendRequests.filter { it.status == "PENDING" && it.fromUid == currentUserId }
        val accepted = friendRequests.filter { it.status == "ACCEPTED" }

        val friendComparisons = accepted.mapNotNull { req ->
            val friendUid = if (req.fromUid == currentUserId) req.toUid else req.fromUid
            val profile = local.friendProfilesByUid[friendUid]
                ?: leaderboard.firstOrNull { it.userId == friendUid }
            profile?.let { FriendComparisonItem(requestId = req.id, profile = it) }
        }.sortedByDescending { it.profile.totalDistanceMeters }

        SocialDashboardData(
            privateUser = privateUser,
            myPublicProfile = myPublic,
            selectedRankingPeriod = local.rankingPeriod,
            rankedRunners = ranked,
            myRank = myRank,
            incomingRequests = incoming,
            outgoingRequests = outgoing,
            friends = friendComparisons,
            workoutsCache = workouts,
            dailyStepsCache = steps,
            isSavingUsername = local.isSavingUsername,
            usernameAvailability = local.usernameAvailability,
            usernameMessage = local.usernameMessage,
            friendSearchQuery = local.friendSearchQuery,
            isSearchingFriend = local.isSearchingFriend,
            searchedRunner = local.searchedRunner,
            searchFeedback = local.searchFeedback,
            actionFeedback = local.actionFeedback
        )
    }
        .map<SocialDashboardData, UiState<SocialDashboardData>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            emit(UiState.Error(rootError.message ?: "Failed to load social data"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = UiState.Loading
        )

    init {
        viewModelScope.launch {
            cloudRepository.ensureUserAccountInitialized(currentUserId, currentUserEmail)
            cloudRepository.refreshPublicProfileStats(currentUserId)
        }
        viewModelScope.launch {
            cloudRepository.observeFriendRequests(currentUserId)
                .catch { /* handled by primary stream */ }
                .collect { reqs ->
                    val acceptedFriendIds = reqs
                        .filter { it.status == "ACCEPTED" }
                        .map { if (it.fromUid == currentUserId) it.toUid else it.fromUid }
                    if (acceptedFriendIds.isNotEmpty()) {
                        cloudRepository.fetchProfilesByIds(acceptedFriendIds).onSuccess { profiles ->
                            _localState.update { state ->
                                state.copy(friendProfilesByUid = profiles.associateBy { it.userId })
                            }
                        }
                    } else {
                        _localState.update { it.copy(friendProfilesByUid = emptyMap()) }
                    }
                }
        }
    }

    fun selectRankingPeriod(period: RankingPeriod) {
        _localState.update { it.copy(rankingPeriod = period) }
    }

    fun checkUsernameAvailability(rawUsername: String) {
        val trimmed = rawUsername.trim()
        if (!MetricUtils.isValidUsernameFormat(trimmed)) {
            _localState.update {
                it.copy(
                    usernameAvailability = false,
                    usernameMessage = "Use 2–24 characters with no spaces or '/'."
                )
            }
            return
        }
        viewModelScope.launch {
            cloudRepository.checkUsernameAvailable(trimmed).fold(
                onSuccess = { available ->
                    _localState.update {
                        it.copy(
                            usernameAvailability = available,
                            usernameMessage = if (available) {
                                "Nickname '$trimmed' is available!"
                            } else {
                                "Nickname '$trimmed' is already taken."
                            }
                        )
                    }
                },
                onFailure = { err ->
                    _localState.update {
                        it.copy(
                            usernameAvailability = null,
                            usernameMessage = err.localizedMessage ?: "Could not verify nickname."
                        )
                    }
                }
            )
        }
    }

    fun clearUsernameFeedback() {
        _localState.update { it.copy(usernameAvailability = null, usernameMessage = null) }
    }

    fun submitUsername(
        rawUsername: String,
        existingPublicProfile: PublicRunnerProfile?,
        workouts: List<CloudWorkoutSession>,
        dailySteps: List<CloudDailySteps>,
        onSuccess: () -> Unit = {}
    ) {
        _localState.update { it.copy(isSavingUsername = true, usernameMessage = null) }
        viewModelScope.launch {
            val res = cloudRepository.claimOrUpdateUsername(
                userId = currentUserId,
                rawUsername = rawUsername,
                existingPublicProfile = existingPublicProfile,
                workouts = workouts,
                dailySteps = dailySteps
            )
            res.fold(
                onSuccess = {
                    _localState.update {
                        it.copy(
                            isSavingUsername = false,
                            usernameAvailability = true,
                            usernameMessage = null,
                            actionFeedback = "Runner nickname updated!"
                        )
                    }
                    onSuccess()
                },
                onFailure = { err ->
                    _localState.update {
                        it.copy(
                            isSavingUsername = false,
                            usernameAvailability = false,
                            usernameMessage = err.localizedMessage ?: "Failed to save nickname."
                        )
                    }
                }
            )
        }
    }

    fun updateFriendSearchQuery(query: String) {
        _localState.update {
            it.copy(friendSearchQuery = query, searchFeedback = null)
        }
    }

    fun searchFriendByUsername() {
        val query = _localState.value.friendSearchQuery.trim()
        if (!MetricUtils.isValidUsernameFormat(query)) {
            _localState.update {
                it.copy(
                    searchedRunner = null,
                    searchFeedback = "Enter an exact nickname (2–24 characters, no spaces)."
                )
            }
            return
        }
        _localState.update {
            it.copy(isSearchingFriend = true, searchedRunner = null, searchFeedback = null)
        }
        viewModelScope.launch {
            cloudRepository.findRunnerByExactUsername(query).fold(
                onSuccess = { runner ->
                    _localState.update {
                        it.copy(
                            isSearchingFriend = false,
                            searchedRunner = runner,
                            searchFeedback = if (runner == null) {
                                "No runner found with nickname '$query'."
                            } else if (runner.userId == currentUserId) {
                                "That's your own runner profile!"
                            } else null
                        )
                    }
                },
                onFailure = { err ->
                    _localState.update {
                        it.copy(
                            isSearchingFriend = false,
                            searchFeedback = err.localizedMessage ?: "Search failed."
                        )
                    }
                }
            )
        }
    }

    fun sendFriendRequest(myProfile: PublicRunnerProfile, targetProfile: PublicRunnerProfile) {
        viewModelScope.launch {
            cloudRepository.sendFriendRequest(myProfile, targetProfile).fold(
                onSuccess = {
                    _localState.update {
                        it.copy(
                            searchedRunner = null,
                            friendSearchQuery = "",
                            actionFeedback = "Friend request sent to ${targetProfile.username}"
                        )
                    }
                },
                onFailure = { err ->
                    _localState.update {
                        it.copy(actionFeedback = err.localizedMessage ?: "Could not send request.")
                    }
                }
            )
        }
    }

    fun acceptFriendRequest(requestId: String) {
        viewModelScope.launch {
            cloudRepository.acceptFriendRequest(requestId).fold(
                onSuccess = {
                    _localState.update { it.copy(actionFeedback = "Friend request accepted!") }
                },
                onFailure = { err ->
                    _localState.update {
                        it.copy(actionFeedback = err.localizedMessage ?: "Failed to accept request.")
                    }
                }
            )
        }
    }

    fun removeFriendOrRequest(requestId: String) {
        viewModelScope.launch {
            cloudRepository.deleteFriendRequestOrFriendship(requestId).fold(
                onSuccess = {
                    _localState.update { it.copy(actionFeedback = "Updated friends list.") }
                },
                onFailure = { err ->
                    _localState.update {
                        it.copy(actionFeedback = err.localizedMessage ?: "Failed to update.")
                    }
                }
            )
        }
    }

    fun consumeActionFeedback() {
        _localState.update { it.copy(actionFeedback = null) }
    }

    class Factory(
        private val cloudRepository: CloudFitnessRepository,
        private val currentUserId: String,
        private val currentUserEmail: String?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SocialViewModel(cloudRepository, currentUserId, currentUserEmail) as T
        }
    }
}
