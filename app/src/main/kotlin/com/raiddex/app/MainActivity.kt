package com.raiddex.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import android.os.Bundle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface { Root(vm) }
            }
        }
    }
}

@Composable
private fun Root(vm: MainViewModel) {
    BackHandler(enabled = vm.screen != Screen.Collection) { vm.screen = Screen.Collection }
    when (val s = vm.screen) {
        Screen.Collection -> CollectionScreen(vm)
        is Screen.Edit -> EditScreen(vm, s.id)
        Screen.Team -> TeamScreen(vm)
        Screen.Settings -> SettingsScreen(vm)
    }
}
