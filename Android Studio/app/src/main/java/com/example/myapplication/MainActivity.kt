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
import com.example.myapplication.analysis.AnalysisScreen
import com.example.myapplication.analysis.SavedSessionsScreen
import com.example.myapplication.detection.VideoDetectionScreen
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!OpenCVLoader.initLocal()) {
            android.util.Log.e("OPENCV", "OpenCV failed to initialise")
        } else {
            android.util.Log.d("OPENCV", "OpenCV initialised successfully")
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()

                NavHost(navController, startDestination = "home") {
                    // Home screen
                    composable("home") {
                        HomeScreen(
                            navController = navController,
                            onViewSessionsClick = {
                                navController.navigate("saved_sessions")
                            }
                        )
                    }

                    // Video processing screen (shows progress while loading)
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

                    // Video detection screen (plays video with YOLO detection overlay)
                    composable(
                        route = "detection/{videoUri}",
                        arguments = listOf(
                            navArgument("videoUri") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val videoUri = backStackEntry.arguments?.getString("videoUri") ?: ""
                        VideoDetectionScreen(
                            videoUriString = videoUri,
                            navController = navController
                        )
                    }

                    // Analysis screen — receives angles as comma-separated string
                    composable(
                        route = "analysis/{angles}/{shotType}",
                        arguments = listOf(
                            navArgument("angles") { type = NavType.StringType },
                            navArgument("shotType") {
                                type = NavType.StringType
                                defaultValue = "free_throw"
                            }
                        )
                    ) { backStackEntry ->
                        val anglesString = backStackEntry.arguments?.getString("angles") ?: ""
                        val shotType = backStackEntry.arguments?.getString("shotType") ?: "free_throw"
                        val angles = anglesString.split(",").mapNotNull { it.toDoubleOrNull() }
                        AnalysisScreen(
                            shotAngles = angles,
                            navController = navController,
                            initialShotType = shotType
                        )
                    }

                    // Saved sessions screen
                    composable("saved_sessions") {
                        SavedSessionsScreen(navController = navController)
                    }
                }
            }
        }
    }
}
