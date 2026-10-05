package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.audio.SoundAndHapticsManager
import com.example.audio.SoundEffect
import com.example.data.local.LocalLudoRepository
import com.example.data.local.LudoDatabase
import com.example.data.remote.FirestoreRepository
import com.example.engine.AiController
import com.example.engine.LudoEngine
import com.example.localization.AppStrings
import com.example.model.AiDifficulty
import com.example.model.GameMode
import com.example.model.GameState
import com.example.model.GameStatus
import com.example.model.TurnPhase
import com.example.ui.AppScreen
import com.example.ui.ConnectionStatus
import com.example.ui.RoyalLudoViewModel
import com.example.ui.screens.AchievementsScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.GameScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LeaderboardScreen
import com.example.ui.screens.LobbyScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.signOutUser
import com.example.ui.theme.RoyalCardSurface
import com.example.ui.theme.RoyalCardVariant
import com.example.ui.theme.RoyalDiceLudoTheme
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.RoyalNavyDark
import com.example.ui.theme.RoyalTextMuted
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RoyalDiceLudoTheme {
                AppNavigation()
            }
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember {
        runCatching { Firebase.auth }.getOrNull()
    }
    val currentUser by if (auth != null) {
        auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    } else {
        remember { mutableStateOf<FirebaseUser?>(null) }
    }

    val localRepo = remember {
        val db = LudoDatabase.getInstance(context)
        LocalLudoRepository(context, db.savedGameDao(), db.localMatchHistoryDao())
    }
    val soundManager = remember { SoundAndHapticsManager(context) }
    val savedOfflineGame by localRepo.savedOfflineGameFlow.collectAsStateWithLifecycle(initialValue = null)
    var offlineGuestGame by remember { mutableStateOf<GameState?>(null) }

    val user = currentUser
    if (user == null) {
        val activeGuestGame = offlineGuestGame
        if (activeGuestGame != null) {
            OfflineGuestGameHost(
                initialState = activeGuestGame,
                localRepository = localRepo,
                soundManager = soundManager,
                onExit = { offlineGuestGame = null }
            )
        } else {
            AuthScreen(
                onAuthSuccess = { /* AuthStateListener updates currentUser automatically */ },
                onStartOfflineLocal = { count ->
                    val players = LudoEngine.initializePlayers(count, GameMode.OFFLINE_LOCAL)
                    val newGame = LudoEngine.createGame(GameMode.OFFLINE_LOCAL, players)
                    scope.launch { localRepo.saveOfflineGameState(newGame) }
                    offlineGuestGame = newGame
                },
                onStartOfflineAi = { count, diff ->
                    val players = LudoEngine.initializePlayers(
                        playerCount = count,
                        mode = GameMode.VS_AI,
                        humanNames = listOf("You"),
                        aiDifficulty = diff
                    )
                    val newGame = LudoEngine.createGame(GameMode.VS_AI, players)
                    scope.launch { localRepo.saveOfflineGameState(newGame) }
                    offlineGuestGame = newGame
                },
                hasSavedOfflineGame = savedOfflineGame != null,
                onResumeOfflineGame = {
                    offlineGuestGame = savedOfflineGame
                }
            )
        }
    } else {
        val viewModel: RoyalLudoViewModel = viewModel(
            key = user.uid,
            factory = viewModelFactory {
                initializer {
                    val app = checkNotNull(this[APPLICATION_KEY])
                    val databaseId = app.getString(R.string.firestore_database_id)
                    val firestore = FirebaseFirestore.getInstance(databaseId)
                    val ludoDb = LudoDatabase.getInstance(app)
                    RoyalLudoViewModel(
                        firestoreRepository = FirestoreRepository(firestore, FirebaseAuth.getInstance()),
                        localRepository = LocalLudoRepository(
                            app,
                            ludoDb.savedGameDao(),
                            ludoDb.localMatchHistoryDao()
                        ),
                        soundManager = SoundAndHapticsManager(app),
                        currentUserId = user.uid,
                        defaultDisplayName = user.displayName ?: "RoyalMonarch"
                    )
                }
            }
        )
        AuthenticatedRoyalLudoApp(
            viewModel = viewModel,
            onSignOut = {
                signOutUser(
                    context = context,
                    onSignOutComplete = {},
                    scope = scope
                )
            }
        )
    }
}

@Composable
private fun OfflineGuestGameHost(
    initialState: GameState,
    localRepository: LocalLudoRepository,
    soundManager: SoundAndHapticsManager,
    onExit: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val settings by localRepository.settingsFlow.collectAsStateWithLifecycle()
    val strings = AppStrings.forLang(settings.languageCode)
    var state by remember(initialState.gameId) { mutableStateOf(initialState) }

    LaunchedEffect(state.currentTurnIndex, state.phase, state.status) {
        if (state.status == GameStatus.PLAYING && state.currentPlayer?.isAi == true) {
            delay(if (settings.isReducedMotion) 200L else 550L)
            if (state.phase == TurnPhase.WAITING_FOR_ROLL) {
                soundManager.playEffect(SoundEffect.DICE_ROLL, settings.isSoundEnabled, settings.isVibrationEnabled)
                state = LudoEngine.rollDice(state)
                localRepository.saveOfflineGameState(state)
            } else if (state.phase == TurnPhase.WAITING_FOR_MOVE) {
                val aiMove = AiController.selectBestMove(state)
                if (aiMove != null) {
                    soundManager.playEffect(SoundEffect.TOKEN_MOVE, settings.isSoundEnabled, settings.isVibrationEnabled)
                    state = LudoEngine.moveToken(state, aiMove.tokenId)
                    localRepository.saveOfflineGameState(state)
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = RoyalNavyDark,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            GameScreen(
                gameState = state,
                gameResult = null,
                strings = strings,
                languageCode = settings.languageCode,
                reducedMotion = settings.isReducedMotion,
                roomCode = null,
                connectionStatus = ConnectionStatus.OFFLINE,
                currentUserId = "local_p0",
                onRollDice = {
                    if (state.phase == TurnPhase.WAITING_FOR_ROLL && state.currentPlayer?.isAi == false) {
                        soundManager.playEffect(SoundEffect.DICE_ROLL, settings.isSoundEnabled, settings.isVibrationEnabled)
                        val rolled = LudoEngine.rollDice(state)
                        state = rolled
                        scope.launch { localRepository.saveOfflineGameState(rolled) }
                    }
                },
                onSelectToken = { tokenId ->
                    if (state.phase == TurnPhase.WAITING_FOR_MOVE && state.currentPlayer?.isAi == false) {
                        soundManager.playEffect(SoundEffect.TOKEN_MOVE, settings.isSoundEnabled, settings.isVibrationEnabled)
                        val moved = LudoEngine.moveToken(state, tokenId)
                        state = moved
                        scope.launch { localRepository.saveOfflineGameState(moved) }
                    }
                },
                onExitGame = onExit
            )
        }
    }
}

@Composable
private fun AuthenticatedRoyalLudoApp(
    viewModel: RoyalLudoViewModel,
    onSignOut: () -> Unit
) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val savedOfflineGame by viewModel.savedOfflineGame.collectAsStateWithLifecycle()
    val gameState by viewModel.gameState.collectAsStateWithLifecycle()
    val gameResult by viewModel.gameResult.collectAsStateWithLifecycle()
    val activeRoom by viewModel.activeOnlineRoom.collectAsStateWithLifecycle()
    val reconnectableRoom by viewModel.reconnectableRoom.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val leaderboard by viewModel.leaderboard.collectAsStateWithLifecycle()
    val cloudHistory by viewModel.cloudMatchHistory.collectAsStateWithLifecycle()
    val localHistory by viewModel.localHistory.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val strings = AppStrings.forLang(settings.languageCode)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(bannerMessage) {
        val msg = bannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBannerMessage()
        }
    }

    val showBottomBar = currentScreen != AppScreen.GAME

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = RoyalNavyDark,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = RoyalCardSurface,
                    contentColor = Color.White
                ) {
                    val navItems = listOf(
                        Triple(AppScreen.HOME, strings.navHome, Icons.Default.Home),
                        Triple(AppScreen.LEADERBOARD, strings.navLeaderboard, Icons.Default.Leaderboard),
                        Triple(AppScreen.ACHIEVEMENTS, strings.navAchievements, Icons.Default.EmojiEvents),
                        Triple(AppScreen.HISTORY, strings.navHistory, Icons.Default.History),
                        Triple(AppScreen.PROFILE, strings.navProfile, Icons.Default.Person)
                    )
                    navItems.forEach { (screen, label, icon) ->
                        NavigationBarItem(
                            selected = currentScreen == screen,
                            onClick = { viewModel.navigateTo(screen) },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            modifier = Modifier.testTag("bottom_nav_${screen.name.lowercase()}"),
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = RoyalNavyDark,
                                selectedTextColor = RoyalGold,
                                indicatorColor = RoyalGold,
                                unselectedIconColor = RoyalTextMuted,
                                unselectedTextColor = RoyalTextMuted
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                AppScreen.HOME -> {
                    HomeScreen(
                        profile = profile,
                        strings = strings,
                        savedOfflineGame = savedOfflineGame,
                        reconnectableRoom = reconnectableRoom,
                        connectionStatus = connectionStatus,
                        onStartQuickMatch = { viewModel.startOnlineQuickMatch() },
                        onStartAiMatch = { players, diff -> viewModel.startAiMatch(players, diff) },
                        onStartOfflineLocal = { players -> viewModel.startOfflinePassAndPlay(players) },
                        onOpenLobby = { viewModel.openLobbyScreen() },
                        onResumeSavedGame = { viewModel.resumeSavedOfflineMatch() },
                        onReconnectRoom = { room -> viewModel.reconnectToOnlineRoom(room) },
                        onNavigate = { screen -> viewModel.navigateTo(screen) }
                    )
                }

                AppScreen.GAME -> {
                    val activeGame = gameState
                    if (activeGame != null) {
                        GameScreen(
                            gameState = activeGame,
                            gameResult = gameResult,
                            strings = strings,
                            languageCode = settings.languageCode,
                            reducedMotion = settings.isReducedMotion,
                            roomCode = activeRoom?.roomCode,
                            connectionStatus = connectionStatus,
                            currentUserId = viewModel.currentUserId,
                            onRollDice = { viewModel.onRollDiceClicked() },
                            onSelectToken = { tokenId -> viewModel.onTokenSelected(tokenId) },
                            onExitGame = { viewModel.navigateBackToHome() }
                        )
                    } else {
                        viewModel.navigateBackToHome()
                    }
                }

                AppScreen.LOBBY -> {
                    LobbyScreen(
                        activeRoom = activeRoom,
                        strings = strings,
                        isBusy = isBusy,
                        onCreateRoom = { maxP -> viewModel.createPrivateRoom(maxP) },
                        onJoinByCode = { code -> viewModel.joinPrivateRoomByCode(code) },
                        onHostStartMatch = { viewModel.hostStartOnlineRoomMatch() },
                        onBack = { viewModel.navigateBackToHome() }
                    )
                }

                AppScreen.LEADERBOARD -> {
                    LeaderboardScreen(
                        entries = leaderboard,
                        currentUserId = viewModel.currentUserId,
                        strings = strings,
                        onBack = { viewModel.navigateBackToHome() }
                    )
                }

                AppScreen.ACHIEVEMENTS -> {
                    AchievementsScreen(
                        profile = profile,
                        strings = strings,
                        languageCode = settings.languageCode,
                        onBack = { viewModel.navigateBackToHome() }
                    )
                }

                AppScreen.HISTORY -> {
                    HistoryScreen(
                        cloudHistory = cloudHistory,
                        localHistory = localHistory,
                        strings = strings,
                        onBack = { viewModel.navigateBackToHome() }
                    )
                }

                AppScreen.PROFILE -> {
                    ProfileScreen(
                        profile = profile,
                        strings = strings,
                        onSaveProfile = { name, avatar -> viewModel.updateProfile(name, avatar) },
                        onSignOut = onSignOut,
                        onBack = { viewModel.navigateBackToHome() }
                    )
                }

                AppScreen.SETTINGS -> {
                    SettingsScreen(
                        settings = settings,
                        strings = strings,
                        onUpdateSettings = { transform -> viewModel.updateAppSettings(transform) },
                        onBack = { viewModel.navigateBackToHome() }
                    )
                }
            }
        }
    }
}
