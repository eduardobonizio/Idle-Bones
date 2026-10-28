/*
 * Project Idle RPG - Idle Bones
 * Tela de Combate: zona, monstro ativo, HP em tempo real, gold e log.
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idlebones.game.data.generator.DiscoveryManager
import com.idlebones.game.data.generator.GameDatabase
import com.idlebones.game.data.generator.Monster
import com.idlebones.game.data.save.GameState
import com.idlebones.game.engine.GameEngine
import com.idlebones.game.ui.components.GameCard
import com.idlebones.game.ui.components.SectionHeader
import com.idlebones.game.ui.components.StatBar
import com.idlebones.game.ui.theme.GameColors

@Composable
fun CombatScreen(gameEngine: GameEngine) {
    val state by gameEngine.gameState.collectAsState()
    val zone = remember(state.currentZoneId) {
        GameDatabase.zones.firstOrNull { it.id == state.currentZoneId } ?: GameDatabase.zones.first()
    }
    val monster = remember(state.currentMonsterId) { gameEngine.currentMonster() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Cabeçalho: zona + gold
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (zone.isDiscovered || zone.id == "zone_1") zone.realName else zone.displayName,
                    color = GameColors.Gold,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Nv. ${zone.levelRequirement}-${zone.maxLevel}",
                    color = GameColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
            Text(
                text = "\uD83D\uDCB0 ${state.gold}",
                color = GameColors.Gold,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(16.dp))

        // Card do monstro + HP do jogador
        GameCard {
            Column {
                if (monster != null) {
                    MonsterHeader(monster, state)
                    Spacer(Modifier.height(12.dp))
                    MonsterHpBar(monster, state)
                } else {
                    Text(
                        text = "Nenhum monstro selecionado.",
                        color = GameColors.TextSecondary,
                        fontSize = 14.sp
                    )
                }
                Spacer(Modifier.height(16.dp))
                PlayerBars(state)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Seletor de zona
        SectionHeader("Zonas")
        var zonePickerExpanded by remember { mutableStateOf(false) }
        Button(
            onClick = { zonePickerExpanded = !zonePickerExpanded },
            colors = ButtonDefaults.buttonColors(
                containerColor = GameColors.SurfaceVariant,
                contentColor = GameColors.Gold
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (zonePickerExpanded) "Fechar seletor" else "Trocar Zona / Monstro")
        }

        if (zonePickerExpanded) {
            Spacer(Modifier.height(8.dp))
            ZoneMonsterPicker(gameEngine, state) { zonePickerExpanded = false }
        }

        Spacer(Modifier.height(16.dp))

        // Log de combate
        SectionHeader("Registro")
        CombatLog(state, monster)
    }
}

@Composable
private fun MonsterHeader(monster: Monster, state: GameState) {
    val revealed = state.discoveredMonsterIds.contains(monster.id)
    val name = DiscoveryManager.getRevealedName(monster.realName, revealed)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = name,
                color = if (revealed) GameColors.TextPrimary else GameColors.Iron,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Nv. ${monster.level}" + if (monster.isBoss) "  CHEFÃO" else "",
                color = if (monster.isBoss) GameColors.CritOrange else GameColors.TextSecondary,
                fontSize = 12.sp
            )
        }
        Text(
            text = "\u2694 ${monster.attack}   \uD83D\uDEE1 ${monster.defense}",
            color = GameColors.TextSecondary,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun MonsterHpBar(monster: Monster, state: GameState) {
    val hpMax = monster.hp.toFloat()
    val current = (state.monsterCombatHp ?: monster.hp).coerceIn(0, monster.hp).toFloat()
    StatBar(
        fraction = if (hpMax > 0) current / hpMax else 0f,
        label = "HP do Monstro",
        valueText = "${current.toLong()} / ${monster.hp}",
        color = GameColors.HealthRed
    )
}

@Composable
private fun PlayerBars(state: GameState) {
    val maxHp = (state.playerStats.strengthLevel * 10L + 100L)
    val curHp = state.playerCurrentHp ?: maxHp
    val mana = 100L // Mana simplificada (placeholder visual)

    StatBar(
        fraction = curHp.toFloat() / maxHp.toFloat(),
        label = "HP",
        valueText = "$curHp / $maxHp",
        color = GameColors.HealthRed
    )
    Spacer(Modifier.height(6.dp))
    StatBar(
        fraction = mana.toFloat() / 100f,
        label = "Mana",
        valueText = "$mana / 100",
        color = GameColors.ManaBlue
    )
}

@Composable
private fun ZoneMonsterPicker(
    gameEngine: GameEngine,
    state: GameState,
    onSelected: () -> Unit
) {
    val zones = GameDatabase.zones.take(20)
    LazyColumn(modifier = Modifier.height(220.dp)) {
        zones.forEach { zone ->
            item {
                Text(
                    text = "${zone.realName} (Nv. ${zone.levelRequirement}+)",
                    color = GameColors.Gold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
            items(zone.monsters) { monster ->
                val revealed = state.discoveredMonsterIds.contains(monster.id)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, GameColors.IronDark, RoundedCornerShape(6.dp))
                        .background(
                            if (state.currentMonsterId == monster.id)
                                GameColors.GoldDim.copy(alpha = 0.15f)
                            else GameColors.SurfaceVariant
                        )
                        .clickable {
                            gameEngine.selectMonster(monster.id)
                            onSelected()
                        }
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = DiscoveryManager.getRevealedName(monster.realName, revealed),
                        color = GameColors.TextPrimary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = if (monster.isBoss) "CHEFÃO" else "Nv. ${monster.level}",
                        color = if (monster.isBoss) GameColors.CritOrange else GameColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun CombatLog(state: GameState, monster: Monster?) {
    // Log derivado do estado (sem buffer persistido): mostra último status.
    val lines = buildList {
        if (monster == null) {
            add("Selecione um monstro para iniciar a batalha.")
        } else {
            val revealed = state.discoveredMonsterIds.contains(monster.id)
            add("Lutando contra ${DiscoveryManager.getRevealedName(monster.realName, revealed)}.")
            add("Gold acumulado: ${state.gold}.")
            if (revealed) add("Monstro já descoberto. Drops registrados no baú.")
        }
    }
    GameCard {
        Column {
            lines.forEach {
                Text(text = "> $it", color = GameColors.TextSecondary, fontSize = 12.sp)
            }
        }
    }
}
