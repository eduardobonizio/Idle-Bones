/*
 * Project Idle RPG - Idle Bones
 * Tela de Cidade: lojas (Ferreiro, Alquimista) + forja de crafting.
 */
package com.idlebones.game.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idlebones.game.data.economy.CraftRecipe
import com.idlebones.game.data.economy.ShopOffer
import com.idlebones.game.data.economy.ShopSystem
import com.idlebones.game.data.economy.ShopType
import com.idlebones.game.data.save.GameState
import com.idlebones.game.engine.GameEngine
import com.idlebones.game.engine.ShopFeedback
import com.idlebones.game.ui.components.SectionHeader
import com.idlebones.game.ui.components.TierBadge
import com.idlebones.game.ui.theme.GameColors
import kotlinx.coroutines.delay

private fun playerLevel(state: GameState): Int = state.playerStats.attackLevel

@Composable
fun CityScreen(gameEngine: GameEngine) {
    val state by gameEngine.gameState.collectAsState()
    val feedback by gameEngine.shopFeedback.collectAsState()

    var selectedTab by remember { mutableStateOf(ShopType.BLACKSMITH) }

    // Feedback auto-limpa apos 2.6s (re-key por id reanima mensagens repetidas).
    LaunchedEffect(feedback?.id) {
        if (feedback != null) {
            delay(2600)
            gameEngine.clearFeedback()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Cidade de Pedra",
                    color = GameColors.Gold,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Com\u00e9rcio, forja e servi\u00e7os da guilda.",
                    color = GameColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "\uD83D\uDCB0 ${state.gold}",
                    color = GameColors.Gold,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(text = "Nv. ${playerLevel(state)}", color = GameColors.TextSecondary, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(10.dp))

        if (feedback != null) {
            FeedbackBanner(feedback!!)
            Spacer(Modifier.height(8.dp))
        }

        val tabs = listOf(ShopType.BLACKSMITH, ShopType.ALCHEMIST, ShopType.FORGE)
        TabRow(
            selectedTabIndex = tabs.indexOf(selectedTab),
            containerColor = GameColors.Surface,
            contentColor = GameColors.Gold
        ) {
            tabs.forEach { shop ->
                Tab(
                    selected = selectedTab == shop,
                    onClick = { selectedTab = shop },
                    text = {
                        Text(
                            text = shop.displayName,
                            color = if (selectedTab == shop) GameColors.Gold else GameColors.TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == shop) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = selectedTab.description,
            color = GameColors.TextSecondary,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))

        when (selectedTab) {
            ShopType.FORGE -> ForgeTab(gameEngine, state)
            ShopType.BLACKSMITH, ShopType.ALCHEMIST -> {
                val catalog = remember(selectedTab, playerLevel(state)) {
                    ShopSystem.catalogForShop(selectedTab, playerLevel(state))
                }
                ShopCatalog(gameEngine, state, catalog)
            }
        }
    }
}

@Composable
private fun FeedbackBanner(feedback: ShopFeedback) {
    val color = if (feedback.success) GameColors.XpGreen else GameColors.HealthRed
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = feedback.message,
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ShopCatalog(gameEngine: GameEngine, state: GameState, catalog: List<ShopOffer>) {
    if (catalog.isEmpty()) {
        Text(
            text = "Nenhum item dispon\u00edvel no seu n\u00edvel.",
            color = GameColors.TextSecondary,
            fontSize = 13.sp
        )
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(catalog, key = { it.item.id }) { offer ->
            val ownedQty = state.inventory.firstOrNull { it.id == offer.item.id }?.quantity ?: 0
            ShopOfferRow(
                offer = offer,
                ownedQty = ownedQty,
                canAfford = state.gold >= offer.buyPrice,
                onBuy = { gameEngine.buyItem(offer.item.id) },
                onSell = { gameEngine.sellItem(offer.item.id) }
            )
        }
    }
}

@Composable
private fun ShopOfferRow(
    offer: ShopOffer,
    ownedQty: Int,
    canAfford: Boolean,
    onBuy: () -> Unit,
    onSell: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, GameColors.IronDark, RoundedCornerShape(8.dp))
            .background(GameColors.Surface)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            TierBadge(item = offer.item, showMultiplier = false)
            Spacer(Modifier.height(4.dp))
            Row {
                Text(text = "Compra: ", color = GameColors.TextSecondary, fontSize = 11.sp)
                Text(
                    text = "${offer.buyPrice} \uD83D\uDCB0",
                    color = if (canAfford) GameColors.Gold else GameColors.HealthRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(text = "   Venda: ", color = GameColors.TextSecondary, fontSize = 11.sp)
                Text(
                    text = "${offer.sellPrice} \uD83D\uDCB0",
                    color = GameColors.XpGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (ownedQty > 0) {
                Text(
                    text = "No ba\u00fa: $ownedQty",
                    color = GameColors.TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Column(horizontalAlignment = Alignment.End) {
            Button(
                onClick = onBuy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canAfford) GameColors.Gold else GameColors.IronDark,
                    contentColor = if (canAfford) Color(0xFF1A1A1A) else GameColors.TextSecondary
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Comprar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = onSell,
                enabled = ownedQty > 0,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Vender", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ForgeTab(gameEngine: GameEngine, state: GameState) {
    val recipes = remember(playerLevel(state)) {
        ShopSystem.craftRecipesForLevel(playerLevel(state))
    }
    Column(modifier = Modifier.fillMaxSize()) {
        SectionHeader("Forja de Equipamentos")
        if (recipes.isEmpty()) {
            Text(
                text = "Sem receitas dispon\u00edveis no seu n\u00edvel.",
                color = GameColors.TextSecondary,
                fontSize = 13.sp
            )
            return
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(recipes, key = { it.id }) { recipe ->
                CraftRecipeRow(gameEngine, state, recipe)
            }
        }
    }
}

@Composable
private fun CraftRecipeRow(gameEngine: GameEngine, state: GameState, recipe: CraftRecipe) {
    val result = recipe.resultItem ?: return
    val canAffordGold = state.gold >= recipe.goldCost
    val hasIngredients = recipe.ingredients.all { (id, need) ->
        (state.inventory.firstOrNull { it.id == id }?.quantity ?: 0) >= need
    }
    val canCraft = canAffordGold && hasIngredients

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, GameColors.IronDark, RoundedCornerShape(8.dp))
            .background(GameColors.Surface)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TierBadge(item = result, showMultiplier = false)
            Text(
                text = "Custo: ${recipe.goldCost} \uD83D\uDCB0",
                color = if (canAffordGold) GameColors.Gold else GameColors.HealthRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(6.dp))
        recipe.ingredients.forEach { (ingredientId, need) ->
            val have = state.inventory.firstOrNull { it.id == ingredientId }?.quantity ?: 0
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = ShopSystem.resourceDisplayName(ingredientId),
                    color = GameColors.TextSecondary,
                    fontSize = 11.sp
                )
                Text(
                    text = "$have / $need",
                    color = if (have >= need) GameColors.XpGreen else GameColors.HealthRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { gameEngine.craftRecipe(recipe) },
            enabled = canCraft,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = GameColors.Gold,
                contentColor = Color(0xFF1A1A1A),
                disabledContainerColor = GameColors.IronDark,
                disabledContentColor = GameColors.TextSecondary
            )
        ) {
            Text(
                text = if (canCraft) "Forjar" else "Recursos insuficientes",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}
