/*
 * Project Idle RPG - Idle Bones
 */
package com.idlebones.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.idlebones.game.engine.GameEngine
import com.idlebones.game.ui.screens.MainGameScreen
import com.idlebones.game.ui.theme.GameColors
import com.idlebones.game.ui.theme.IdleBonesTheme

class MainActivity : ComponentActivity() {

    private lateinit var gameEngine: GameEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cycle-safe: usa o CoroutineScope do lifecycle da Activity.
        gameEngine = GameEngine(applicationContext, lifecycleScope)

        setContent {
            IdleBonesTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = GameColors.Background
                ) {
                    MainGameScreen(gameEngine)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Registra o timestamp real da pausa e salva (RAM + disco).
        gameEngine.forceSave()
    }

    override fun onStop() {
        super.onStop()
        // Grava o timestamp tambem ao ir para background / tela travada.
        gameEngine.forceSave()
    }

    override fun onResume() {
        super.onResume()
        // Ao voltar sem recriar o processo, aplica o catch-up offline.
        gameEngine.resumeFromBackground()
    }

    override fun onDestroy() {
        gameEngine.shutdown()
        super.onDestroy()
    }
}