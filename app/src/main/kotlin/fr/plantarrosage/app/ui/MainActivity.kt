package fr.plantarrosage.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import fr.plantarrosage.app.PlantArrosageApplication
import fr.plantarrosage.app.di.LocalAppContainer
import fr.plantarrosage.app.ui.navigation.PlantArrosageNavHost
import fr.plantarrosage.app.ui.theme.PlantArrosageTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as PlantArrosageApplication).container

        setContent {
            PlantArrosageTheme {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        PlantArrosageNavHost(container = container)
                    }
                }
            }
        }
    }
}
