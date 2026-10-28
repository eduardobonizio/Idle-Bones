/*
 * Project Idle RPG - Idle Bones
 * Tela de Habilidades (Skills): Habilidades de Combate e Profissões de Coleta/Artesanato.
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idlebones.game.data.generator.GameDatabase
import com.idlebones.game.data.generator.GameDatabaseGenerator
import com.idlebones.game.data.models.SkillRecipe
import com.idlebones.game.data.models.SkillType
import com.idlebones.game.data.save.GameState
import com.idlebones.game.engine.GameEngine
import com.idlebones.game.ui.components.GameCard
import com.idlebones.game.ui.components.SectionHeader
import com.idlebones.game.ui.components.StatBar
import com.idlebones.game.ui.theme.GameColors

private val COMBAT_SKILLS = listOf(
    SkillType.ATTACK,
    SkillType.STRENGTH,
    SkillType.DEFENSE,
    SkillType.RANGED,
    SkillType.MAGIC,
    SkillType.PRAYER,
    SkillType.AGILITY
)

private val PROFESSIONS_SKILLS = listOf(
    SkillType.MINING,
    SkillType.WOODCUTTING,
    SkillType.FISHING,
    SkillType.COOKING,
    SkillType.SMITHING,
    SkillType.RUNECRAFTING,
    SkillType.FLETCHING,
    SkillType.FIREMAKING,
    SkillType.ALCHEMY
)

@Composable
fun SkillsScreen(gameEngine: GameEngine) {
    val state by gameEngine.gameState.collectAsState()
    var selectedSkillForDetail by remember { mutableStateOf<SkillType?>(null) }

    if (selectedSkillForDetail != null) {
        SkillDetailScreen(
            skill = selectedSkillForDetail!!,
            gameEngine = gameEngine,
            state = state,
            onBack = { selectedSkillForDetail = null }
        )
    } else {
        MainSkillsList(
            state = state,
            onSelectProfession = { selectedSkillForDetail = it }
        )
    }
}

/**
 * Visualização principal das Skills dividida em Combate e Profissões.
 */
@Composable
private fun MainSkillsList(
    state: GameState,
    onSelectProfession: (SkillType) -> Unit
) {
    val activeRecipe = remember(state.activeRecipeId) {
        state.activeRecipeId?.let { id -> GameDatabase.skillRecipes.firstOrNull { it.id == id } }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Skills",
                color = GameColors.Gold,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = when {
                    activeRecipe != null -> "Atividade: ${activeRecipe.name} (${activeRecipe.skillType.name})"
                    state.activeSkill != null -> "Coletando: ${skillDisplayName(SkillType.valueOf(state.activeSkill!!))}"
                    else -> "Nenhuma atividade em andamento"
                },
                color = if (activeRecipe != null || state.activeSkill != null) GameColors.Gold else GameColors.TextSecondary,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
        }

        // Seção 1 - Habilidades de Combate (Cards estáticos, cliques desabilitados)
        item {
            SectionHeader("Habilidades de Combate")
        }
        items(COMBAT_SKILLS) { skill ->
            CombatSkillCard(state = state, skill = skill)
        }

        // Seção 2 - Profissões e Coleta (Clicáveis para navegar ao detalhe)
        item {
            Spacer(Modifier.height(8.dp))
            SectionHeader("Profissões e Coleta")
        }
        items(PROFESSIONS_SKILLS) { skill ->
            ProfessionSkillCard(
                state = state,
                skill = skill,
                onClick = { onSelectProfession(skill) }
            )
        }
    }
}

/**
 * Card estático informativo para Habilidades de Combate (cliques desabilitados).
 */
@Composable
private fun CombatSkillCard(state: GameState, skill: SkillType) {
    val (level, xp) = remember(state.playerStats, skill) { levelXp(state.playerStats, skill) }
    val xpForCurrent = remember(level) { GameDatabaseGenerator.xpForLevel(level) }
    val xpForNext = remember(level) { GameDatabaseGenerator.xpForLevel(level + 1) }
    val span = (xpForNext - xpForCurrent).coerceAtLeast(1L)
    val progress = ((xp - xpForCurrent).coerceAtLeast(0L)).toFloat() / span.toFloat()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, GameColors.IronDark, RoundedCornerShape(8.dp))
            .background(GameColors.Surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = skillIcon(skill), fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = skillDisplayName(skill),
                    color = GameColors.TextPrimary,
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
                height = 8
            )
        }
    }
}

/**
 * Card navegável para Profissões e Coleta.
 */
@Composable
private fun ProfessionSkillCard(
    state: GameState,
    skill: SkillType,
    onClick: () -> Unit
) {
    val (level, xp) = remember(state.playerStats, skill) { levelXp(state.playerStats, skill) }
    val xpForCurrent = remember(level) { GameDatabaseGenerator.xpForLevel(level) }
    val xpForNext = remember(level) { GameDatabaseGenerator.xpForLevel(level + 1) }
    val span = (xpForNext - xpForCurrent).coerceAtLeast(1L)
    val progress = ((xp - xpForCurrent).coerceAtLeast(0L)).toFloat() / span.toFloat()

    val isRecipeActive = remember(state.activeRecipeId) {
        val activeRecipe = GameDatabase.skillRecipes.firstOrNull { it.id == state.activeRecipeId }
        activeRecipe?.skillType == skill
    }
    val isSkillActive = state.activeSkill == skill.name || isRecipeActive

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isSkillActive) 2.dp else 1.dp,
                color = if (isSkillActive) GameColors.Gold else GameColors.IronDark,
                shape = RoundedCornerShape(8.dp)
            )
            .background(
                if (isSkillActive) GameColors.GoldDim.copy(alpha = 0.15f) else GameColors.Surface
            )
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = skillIcon(skill), fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = skillDisplayName(skill),
                        color = if (isSkillActive) GameColors.Gold else GameColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isSkillActive) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(GameColors.Gold)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "ATIVO",
                                color = Color.Black,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
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
                height = 8
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(text = "›", color = GameColors.TextSecondary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Tela Detalhada da Skill: exibe cabeçalho com botão voltar, status da skill e lista de receitas/recursos.
 */
@Composable
private fun SkillDetailScreen(
    skill: SkillType,
    gameEngine: GameEngine,
    state: GameState,
    onBack: () -> Unit
) {
    val (level, xp) = remember(state.playerStats, skill) { levelXp(state.playerStats, skill) }
    val xpForCurrent = remember(level) { GameDatabaseGenerator.xpForLevel(level) }
    val xpForNext = remember(level) { GameDatabaseGenerator.xpForLevel(level + 1) }
    val span = (xpForNext - xpForCurrent).coerceAtLeast(1L)
    val progress = ((xp - xpForCurrent).coerceAtLeast(0L)).toFloat() / span.toFloat()

    val recipes = remember(skill) {
        GameDatabase.skillRecipes.filter { it.skillType == skill }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Cabeçalho com botão Voltar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GameColors.SurfaceVariant,
                    contentColor = GameColors.Gold
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(text = "⬅️ Voltar", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "${skillIcon(skill)} ${skillDisplayName(skill)}",
                    color = GameColors.Gold,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Nível Atual: $level",
                    color = GameColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Card com progresso da skill
        GameCard {
            Column {
                Text(
                    text = "Progresso de Nível",
                    color = GameColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                StatBar(
                    fraction = progress,
                    label = "XP",
                    valueText = "$xp / $xpForNext XP",
                    color = GameColors.XpGreen
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Lista de Recursos / Receitas da Skill
        SectionHeader("Recursos & Receitas (${recipes.size})")

        if (recipes.isEmpty()) {
            Text(
                text = "Nenhum recurso ou receita disponível para esta profissão.",
                color = GameColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                recipes.forEach { recipe ->
                    RecipeCard(
                        recipe = recipe,
                        playerLevel = level,
                        state = state,
                        onStart = { gameEngine.startSkillAction(recipe.id) },
                        onStop = { gameEngine.stopSkillAction() }
                    )
                }
            }
        }
    }
}

/**
 * Card de cada Recurso ou Receita da Skill.
 */
@Composable
private fun RecipeCard(
    recipe: SkillRecipe,
    playerLevel: Int,
    state: GameState,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val isUnlocked = playerLevel >= recipe.requiredLevel
    val isActive = state.activeRecipeId == recipe.id

    val outputItem = remember(recipe.outputItemId) {
        GameDatabase.items.firstOrNull { it.id == recipe.outputItemId }
    }
    val outputName = outputItem?.name ?: recipe.outputItemId

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) GameColors.XpGreen else if (!isUnlocked) GameColors.IronDark.copy(alpha = 0.5f) else GameColors.IronDark,
                shape = RoundedCornerShape(8.dp)
            )
            .background(
                if (isActive) GameColors.XpGreen.copy(alpha = 0.1f)
                else if (!isUnlocked) GameColors.Surface.copy(alpha = 0.5f)
                else GameColors.Surface
            )
            .padding(12.dp)
    ) {
        Column {
            // Cabeçalho da Receita: Nome e Nível Requerido
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = recipe.name,
                    color = if (isActive) GameColors.XpGreen else if (isUnlocked) GameColors.TextPrimary else GameColors.Iron,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                if (isUnlocked) {
                    Text(
                        text = "Nv. ${recipe.requiredLevel}",
                        color = GameColors.Gold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(GameColors.IronDark)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "🔒 Nv. ${recipe.requiredLevel}",
                            color = GameColors.Iron,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Recompensa de XP e Item produzido
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "⚡ +${recipe.xpReward} XP",
                    color = GameColors.XpGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "📦 Produz: ${recipe.outputQuantity}x $outputName",
                    color = GameColors.TextSecondary,
                    fontSize = 12.sp
                )
            }

            // Ingredientes necessários (se for craft)
            if (recipe.inputs.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Ingredientes necessários:",
                    color = GameColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                recipe.inputs.forEach { (inputItemId, neededQty) ->
                    val availableQty = state.inventory.firstOrNull { it.id == inputItemId }?.quantity ?: 0
                    val inputItem = GameDatabase.items.firstOrNull { it.id == inputItemId }
                    val inputName = inputItem?.name ?: inputItemId
                    val hasEnough = availableQty >= neededQty

                    Text(
                        text = " • $neededQty x $inputName (Você tem: $availableQty)",
                        color = if (hasEnough) GameColors.TextPrimary else GameColors.HealthRed,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Botão de Ação
            if (!isUnlocked) {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = GameColors.IronDark.copy(alpha = 0.4f),
                        disabledContentColor = GameColors.Iron
                    )
                ) {
                    Text(text = "🔒 Bloqueado (Requer Nv. ${recipe.requiredLevel})", fontSize = 12.sp)
                }
            } else if (isActive) {
                Button(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GameColors.XpGreen,
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = "⏹️ Parar (Ativo)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GameColors.Gold,
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = "▶️ Iniciar",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
