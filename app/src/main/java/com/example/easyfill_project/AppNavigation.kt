package com.example.easyfill_project

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*

// Compose state & runtime
import androidx.compose.runtime.*

// Modifier for layout control
import androidx.compose.ui.Modifier

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

        // From here → screens with drawer
        composable("app") {
            //when user login then go to route app that opens the home screen as first destination +side menu(inner navHost)
            //"app" = opens the layout with drawer
            AppWithDrawer()
        }
    }
}


// This function wraps screens WITH the side drawer
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppWithDrawer() {

    // Controls whether drawer is open or closed
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // Coroutine scope required for drawer actions (open/close)
    val scope = rememberCoroutineScope()

    // This controls only screens INSIDE the app area
    val innerNavController = rememberNavController()

    var contrastMode by remember { mutableStateOf(ContrastMode.DEFAULT) }

    // for marking the current screen
    val currentBackStackEntry by innerNavController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    // This forces the drawer to open from RIGHT side
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {

        MaterialTheme(//theme here
            colorScheme = getContrastColorScheme(contrastMode)
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
                            label = { Text("דף הבית",
                                color = MaterialTheme.colorScheme.onSurface
                                ) },
                            selected = currentRoute == "home",
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = "Home"
                                )
                            },
                            onClick = {
                                // Navigate to home screen
                                innerNavController.navigate("home")

                                // Close drawer after click
                                scope.launch { drawerState.close() }
                            }
                        )

                        // additional item of profile
                        NavigationDrawerItem(
                            label = { Text("ניהול חשבון",
                                color = MaterialTheme.colorScheme.onSurface) },
                            selected = currentRoute == "profile",
                            // it means mark down only if this is the current route
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Profile"
                                )
                            },
                            onClick = {
                                innerNavController.navigate("profile")
                                scope.launch { drawerState.close() }
                            }
                        )

                        NavigationDrawerItem(
                            label = { Text("מדריך למשתמש",
                                color = MaterialTheme.colorScheme.onSurface) },
                            selected = currentRoute == "Guidance",
                            // mark only if current route is Guidance
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "User guide"
                                )
                            },
                            onClick = {
                                innerNavController.navigate("Guidance")
                                scope.launch { drawerState.close() }
                            }
                        )

                        NavigationDrawerItem(
                            label = { Text("התאמה אישית",
                                color = MaterialTheme.colorScheme.onSurface) },
                            selected = currentRoute == "Personal Settings",
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Personal Settings"
                                )
                            },
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

                    // Scaffold = basic screen layout (top bar, content, etc.)
                    Scaffold(
                        // about the colors - > scaffold automatically check background color

                        // Top bar that appears on ALL drawer screens
                        topBar = {
                            TopAppBar(
                                // automatic colors as well takes surface

                                // App title
                                title = { Text("EasyFill") },

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
                                }
                            )
                        }
                    ) { innerPadding ->

                        NavHost(
                            navController = innerNavController,
                            startDestination = "home",
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            composable("home") {
                                HomeScreen()
                            }

                            composable("profile") {
                                ProfileScreen()
                            }

                            composable("Guidance") {
                                GuidanceScreen()
                            }

                            composable("Personal Settings") {
                                PersonalSettingScreen(innerNavController)
                            }

                            composable("backgroundSounds") {
                                BackgroundSoundsScreen()
                            }

                            composable("contrastSettings") {
                                ContrastSettingsScreen(
                                    selectedMode = contrastMode,
                                    onModeSelected = { contrastMode = it }
                                )
                            }

                            composable("fontSizeSettings") {
                                FontSizeSettingsScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}