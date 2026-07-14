package com.example.easyfill_project

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.easyfill_project.chatbot.model.BotAction
import com.example.easyfill_project.chatbot.model.BotAppState
import com.example.easyfill_project.chatbot.model.DistressSnapshot
import com.example.easyfill_project.chatbot.navigation.BotNavigationHandler
import com.example.easyfill_project.chatbot.ui.FloatingChatOverlay
import com.example.easyfill_project.distress_scoring.DistressScoringManager

// Face detection test screen
import com.example.easyfill_project.face_analysis.FaceDetectionTestScreen

import com.example.easyfill_project.forms_screens.DemoFormsOptions
import com.example.easyfill_project.forms_screens.HousingAssistanceFormScreen
import com.example.easyfill_project.screen.AuthScreen
import com.example.easyfill_project.screen.BackgroundSoundsScreen
import com.example.easyfill_project.screen.ContrastMode
import com.example.easyfill_project.screen.ContrastSettingsScreen
import com.example.easyfill_project.screen.EasyFillMainScreen
import com.example.easyfill_project.screen.FontSizeMode
import com.example.easyfill_project.screen.FontSizeSettingsScreen
import com.example.easyfill_project.screen.GuidanceScreen
import com.example.easyfill_project.screen.GuidanceSlidesScreen
import com.example.easyfill_project.screen.HomeScreen
import com.example.easyfill_project.screen.MyFormsProgressScreen
import com.example.easyfill_project.screen.PersonalSettingScreen
import com.example.easyfill_project.screen.ProfileScreen
import com.example.easyfill_project.screen.RegisterScreen
import com.example.easyfill_project.screen.SoundManager
import com.example.easyfill_project.screen.UploadPdfScreen
import com.example.easyfill_project.screen.getAppTypography
import com.example.easyfill_project.screen.getContrastColorScheme
import com.example.easyfill_project.speechtotext.SpeechToTextManager
import com.example.easyfill_project.texttospeech.TextToSpeechManager
import com.example.easyfill_project.texttospeech.TtsTexts
import com.example.easyfill_project.voiceanalysis.BaselineVoiceScreen
import com.example.easyfill_project.voiceanalysis.VoiceBaselineRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


// Main navigation function
@Composable
fun AppNavigation() {

    // Controls navigation between screens
    val navController = rememberNavController()

    // Defines all app routes
    NavHost(
        navController = navController,
        startDestination = "main"
    ) {

        // First screen
        composable("main") {
            EasyFillMainScreen(navController)
        }

        // Authentication screen
        composable("auth") {
            AuthScreen(navController)
        }

        composable("register") {
            RegisterScreen(navController)
        }

        // Opens the app area that contains the drawer and inner navigation
        composable("app") {
            AppWithDrawer(navController)
        }
    }
}


// Wraps the application screens that use the side drawer
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppWithDrawer(
    mainNavController: NavHostController
) {

    // Controls whether the drawer is open or closed
    val drawerState =
        rememberDrawerState(DrawerValue.Closed)

    // Coroutine scope required for drawer actions
    val scope = rememberCoroutineScope()

    // Controls navigation inside the app area
    val innerNavController = rememberNavController()

    var contrastMode by remember {
        mutableStateOf(ContrastMode.DEFAULT)
    }

    var fontSizeMode by remember {
        mutableStateOf(FontSizeMode.NORMAL)
    }

    // Marks the current screen
    val currentBackStackEntry by
    innerNavController.currentBackStackEntryAsState()

    val currentRoute =
        currentBackStackEntry?.destination?.route

    val screenWidth =
        LocalConfiguration.current.screenWidthDp.dp

    // TTS and STT managers
    val context = LocalContext.current

    val ttsManager = remember {
        TextToSpeechManager(context)
    }

    val speechManager = remember {
        SpeechToTextManager(context)
    }

    var baselineDone by remember {
        mutableStateOf(false)
    }

    val voiceBaselineRepository = remember {
        VoiceBaselineRepository()
    }

    LaunchedEffect(Unit) {
        voiceBaselineRepository.hasBaseline(
            onResult = { exists ->
                baselineDone = exists
            }
        )
    }

    var screenTextToRead by remember {
        mutableStateOf("")
    }

    var isTtsSpeaking by remember {
        mutableStateOf(false)
    }

    // SharedPreferences for automatic reading
    val prefs =
        context.getSharedPreferences(
            "user_settings",
            Context.MODE_PRIVATE
        )

    var autoReadEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                "auto_read_enabled",
                false
            )
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            ttsManager.shutdown()
        }
    }

    // Opens the drawer from the right side
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {

        MaterialTheme(
            colorScheme =
                getContrastColorScheme(contrastMode),
            typography =
                getAppTypography(fontSizeMode)
        ) {

            ModalNavigationDrawer(
                drawerState = drawerState,

                drawerContent = {
                    ModalDrawerSheet(
                        modifier =
                            Modifier.width(screenWidth * 0.6f),
                        drawerContainerColor =
                            MaterialTheme.colorScheme.surface,
                        drawerContentColor =
                            MaterialTheme.colorScheme.onSurface
                    ) {

                        Text(
                            text = "תפריט ראשי",
                            modifier = Modifier.padding(16.dp),
                            color =
                                MaterialTheme.colorScheme.onSurface
                        )

                        NavigationDrawerItem(
                            label = {
                                Text("דף הבית")
                            },
                            selected =
                                currentRoute == "home",
                            icon = {
                                Icon(
                                    imageVector =
                                        Icons.Default.Home,
                                    contentDescription =
                                        "Home"
                                )
                            },
                            colors =
                                NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor =
                                        MaterialTheme.colorScheme.primary,
                                    selectedTextColor =
                                        MaterialTheme.colorScheme.onPrimary,
                                    selectedIconColor =
                                        MaterialTheme.colorScheme.onPrimary,
                                    unselectedContainerColor =
                                        MaterialTheme.colorScheme.surface,
                                    unselectedTextColor =
                                        MaterialTheme.colorScheme.onSurface,
                                    unselectedIconColor =
                                        MaterialTheme.colorScheme.onSurface
                                ),
                            onClick = {
                                innerNavController.navigate("home")

                                scope.launch {
                                    drawerState.close()
                                }
                            }
                        )

                        NavigationDrawerItem(
                            label = {
                                Text("ניהול חשבון")
                            },
                            selected =
                                currentRoute == "profile",
                            icon = {
                                Icon(
                                    imageVector =
                                        Icons.Default.Person,
                                    contentDescription =
                                        "Profile"
                                )
                            },
                            colors =
                                NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor =
                                        MaterialTheme.colorScheme.primary,
                                    selectedTextColor =
                                        MaterialTheme.colorScheme.onPrimary,
                                    selectedIconColor =
                                        MaterialTheme.colorScheme.onPrimary,
                                    unselectedContainerColor =
                                        MaterialTheme.colorScheme.surface,
                                    unselectedTextColor =
                                        MaterialTheme.colorScheme.onSurface,
                                    unselectedIconColor =
                                        MaterialTheme.colorScheme.onSurface
                                ),
                            onClick = {
                                innerNavController.navigate("profile")

                                scope.launch {
                                    drawerState.close()
                                }
                            }
                        )

                        NavigationDrawerItem(
                            label = {
                                Text("מדריך למשתמש")
                            },
                            selected =
                                currentRoute == "Guidance",
                            icon = {
                                Icon(
                                    imageVector =
                                        Icons.Default.Info,
                                    contentDescription =
                                        "User guide"
                                )
                            },
                            colors =
                                NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor =
                                        MaterialTheme.colorScheme.primary,
                                    selectedTextColor =
                                        MaterialTheme.colorScheme.onPrimary,
                                    selectedIconColor =
                                        MaterialTheme.colorScheme.onPrimary,
                                    unselectedContainerColor =
                                        MaterialTheme.colorScheme.surface,
                                    unselectedTextColor =
                                        MaterialTheme.colorScheme.onSurface,
                                    unselectedIconColor =
                                        MaterialTheme.colorScheme.onSurface
                                ),
                            onClick = {
                                innerNavController.navigate("Guidance")

                                scope.launch {
                                    drawerState.close()
                                }
                            }
                        )

                        NavigationDrawerItem(
                            label = {
                                Text("התאמה אישית")
                            },
                            selected =
                                currentRoute == "Personal Settings",
                            icon = {
                                Icon(
                                    imageVector =
                                        Icons.Default.Settings,
                                    contentDescription =
                                        "Personal Settings"
                                )
                            },
                            colors =
                                NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor =
                                        MaterialTheme.colorScheme.primary,
                                    selectedTextColor =
                                        MaterialTheme.colorScheme.onPrimary,
                                    selectedIconColor =
                                        MaterialTheme.colorScheme.onPrimary,
                                    unselectedContainerColor =
                                        MaterialTheme.colorScheme.surface,
                                    unselectedTextColor =
                                        MaterialTheme.colorScheme.onSurface,
                                    unselectedIconColor =
                                        MaterialTheme.colorScheme.onSurface
                                ),
                            onClick = {
                                innerNavController.navigate(
                                    "Personal Settings"
                                )

                                scope.launch {
                                    drawerState.close()
                                }
                            }
                        )
                    }
                }
            ) {

                CompositionLocalProvider(
                    LocalLayoutDirection provides
                            LayoutDirection.Rtl
                ) {

                    var userName by remember {
                        mutableStateOf("")
                    }

                    LaunchedEffect(Unit) {
                        val userId =
                            FirebaseAuth
                                .getInstance()
                                .currentUser
                                ?.uid

                        if (userId != null) {
                            FirebaseFirestore
                                .getInstance()
                                .collection("users")
                                .document(userId)
                                .get()
                                .addOnSuccessListener { document ->

                                    userName =
                                        document.getString(
                                            "fullName"
                                        ) ?: ""
                                }
                        }
                    }

                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = {
                                    Column {
                                        Text("EasyFill")

                                        if (userName.isNotEmpty()) {
                                            Text(
                                                text =
                                                    "שלום, $userName",
                                                fontSize = 20.sp
                                            )
                                        }
                                    }
                                },

                                navigationIcon = {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                drawerState.open()
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector =
                                                Icons.Default.Menu,
                                            contentDescription =
                                                "Open menu"
                                        )
                                    }
                                },

                                actions = {
                                    IconButton(
                                        onClick = {
                                            if (isTtsSpeaking) {
                                                ttsManager.stop()
                                                isTtsSpeaking = false
                                            } else {
                                                ttsManager.speak(
                                                    screenTextToRead
                                                )
                                                isTtsSpeaking = true
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector =
                                                if (isTtsSpeaking) {
                                                    Icons.Default.VolumeOff
                                                } else {
                                                    Icons.Default.VolumeUp
                                                },
                                            contentDescription =
                                                if (isTtsSpeaking) {
                                                    "עצירת הקראה"
                                                } else {
                                                    "הקראת טקסט"
                                                }
                                        )
                                    }
                                }
                            )
                        }
                    ) { innerPadding ->

                        // Updates the text used by TTS
                        fun updateScreenText(
                            text: String
                        ) {
                            ttsManager.stop()
                            isTtsSpeaking = false
                            screenTextToRead = text

                            if (autoReadEnabled) {
                                scope.launch {
                                    delay(400)
                                    ttsManager.speak(text)
                                    isTtsSpeaking = true
                                }
                            }
                        }

                        NavHost(
                            navController =
                                innerNavController,

                            /*
                             * Temporary test destination.
                             * Change this back to "home"
                             * after the face test is complete.
                             */
                            startDestination =
                                "faceDetectionTest",

                            modifier =
                                Modifier.padding(innerPadding)
                        ) {

                            /*
                             * Temporary MediaPipe and CameraX
                             * face detection test screen.
                             */
                            composable(
                                route = "faceDetectionTest"
                            ) {
                                FaceDetectionTestScreen()
                            }

                            composable("home") {
                                LaunchedEffect(Unit) {
                                    updateScreenText(
                                        TtsTexts.HOME
                                    )
                                }

                                HomeScreen(
                                    navController =
                                        innerNavController,
                                    baselineDone =
                                        baselineDone
                                )
                            }

                            composable("profile") {
                                LaunchedEffect(Unit) {
                                    updateScreenText(
                                        TtsTexts.PROFILE
                                    )
                                }

                                ProfileScreen(
                                    navController =
                                        mainNavController,
                                    onNameUpdated = {
                                            updatedName ->

                                        userName =
                                            updatedName
                                    }
                                )
                            }

                            composable("Guidance") {
                                LaunchedEffect(Unit) {
                                    updateScreenText(
                                        TtsTexts.GUIDANCE
                                    )
                                }

                                GuidanceScreen()
                            }

                            composable(
                                "Personal Settings"
                            ) {
                                LaunchedEffect(Unit) {
                                    updateScreenText(
                                        TtsTexts.PERSONAL_SETTINGS
                                    )
                                }

                                PersonalSettingScreen(
                                    innerNavController,
                                    autoReadEnabled =
                                        autoReadEnabled,
                                    onAutoReadChange = {
                                            enabled ->

                                        autoReadEnabled =
                                            enabled

                                        if (enabled) {
                                            scope.launch {
                                                delay(400)

                                                ttsManager.speak(
                                                    screenTextToRead
                                                )

                                                isTtsSpeaking =
                                                    true
                                            }
                                        } else {
                                            ttsManager.stop()

                                            isTtsSpeaking =
                                                false
                                        }
                                    }
                                )
                            }

                            composable(
                                "backgroundSounds"
                            ) {
                                LaunchedEffect(Unit) {
                                    updateScreenText(
                                        TtsTexts.SOUND
                                    )
                                }

                                BackgroundSoundsScreen(
                                    innerNavController
                                )
                            }

                            composable(
                                "contrastSettings"
                            ) {
                                LaunchedEffect(Unit) {
                                    updateScreenText(
                                        TtsTexts.CONTRAST
                                    )
                                }

                                ContrastSettingsScreen(
                                    selectedMode =
                                        contrastMode,
                                    onModeSelected = {
                                        contrastMode = it
                                    },
                                    navController =
                                        innerNavController
                                )
                            }

                            composable(
                                "fontSizeSettings"
                            ) {
                                LaunchedEffect(Unit) {
                                    updateScreenText(
                                        TtsTexts.FONT_SIZE
                                    )
                                }

                                FontSizeSettingsScreen(
                                    selectedMode =
                                        fontSizeMode,
                                    onModeSelected = {
                                        fontSizeMode = it
                                    },
                                    navController =
                                        innerNavController
                                )
                            }

                            // Voice baseline screen
                            composable("baselineVoice") {
                                BaselineVoiceScreen(
                                    speechManager =
                                        speechManager,

                                    onBaselineFinished = {
                                        baselineDone = true

                                        innerNavController
                                            .navigate(
                                                "uploadPdf"
                                            ) {
                                                popUpTo(
                                                    "baselineVoice"
                                                ) {
                                                    inclusive =
                                                        true
                                                }
                                            }
                                    }
                                )
                            }

                            // Upload PDF screen
                            composable("uploadPdf") {
                                LaunchedEffect(Unit) {
                                    updateScreenText(
                                        TtsTexts.UPLOAD_PDF
                                    )
                                }

                                UploadPdfScreen(
                                    navController =
                                        innerNavController
                                )
                            }

                            // Form selection screen
                            composable(
                                "demoFormOptions"
                            ) {
                                DemoFormsOptions(
                                    navController =
                                        innerNavController
                                )

                                LaunchedEffect(Unit) {
                                    updateScreenText(
                                        TtsTexts.FORM_OPTIONS
                                    )
                                }
                            }

                            // First form
                            composable(
                                "housingAssistanceForm"
                            ) {
                                HousingAssistanceFormScreen(
                                    navController =
                                        innerNavController,
                                    startStep = 0
                                )
                            }

                            composable(
                                "housingAssistanceForm/{startStep}"
                            ) { backStackEntry ->

                                val startStep =
                                    backStackEntry
                                        .arguments
                                        ?.getString(
                                            "startStep"
                                        )
                                        ?.toIntOrNull()
                                        ?: 0

                                HousingAssistanceFormScreen(
                                    navController =
                                        innerNavController,
                                    startStep =
                                        startStep
                                )
                            }

                            composable(
                                "myFormsProgress"
                            ) {
                                MyFormsProgressScreen(
                                    navController =
                                        innerNavController
                                )
                            }

                            composable(
                                "guidanceSlides"
                            ) {
                                GuidanceSlidesScreen(
                                    navController =
                                        innerNavController
                                )
                            }

                            // Second form
                            composable(
                                "bankDetailsForm"
                            ) {
                                // BankDetailsFormScreen(...)
                            }
                        }

                        // Distress scoring values
                        val handScore by
                        DistressScoringManager
                            .handScore
                            .collectAsState()

                        val voiceScore by
                        DistressScoringManager
                            .voiceScore
                            .collectAsState()

                        val faceScore by
                        DistressScoringManager
                            .faceScore
                            .collectAsState()

                        val formBehaviorScore by
                        DistressScoringManager
                            .formBehaviorScore
                            .collectAsState()

                        val totalScore by
                        DistressScoringManager
                            .totalScore
                            .collectAsState()

                        val realDistressSnapshot =
                            DistressSnapshot(
                                globalScore =
                                    totalScore,
                                semanticTextScore = 0,
                                faceScore =
                                    faceScore,
                                voiceScore =
                                    voiceScore,
                                touchScore =
                                    handScore,
                                formBehaviorScore =
                                    formBehaviorScore
                            )

                        val botAppState =
                            BotAppState(
                                isMusicPlaying =
                                    SoundManager
                                        .selectedSound !=
                                            "none",
                                selectedSound =
                                    SoundManager
                                        .selectedSound,
                                isTtsSpeaking =
                                    isTtsSpeaking,
                                autoReadEnabled =
                                    autoReadEnabled,
                                fontSizeMode =
                                    fontSizeMode.name,
                                contrastMode =
                                    contrastMode.name
                            )

                        FloatingChatOverlay(
                            currentScreen =
                                currentRoute
                                    ?: "לא ידוע",

                            autoOpenOnDistress =
                                false,

                            distressSnapshot =
                                realDistressSnapshot,

                            appState =
                                botAppState,

                            onBotAction = { action ->

                                val navigationHandled =
                                    BotNavigationHandler
                                        .handle(
                                            action =
                                                action,
                                            navController =
                                                innerNavController
                                        )

                                if (!navigationHandled) {
                                    when (action) {

                                        BotAction.ReadAloud -> {
                                            ttsManager.speak(
                                                screenTextToRead
                                            )

                                            isTtsSpeaking =
                                                true
                                        }

                                        BotAction.StopReading -> {
                                            ttsManager.stop()

                                            isTtsSpeaking =
                                                false
                                        }

                                        BotAction.EnableAutoRead -> {
                                            autoReadEnabled =
                                                true

                                            prefs.edit()
                                                .putBoolean(
                                                    "auto_read_enabled",
                                                    true
                                                )
                                                .apply()

                                            ttsManager.speak(
                                                screenTextToRead
                                            )

                                            isTtsSpeaking =
                                                true
                                        }

                                        BotAction.DisableAutoRead -> {
                                            autoReadEnabled =
                                                false

                                            prefs.edit()
                                                .putBoolean(
                                                    "auto_read_enabled",
                                                    false
                                                )
                                                .apply()

                                            ttsManager.stop()

                                            isTtsSpeaking =
                                                false
                                        }

                                        is BotAction.PlaySound -> {
                                            SoundManager.play(
                                                context =
                                                    context,
                                                soundName =
                                                    action.option.key,
                                                soundRes =
                                                    action.option.soundRes
                                            )
                                        }

                                        BotAction.StopBackgroundMusic -> {
                                            SoundManager.stop()
                                        }

                                        is BotAction.SetContrast -> {
                                            contrastMode =
                                                action.option.mode
                                        }

                                        is BotAction.SetFontSize -> {
                                            fontSizeMode =
                                                action.option.mode
                                        }

                                        BotAction.None -> Unit

                                        else -> Unit
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}