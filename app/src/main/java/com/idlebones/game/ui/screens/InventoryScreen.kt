/*
 * Project Idle RPG - Idle Bones
 * Tela de Inventário & Equipamentos: grade de itens + 9 slots equipáveis.
 */
package com.idlebones.game.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idlebones.game.data.models.EquipmentSlot
import com.idlebones.game.data.save.ItemData
import com.idlebones.game.engine.GameEngine
import com.idlebones.game.ui.components.SectionHeader
import com.idlebones.game.ui.components.TierBadge
import com.idlebones.game.ui.theme.GameColors

@Composable
fun InventoryScreen(gameEngine: GameEngine) {
    val state by gameEngine.gameState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Baú & Equipamentos",
            color = GameColors.Gold,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        SectionHeader("Equipado")
        EquippedSlots(state, gameEngine)

        Spacer(Modifier.height(8.dp))

        SectionHeader("Inventário")
        if (state.inventory.isEmpty()) {
            Text(
                text = "O baú está vazio. Derrote monstros para coletar itens.",
                color = GameColors.TextSecondary,
                fontSize = 13.sp
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.inventory) { item ->
                    InventoryCell(item, onClick = {
                        if (item.slot != null) gameEngine.equipItem(item.id)
                    })
                }
            }
        }
    }
}

@Composable
private fun EquippedSlots(state: com.idlebones.game.data.save.GameState, gameEngine: GameEngine) {
    val slots = listOf(
        EquipmentSlot.WEAPON, EquipmentSlot.HELMET, EquipmentSlot.CHEST,
        EquipmentSlot.LEGS, EquipmentSlot.BOOTS, EquipmentSlot.GLOVES,
        EquipmentSlot.AMULET, EquipmentSlot.RING_1, EquipmentSlot.CAPE
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.height(180.dp)
    ) {
        items(slots) { slot ->
            val equipped = state.equippedItems[slot.name]
            SlotCell(slot, equipped, onClick = {
                if (equipped != null) gameEngine.unequipSlot(slot.name)
            })
        }
    }
}

@Composable
private fun SlotCell(slot: EquipmentSlot, equipped: ItemData?, onClick: () -> Unit) {
    val slotLabel = when (slot) {
        EquipmentSlot.WEAPON -> "Arma"
        EquipmentSlot.HELMET -> "Elmo"
        EquipmentSlot.CHEST -> "Peito"
        EquipmentSlot.LEGS -> "Pernas"
        EquipmentSlot.BOOTS -> "Botas"
        EquipmentSlot.GLOVES -> "Luvas"
        EquipmentSlot.AMULET -> "Amuleto"
        EquipmentSlot.RING_1, EquipmentSlot.RING_2 -> "Anel"
        EquipmentSlot.CAPE -> "Capa"
        EquipmentSlot.SHIELD -> "Escudo"
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, GameColors.IronDark, RoundedCornerShape(8.dp))
            .background(GameColors.Surface)
            .clickable { onClick() }
            .padding(8.dp)
            .aspectRatio(1f),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = slotLabel, color = GameColors.TextSecondary, fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))
        if (equipped != null) {
            val item = itemDataToItem(equipped)
            TierBadge(item = item, showMultiplier = false)
        } else {
            Text(text = "—", color = GameColors.Iron, fontSize = 18.sp)
        }
    }
}

@Composable
private fun InventoryCell(item: ItemData, onClick: () -> Unit) {
    val model = itemDataToItem(item)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, GameColors.IronDark, RoundedCornerShape(8.dp))
            .background(GameColors.Surface)
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Column {
            TierBadge(item = model, showMultiplier = false)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "x${item.quantity}",
                color = GameColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            if (item.slot != null) {
                Text(
                    text = "Equipável",
                    color = GameColors.XpGreen,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Converte ItemData (save) em Item (domínio) para alimentar o TierBadge. */
private fun itemDataToItem(data: ItemData): com.idlebones.game.data.models.Item {
    return com.idlebones.game.data.models.Item(
        id = data.id,
        name = data.name,
        category = runCatching { com.idlebones.game.data.models.ItemCategory.valueOf(data.category) }
            .getOrDefault(com.idlebones.game.data.models.ItemCategory.RESOURCES),
        slot = data.slot?.let { runCatching { EquipmentSlot.valueOf(it) }.getOrNull() },
        tier = runCatching { com.idlebones.game.data.models.ItemTier.valueOf(data.tier) }
            .getOrDefault(com.idlebones.game.data.models.ItemTier.COMMON),
        stats = data.stats,
        isDiscovered = data.isDiscovered,
        quantity = data.quantity
    )
}
