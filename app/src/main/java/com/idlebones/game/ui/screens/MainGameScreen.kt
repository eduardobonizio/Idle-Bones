/*
 * Project Idle RPG - Idle Bones
 * Tela principal: Scaffold + barra de navegacao inferior (5 abas).
 */
package com.idlebones.game.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idlebones.game.engine.GameEngine
import com.idlebones.game.ui.theme.GameColors

/** Abas do jogo. */
enum class GameTab(val title: String, val icon: String) {
    COMBAT("Batalha", "\u2694"),
    PROFESSIONS("Of\u00edcios", "\u26CF"),
    INVENTORY("Ba\u00fa", "\uD83C\uDF92"),
    CHARACTER("Her\u00f3i", "\uD83D\uDEE1"),
    CITY("Cidade", "\uD83C\uDFF0")
}

@Composable
fun MainGameScreen(gameEngine: GameEngine) {
    var selectedTab by remember { mutableStateOf(GameTab.COMBAT) }

    Scaffold(
        containerColor = GameColors.Background,
        bottomBar = {
            BottomNavBar(selected = selectedTab, onSelect = { selectedTab = it })
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                GameTab.COMBAT -> CombatScreen(gameEngine)
                GameTab.PROFESSIONS -> ProfessionsScreen(gameEngine)
                GameTab.INVENTORY -> InventoryScreen(gameEngine)
                GameTab.CHARACTER -> CharacterScreen(gameEngine)
                GameTab.CITY -> CityScreen(gameEngine)
            }
        }
    }
}

@Composable
private fun BottomNavBar(selected: GameTab, onSelect: (GameTab) -> Unit) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(GameColors.GoldDim.copy(alpha = 0.5f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(GameColors.Surface)
                .navigationBarsPadding()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GameTab.entries.forEach { tab ->
                NavButton(tab = tab, selected = tab == selected, onClick = { onSelect(tab) })
            }
        }
    }
}

@Composable
private fun NavButton(tab: GameTab, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) GameColors.Gold else GameColors.TextSecondary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Text(text = tab.icon, fontSize = 20.sp)
        Text(
            text = tab.title,
            color = tint,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
