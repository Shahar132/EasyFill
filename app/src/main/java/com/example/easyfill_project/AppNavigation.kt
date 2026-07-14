package com.example.easyfill_project

import android.content.Context
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

// Compose state & runtime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue

// Modifier for layout control
import androidx.compose.ui.Modifier

// Needed to force RTL (right-to-left) for right-side drawer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Navigation
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

// Your screens
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

// Imports regarding the chatbot
import com.example.easyfill_project.chatbot.help.FieldHelpCatalog
import com.example.easyfill_project.chatbot.logic.BotSupportActionHandler
import com.example.easyfill_project.chatbot.model.BotAppState
import com.example.easyfill_project.chatbot.model.DistressSnapshot

// Imports regarding distress scoring
import com.example.easyfill_project.distress_scoring.DistressScoringManager

// Imports regarding speech and TTS
import com.example.easyfill_project.speechtotext.SpeechToTextManager
import com.example.easyfill_project.texttospeech.TextToSpeechManager
import com.example.easyfill_project.texttospeech.TtsTexts
import com.example.easyfill_project.voiceanalysis.BaselineVoiceScreen
import com.example.easyfill_project.voiceanalysis.VoiceBaselineRepository

// Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// Coroutines
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


// Main navigation function
@Composable
fun AppNavigation() {

    // Controls navigation between screens
    val navController = rememberNavController()

    // Defines all app routes (screens)
    NavHost(
        navController = navController,
        startDestination = "main" // First screen when app opens
    ) {

        // First screen (NO drawer here - will be on the home screen)
        composable("main") {
            EasyFillMainScreen(navController)
        }

        // Auth screen (NO drawer here as well)
        composable("auth") {
            AuthScreen(navController)
        }

        composable("register") {
            RegisterScreen(navController)
        }

        // From here → screens with drawer
        composable("app") {
            // When user logs in, route "app" opens the home screen
            // together with the side menu and inner NavHost.
            AppWithDrawer(navController)
        }
    }
}


// This function wraps screens WITH the side drawer
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppWithDrawer(
    mainNavController: NavHostController
) {

    // Controls whether drawer is open or closed
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // Coroutine scope required for drawer actions (open/close)
    val scope = rememberCoroutineScope()

    // This controls only screens INSIDE the app area
    val innerNavController = rememberNavController()

    // Current color/contrast mode used by the app theme
    var contrastMode by remember {
        mutableStateOf(ContrastMode.DEFAULT)
    }

    // Current font-size mode used by the app theme
    var fontSizeMode by remember {
        mutableStateOf(FontSizeMode.NORMAL)
    }

    // For marking the current screen
    val currentBackStackEntry by
    innerNavController.currentBackStackEntryAsState()

    val currentRoute =
        currentBackStackEntry?.destination?.route

    val screenWidth =
        LocalConfiguration.current.screenWidthDp.dp

    // Regarding TTS
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

    // Text currently prepared for whole-screen reading
    var screenTextToRead by remember {
        mutableStateOf("")
    }

    // Stores whether TTS is currently speaking
    var isTtsSpeaking by remember {
        mutableStateOf(false)
    }

    // For automatic reading if user picks ON toggle + SharedPreferences
    val prefs = context.getSharedPreferences(
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

    // Shut down the shared TTS manager when AppWithDrawer leaves composition
    DisposableEffect(Unit) {
        onDispose {
            ttsManager.shutdown()
        }
    }

    // Stores the housing-form section currently displayed.
    var currentHousingStep by remember {
        mutableIntStateOf(0)
    }

    // Stores the last SmartTextField selected by the user.
    //
    // null means that no field has been selected yet.
    // In that case, we read the first field of the current step.
    var focusedFieldId by remember {
        mutableStateOf<String?>(null)
    }

    // True only while the user is inside one of the housing-form routes.
    val isHousingAssistanceForm =
        currentRoute == "housingAssistanceForm" ||
                currentRoute == "housingAssistanceForm/{startStep}"

    // When the user leaves the housing form,
    // clear the old step and focused-field information.
    LaunchedEffect(currentRoute) {
        if (!isHousingAssistanceForm) {
            focusedFieldId = null
            currentHousingStep = 0
        }
    }

    // Observes music changes.
    // This requires SoundManager.selectedSound to be a StateFlow<String>.
    val selectedSound = SoundManager.selectedSound

    // This forces the drawer to open from RIGHT side
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {

        MaterialTheme(
            colorScheme = getContrastColorScheme(contrastMode),
            typography = getAppTypography(fontSizeMode)
        ) {

            // Main drawer component
            ModalNavigationDrawer(
                drawerState = drawerState,

                // Content of the side menu
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(screenWidth * 0.6f),
                        drawerContainerColor =
                            MaterialTheme.colorScheme.surface,
                        drawerContentColor =
                            MaterialTheme.colorScheme.onSurface
                    ) {

                        // Menu title
                        Text(
                            text = "תפריט ראשי",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Home item in menu
                        NavigationDrawerItem(
                            label = {
                                Text("דף הבית")
                            },
                            selected = currentRoute == "home",
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = "Home"
                                )
                            },
                            colors =
                                NavigationDrawerItemDefaults.colors(
                                    // Selected
                                    selectedContainerColor =
                                        MaterialTheme.colorScheme.primary,
                                    selectedTextColor =
                                        MaterialTheme.colorScheme.onPrimary,
                                    selectedIconColor =
                                        MaterialTheme.colorScheme.onPrimary,

                                    // Unselected
                                    unselectedContainerColor =
                                        MaterialTheme.colorScheme.surface,
                                    unselectedTextColor =
                                        MaterialTheme.colorScheme.onSurface,
                                    unselectedIconColor =
                                        MaterialTheme.colorScheme.onSurface
                                ),
                            onClick = {
                                // Navigate to home screen
                                innerNavController.navigate("home")

                                // Close drawer after click
                                scope.launch {
                                    drawerState.close()
                                }
                            }
                        )

                        // Additional item of profile
                        NavigationDrawerItem(
                            label = {
                                Text("ניהול חשבון")
                            },
                            selected = currentRoute == "profile",
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Profile"
                                )
                            },
                            colors =
                                NavigationDrawerItemDefaults.colors(
                                    // Selected
                                    selectedContainerColor =
                                        MaterialTheme.colorScheme.primary,
                                    selectedTextColor =
                                        MaterialTheme.colorScheme.onPrimary,
                                    selectedIconColor =
                                        MaterialTheme.colorScheme.onPrimary,

                                    // Unselected
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
                            selected = currentRoute == "Guidance",
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "User guide"
                                )
                            },
                            colors =
                                NavigationDrawerItemDefaults.colors(
                                    // Selected
                                    selectedContainerColor =
                                        MaterialTheme.colorScheme.primary,
                                    selectedTextColor =
                                        MaterialTheme.colorScheme.onPrimary,
                                    selectedIconColor =
                                        MaterialTheme.colorScheme.onPrimary,

                                    // Unselected
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
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Personal Settings"
                                )
                            },
                            colors =
                                NavigationDrawerItemDefaults.colors(
                                    // Selected
                                    selectedContainerColor =
                                        MaterialTheme.colorScheme.primary,
                                    selectedTextColor =
                                        MaterialTheme.colorScheme.onPrimary,
                                    selectedIconColor =
                                        MaterialTheme.colorScheme.onPrimary,

                                    // Unselected
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

                // Return layout direction to RTL for content
                CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl
                ) {

                    // This part takes the current uid and extracts full name
                    var userName by remember {
                        mutableStateOf("")
                    }

                    LaunchedEffect(Unit) {
                        val userId =
                            FirebaseAuth.getInstance()
                                .currentUser
                                ?.uid

                        if (userId != null) {
                            FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(userId)
                                .get()
                                .addOnSuccessListener { document ->
                                    userName =
                                        document.getString("fullName")
                                            ?: ""
                                }
                        }
                    }

                    // Scaffold = basic screen layout
                    Scaffold(

                        // Top bar that appears on ALL drawer screens
                        topBar = {
                            TopAppBar(
                                title = {
                                    Column {
                                        Text("EasyFill")

                                        if (userName.isNotEmpty()) {
                                            Text(
                                                text = "שלום, $userName",
                                                fontSize = 20.sp
                                            )
                                        }
                                    }
                                },

                                // Menu button
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
                                                // Stop active TTS.
                                                ttsManager.stop()
                                                isTtsSpeaking = false

                                            } else if (
                                                screenTextToRead.isNotBlank()
                                            ) {
                                                // Start reading only when
                                                // valid text exists.
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

                        // Helper function for either manual or auto reading
                        fun updateScreenText(text: String) {

                            // Stop text from the previous screen.
                            ttsManager.stop()
                            isTtsSpeaking = false

                            // Save the new current-screen text.
                            screenTextToRead = text

                            // Automatically read only when enabled
                            // and when the supplied text is not blank.
                            if (
                                autoReadEnabled &&
                                text.isNotBlank()
                            ) {
                                scope.launch {
                                    delay(400)
                                    ttsManager.speak(text)
                                    isTtsSpeaking = true
                                }
                            }
                        }

                        // Text used when the chatbot reads the current form field.
                        val currentFieldTextToRead =
                            if (isHousingAssistanceForm) {
                                focusedFieldId
                                    ?.let { selectedFieldId ->
                                        FieldHelpCatalog.getFieldExplanation(
                                            step = currentHousingStep,
                                            fieldId = selectedFieldId
                                        )
                                    }
                                    ?: FieldHelpCatalog.getFirstFieldExplanation(
                                        step = currentHousingStep
                                    )
                            } else {
                                ""
                            }

// Listen to the current distress scores.
                        val handScore by
                        DistressScoringManager.handScore.collectAsState()

                        val voiceScore by
                        DistressScoringManager.voiceScore.collectAsState()

                        val faceScore by
                        DistressScoringManager.faceScore.collectAsState()

                        val totalScore by
                        DistressScoringManager.totalScore.collectAsState()

                        val formBehaviorScore by
                        DistressScoringManager.formBehaviorScore.collectAsState()

                        val distressMode by
                        DistressScoringManager.mode.collectAsState()

// Combine the distress scores into the object used by the chatbot.
                        val realDistressSnapshot = DistressSnapshot(
                            globalScore = totalScore,
                            semanticTextScore = 0,
                            faceScore = faceScore,
                            voiceScore = voiceScore,
                            touchScore = handScore,
                            formBehaviorScore = formBehaviorScore
                        )

// Current application settings used by chatbot suggestions.
                        val botAppState = BotAppState(
                            isMusicPlaying = selectedSound != "none",
                            selectedSound = selectedSound,
                            isTtsSpeaking = isTtsSpeaking,
                            autoReadEnabled = autoReadEnabled,
                            fontSizeMode = fontSizeMode.name,
                            contrastMode = contrastMode.name
                        )

                        // Box places the chatbot overlay above
                        // the currently displayed navigation screen.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {

                            NavHost(
                                navController = innerNavController,
                                startDestination = "home",

                                // innerPadding is already applied
                                // by the parent Box.
                                modifier = Modifier.fillMaxSize()
                            ) {

                                composable("home") {
                                    LaunchedEffect(Unit) {
                                        updateScreenText(TtsTexts.HOME)
                                    }

                                    HomeScreen(
                                        navController =
                                            innerNavController,
                                        baselineDone = baselineDone
                                    )
                                }

                                composable("profile") {
                                    LaunchedEffect(Unit) {
                                        updateScreenText(TtsTexts.PROFILE)
                                    }

                                    ProfileScreen(
                                        navController =
                                            mainNavController,
                                        onNameUpdated = { updatedName ->
                                            userName = updatedName
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

                                composable("Personal Settings") {
                                    LaunchedEffect(Unit) {
                                        updateScreenText(
                                            TtsTexts.PERSONAL_SETTINGS
                                        )
                                    }

                                    PersonalSettingScreen(
                                        innerNavController,
                                        autoReadEnabled =
                                            autoReadEnabled,
                                        onAutoReadChange = { enabled ->

                                            autoReadEnabled = enabled

                                            prefs.edit()
                                                .putBoolean(
                                                    "auto_read_enabled",
                                                    enabled
                                                )
                                                .apply()

                                            if (
                                                enabled &&
                                                screenTextToRead.isNotBlank()
                                            ) {
                                                scope.launch {
                                                    delay(400)
                                                    ttsManager.speak(
                                                        screenTextToRead
                                                    )
                                                    isTtsSpeaking = true
                                                }
                                            } else if (!enabled) {
                                                ttsManager.stop()
                                                isTtsSpeaking = false
                                            }
                                        }
                                    )
                                }

                                composable("backgroundSounds") {
                                    LaunchedEffect(Unit) {
                                        updateScreenText(TtsTexts.SOUND)
                                    }

                                    BackgroundSoundsScreen(
                                        innerNavController
                                    )
                                }

                                composable("contrastSettings") {
                                    LaunchedEffect(Unit) {
                                        updateScreenText(
                                            TtsTexts.CONTRAST
                                        )
                                    }

                                    ContrastSettingsScreen(
                                        selectedMode = contrastMode,
                                        onModeSelected = {
                                            contrastMode = it
                                        },
                                        navController =
                                            innerNavController
                                    )
                                }

                                composable("fontSizeSettings") {
                                    LaunchedEffect(Unit) {
                                        updateScreenText(
                                            TtsTexts.FONT_SIZE
                                        )
                                    }

                                    FontSizeSettingsScreen(
                                        selectedMode = fontSizeMode,
                                        onModeSelected = {
                                            fontSizeMode = it
                                        },
                                        navController =
                                            innerNavController
                                    )
                                }

                                // Screen of audio baseline
                                composable("baselineVoice") {

                                    // Updates the text used by
                                    // the top-bar reading button.
                                    LaunchedEffect(Unit) {
                                        updateScreenText(
                                            TtsTexts.BASELINE_VOICE
                                        )
                                    }

                                    BaselineVoiceScreen(
                                        speechManager = speechManager,
                                        onBaselineFinished = {
                                            baselineDone = true

                                            innerNavController.navigate(
                                                "uploadPdf"
                                            ) {
                                                popUpTo(
                                                    "baselineVoice"
                                                ) {
                                                    inclusive = true
                                                }
                                            }
                                        }
                                    )
                                }

                                // Navigate to screen upload file
                                // to extract data from it
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

                                // Navigate to form options screen
                                composable("demoFormOptions") {
                                    LaunchedEffect(Unit) {
                                        updateScreenText(
                                            TtsTexts.FORM_OPTIONS
                                        )
                                    }

                                    DemoFormsOptions(
                                        navController =
                                            innerNavController
                                    )
                                }

                                // Navigate to the housing-assistance form from the first section.
                                composable("housingAssistanceForm") {
                                    HousingAssistanceFormScreen(
                                        navController = innerNavController,
                                        startStep = 0,

                                        // Pass the live distress scores to the chatbot
                                        // displayed inside HousingAssistanceFormScreen.
                                        distressSnapshot = realDistressSnapshot,

                                        // Pass the current distress-analysis mode.
                                        distressMode = distressMode,

                                        // Pass the current music, TTS, font and contrast settings.
                                        botAppState = botAppState,

                                        // Handle chatbot button actions in AppNavigation,
                                        // where the TTS and app-setting state are stored.
                                        onBotAction = { action ->
                                            BotSupportActionHandler.handle(
                                                action = action,
                                                context = context,
                                                ttsManager = ttsManager,

                                                // Text representing the complete current form section.
                                                screenTextToRead = screenTextToRead,

                                                // Explanation of the currently selected form field.
                                                currentFieldTextToRead = currentFieldTextToRead,

                                                onTtsSpeakingChange = { speaking ->
                                                    isTtsSpeaking = speaking
                                                },

                                                onContrastModeChange = { newMode ->
                                                    contrastMode = newMode
                                                },

                                                onFontSizeModeChange = { newMode ->
                                                    fontSizeMode = newMode
                                                }
                                            )
                                        },

                                        // Receives the new form step whenever
                                        // the user presses Continue or Back.
                                        onStepChanged = { newStep ->

                                            // Store the current step for FieldHelpCatalog.
                                            currentHousingStep = newStep

                                            // The previously focused field belongs
                                            // to the previous section.
                                            focusedFieldId = null

                                            // Update the text used by whole-screen TTS.
                                            updateScreenText(
                                                TtsTexts.getHousingAssistanceStepText(newStep)
                                            )
                                        },

                                        // Receives the field ID reported by SmartTextField.
                                        onFocusedFieldChange = { fieldId ->
                                            focusedFieldId = fieldId
                                        }
                                    )
                                }

                                composable(
                                    "housingAssistanceForm/{startStep}"
                                ) { backStackEntry ->

                                    // Reads the requested starting step from the navigation route.
                                    val startStep =
                                        backStackEntry.arguments
                                            ?.getString("startStep")
                                            ?.toIntOrNull()
                                            ?: 0

                                    HousingAssistanceFormScreen(
                                        navController = innerNavController,
                                        startStep = startStep,

                                        // Pass the live distress information to the chatbot.
                                        distressSnapshot = realDistressSnapshot,

                                        // Pass the current distress mode.
                                        distressMode = distressMode,

                                        // Pass the current app settings used by chatbot suggestions.
                                        botAppState = botAppState,

                                        // Handle chatbot actions in AppNavigation.
                                        onBotAction = { action ->
                                            BotSupportActionHandler.handle(
                                                action = action,
                                                context = context,
                                                ttsManager = ttsManager,
                                                screenTextToRead = screenTextToRead,
                                                currentFieldTextToRead = currentFieldTextToRead,

                                                onTtsSpeakingChange = { speaking ->
                                                    isTtsSpeaking = speaking
                                                },

                                                onContrastModeChange = { newMode ->
                                                    contrastMode = newMode
                                                },

                                                onFontSizeModeChange = { newMode ->
                                                    fontSizeMode = newMode
                                                }
                                            )
                                        },

                                        // Receives the current form section.
                                        onStepChanged = { newStep ->
                                            currentHousingStep = newStep

                                            // Clear the field selected in the previous section.
                                            focusedFieldId = null

                                            updateScreenText(
                                                TtsTexts.getHousingAssistanceStepText(newStep)
                                            )
                                        },

                                        // Receives the field selected by SmartTextField.
                                        onFocusedFieldChange = { fieldId ->
                                            focusedFieldId = fieldId
                                        }
                                    )
                                }

                                // View progress forms
                                composable("myFormsProgress") {
                                    LaunchedEffect(Unit) {
                                        updateScreenText(
                                            TtsTexts.FORMS_PROGRESS
                                        )
                                    }

                                    MyFormsProgressScreen(
                                        navController =
                                            innerNavController
                                    )
                                }

                                // Guidance slides
                                composable("guidanceSlides") {
                                    LaunchedEffect(Unit) {
                                        updateScreenText(
                                            TtsTexts.GUIDANCE_SLIDES
                                        )
                                    }

                                    GuidanceSlidesScreen(
                                        navController =
                                            innerNavController
                                    )
                                }
                            }

                        }
                    }
                }
            }
        }
    }
}