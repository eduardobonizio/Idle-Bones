/*
 * Project Idle RPG - Idle Bones
 */
package com.idlebones.game.data.save

import com.idlebones.game.data.models.*
import kotlinx.serialization.Serializable

/**
 * Estado completo do jogo.
 * Singleton StateHolder para gerenciar todo o progresso do jogador.
 */
@Serializable
data class GameState(
    // Versão do save para migrações futuras
    val version: Int = 1,
    
    // Estatísticas de todas as habilidades (16 habilidades)
    val playerStats: PlayerStatsData = PlayerStatsData(),

    // Inventário do jogador
    val inventory: List<ItemData> = emptyList(),

    // Equipamentos ativos por slot (chave = EquipmentSlot.name)
    val equippedItems: Map<String, ItemData> = emptyMap(),
    
    // Pet ativo equipado (opcional)
    val activePet: PetData? = null,
    
    // Zona e monstro atualmente selecionados
    val currentZoneId: String = "zone_1",
    val currentMonsterId: String = "",
    
    // Timestamp do último save para cálculo offline
    val lastSaveTimestamp: Long = System.currentTimeMillis(),

    // Habilidade ativa no momento (para cálculo offline e loop de coleta)
    val activeSkill: String? = null,

    // Recurso ou receita de skill sendo ativamente coletada/fabricada
    val activeRecipeId: String? = null,

    // Ouro acumulado
    val gold: Long = 0L,

    // IDs de monstros descobertos
    val discoveredMonsterIds: Set<String> = emptySet(),

    // IDs de zonas descobertas
    val discoveredZoneIds: Set<String> = setOf("zone_1"),

    // Pets obtidos
    val pets: List<PetData> = emptyList(),

    // --- Estado transiente de combate (persistido para continuidade entre ticks/resume) ---

    // HP atual do jogador (null = cheio / ainda não iniciado)
    val playerCurrentHp: Long? = null,

    // HP atual do monstro em combate (null = combate não iniciado)
    val monsterCombatHp: Long? = null,

    // ID do monstro ao qual o monsterCombatHp se refere (evita HP "vazado" entre monstros)
    val combatMonsterId: String? = null,

    // Dano do último turno de combate (inclusive se 0)
    val lastDamageToMonster: Int? = null,
    val lastDamageToPlayer: Int? = null,

    // Zonas em que o boss foi derrotado para progressão de mapas
    val defeatedBossZoneIds: Set<String> = emptySet(),

    // Porcentagem de HP para comer comida automaticamente durante combate (default 70%)
    val autoEatHpPercentage: Int = 70,

    // Tracking de sessão de batalha
    val sessionMonstersKilled: Int = 0,
    val sessionLoot: Map<String, Int> = emptyMap()
)

/**
 * Versão serializável de PlayerStats para JSON
 */
@Serializable
data class PlayerStatsData(
    val woodcuttingLevel: Int = 1,
    val woodcuttingXp: Long = 0L,
    val miningLevel: Int = 1,
    val miningXp: Long = 0L,
    val fishingLevel: Int = 1,
    val fishingXp: Long = 0L,
    val firemakingLevel: Int = 1,
    val firemakingXp: Long = 0L,
    val smithingLevel: Int = 1,
    val smithingXp: Long = 0L,
    val fletchingLevel: Int = 1,
    val fletchingXp: Long = 0L,
    val runecraftingLevel: Int = 1,
    val runecraftingXp: Long = 0L,
    val cookingLevel: Int = 1,
    val cookingXp: Long = 0L,
    val alchemyLevel: Int = 1,
    val alchemyXp: Long = 0L,
    val prayerLevel: Int = 1,
    val prayerXp: Long = 0L,
    val agilityLevel: Int = 1,
    val agilityXp: Long = 0L,
    val attackLevel: Int = 1,
    val attackXp: Long = 0L,
    val strengthLevel: Int = 1,
    val strengthXp: Long = 0L,
    val defenseLevel: Int = 1,
    val defenseXp: Long = 0L,
    val rangedLevel: Int = 1,
    val rangedXp: Long = 0L,
    val magicLevel: Int = 1,
    val magicXp: Long = 0L
)

/**
 * Versão serializável de Item para JSON
 */
@Serializable
data class ItemData(
    val id: String = "",
    val name: String = "",
    val category: String = "RESOURCES",
    val slot: String? = null,
    val tier: String = "COMMON",
    val stats: Map<String, Int> = emptyMap(),
    val isDiscovered: Boolean = false,
    val quantity: Int = 0
)

/**
 * Versão serializável de Pet para JSON
 */
@Serializable
data class PetData(
    val id: String = "",
    val skillType: String = "WOODCUTTING",
    val name: String = "",
    val bonusDescription: String = "",
    val isDiscovered: Boolean = false,
    val dropChance: Double = 0.0009
)
