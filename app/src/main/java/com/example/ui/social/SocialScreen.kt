package com.example.ui.social

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.GoogleAuthHelper
import com.example.data.remote.PublicRunnerProfile
import com.example.data.remote.UiState
import com.example.ui.auth.LanguageSelectorBar
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalStrings
import com.example.util.DateUtils
import com.example.util.MetricUtils

@Composable
fun SocialScreen(
    viewModel: SocialViewModel,
    userEmail: String?,
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onSignOutClick: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var subTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showNicknameDialog by rememberSaveable { mutableStateOf(false) }

    when (val ui = state) {
        is UiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(strings.loadingLabel, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        is UiState.Error -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ui.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        is UiState.Success -> {
            val data = ui.data

            LaunchedEffect(data.actionFeedback) {
                val msg = data.actionFeedback
                if (msg != null) {
                    onShowMessage(msg)
                    viewModel.consumeActionFeedback()
                }
            }

            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = strings.socialHeaderTag,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = strings.socialTitle,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }

                            OutlinedButton(
                                onClick = onSignOutClick,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = strings.signOut,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.signOut)
                            }
                        }
                    }

                    // Runner Profile & Language Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = data.myPublicProfile?.username?.let { "@$it" }
                                                ?: strings.unrankedLabel,
                                            style = MaterialTheme.typography.titleLarge,
                                            color = if (data.myPublicProfile != null) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.tertiary
                                            }
                                        )
                                        if (!userEmail.isNullOrBlank()) {
                                            Text(
                                                text = userEmail,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.clearUsernameFeedback()
                                            showNicknameDialog = true
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("edit_nickname_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            if (data.myPublicProfile == null) strings.nicknameSetupTitle else strings.editNickname
                                        )
                                    }
                                }

                                LanguageSelectorBar(
                                    currentLanguage = currentLanguage,
                                    onLanguageSelected = onLanguageSelected
                                )
                            }
                        }
                    }

                    // Rankings vs Friends Sub-Tab Switcher
                    item {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                selected = subTabIndex == 0,
                                onClick = { subTabIndex = 0 },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.EmojiEvents,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("social_tab_rankings")
                            ) {
                                Text(strings.subTabRankings)
                            }
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                selected = subTabIndex == 1,
                                onClick = { subTabIndex = 1 },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Group,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("social_tab_friends")
                            ) {
                                val badge = if (data.incomingRequests.isNotEmpty()) {
                                    " (${data.incomingRequests.size})"
                                } else ""
                                Text("${strings.subTabFriends}$badge")
                            }
                        }
                    }

                    if (subTabIndex == 0) {
                        // Ranking Period Filter Chips
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RankingPeriod.entries.forEach { period ->
                                    val label = when (period) {
                                        RankingPeriod.ALL_TIME -> strings.rankingAllTime
                                        RankingPeriod.WEEKLY -> strings.rankingWeekly
                                        RankingPeriod.MONTHLY -> strings.rankingMonthly
                                        RankingPeriod.YEARLY -> strings.rankingYearly
                                    }
                                    FilterChip(
                                        selected = data.selectedRankingPeriod == period,
                                        onClick = { viewModel.selectRankingPeriod(period) },
                                        label = { Text(label) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("rank_period_${period.name.lowercase()}")
                                    )
                                }
                            }
                        }

                        // Your Rank Highlight Card
                        item {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = strings.yourRankLabel,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = data.myRank?.let { "#$it" } ?: strings.unrankedLabel,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (data.rankedRunners.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                                    )
                                ) {
                                    Text(
                                        text = strings.noRankingsYet,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(20.dp)
                                    )
                                }
                            }
                        } else {
                            items(
                                items = data.rankedRunners,
                                key = { it.profile.userId }
                            ) { item ->
                                LeaderboardRowCard(item = item)
                            }
                        }
                    } else {
                        // FRIENDS TAB
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = strings.addFriendByName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = data.friendSearchQuery,
                                            onValueChange = { viewModel.updateFriendSearchQuery(it) },
                                            placeholder = { Text(strings.searchNameHint) },
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("friend_search_input")
                                        )
                                        Button(
                                            onClick = { viewModel.searchFriendByUsername() },
                                            enabled = !data.isSearchingFriend,
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("friend_search_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = strings.searchAction
                                            )
                                        }
                                    }

                                    if (data.searchFeedback != null) {
                                        Text(
                                            text = data.searchFeedback,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    val found = data.searchedRunner
                                    val myPub = data.myPublicProfile
                                    if (found != null && myPub != null && found.userId != myPub.userId) {
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                                                        text = "@${found.username}",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = "${MetricUtils.formatDistanceKm(found.totalDistanceMeters)} km • ${found.totalWorkouts} ${strings.savedUnit}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Button(
                                                    onClick = { viewModel.sendFriendRequest(myPub, found) },
                                                    modifier = Modifier
                                                        .minimumInteractiveComponentSize()
                                                        .testTag("send_friend_request_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PersonAdd,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(strings.sendFriendRequest)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (data.incomingRequests.isNotEmpty()) {
                            item {
                                Text(
                                    text = strings.incomingRequestsTitle,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            items(
                                items = data.incomingRequests,
                                key = { it.id }
                            ) { req ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "@${req.fromUsername}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { viewModel.acceptFriendRequest(req.id) },
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("accept_friend_${req.id}")
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(strings.acceptAction)
                                            }
                                            IconButton(
                                                onClick = { viewModel.removeFriendOrRequest(req.id) },
                                                modifier = Modifier.minimumInteractiveComponentSize()
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = strings.declineAction)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (data.outgoingRequests.isNotEmpty()) {
                            item {
                                Text(
                                    text = strings.outgoingRequestsTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            items(
                                items = data.outgoingRequests,
                                key = { it.id }
                            ) { req ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "@${req.toUsername} (${strings.pendingStatus})",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        TextButton(
                                            onClick = { viewModel.removeFriendOrRequest(req.id) },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Text(strings.cancel)
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                text = strings.friendsComparisonTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (data.friends.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                                    )
                                ) {
                                    Text(
                                        text = strings.noFriendsYet,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(20.dp)
                                    )
                                }
                            }
                        } else {
                            items(
                                items = data.friends,
                                key = { it.requestId }
                            ) { friend ->
                                FriendComparisonCard(
                                    item = friend,
                                    onRemove = { viewModel.removeFriendOrRequest(friend.requestId) }
                                )
                            }
                        }
                    }
                }
            }

            if (showNicknameDialog) {
                NicknameEditorDialog(
                    initialUsername = data.myPublicProfile?.username ?: "",
                    isSaving = data.isSavingUsername,
                    availability = data.usernameAvailability,
                    message = data.usernameMessage,
                    onCheckAvailability = { viewModel.checkUsernameAvailability(it) },
                    onDismiss = { showNicknameDialog = false },
                    onSave = { name ->
                        viewModel.submitUsername(
                            rawUsername = name,
                            existingPublicProfile = data.myPublicProfile,
                            workouts = data.workoutsCache,
                            dailySteps = data.dailyStepsCache,
                            onSuccess = { showNicknameDialog = false }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun LeaderboardRowCard(item: RankedRunner) {
    val strings = LocalStrings.current
    val highlight = item.isCurrentUser
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        border = BorderStroke(
            width = if (highlight) 1.5.dp else 1.dp,
            color = if (highlight) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when (item.rank) {
                    1 -> MaterialTheme.colorScheme.primary
                    2 -> MaterialTheme.colorScheme.secondary
                    3 -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                }
            ) {
                Text(
                    text = "#${item.rank}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = when (item.rank) {
                        1 -> MaterialTheme.colorScheme.onPrimary
                        2 -> MaterialTheme.colorScheme.onSecondary
                        3 -> MaterialTheme.colorScheme.onTertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "@${item.profile.username}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${item.workoutsCount} ${strings.savedUnit} • ${strings.avgPace} ${MetricUtils.formatPace(item.profile.averagePaceSecPerKm.toInt())}/km",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "${MetricUtils.formatDistanceKm(item.distanceMeters)} km",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun FriendComparisonCard(
    item: FriendComparisonItem,
    onRemove: () -> Unit
) {
    val strings = LocalStrings.current
    val p = item.profile
    val nowWeek = DateUtils.weekKeyFor()
    val nowMonth = DateUtils.monthKeyFor()
    val nowYear = DateUtils.yearKeyFor()
    val weekDist = if (p.weekKey == nowWeek) p.weeklyDistanceMeters else 0.0
    val monthDist = if (p.monthKey == nowMonth) p.monthlyDistanceMeters else 0.0
    val yearDist = if (p.yearKey == nowYear) p.yearlyDistanceMeters else 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "@${p.username}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${p.totalWorkouts} ${strings.savedUnit} • ${strings.avgPace} ${MetricUtils.formatPace(p.averagePaceSecPerKm.toInt())}/km",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onRemove,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(strings.removeFriend, color = MaterialTheme.colorScheme.error)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FriendStatPill(strings.weekShort, "${MetricUtils.formatDistanceKm(weekDist)} km")
                FriendStatPill(strings.monthShort, "${MetricUtils.formatDistanceKm(monthDist)} km")
                FriendStatPill(strings.yearShort, "${MetricUtils.formatDistanceKm(yearDist)} km")
                FriendStatPill(strings.allTimeShort, "${MetricUtils.formatDistanceKm(p.totalDistanceMeters)} km")
            }
        }
    }
}

@Composable
private fun FriendStatPill(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun NicknameOnboardingScreen(
    viewModel: SocialViewModel,
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val data = (state as? UiState.Success)?.data
    var input by rememberSaveable { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LanguageSelectorBar(
                currentLanguage = currentLanguage,
                onLanguageSelected = onLanguageSelected
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = strings.nicknameSetupTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = strings.nicknameSetupSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = {
                                input = it.take(24)
                                viewModel.clearUsernameFeedback()
                            },
                            label = { Text(strings.nicknameLabel) },
                            placeholder = { Text(strings.nicknameHint) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("nickname_onboarding_input")
                        )
                        OutlinedButton(
                            onClick = { viewModel.checkUsernameAvailability(input) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("check_nickname_button")
                        ) {
                            Text(strings.checkAvailability)
                        }
                    }

                    val msg = data?.usernameMessage
                    if (msg != null) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = when (data.usernameAvailability) {
                                true -> MaterialTheme.colorScheme.primary
                                false -> MaterialTheme.colorScheme.error
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.submitUsername(
                                rawUsername = input,
                                existingPublicProfile = data?.myPublicProfile,
                                workouts = data?.workoutsCache.orEmpty(),
                                dailySteps = data?.dailyStepsCache.orEmpty()
                            )
                        },
                        enabled = data?.isSavingUsername != true && MetricUtils.isValidUsernameFormat(input),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("save_nickname_onboarding_button")
                    ) {
                        if (data?.isSavingUsername == true) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(strings.saveNickname)
                        }
                    }

                    TextButton(
                        onClick = onSignOutClick,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(strings.signOut)
                    }
                }
            }
        }
    }
}

@Composable
private fun NicknameEditorDialog(
    initialUsername: String,
    isSaving: Boolean,
    availability: Boolean?,
    message: String?,
    onCheckAvailability: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val strings = LocalStrings.current
    var input by remember(initialUsername) { mutableStateOf(initialUsername) }
    val validFormat = MetricUtils.isValidUsernameFormat(input)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.nicknameSetupTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = strings.nicknameSetupSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.take(24) },
                        label = { Text(strings.nicknameLabel) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nickname_dialog_input")
                    )
                    OutlinedButton(
                        onClick = { onCheckAvailability(input) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text(strings.checkAvailability)
                    }
                }
                if (message != null) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (availability) {
                            true -> MaterialTheme.colorScheme.primary
                            false -> MaterialTheme.colorScheme.error
                            null -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(input) },
                enabled = !isSaving && validFormat,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("confirm_nickname_dialog_button")
            ) {
                Text(strings.saveNickname)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text(strings.cancel)
            }
        }
    )
}

@Composable
fun GuestSocialScreen(
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onExitGuestMode: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var isSigningIn by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LanguageSelectorBar(
                currentLanguage = currentLanguage,
                onLanguageSelected = onLanguageSelected
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonOutline,
                                contentDescription = strings.guestModeBadge,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = strings.guestModeBadge,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Text(
                        text = strings.guestSocialTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = strings.guestSocialDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Button(
                        onClick = {
                            isSigningIn = true
                            GoogleAuthHelper.onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                scope = scope,
                                onAuthSuccess = {
                                    isSigningIn = false
                                },
                                onAuthError = { msg ->
                                    isSigningIn = false
                                    onShowMessage(msg)
                                },
                                onAuthCancelled = {
                                    isSigningIn = false
                                }
                            )
                        },
                        enabled = !isSigningIn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("guest_upgrade_google_button"),
                        shape = MaterialTheme.shapes.large
                    ) {
                        if (isSigningIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.signingIn)
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Login,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.signInWithGoogle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    TextButton(
                        onClick = onExitGuestMode,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("exit_guest_mode_button")
                    ) {
                        Text(strings.exitGuestMode)
                    }
                }
            }
        }
    }
}
