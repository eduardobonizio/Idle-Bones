/*
 * Project Idle RPG - Idle Bones
 * Gerador Procedural de Dados Ocultos
 */
package com.idlebones.game.data.generator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idlebones.game.data.models.*
import kotlin.math.floor
import kotlin.math.pow

/**
 * Gerador procedural de conteúdo do jogo.
 * Gera zonas, monstros, itens, pets e curva de XP balanceados para níveis 1-130
 */
object GameDatabaseGenerator {
    
    /**
     * Curva de XP do RuneScape adaptada até nível 130
     * Fórmula aproximada baseada na curva exponencial oficial
     */
    fun xpForLevel(level: Int): Long {
        if (level <= 1) return 0L
        if (level > 130) return xpForLevel(130)

        var xp: Double = 0.0
        for (i in 1 until level) {
            xp += floor(i + 300.0 * 2.0.pow(i.toDouble() / 7.0))
        }
        return (floor(xp / 4.0)).toLong()
    }
    
    /**
     * Calcula XP necessário para subir do nível atual
     */
    fun xpToNextLevel(currentLevel: Int): Long {
        return if (currentLevel >= 130) Long.MAX_VALUE / 2 else xpForLevel(currentLevel + 1) - xpForLevel(currentLevel)
    }
    
    /**
     * Gera todas as zonas progressivas do nível 1 ao 130
     */
    fun generateZones(): List<Zone> {
        val zones = mutableListOf<Zone>()
        val zoneNames = listOf(
            "Floresta Sombria", "Caverna dos Goblins", "Mina Abandonada", "Pântano Corrompido",
            "Ruínas Antigas", "Deserto Assombrado", "Montanhas Geladas", "Cemitério Maldito",
            "Templo Perdido", "Fornalha Profunda", "Floresta Petrificada", "Catacumbas Sombrias",
            "Vale dos Mortos", "Torre Arruinada", "Cratera Vulcânica", "Abismo Congelado",
            "Jardim Corrompido", "Fortaleza Caída", "Laboratório Arcano", "Dimensão Obscura"
        )
        
        for (i in 1..20) {
            val minLevel = ((i - 1) * 6.5).toInt() + 1
            val maxLevel = if (i == 20) 130 else (i * 6.5).toInt().coerceAtMost(130)
            val zoneLevel = minLevel
            
            val monsters = mutableListOf<Monster>()
            // Gera 6 monstros comuns
            for (m in 1..6) {
                monsters.add(generateMonster(i, m, false, zoneLevel, m))
            }
            // Gera 1 chefão (boss)
            monsters.add(generateMonster(i, 7, true, zoneLevel, 7))
            
            zones.add(
                Zone(
                    id = "zone_$i",
                    realName = zoneNames[(i - 1) % zoneNames.size],
                    levelRequirement = minLevel,
                    maxLevel = maxLevel,
                    description = "Região obscura e perigosa do Project Idle RPG",
                    monsters = monsters,
                    isDiscovered = false
                )
            )
        }
        return zones
    }
    
    private fun generateMonster(zoneIndex: Int, monsterIndex: Int, isBoss: Boolean, baseLevel: Int, rank: Int): Monster {
        val monsterNames = listOf(
            "Esqueleto Rastejante", "Goblin Sorrateiro", "Zumbi Podre", "Aranha Venenosa", "Lobo Sombrio",
            "Morto-Vivo Faminto", "Necromante Corrompido", "Golem de Osso", "Espírito Errante", "Wight Ancião",
            "Aberração Mutante", "Cavaleiro Amaldiçoado", "Revenant Vingativo", "Demônio Menor", "Lich Renegado"
        )
        
        val bossNames = listOf(
            "Rei Goblin Corrompido", "Senhor dos Esqueletos", "Abominação do Pântano", "Guardião Ancião",
            "Arquidemônio Sombrio", "Lich Supremo", "Devorador de Almas", "Colosso Amaldiçoado",
            "Avatar da Morte", "Senhor das Trevas"
        )
        
        val level = if (isBoss) baseLevel + rank * 2 else baseLevel + rank
        val multiplier = if (isBoss) 3.5 else 1.0
        
        return Monster(
            id = "monster_zone${zoneIndex}_${if (isBoss) "boss" else "common$monsterIndex"}",
            realName = if (isBoss) bossNames[(zoneIndex - 1) % bossNames.size] else monsterNames[(zoneIndex * monsterIndex - 1) % monsterNames.size],
            level = level,
            hp = (50L * level * multiplier * (1.0 + zoneIndex * 0.15)).toLong(),
            attack = (5 + level * 1.5 * multiplier).toInt(),
            defense = (3 + level * 1.2 * multiplier).toInt(),
            agility = (2 + level * 0.8 * multiplier).toInt(),
            goldDrop = (level * 10 * multiplier).toLong(),
            isBoss = isBoss,
            drops = generateMonsterDrops(zoneIndex, isBoss),
            isDiscovered = false
        )
    }
    
    private fun generateMonsterDrops(zoneIndex: Int, isBoss: Boolean): List<DropTableEntry> {
        val drops = mutableListOf<DropTableEntry>()
        // Recursos sempre dropam
        drops.add(DropTableEntry("res_ore_tier${zoneIndex}", 0.8, if (isBoss) 3..8 else 1..3))
        drops.add(DropTableEntry("res_wood_tier${zoneIndex}", 0.8, if (isBoss) 3..6 else 1..3))
        drops.add(DropTableEntry("res_fish_tier${zoneIndex}", 0.6, if (isBoss) 2..5 else 1..2))
        // Equipamentos: usam IDs determinísticos produzidos por equipmentDropIdFor()
        val eqTier = tierOf(zoneIndex)
        if (isBoss) {
            drops.add(DropTableEntry(equipmentDropIdFor(eqTier, "wpn"), 0.15, 1..1))
            drops.add(DropTableEntry(equipmentDropIdFor(eqTier, "amu"), 0.08, 1..1))
        } else {
            drops.add(DropTableEntry(equipmentDropIdFor(eqTier, "helmet"), 0.05, 1..1))
        }
        // Consumíveis
        drops.add(DropTableEntry("cons_potion_${zoneIndex}", if (isBoss) 0.4 else 0.2, 1..if (isBoss) 3 else 1))
        return drops
    }
    
    /**
     * Gera lista abrangente de itens procedural
     */
    fun generateItems(): List<Item> {
        val items = mutableListOf<Item>()
        val tiers = ItemTier.values()
        
        // Recursos por tier de zona
        for (tierZone in 1..20) {
            val tier = tiers[minOf(tierZone / 3, 9)]
            items.add(Item("res_ore_tier$tierZone", "Minério de Bronze", ItemCategory.RESOURCES, null, tier, mapOf("gathering_xp" to (tierZone * 10 * tier.multiplier).toInt()), false, 0))
            items.add(Item("res_wood_tier$tierZone", "Madeira de Carvalho", ItemCategory.RESOURCES, null, tier, mapOf("gathering_xp" to (tierZone * 10 * tier.multiplier).toInt()), false, 0))
            items.add(Item("res_fish_tier$tierZone", "Camarão", ItemCategory.RESOURCES, null, tier, mapOf("gathering_xp" to (tierZone * 10 * tier.multiplier).toInt()), false, 0))
        }
        
        // Consumíveis
        for (i in 1..20) {
            val tier = tiers[minOf(i / 2, 9)]
            items.add(Item("cons_potion_$i", "Poção de Cura $i", ItemCategory.CONSUMABLES, null, tier, mapOf("heal" to (20 * i * tier.multiplier).toInt()), false, 0))
        }

        // Equipamentos por tier (IDs determinísticos por tier.ordinal)
        for (tier in tiers) {
            val ti = tier.ordinal
            val basePower = (ti + 1) * 10

            // Armas
            items.add(Item("eq_wpn_${tier.name}", "Espada de ${tierName(tier)}", ItemCategory.SWORDS, EquipmentSlot.WEAPON, tier, mapOf("attack" to (basePower * tier.multiplier).toInt()), false, 0))

            // Armaduras (Set completo)
            val slots = listOf(EquipmentSlot.HELMET, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.BOOTS, EquipmentSlot.GLOVES)
            for (slot in slots) {
                items.add(Item("eq_arm_${tier.name}_${slot.name}", "${slot.name} de ${tierName(tier)}", ItemCategory.HEAVY_ARMOR, slot, tier, mapOf("defense" to (basePower * 0.8 * tier.multiplier).toInt()), false, 0))
            }
            
            // Acessórios
            items.add(Item("eq_acc_amu_${tier.name}", "Amuleto de ${tierName(tier)}", ItemCategory.SHIELDS_ACCESSORIES, EquipmentSlot.AMULET, tier, mapOf("attack" to (basePower * 0.3 * tier.multiplier).toInt(), "defense" to (basePower * 0.3 * tier.multiplier).toInt()), false, 0))
            items.add(Item("eq_acc_ring_${tier.name}", "Anel de ${tierName(tier)}", ItemCategory.SHIELDS_ACCESSORIES, EquipmentSlot.RING_1, tier, mapOf("agility" to (basePower * 0.4 * tier.multiplier).toInt()), false, 0))
            items.add(Item("eq_acc_cape_${tier.name}", "Capa de ${tierName(tier)}", ItemCategory.SHIELDS_ACCESSORIES, EquipmentSlot.CAPE, tier, mapOf("hp" to (basePower * 2 * tier.multiplier).toInt()), false, 0))
        }
        
        return items
    }

    /** ID determinístico de equipamento, batendo com generateItems(). */
    private fun equipmentDropIdFor(tier: ItemTier, kind: String): String = when (kind) {
        "wpn" -> "eq_wpn_${tier.name}"
        "amu" -> "eq_acc_amu_${tier.name}"
        "helmet" -> "eq_arm_${tier.name}_${EquipmentSlot.HELMET.name}"
        else -> "eq_wpn_${tier.name}"
    }

    private fun tierName(tier: ItemTier): String = when (tier) {
        ItemTier.COMMON -> "Cobre"
        ItemTier.UNCOMMON -> "Ferro"
        ItemTier.RARE -> "Aço"
        ItemTier.SUPER_RARE -> "Mithril"
        ItemTier.EPIC -> "Adamantio"
        ItemTier.LEGENDARY -> "Rúnico"
        ItemTier.MYTHIC -> "Dragão"
        ItemTier.ANCESTRAL -> "Ancestral"
        ItemTier.DIVINE -> "Divino"
        ItemTier.GOD -> "Celestial"
    }
    
    /**
     * Gera os 16 pets exclusivos, um por habilidade
     * Chance de drop <= 0.09% (0.0009)
     */
    fun generatePets(): List<Pet> {
        return SkillType.values().map { skill ->
            Pet(
                id = "pet_${skill.name.lowercase()}",
                skillType = skill,
                name = "Pet de ${skill.name.lowercase().replaceFirstChar { it.uppercase() }}",
                bonusDescription = "Bônus passivo de ${skill.name}: +5% de eficiência.",
                isDiscovered = false,
                dropChance = 0.0009
            )
        }
    }

    /** Mapeia a zona (1..20) para um ItemTier consistente com generateItems(). */
    private fun tierOf(zoneIndex: Int): ItemTier =
        ItemTier.values()[minOf(zoneIndex / 3, ItemTier.values().size - 1)]
}

/**
 * Helper visual Composable para exibir o nome e Tier do item
 */
@Composable
fun TierBadge(item: Item) {
    val tierColor = Color(android.graphics.Color.parseColor(item.tier.colorHex))
    val displayName = if (item.isDiscovered) {
        "${item.name} [${item.tier.name} - ${item.tier.multiplier}x]"
    } else {
        "[Item Sombrio] - ${item.tier.name}"
    }

    Box(
        modifier = Modifier
            .background(tierColor.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = displayName,
            color = tierColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Dados auxiliares para geração procedural
 */
data class Zone(
    val id: String,
    val realName: String,
    val levelRequirement: Int,
    val maxLevel: Int,
    val description: String,
    val monsters: List<Monster>,
    val isDiscovered: Boolean = false
) {
    val displayName: String get() = if (isDiscovered) realName else "[Zona Sombria]"
}

data class Monster(
    val id: String,
    val realName: String,
    val level: Int,
    val hp: Long,
    val attack: Int,
    val defense: Int,
    val agility: Int,
    val goldDrop: Long,
    val isBoss: Boolean,
    val drops: List<DropTableEntry>,
    val isDiscovered: Boolean = false
) {
    val displayName: String get() = if (isDiscovered) realName else "[Monstro Sombrio]"
}

data class DropTableEntry(
    val itemId: String,
    val dropChance: Double,
    val quantityRange: IntRange
)

