package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.app.ui.home.HomeScreen
import com.example.app.ui.processing.VideoProcessingScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()

                NavHost(navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(navController = navController)
                    }

                    composable(
                        route = "processing/{videoUri}",
                        arguments = listOf(
                            navArgument("videoUri") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val videoUri = backStackEntry.arguments?.getString("videoUri") ?: ""
                        VideoProcessingScreen(
                            videoUriString = videoUri,
                            navController = navController
                        )
                    }
                }
            }
        }
    }
}