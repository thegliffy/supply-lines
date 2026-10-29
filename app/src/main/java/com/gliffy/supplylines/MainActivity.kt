package com.gliffy.supplylines

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gliffy.supplylines.ui.GameScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: GameViewModel = viewModel(
                factory = viewModelFactory { initializer { GameViewModel(getApplication()) } }
            )
            GameScreen(vm)
        }
    }
}
