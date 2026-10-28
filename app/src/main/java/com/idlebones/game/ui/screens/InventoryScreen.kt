/*
 * Project Idle RPG - Idle Bones
 * Tela de Inventário (Mochila) & Equipamentos: grade de 10 slots equipáveis + baú de itens.
 */
package com.idlebones.game.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(gameEngine: GameEngine) {
    val state by gameEngine.gameState.collectAsState()
    var selectedClass by remember { mutableStateOf("Melee") }
    var selectedSlotForSheet by remember { mutableStateOf<EquipmentSlot?>(null) }

    // 10 slots correspondentes à classe selecionada
    val slots = remember(selectedClass) {
        if (selectedClass.equals("Melee", ignoreCase = true)) {
            listOf(
                EquipmentSlot.WEAPON, EquipmentSlot.SHIELD,
                EquipmentSlot.HELMET, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.BOOTS, EquipmentSlot.GLOVES, EquipmentSlot.CAPE,
                EquipmentSlot.AMULET, EquipmentSlot.RING_1
            )
        } else {
            listOf(
                EquipmentSlot.WEAPON, EquipmentSlot.AMMO,
                EquipmentSlot.HELMET, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.BOOTS, EquipmentSlot.GLOVES, EquipmentSlot.CAPE,
                EquipmentSlot.AMULET, EquipmentSlot.RING_1
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Mochila & Equipamentos",
            color = GameColors.Gold,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        SectionHeader("Equipamento Automático")

        // Botões de auto-equip das 3 classes
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Melee", "Ranged", "Magic").forEach { cls ->
                val isSelected = selectedClass.equals(cls, ignoreCase = true)
                Button(
                    onClick = {
                        selectedClass = cls
                        gameEngine.autoEquipClass(cls)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) GameColors.Gold else GameColors.Surface
                    ),
                    border = BorderStroke(1.dp, if (isSelected) GameColors.Gold else GameColors.IronDark),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Text(
                        text = cls,
                        color = if (isSelected) Color.Black else GameColors.Gold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        SectionHeader("Equipado (10 Slots)")

        // Grade com os 10 slots da classe
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.height(160.dp)
        ) {
            items(slots) { slot ->
                val equipped = state.equippedItems[slot.name]
                SlotCell(slot, equipped, onClick = {
                    selectedSlotForSheet = slot
                })
            }
        }

        Spacer(Modifier.height(8.dp))
        SectionHeader("Inventário da Mochila")

        if (state.inventory.isEmpty()) {
            Text(
                text = "A mochila está vazia. Derrote monstros para coletar itens.",
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
                        val slotName = item.slot
                        if (slotName != null) {
                            gameEngine.equipItem(item, slotName)
                        }
                    })
                }
            }
        }
    }

    // ModalBottomSheet exibida ao clicar em um slot
    if (selectedSlotForSheet != null) {
        val targetSlot = selectedSlotForSheet!!
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { selectedSlotForSheet = null },
            sheetState = sheetState,
            containerColor = GameColors.Surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Slot: ${slotLabel(targetSlot)}",
                        color = GameColors.Gold,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Se já houver item equipado no slot, oferece opção de desequipar
                    val currentlyEquipped = state.equippedItems[targetSlot.name]
                    if (currentlyEquipped != null) {
                        Button(
                            onClick = {
                                gameEngine.unequipSlot(targetSlot.name)
                                selectedSlotForSheet = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GameColors.IronDark),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(text = "Desequipar", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Itens do inventário compatíveis com o slot, ordenados por stats decrescentes
                val compatibleItems = remember(state.inventory, targetSlot) {
                    state.inventory.filter { invItem ->
                        isSlotCompatible(invItem.slot, targetSlot.name)
                    }.sortedByDescending { it.stats.values.sum() }
                }

                if (compatibleItems.isEmpty()) {
                    Text(
                        text = "Nenhum item compatível no inventário.",
                        color = GameColors.TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(compatibleItems) { item ->
                            val domainItem = itemDataToItem(item)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, GameColors.IronDark, RoundedCornerShape(8.dp))
                                    .background(GameColors.Background)
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.name,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        TierBadge(item = domainItem, showMultiplier = false)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    val statsFormatted = if (item.stats.isEmpty()) {
                                        "Sem stats"
                                    } else {
                                        item.stats.entries.joinToString(" | ") { (k, v) ->
                                            "${k.uppercase()}: +$v"
                                        }
                                    }
                                    Text(
                                        text = statsFormatted,
                                        color = GameColors.XpGreen,
                                        fontSize = 11.sp
                                    )
                                    if (item.quantity > 1) {
                                        Text(
                                            text = "Qtd: ${item.quantity}",
                                            color = GameColors.TextSecondary,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                Spacer(Modifier.width(8.dp))

                                Button(
                                    onClick = {
                                        gameEngine.equipItem(item, targetSlot.name)
                                        selectedSlotForSheet = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GameColors.Gold),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Equipar",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SlotCell(slot: EquipmentSlot, equipped: ItemData?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, GameColors.IronDark, RoundedCornerShape(8.dp))
            .background(GameColors.Surface)
            .clickable { onClick() }
            .padding(4.dp)
            .aspectRatio(1f),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = slotLabel(slot),
            color = GameColors.TextSecondary,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        if (equipped != null) {
            val item = itemDataToItem(equipped)
            TierBadge(item = item, showMultiplier = false)
        } else {
            Text(text = "—", color = GameColors.Iron, fontSize = 16.sp)
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

private fun slotLabel(slot: EquipmentSlot): String = when (slot) {
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
    EquipmentSlot.AMMO -> "Munição"
}

private fun isSlotCompatible(itemSlot: String?, targetSlot: String): Boolean {
    if (itemSlot == null) return false
    if (itemSlot.equals(targetSlot, ignoreCase = true)) return true
    if (targetSlot.equals("RING_1", ignoreCase = true) &&
        (itemSlot.equals("RING", ignoreCase = true) || itemSlot.equals("RING_2", ignoreCase = true))) {
        return true
    }
    return false
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
