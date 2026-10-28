/*
 * Project Idle RPG - Idle Bones
 * Tela de Combate: zona/mapa, monstro ativo, HP em tempo real, gold, auto-eat, seleção de mapas/monstros e loot.
 */
package com.idlebones.game.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idlebones.game.data.generator.DiscoveryManager
import com.idlebones.game.data.generator.GameDatabase
import com.idlebones.game.data.generator.Monster
import com.idlebones.game.data.models.ItemCategory
import com.idlebones.game.data.models.ItemTier
import com.idlebones.game.data.save.GameState
import com.idlebones.game.engine.GameEngine
import com.idlebones.game.ui.components.GameCard
import com.idlebones.game.ui.components.SectionHeader
import com.idlebones.game.ui.components.StatBar
import com.idlebones.game.ui.theme.GameColors
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CombatScreen(gameEngine: GameEngine) {
    val state by gameEngine.gameState.collectAsState()
    val zone = remember(state.currentZoneId) {
        GameDatabase.zones.firstOrNull { it.id == state.currentZoneId } ?: GameDatabase.zones.first()
    }
    val monster = remember(state.currentMonsterId) { gameEngine.currentMonster() }

    var showMapsSheet by remember { mutableStateOf(false) }
    var selectedMonsterForDetail by remember { mutableStateOf<Monster?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
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
                text = "💰 ${state.gold}",
                color = GameColors.Gold,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(12.dp))

        // 1. Painel de Atributos Totais de Batalha (skill + equips)
        TotalCombatStatsPanel(gameEngine = gameEngine, state = state)

        Spacer(Modifier.height(10.dp))

        // Card do monstro em combate + HP do jogador + Auto-Eat
        GameCard {
            Column {
                if (monster != null) {
                    MonsterHeader(monster, state)
                    Spacer(Modifier.height(10.dp))
                    MonsterHpBar(monster, state)
                } else {
                    Text(
                        text = "Nenhum monstro selecionado. Escolha um monstro abaixo para iniciar o combate.",
                        color = GameColors.TextSecondary,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.height(12.dp))
                PlayerBars(state)

                Spacer(Modifier.height(12.dp))

                // Seletor de Auto-Eat e Comidas
                AutoEatSection(state, gameEngine)
            }
        }

        Spacer(Modifier.height(12.dp))

        // 1. Botão "Mapas" (antigo "Zonas")
        SectionHeader("Mapas")
        Button(
            onClick = { showMapsSheet = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = GameColors.SurfaceVariant,
                contentColor = GameColors.Gold
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("🗺️ Mapas (Atual: ${zone.realName})", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))

        // 2. Seção de Monstros do Mapa Atual (6 comuns + 1 Boss destacado)
        SectionHeader("Monstros do Mapa (${zone.realName})")
        MonstersOfMapSection(
            zoneMonsters = zone.monsters,
            currentMonsterId = state.currentMonsterId,
            discoveredIds = state.discoveredMonsterIds,
            onMonsterClick = { selectedMonsterForDetail = it }
        )

        Spacer(Modifier.height(12.dp))

        // Seção "Loot"
        SectionHeader("Loot")
        LootSection(state)
    }

    // BottomSheet listando todos os mapas (zonas)
    if (showMapsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showMapsSheet = false },
            sheetState = sheetState,
            containerColor = GameColors.Surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Seleção de Mapas",
                    color = GameColors.Gold,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(GameDatabase.zones) { z ->
                        val isUnlocked = gameEngine.isZoneUnlocked(z.id)
                        val isCurrent = state.currentZoneId == z.id

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isCurrent) 2.dp else 1.dp,
                                    color = if (isCurrent) GameColors.Gold else if (isUnlocked) GameColors.IronDark else GameColors.IronDark.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .background(
                                    if (isCurrent) GameColors.GoldDim.copy(alpha = 0.2f)
                                    else if (isUnlocked) GameColors.Background
                                    else GameColors.Background.copy(alpha = 0.4f)
                                )
                                .clickable(enabled = isUnlocked) {
                                    gameEngine.selectZone(z.id)
                                    showMapsSheet = false
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isUnlocked) z.realName else "🔒 ${z.realName}",
                                    color = if (isUnlocked) GameColors.TextPrimary else GameColors.TextSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                if (isUnlocked) {
                                    Text(
                                        text = "Nível recomendado: ${z.levelRequirement} - ${z.maxLevel}",
                                        color = GameColors.TextSecondary,
                                        fontSize = 12.sp
                                    )
                                } else {
                                    Text(
                                        text = "Derrote o Boss do mapa anterior",
                                        color = GameColors.CritOrange,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            if (isCurrent) {
                                Text(
                                    text = "Mapa Atual",
                                    color = GameColors.Gold,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else if (isUnlocked) {
                                Text(
                                    text = "Liberado",
                                    color = GameColors.XpGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // 3. Diálogo de Detalhes do Monstro (ao clicar no card do monstro)
    if (selectedMonsterForDetail != null) {
        val m = selectedMonsterForDetail!!
        val isFighting = state.currentMonsterId == m.id
        val revealed = state.discoveredMonsterIds.contains(m.id)
        val monsterName = DiscoveryManager.getRevealedName(m.realName, revealed)

        AlertDialog(
            onDismissRequest = { selectedMonsterForDetail = null },
            containerColor = GameColors.Surface,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = monsterName,
                        color = GameColors.Gold,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (m.isBoss) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(GameColors.CritOrange.copy(alpha = 0.2f))
                                .border(1.dp, GameColors.CritOrange, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "CHEFÃO",
                                color = GameColors.CritOrange,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(GameColors.IronDark)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Comum",
                                color = GameColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Nível: ${m.level}", color = GameColors.TextPrimary, fontSize = 13.sp)
                        Text("HP: ${m.hp}", color = GameColors.HealthRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("⚔️ ${m.attack}", color = GameColors.TextSecondary, fontSize = 12.sp)
                        Text("🛡️ ${m.defense}", color = GameColors.TextSecondary, fontSize = 12.sp)
                        Text("⚡ ${m.agility}", color = GameColors.TextSecondary, fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Possíveis Drops:",
                        color = GameColors.Gold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))

                    // Lista de drops do monstro na variante base comum
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(GameColors.Background)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        m.drops.forEach { entry ->
                            val item = GameDatabase.items.firstOrNull { it.id == entry.itemId }
                            val itemName = item?.name ?: entry.itemId
                            val categoryName = item?.category?.name ?: "RESOURCES"
                            val icon = getCategoryIcon(item?.category)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = icon, fontSize = 14.sp)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = itemName,
                                        color = GameColors.TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = categoryName,
                                        color = GameColors.TextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Tiers raros até GOD podem dropar aleatoriamente",
                        color = GameColors.TextSecondary,
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        gameEngine.startCombatWith(m.id)
                        selectedMonsterForDetail = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFighting) GameColors.IronDark else GameColors.Gold
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isFighting) "Em Combate" else "Lutar",
                        color = if (isFighting) Color.White else Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                Button(
                    onClick = { selectedMonsterForDetail = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Fechar", color = GameColors.TextSecondary)
                }
            }
        )
    }
}

/**
 * 2. Seção de Monstros do Mapa Atual:
 * Exibe os 7 monstros (6 comuns + 1 Boss destacado) com indicação visual de combate ativo.
 */
@Composable
private fun MonstersOfMapSection(
    zoneMonsters: List<Monster>,
    currentMonsterId: String,
    discoveredIds: Set<String>,
    onMonsterClick: (Monster) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        zoneMonsters.forEach { m ->
            val isFighting = currentMonsterId == m.id
            val revealed = discoveredIds.contains(m.id)
            val mName = DiscoveryManager.getRevealedName(m.realName, revealed)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = if (isFighting) 2.dp else 1.dp,
                        color = if (isFighting) GameColors.Gold else if (m.isBoss) GameColors.CritOrange.copy(alpha = 0.6f) else GameColors.IronDark,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .background(
                        if (isFighting) GameColors.GoldDim.copy(alpha = 0.2f) else GameColors.Surface
                    )
                    .clickable { onMonsterClick(m) }
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = mName,
                            color = if (isFighting) GameColors.Gold else GameColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = if (isFighting || m.isBoss) FontWeight.Bold else FontWeight.Medium
                        )
                        Spacer(Modifier.width(8.dp))
                        if (m.isBoss) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(GameColors.CritOrange.copy(alpha = 0.2f))
                                    .border(1.dp, GameColors.CritOrange, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "CHEFÃO",
                                    color = GameColors.CritOrange,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Nv. ${m.level} • ❤️ ${m.hp} • ⚔️ ${m.attack} • 🛡️ ${m.defense}",
                        color = GameColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }

                if (isFighting) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(GameColors.Gold)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "EM COMBATE",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = "Detalhes >",
                        color = GameColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
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
            text = "⚔ ${monster.attack}   🛡 ${monster.defense}",
            color = GameColors.TextSecondary,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun DamageSplashIndicator(lastDamage: Int?) {
    if (lastDamage == null) return

    if (lastDamage > 0) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(GameColors.HealthRed.copy(alpha = 0.2f))
                .border(1.dp, GameColors.HealthRed, RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp)
        ) {
            Text(
                text = "-$lastDamage",
                color = GameColors.HealthRed,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(GameColors.IronDark)
                .border(1.dp, GameColors.Iron.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(
                text = "🛡️",
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun MonsterHpBar(monster: Monster, state: GameState) {
    val hpMax = monster.hp.toFloat()
    val isDead = state.monsterCombatHp != null && state.monsterCombatHp <= 0L
    val current = if (isDead) 0L else (state.monsterCombatHp ?: monster.hp).coerceIn(0, monster.hp)
    val fraction = if (isDead || hpMax <= 0f) 0f else (current.toFloat() / hpMax).coerceIn(0f, 1f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isDead) "HP do Monstro (Ressurgindo...)" else "HP do Monstro",
                color = if (isDead) GameColors.Iron else GameColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = if (isDead) FontWeight.Bold else FontWeight.Normal
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DamageSplashIndicator(state.lastDamageToMonster)
                Text(
                    text = "$current / ${monster.hp}",
                    color = if (isDead) GameColors.Iron else GameColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        StatBar(
            fraction = fraction,
            color = if (isDead) GameColors.IronDark else GameColors.HealthRed
        )
    }
}

@Composable
private fun PlayerBars(state: GameState) {
    val equipHpBonus = state.equippedItems.values.sumOf { it.stats["hp"] ?: 0 }
    val maxHp = (state.playerStats.strengthLevel * 10L + 100L + equipHpBonus)
    val curHp = (state.playerCurrentHp ?: maxHp).coerceIn(0, maxHp)
    val fraction = if (maxHp > 0) curHp.toFloat() / maxHp.toFloat() else 0f
    val mana = 100L // Mana simplificada (placeholder visual)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "HP",
                color = GameColors.TextSecondary,
                fontSize = 11.sp
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DamageSplashIndicator(state.lastDamageToPlayer)
                Text(
                    text = "$curHp / $maxHp",
                    color = GameColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        StatBar(
            fraction = fraction,
            color = GameColors.HealthRed
        )
    }

    Spacer(Modifier.height(6.dp))
    StatBar(
        fraction = mana.toFloat() / 100f,
        label = "Mana",
        valueText = "$mana / 100",
        color = GameColors.ManaBlue
    )
}

/**
 * Painel de Atributos Totais de Batalha:
 * Exibe os 4 stats consolidados do jogador (skill + equips).
 */
@Composable
private fun TotalCombatStatsPanel(gameEngine: GameEngine, state: GameState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TotalStatChip(
            icon = "⚔️",
            label = "Melee",
            value = gameEngine.totalMeleeAttack(state),
            modifier = Modifier.weight(1f)
        )
        TotalStatChip(
            icon = "🏹",
            label = "Ranged",
            value = gameEngine.totalRangedAttack(state),
            modifier = Modifier.weight(1f)
        )
        TotalStatChip(
            icon = "🔮",
            label = "Magic",
            value = gameEngine.totalMagicAttack(state),
            modifier = Modifier.weight(1f)
        )
        TotalStatChip(
            icon = "🛡️",
            label = "Defesa",
            value = gameEngine.totalDefense(state),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TotalStatChip(
    icon: String,
    label: String,
    value: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(GameColors.SurfaceVariant)
            .border(1.dp, GameColors.IronDark, RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$icon $label",
                color = GameColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "$value",
                color = GameColors.Gold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

/**
 * Seletor de Auto-Eat e Comidas (logo abaixo das barras de HP)
 */
@Composable
private fun AutoEatSection(state: GameState, gameEngine: GameEngine) {
    val bestFood = remember(state.inventory) {
        state.inventory.filter { invItem ->
            invItem.quantity > 0 && (
                invItem.category.equals("FOOD", ignoreCase = true) ||
                invItem.category.equals("COOKING", ignoreCase = true) ||
                (invItem.category.equals("CONSUMABLES", ignoreCase = true) && (invItem.stats["heal"] ?: 0) > 0)
            )
        }.maxByOrNull { it.stats["heal"] ?: it.stats["hp"] ?: 0 }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(GameColors.SurfaceVariant)
            .border(1.dp, GameColors.IronDark, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        // Título e botões rápidos
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Auto-Eat em: ${state.autoEatHpPercentage}% HP",
                color = GameColors.Gold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            // Botões rápidos (50%, 70%, 80%)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(50, 70, 80).forEach { pct ->
                    val isSelected = state.autoEatHpPercentage == pct
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) GameColors.Gold else GameColors.Surface)
                            .border(1.dp, if (isSelected) GameColors.Gold else GameColors.IronDark, RoundedCornerShape(4.dp))
                            .clickable { gameEngine.setAutoEatPercentage(pct) }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$pct%",
                            color = if (isSelected) Color.Black else GameColors.TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Slider de 10% a 90%
        Slider(
            value = state.autoEatHpPercentage.toFloat(),
            onValueChange = { gameEngine.setAutoEatPercentage(it.roundToInt()) },
            valueRange = 10f..90f,
            steps = 7,
            colors = SliderDefaults.colors(
                thumbColor = GameColors.Gold,
                activeTrackColor = GameColors.Gold,
                inactiveTrackColor = GameColors.IronDark
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
        )

        Spacer(Modifier.height(6.dp))

        // Card compacto com a comida ativa ou aviso de sem comida
        if (bestFood != null) {
            val healAmount = bestFood.stats["heal"] ?: bestFood.stats["hp"] ?: 0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(GameColors.Surface)
                    .border(1.dp, GameColors.IronDark, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🍗", fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${bestFood.name} (+${healAmount} HP)",
                        color = GameColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = "x${bestFood.quantity}",
                    color = GameColors.XpGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(GameColors.CritOrange.copy(alpha = 0.15f))
                    .border(1.dp, GameColors.CritOrange.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚠️ Sem comida no inventário!",
                    color = GameColors.CritOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Seção "Loot"
 */
@Composable
private fun LootSection(state: GameState) {
    GameCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Monstros Derrotados: ${state.sessionMonstersKilled}",
                color = GameColors.Gold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            if (state.sessionLoot.isEmpty()) {
                Text(
                    text = "Nenhum loot coletado nesta sessão",
                    color = GameColors.TextSecondary,
                    fontSize = 12.sp
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val lootEntries = state.sessionLoot.entries.toList()
                    lootEntries.forEach { (key, quantity) ->
                        val lastUnderscore = key.lastIndexOf('_')
                        val (itemId, rarity) = if (lastUnderscore > 0) {
                            key.substring(0, lastUnderscore) to key.substring(lastUnderscore + 1)
                        } else {
                            key to "COMMON"
                        }

                        val itemName = GameDatabase.items.firstOrNull { it.id == itemId }?.name ?: itemId
                        val rarityName = rarityDisplayName(rarity)
                        val color = rarityColor(rarity)

                        Text(
                            text = "${quantity}x $itemName ($rarityName)",
                            color = color,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

private fun rarityDisplayName(rarityKey: String): String = when (rarityKey.uppercase()) {
    "COMMON" -> "Comum"
    "UNCOMMON" -> "Incomum"
    "RARE" -> "Raro"
    "SUPER_RARE" -> "Super Raro"
    "EPIC" -> "Épico"
    "LEGENDARY" -> "Lendário"
    "MYTHIC" -> "Mítico"
    "ANCESTRAL" -> "Ancestral"
    "DIVINE" -> "Divino"
    "GOD" -> "Celestial"
    else -> rarityKey.lowercase().replaceFirstChar { it.uppercase() }
}

private fun rarityColor(rarityKey: String): Color {
    val tier = runCatching { ItemTier.valueOf(rarityKey.uppercase()) }.getOrNull()
    return tier?.let { Color(android.graphics.Color.parseColor(it.colorHex)) } ?: GameColors.Gold
}

private fun getCategoryIcon(category: ItemCategory?): String = when (category) {
    ItemCategory.SWORDS -> "⚔️"
    ItemCategory.HEAVY_ARMOR, ItemCategory.LEATHER_ARMOR, ItemCategory.CLOTH_ARMOR -> "🛡️"
    ItemCategory.RANGED_WEAPONS_AMMO -> "🏹"
    ItemCategory.MAGIC_WEAPONS -> "🔮"
    ItemCategory.SHIELDS_ACCESSORIES -> "💍"
    ItemCategory.CONSUMABLES -> "🧪"
    ItemCategory.FOOD, ItemCategory.COOKING -> "🍖"
    ItemCategory.RESOURCES -> "⛏️"
    null -> "📦"
}
