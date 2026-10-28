/*
 * Project Idle RPG - Idle Bones
 * Tela de Profissoes: lista as 16 skills e permite iniciar a acao de coleta.
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
import androidx.compose.foundation.layout.width
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
import com.idlebones.game.data.generator.GameDatabaseGenerator
import com.idlebones.game.data.models.SkillType
import com.idlebones.game.data.save.GameState
import com.idlebones.game.engine.GameEngine
import com.idlebones.game.ui.components.StatBar
import com.idlebones.game.ui.theme.GameColors

private val GATHERING_SKILLS = listOf(
    SkillType.WOODCUTTING, SkillType.MINING, SkillType.FISHING,
    SkillType.FIREMAKING, SkillType.SMITHING, SkillType.FLETCHING,
    SkillType.RUNECRAFTING, SkillType.COOKING, SkillType.ALCHEMY,
    SkillType.PRAYER, SkillType.AGILITY, SkillType.ATTACK,
    SkillType.STRENGTH, SkillType.DEFENSE, SkillType.RANGED, SkillType.MAGIC
)

@Composable
fun ProfessionsScreen(gameEngine: GameEngine) {
    val state by gameEngine.gameState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Profiss\u00f5es",
            color = GameColors.Gold,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (state.activeSkill != null)
                "Coletando: ${skillDisplayName(SkillType.valueOf(state.activeSkill!!))}"
            else "Nenhuma atividade em andamento",
            color = GameColors.TextSecondary,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(GATHERING_SKILLS) { skill ->
                ProfessionRow(gameEngine, state, skill)
            }
        }
    }
}

@Composable
private fun ProfessionRow(gameEngine: GameEngine, state: GameState, skill: SkillType) {
    val (level, xp) = remember(state.playerStats, skill) { levelXp(state.playerStats, skill) }
    val xpForCurrent = remember(level) { GameDatabaseGenerator.xpForLevel(level) }
    val xpForNext = remember(level) { GameDatabaseGenerator.xpForLevel(level + 1) }
    val span = (xpForNext - xpForCurrent).coerceAtLeast(1L)
    val progress = ((xp - xpForCurrent).coerceAtLeast(0L)).toFloat() / span.toFloat()
    val isActive = state.activeSkill == skill.name

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(
                1.dp,
                if (isActive) GameColors.Gold else GameColors.IronDark,
                RoundedCornerShape(8.dp)
            )
            .background(if (isActive) GameColors.GoldDim.copy(alpha = 0.12f) else GameColors.Surface)
            .clickable {
                if (isActive) gameEngine.stopActions() else gameEngine.selectSkill(skill)
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = skillDisplayName(skill),
                    color = if (isActive) GameColors.Gold else GameColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Nv. $level",
                    color = GameColors.Gold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(4.dp))
            StatBar(
                fraction = progress,
                valueText = "$xp / $xpForNext XP",
                color = GameColors.XpGreen,
                height = 10
            )
        }
        if (isActive) {
            Spacer(Modifier.width(10.dp))
            Text(text = "\u25CF", color = GameColors.Gold, fontSize = 18.sp)
        }
    }
}
