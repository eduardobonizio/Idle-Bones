/*
 * Project Idle RPG - Idle Bones
 * Tela de Personagem: as 16 habilidades + pets obtidos e pet ativo.
 */
package com.idlebones.game.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idlebones.game.data.generator.GameDatabase
import com.idlebones.game.data.generator.GameDatabaseGenerator
import com.idlebones.game.data.models.SkillType
import com.idlebones.game.data.save.GameState
import com.idlebones.game.data.save.PetData
import com.idlebones.game.data.save.PlayerStatsData
import com.idlebones.game.engine.GameEngine
import com.idlebones.game.ui.components.SectionHeader
import com.idlebones.game.ui.components.StatBar
import com.idlebones.game.ui.theme.GameColors

@Composable
fun CharacterScreen(gameEngine: GameEngine) {
    val state by gameEngine.gameState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                text = "Herói",
                color = GameColors.Gold,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
        item { SectionHeader("Habilidades") }
        items(SkillType.entries.toList()) { skill ->
            SkillRow(state, skill)
        }
        item { SectionHeader("Pets") }
        item { ActivePetCard(state) }
        items(state.pets) { pet ->
            PetRow(pet, isActive = state.activePet?.id == pet.id) {
                gameEngine.setActivePet(pet.id)
            }
        }
        if (state.pets.isEmpty()) {
            item {
                Text(
                    text = "Nenhum pet obtido. Raríssimos (<0.09%) ao coletar ou lutar.",
                    color = GameColors.TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun SkillRow(state: GameState, skill: SkillType) {
    val (level, xp) = remember(state.playerStats, skill) { levelXp(state.playerStats, skill) }
    val xpForCurrent = remember(level) { GameDatabaseGenerator.xpForLevel(level) }
    val xpForNext = remember(level) { GameDatabaseGenerator.xpForLevel(level + 1) }
    val span = (xpForNext - xpForCurrent).coerceAtLeast(1L)
    val progress = ((xp - xpForCurrent).coerceAtLeast(0L)).toFloat() / span.toFloat()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, GameColors.IronDark, RoundedCornerShape(6.dp))
            .background(GameColors.Surface)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = skillDisplayName(skill), color = GameColors.TextPrimary, fontSize = 14.sp)
                Text(text = "Nv. $level", color = GameColors.Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(3.dp))
            StatBar(
                fraction = progress,
                valueText = "$xp / $xpForNext XP",
                color = GameColors.XpGreen,
                height = 8
            )
        }
    }
}

@Composable
private fun ActivePetCard(state: GameState) {
    val active = state.activePet
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, GameColors.Gold.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .background(GameColors.GoldDim.copy(alpha = 0.12f))
            .padding(10.dp)
    ) {
        Column {
            Text(text = "Pet Ativo", color = GameColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                text = active?.name ?: "Nenhum",
                color = GameColors.TextPrimary,
                fontSize = 14.sp
            )
            if (active != null) {
                Text(text = active.bonusDescription, color = GameColors.TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PetRow(pet: PetData, isActive: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(
                1.dp,
                if (isActive) GameColors.Gold else GameColors.IronDark,
                RoundedCornerShape(6.dp)
            )
            .background(GameColors.Surface)
            .clickable { onClick() }
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = if (pet.isDiscovered) pet.name else "[Pet Sombrio]",
                color = GameColors.TextPrimary,
                fontSize = 14.sp
            )
            Text(text = pet.bonusDescription, color = GameColors.TextSecondary, fontSize = 11.sp)
        }
        Text(
            text = if (isActive) "ATIVO" else "Selecionar",
            color = GameColors.Gold,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
