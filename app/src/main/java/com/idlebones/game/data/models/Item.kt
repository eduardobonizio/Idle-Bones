/*
 * Project Idle RPG - Idle Bones
 */
package com.idlebones.game.data.models

/**
 * Representa um item no jogo.
 * @param id Identificador único do item
 * @param name Nome exibido do item
 * @param category Categoria do item
 * @param slot Slot de equipamento opcional (null para itens não-equipáveis)
 * @param tier Tier/Raridade do item
 * @param stats Mapa de estatísticas (ex: "melee" -> 10, "defense" -> 5, "magic" -> 3, "heal" -> 20)
 * @param isDiscovered Se o item já foi descoberto pelo jogador
 * @param quantity Quantidade atual em inventário
 */
data class Item(
    val id: String = "",
    val name: String = "",
    val category: ItemCategory = ItemCategory.RESOURCES,
    val slot: EquipmentSlot? = null,
    val tier: ItemTier = ItemTier.COMMON,
    val stats: Map<String, Int> = emptyMap(),
    val isDiscovered: Boolean = false,
    val quantity: Int = 0
)
