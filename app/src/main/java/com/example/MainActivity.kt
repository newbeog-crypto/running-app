package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.remote.GoogleAuthHelper
import com.example.data.remote.UiState
import com.example.data.remote.authStateFlow
import com.example.di.AppContainer
import com.example.ui.auth.AuthScreen
import com.example.ui.history.HistoryScreen
import com.example.ui.history.HistoryViewModel
import com.example.ui.history.WorkoutDetailScreen
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalStrings
import com.example.ui.i18n.stringsFor
import com.example.ui.navigation.AppRoutes
import com.example.ui.navigation.BottomNavTab
import com.example.ui.run.RunScreen
import com.example.ui.run.RunViewModel
import com.example.ui.social.GuestSocialScreen
import com.example.ui.social.NicknameOnboardingScreen
import com.example.ui.social.SocialScreen
import com.example.ui.social.SocialViewModel
import com.example.ui.stats.StatsScreen
import com.example.ui.stats.StatsViewModel
import com.example.ui.theme.StridePulseTheme
import com.example.ui.today.TodayScreen
import com.example.ui.today.TodayViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as StridePulseApplication).appContainer

        setContent {
            StridePulseTheme {
                StridePulseApp(appContainer = appContainer)
            }
        }
    }
}

@Composable
fun StridePulseApp(appContainer: AppContainer) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val languageCode by appContainer.userPreferences.appLanguageCode
        .collectAsStateWithLifecycle(initialValue = null)
    val currentLanguage = remember(languageCode) { AppLanguage.fromCode(languageCode) }
    val strings = remember(currentLanguage) { stringsFor(currentLanguage) }

    val isGuestMode by appContainer.userPreferences.isGuestMode
        .collectAsStateWithLifecycle(initialValue = false)

    val currentUser by remember { Firebase.auth.authStateFlow() }
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    // Automatically ensure background step tracking service & sensor listener are synced when app resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                appContainer.trackingController.onAppForegrounded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Clear guest flag automatically once user signs in with Google
    LaunchedEffect(currentUser?.uid) {
        if (currentUser != null && isGuestMode) {
            appContainer.userPreferences.setGuestMode(false)
        }
    }

    CompositionLocalProvider(LocalStrings provides strings) {
        val user = currentUser
        if (user == null && !isGuestMode) {
            AuthScreen(
                currentLanguage = currentLanguage,
                onLanguageSelected = { selected ->
                    scope.launch {
                        appContainer.userPreferences.setAppLanguageCode(selected.code)
                    }
                },
                onAuthSuccess = {
                    scope.launch {
                        appContainer.userPreferences.setGuestMode(false)
                    }
                },
                onContinueAsGuest = {
                    scope.launch {
                        appContainer.userPreferences.setGuestMode(true)
                    }
                }
            )
        } else {
            val sessionKey = user?.uid ?: "guest"
            key(sessionKey) {
                StridePulseMainContent(
                    appContainer = appContainer,
                    userId = user?.uid,
                    userEmail = user?.email,
                    currentLanguage = currentLanguage,
                    onLanguageSelected = { selected ->
                        scope.launch {
                            appContainer.userPreferences.setAppLanguageCode(selected.code)
                        }
                    },
                    onSignOut = {
                        appContainer.trackingController.unbindUser()
                        scope.launch {
                            appContainer.userPreferences.setGuestMode(false)
                        }
                        if (user != null) {
                            GoogleAuthHelper.signOut(
                                credentialManager = credentialManager,
                                scope = scope
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun StridePulseMainContent(
    appContainer: AppContainer,
    userId: String?,
    userEmail: String?,
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onSignOut: () -> Unit
) {
    val strings = LocalStrings.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val todayViewModel: TodayViewModel = viewModel(
        factory = TodayViewModel.Factory(
            repository = appContainer.activityRepository,
            cloudRepository = appContainer.cloudRepository,
            stepCounterManager = appContainer.stepCounterManager,
            trackingController = appContainer.trackingController,
            currentUserId = userId
        )
    )
    val runViewModel: RunViewModel = viewModel(
        factory = RunViewModel.Factory(
            repository = appContainer.activityRepository,
            trackingController = appContainer.trackingController,
            currentUserId = userId
        )
    )
    val statsViewModel: StatsViewModel = viewModel(
        factory = StatsViewModel.Factory(
            cloudRepository = appContainer.cloudRepository,
            activityRepository = appContainer.activityRepository,
            currentUserId = userId
        )
    )
    val historyViewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModel.Factory(
            cloudRepository = appContainer.cloudRepository,
            activityRepository = appContainer.activityRepository,
            currentUserId = userId
        )
    )

    val socialViewModel: SocialViewModel? = if (userId != null) {
        viewModel(
            factory = SocialViewModel.Factory(
                cloudRepository = appContainer.cloudRepository,
                currentUserId = userId,
                currentUserEmail = userEmail
            )
        )
    } else {
        null
    }

    if (socialViewModel != null) {
        val socialState by socialViewModel.uiState.collectAsStateWithLifecycle()

        if (socialState is UiState.Loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(strings.loadingLabel, style = MaterialTheme.typography.bodyMedium)
                }
            }
            return
        }

        val needsNicknameSetup = (socialState as? UiState.Success)?.data?.myPublicProfile == null
        if (needsNicknameSetup) {
            NicknameOnboardingScreen(
                viewModel = socialViewModel,
                currentLanguage = currentLanguage,
                onLanguageSelected = onLanguageSelected,
                onSignOutClick = onSignOut,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
            )
            return
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showTopLevelNav = currentDestination?.route in setOf(
        AppRoutes.TODAY,
        AppRoutes.RUN,
        AppRoutes.STATS,
        AppRoutes.HISTORY,
        AppRoutes.SOCIAL
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (showTopLevelNav && !useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        BottomNavTab.entries.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.route == tab.route
                            } == true
                            val label = tab.label(strings)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTopLevelTab(navController, tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showTopLevelNav && useNavRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        BottomNavTab.entries.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.route == tab.route
                            } == true
                            val label = tab.label(strings)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTopLevelTab(navController, tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = AppRoutes.TODAY,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(AppRoutes.TODAY) {
                        TodayScreen(viewModel = todayViewModel)
                    }

                    composable(AppRoutes.RUN) {
                        RunScreen(
                            viewModel = runViewModel,
                            onWorkoutSaved = { sessionId ->
                                navigateToTopLevelTab(navController, AppRoutes.HISTORY)
                                navController.navigate(AppRoutes.workoutDetail(sessionId))
                                scope.launch {
                                    snackbarHostState.showSnackbar(strings.workoutSavedSnackbar)
                                }
                            },
                            onShowMessage = { message ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        )
                    }

                    composable(AppRoutes.STATS) {
                        StatsScreen(viewModel = statsViewModel)
                    }

                    composable(AppRoutes.HISTORY) {
                        HistoryScreen(
                            viewModel = historyViewModel,
                            onSessionClick = { sessionId ->
                                navController.navigate(AppRoutes.workoutDetail(sessionId))
                            },
                            onStartRunClick = {
                                navigateToTopLevelTab(navController, AppRoutes.RUN)
                            }
                        )
                    }

                    composable(AppRoutes.SOCIAL) {
                        if (socialViewModel != null) {
                            SocialScreen(
                                viewModel = socialViewModel,
                                userEmail = userEmail,
                                currentLanguage = currentLanguage,
                                onLanguageSelected = onLanguageSelected,
                                onSignOutClick = onSignOut,
                                onShowMessage = { message ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar(message)
                                    }
                                }
                            )
                        } else {
                            GuestSocialScreen(
                                currentLanguage = currentLanguage,
                                onLanguageSelected = onLanguageSelected,
                                onExitGuestMode = onSignOut,
                                onShowMessage = { message ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar(message)
                                    }
                                }
                            )
                        }
                    }

                    composable(
                        route = AppRoutes.WORKOUT_DETAIL_PATTERN,
                        arguments = listOf(
                            navArgument("sessionId") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
                        WorkoutDetailScreen(
                            sessionId = sessionId,
                            viewModel = historyViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

private fun navigateToTopLevelTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
