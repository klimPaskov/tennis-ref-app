package com.klim.tennisrefapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.klim.tennisrefapp.ui.navigation.TennisRefNavGraph
import com.klim.tennisrefapp.ui.theme.TennisRefTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TennisRefTheme {
                TennisRefNavGraph()
            }
        }
    }
}
