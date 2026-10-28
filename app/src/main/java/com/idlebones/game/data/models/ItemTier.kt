/*
 * Project Idle RPG - Idle Bones
 */
package com.idlebones.game.data.models

/**
 * Níveis de raridade dos itens.
 * Multiplicador aplicado aos stats base do item e cor oficial para UI.
 */
enum class ItemTier(
    val multiplier: Double,
    val colorHex: String
) {
    COMMON(1.0, "#9E9E9E"),
    UNCOMMON(1.3, "#4CAF50"),
    RARE(1.7, "#2196F3"),
    SUPER_RARE(2.2, "#9C27B0"),
    EPIC(2.8, "#FF9800"),
    LEGENDARY(3.5, "#F44336"),
    MYTHIC(4.3, "#E91E63"),
    ANCESTRAL(5.2, "#00BCD4"),
    DIVINE(6.5, "#ECEFF1"),
    GOD(8.0, "#FFD700")
}

