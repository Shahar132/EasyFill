package com.example.easyfill_project

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*

// Compose state & runtime
import androidx.compose.runtime.*

// Modifier for layout control
import androidx.compose.ui.Modifier


//for chatbot ui
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.example.easyfill_project.chatbot.ui.FloatingChatOverlay


//for chatbot navigation
import com.example.easyfill_project.chatbot.model.BotAction
import com.example.easyfill_project.screen.SoundManager

import com.example.easyfill_project.chatbot.model.DistressSnapshot
import com.example.easyfill_project.chatbot.model.BotAppState


// Needed to force RTL (right-to-left) for right-side drawer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Navigation
import androidx.navigation.NavHostController
import androidx.navigation.compose.*

// Your screens
import com.example.easyfill_project.screen.AuthScreen
import com.example.easyfill_project.screen.BackgroundSoundsScreen
import com.example.easyfill_project.screen.ContrastSettingsScreen
import com.example.easyfill_project.screen.EasyFillMainScreen
import com.example.easyfill_project.screen.FontSizeSettingsScreen
import com.example.easyfill_project.screen.GuidanceScreen
import com.example.easyfill_project.screen.HomeScreen
import com.example.easyfill_project.screen.PersonalSettingScreen
import com.example.easyfill_project.screen.ProfileScreen

import kotlinx.coroutines.launch
import com.example.easyfill_project.screen.ContrastMode
import com.example.easyfill_project.screen.getContrastColorScheme
import com.example.easyfill_project.screen.FontSizeMode
import com.example.easyfill_project.screen.RegisterScreen
import com.example.easyfill_project.screen.UploadPdfScreen
import com.example.easyfill_project.screen.getAppTypography
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

//imports regarding the TTS
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.VolumeUp
import com.example.easyfill_project.forms_screens.DemoFormsOptions
import com.example.easyfill_project.forms_screens.HousingAssistanceFormScreen
import com.example.easyfill_project.screen.MyFormsProgressScreen
import com.example.easyfill_project.texttospeech.TextToSpeechManager
import com.example.easyfill_project.texttospeech.TtsTexts
//for delay
import kotlinx.coroutines.delay

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
            //when user login then go to route app that opens the home screen as first destination +side menu(inner navHost)
            //"app" = opens the layout with drawer
            AppWithDrawer(navController)
        }
    }
}


// This function wraps screens WITH the side drawer
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppWithDrawer(mainNavController: NavHostController) {

    // Controls whether drawer is open or closed
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // Coroutine scope required for drawer actions (open/close)
    val scope = rememberCoroutineScope()

    // This controls only screens INSIDE the app area
    val innerNavController = rememberNavController()

    var contrastMode by remember { mutableStateOf(ContrastMode.DEFAULT) }
    //for font size
    var fontSizeMode by remember { mutableStateOf(FontSizeMode.NORMAL) }

    // for marking the current screen
    val currentBackStackEntry by innerNavController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    //regarding tts
    val context = LocalContext.current
    val ttsManager = remember { TextToSpeechManager(context) }

    var screenTextToRead by remember { mutableStateOf("") }
    var isTtsSpeaking by remember { mutableStateOf(false) }


    //for automatic reading if user pick ON toggle+shared prefernce
    val prefs = context.getSharedPreferences("user_settings", Context.MODE_PRIVATE)

    var autoReadEnabled by remember {
        mutableStateOf(prefs.getBoolean("auto_read_enabled", false))
    }

    DisposableEffect(Unit) {
        onDispose {
            ttsManager.shutdown()
        }
    }

    // This forces the drawer to open from RIGHT side
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {

        MaterialTheme(//theme here
            colorScheme = getContrastColorScheme(contrastMode) ,
                    typography = getAppTypography(fontSizeMode)
        ) {

            // Main drawer component
            ModalNavigationDrawer(
                drawerState = drawerState,

                // Content of the side menu
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(screenWidth * 0.6f),
                        drawerContainerColor = MaterialTheme.colorScheme.surface,
                        drawerContentColor = MaterialTheme.colorScheme.onSurface
                    ) {

                        // Menu title
                        Text(
                            text = "תפריט ראשי",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Home item in menu
                        NavigationDrawerItem(
                            label = { Text("דף הבית") },
                            selected = currentRoute == "home",
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = "Home"
                                )
                            },
                            colors = NavigationDrawerItemDefaults.colors(//defines colors when item menu is pressed/not
                                // Selected (when pressed)
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,

                                // Unselected
                                unselectedContainerColor = MaterialTheme.colorScheme.surface,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurface
                            ),
                            onClick = {
                                // Navigate to home screen
                                innerNavController.navigate("home")

                                // Close drawer after click
                                scope.launch { drawerState.close() }
                            }
                        )

                        // additional item of profile
                        NavigationDrawerItem(
                            label = { Text("ניהול חשבון")},
                            selected = currentRoute == "profile",
                            // it means mark down only if this is the current route
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Profile"
                                )
                            },
                            colors = NavigationDrawerItemDefaults.colors(//defines colors when item menu is pressed/not
                                // Selected (when pressed)
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,

                                // Unselected
                                unselectedContainerColor = MaterialTheme.colorScheme.surface,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurface
                            ),
                            onClick = {
                                innerNavController.navigate("profile")
                                scope.launch { drawerState.close() }
                            }
                        )

                        NavigationDrawerItem(
                            label = { Text("מדריך למשתמש") },
                            selected = currentRoute == "Guidance",
                            // mark only if current route is Guidance
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "User guide"
                                )
                            },
                            colors = NavigationDrawerItemDefaults.colors(//defines colors when item menu is pressed/not
                                // Selected (when pressed)
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,

                                // Unselected
                                unselectedContainerColor = MaterialTheme.colorScheme.surface,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurface
                            ),
                            onClick = {
                                innerNavController.navigate("Guidance")
                                scope.launch { drawerState.close() }
                            }
                        )

                        NavigationDrawerItem(
                            label = { Text("התאמה אישית")},
                            selected = currentRoute == "Personal Settings",
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Personal Settings"
                                )
                            },
                            colors = NavigationDrawerItemDefaults.colors(//defines colors when item menu is pressed/not
                                // Selected (when pressed)
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,

                                // Unselected
                                unselectedContainerColor = MaterialTheme.colorScheme.surface,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurface
                            ),

                            onClick = {
                                innerNavController.navigate("Personal Settings")
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                }
            ) {

                // Return layout direction to normal (LTR) for content
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {

                    //this part is taking the current uid and extract full name
                    var userName by remember { mutableStateOf("") }

                    LaunchedEffect(Unit) {
                        val userId = FirebaseAuth.getInstance().currentUser?.uid

                        if (userId != null) {
                            FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(userId)
                                .get()
                                .addOnSuccessListener { document ->
                                    userName = document.getString("fullName") ?: ""
                                }
                        }
                    }

                    // Scaffold = basic screen layout (top bar, content, etc.)
                    Scaffold(
                        // about the colors - > scaffold automatically check background color

                        // Top bar that appears on ALL drawer screens
                        topBar = {
                            TopAppBar(
                                // automatic colors as well takes surface

                                // App title at the upper bar and name for user
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
                                // Menu button (top-left visually, but opens RIGHT drawer)
                                navigationIcon = {
                                    IconButton(
                                        onClick = {
                                            // Open the drawer
                                            scope.launch { drawerState.open() }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Menu,
                                            contentDescription = "Open menu"
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
                                                ttsManager.speak(screenTextToRead)
                                                isTtsSpeaking = true
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isTtsSpeaking) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                            contentDescription = if (isTtsSpeaking) "עצירת הקראה" else "הקראת טקסט"
                                        )
                                    }
                                }

                            )
                        }
                    ) { innerPadding ->

                        //helper function for ether manual or auto
                        fun updateScreenText(text: String) {
                            ttsManager.stop()
                            isTtsSpeaking = false

                            screenTextToRead = text

                            if (autoReadEnabled) {//for auto reading
                                scope.launch {
                                    delay(400)//delay
                                    ttsManager.speak(text)
                                    isTtsSpeaking = true
                                }
                            }
                        }



                        Box(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize()
                        ) {
                            NavHost(
                                navController = innerNavController,
                                startDestination = "home",
                                modifier = Modifier.fillMaxSize()
                            ) {
                                composable("home") {
                                    LaunchedEffect(Unit) {
                                        updateScreenText(TtsTexts.HOME)
                                    }

                                    HomeScreen(innerNavController)
                                }

                                composable("profile") {
                                    LaunchedEffect(Unit) {
                                        updateScreenText(TtsTexts.PROFILE)
                                    }

                                    ProfileScreen(
                                        navController = mainNavController,
                                        onNameUpdated = { updatedName ->
                                            userName = updatedName
                                        }
                                    )
                                }

                                composable("Guidance") {
                                    LaunchedEffect(Unit) {
                                        updateScreenText(TtsTexts.GUIDANCE)
                                    }

                                    GuidanceScreen()
                                }

                                composable("Personal Settings") {
                                    LaunchedEffect(Unit) {
                                        updateScreenText(TtsTexts.PERSONAL_SETTINGS)
                                    }

                                    PersonalSettingScreen(
                                        innerNavController,
                                        autoReadEnabled = autoReadEnabled,
                                        onAutoReadChange = { enabled ->
                                            autoReadEnabled = enabled

                                            if (enabled) {
                                                scope.launch {
                                                    delay(400)
                                                    ttsManager.speak(screenTextToRead)
                                                    isTtsSpeaking = true
                                                }
                                            } else {
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

                                    BackgroundSoundsScreen(innerNavController)
                                }

                                composable("contrastSettings") {
                                    LaunchedEffect(Unit) {
                                        updateScreenText(TtsTexts.CONTRAST)
                                    }

                                    ContrastSettingsScreen(
                                        selectedMode = contrastMode,
                                        onModeSelected = { contrastMode = it },
                                        navController = innerNavController
                                    )
                                }

                                composable("fontSizeSettings") {
                                    LaunchedEffect(Unit) {
                                        updateScreenText(TtsTexts.FONT_SIZE)
                                    }

                                    FontSizeSettingsScreen(
                                        selectedMode = fontSizeMode,
                                        onModeSelected = { fontSizeMode = it },
                                        navController = innerNavController
                                    )
                                }

                                composable("uploadPdf") {
                                    LaunchedEffect(Unit) {
                                        updateScreenText(TtsTexts.UPLOAD_PDF)
                                    }

                                    UploadPdfScreen(navController = innerNavController)
                                }

                                composable("demoFormOptions") {
                                    LaunchedEffect(Unit) {
                                        updateScreenText(TtsTexts.FORM_OPTIONS)
                                    }

                                    DemoFormsOptions(navController = innerNavController)
                                }

                                composable("housingAssistanceForm") {
                                    HousingAssistanceFormScreen(
                                        navController = innerNavController,
                                        startStep = 0
                                    )
                                }

                                composable("housingAssistanceForm/{startStep}") { backStackEntry ->
                                    val startStep = backStackEntry.arguments
                                        ?.getString("startStep")
                                        ?.toIntOrNull()
                                        ?: 0

                                    HousingAssistanceFormScreen(
                                        navController = innerNavController,
                                        startStep = startStep
                                    )
                                }

                                composable("myFormsProgress") {
                                    MyFormsProgressScreen(navController = innerNavController)
                                }

                                composable("bankDetailsForm") {
                                    // BankDetailsFormScreen(navController)
                                }
                            }










                            // for chatbot
                            val testDistressSnapshot = DistressSnapshot(
                                globalScore = 65,
                                semanticTextScore = 0,
                                faceScore = 0,
                                voiceScore = 0,
                                touchScore = 0,
                                formBehaviorScore = 75
                            )

                            val shouldAutoOpenChat =
                                testDistressSnapshot.globalScore >= 60 ||
                                        testDistressSnapshot.formBehaviorScore >= 70 ||
                                        testDistressSnapshot.semanticTextScore >= 70

                            val botAppState = BotAppState(
                                isMusicPlaying = SoundManager.selectedSound != "none",
                                selectedSound = SoundManager.selectedSound,
                                isTtsSpeaking = isTtsSpeaking,
                                autoReadEnabled = autoReadEnabled,
                                fontSizeMode = fontSizeMode.name,
                                contrastMode = contrastMode.name
                            )

                            FloatingChatOverlay(
                                currentScreen = currentRoute ?: "לא ידוע",
                                autoOpenOnDistress = shouldAutoOpenChat,
                                distressSnapshot = testDistressSnapshot,
                                appState = botAppState,
                                onBotAction = { action ->
                                    when (action) {

                                        BotAction.ReadAloud -> {
                                            ttsManager.speak(screenTextToRead)
                                            isTtsSpeaking = true
                                        }

                                        BotAction.StopReading -> {
                                            ttsManager.stop()
                                            isTtsSpeaking = false
                                        }

                                        BotAction.EnableAutoRead -> {
                                            autoReadEnabled = true

                                            prefs.edit()
                                                .putBoolean("auto_read_enabled", true)
                                                .apply()

                                            ttsManager.speak(screenTextToRead)
                                            isTtsSpeaking = true
                                        }

                                        BotAction.DisableAutoRead -> {
                                            autoReadEnabled = false

                                            prefs.edit()
                                                .putBoolean("auto_read_enabled", false)
                                                .apply()

                                            ttsManager.stop()
                                            isTtsSpeaking = false
                                        }

                                        BotAction.OpenPersonalSettings -> {
                                            innerNavController.navigate("Personal Settings")
                                        }

                                        BotAction.OpenContrastSettings -> {
                                            innerNavController.navigate("contrastSettings")
                                        }

                                        BotAction.OpenFontSizeSettings -> {
                                            innerNavController.navigate("fontSizeSettings")
                                        }

                                        BotAction.OpenBackgroundSounds -> {
                                            innerNavController.navigate("backgroundSounds")
                                        }

                                        is BotAction.PlaySound -> {
                                            SoundManager.play(
                                                context = context,
                                                soundName = action.option.key,
                                                soundRes = action.option.soundRes
                                            )
                                        }

                                        BotAction.StopBackgroundMusic -> {
                                            SoundManager.stop()
                                        }

                                        is BotAction.SetContrast -> {
                                            contrastMode = action.option.mode
                                        }

                                        is BotAction.SetFontSize -> {
                                            fontSizeMode = action.option.mode
                                        }

                                        BotAction.None -> Unit
                                    }
                                }
                            )
                        }
                    }







                }
            }
        }
    }

}

