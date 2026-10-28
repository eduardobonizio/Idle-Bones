/*
 * Project Idle RPG - Idle Bones
 */
package com.idlebones.game.data.models

/**
 * Typealias para explicitar o modelo de domínio do Item conforme arquitetura.
 */
typealias ItemDomain = Item

/**
 * Níveis de raridade dos itens com seus respectivos multiplicadores (1.0x a 7.0x).
 */
enum class ItemRarity(val multiplier: Double) {
    COMMON(1.0),
    UNCOMMON(2.0),
    RARE(3.0),
    EPIC(4.0),
    LEGENDARY(5.0),
    MYTHIC(6.0),
    GOD(7.0)
}

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

/**
 * Função utilitária que aplica os multiplicadores de raridade nos atributos do item base.
 */
fun rollItemWithRarity(
    baseItem: Item,
    rarity: ItemRarity = ItemRarity.values().random()
): Item {
    val updatedStats = baseItem.stats.mapValues { (_, value) ->
        (value * rarity.multiplier).toInt()
    }
    return baseItem.copy(stats = updatedStats)
}
