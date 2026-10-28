/*
 * Project Idle RPG - Idle Bones
 * Sistema de Descoberta (Mystery Drop System)
 */
package com.idlebones.game.data.generator

import com.idlebones.game.data.models.*
import com.idlebones.game.data.save.GameState

/**
 * Gerenciador do sistema de descoberta
 * Itens, monstros e pets começam com isDiscovered = false
 * Ao adquirir/derrotar pela primeira vez, revelam nome real e marcam como descoberto
 */
object DiscoveryManager {
    
    /**
     * Processa descoberta de item ao obtê-lo
     * Retorna GameState atualizado com descoberta marcada
     */
    fun discoverItem(gameState: GameState, itemId: String): GameState {
        val updatedInventory = gameState.inventory.map {
            if (it.id == itemId) it.copy(isDiscovered = true) else it
        }
        return gameState.copy(inventory = updatedInventory)
    }
    
    /**
     * Processa descoberta de monstro ao derrotá-lo pela primeira vez
     */
    fun discoverMonster(gameState: GameState, monsterId: String): GameState {
        if (gameState.discoveredMonsterIds.contains(monsterId)) return gameState
        return gameState.copy(discoveredMonsterIds = gameState.discoveredMonsterIds + monsterId)
    }
    
    /**
     * Processa descoberta de pet ao obtê-lo pela primeira vez
     * Chance rigorosamente <= 0.0009 (0.09%)
     */
    fun discoverPet(gameState: GameState, petId: String): GameState {
        val updatedPets = gameState.pets.map {
            if (it.id == petId) it.copy(isDiscovered = true) else it
        }
        return gameState.copy(pets = updatedPets)
    }
    
    /**
     * Verifica se pet deve dropar baseado na chance
     */
    fun shouldPetDrop(pet: Pet): Boolean {
        return Math.random() < pet.dropChance
    }
    
    /**
     * Revela nome real do conteúdo oculto
     * Nomes ocultos são revelados apenas após descoberta
     */
    fun getRevealedName(name: String, isDiscovered: Boolean): String {
        return if (isDiscovered) name else "??? [Não Descoberto]"
    }
}

/**
 * Repositório central do banco de dados gerado
 */
object GameDatabase {
    val zones: List<Zone> by lazy { GameDatabaseGenerator.generateZones() }
    val items: List<ItemDomain> by lazy { generateAllItems() }
    val pets: List<Pet> by lazy { GameDatabaseGenerator.generatePets() }
    val skillRecipes: List<SkillRecipe> by lazy { generateSkillRecipes() }

    private fun generateAllItems(): List<ItemDomain> {
        val list = mutableListOf<ItemDomain>()
        val tiers = ItemTier.values()

        // 1. Recursos de coleta por tier de zona (1..20)
        for (tierZone in 1..20) {
            val tier = tiers[minOf(tierZone / 3, tiers.size - 1)]
            list.add(ItemDomain("res_ore_tier$tierZone", "Minério de Bronze", ItemCategory.RESOURCES, null, tier, mapOf("gathering_xp" to (tierZone * 10 * tier.multiplier).toInt()), false, 0))
            list.add(ItemDomain("res_wood_tier$tierZone", "Madeira de Carvalho", ItemCategory.RESOURCES, null, tier, mapOf("gathering_xp" to (tierZone * 10 * tier.multiplier).toInt()), false, 0))
            list.add(ItemDomain("res_fish_tier$tierZone", "Camarão", ItemCategory.RESOURCES, null, tier, mapOf("gathering_xp" to (tierZone * 10 * tier.multiplier).toInt()), false, 0))
        }

        // 2. Consumíveis (Poções)
        for (i in 1..20) {
            val tier = tiers[minOf(i / 2, tiers.size - 1)]
            list.add(ItemDomain("cons_potion_$i", "Poção de Cura $i", ItemCategory.CONSUMABLES, null, tier, mapOf("heal" to (20 * i * tier.multiplier).toInt()), false, 0))
        }

        // 3. Comidas / Alimentos (FOOD / COOKING) para o sistema de auto-eat
        val foodNames = listOf(
            "Pão Rústico", "Peixe Cozido", "Carne Grelhada", "Torta de Maçã",
            "Ensopado de Carne", "Banquete do Guerreiro", "Elixir Revitalizante"
        )
        for (i in 1..20) {
            val tier = tiers[minOf(i / 2, tiers.size - 1)]
            val healAmount = (25 * i * tier.multiplier).toInt()
            list.add(
                ItemDomain(
                    id = "food_$i",
                    name = "${foodNames[(i - 1) % foodNames.size]} $i",
                    category = ItemCategory.FOOD,
                    slot = null,
                    tier = tier,
                    stats = mapOf("heal" to healAmount, "hp" to healAmount),
                    isDiscovered = false,
                    quantity = 0
                )
            )
        }

        // 4. Equipamentos Base cobrindo faixas de nível até 130 (COMMON até GOD) para Melee, Ranged e Magic
        // Sem restrição de nível para equipar
        for (tier in tiers) {
            val ti = tier.ordinal
            val basePower = (ti + 1) * 10
            val p = basePower * tier.multiplier
            val material = tierName(tier)

            // =================================================================
            // MELEE: Foco em Defesa alta e HP, com dano menor
            // Slots: Arma (Espada/Machado), Escudo, Elmo, Peito, Pernas, Botas, Luvas, Amuleto, Anel, Capa
            // =================================================================
            list.add(
                ItemDomain(
                    id = "eq_wpn_${tier.name}",
                    name = "Espada de $material",
                    category = ItemCategory.SWORDS,
                    slot = EquipmentSlot.WEAPON,
                    tier = tier,
                    stats = mapOf(
                        "attack" to (p * 0.7).toInt(),
                        "defense" to (p * 0.3).toInt(),
                        "hp" to (p * 0.5).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )
            list.add(
                ItemDomain(
                    id = "eq_arm_${tier.name}_SHIELD",
                    name = "Escudo de $material",
                    category = ItemCategory.HEAVY_ARMOR,
                    slot = EquipmentSlot.SHIELD,
                    tier = tier,
                    stats = mapOf(
                        "defense" to (p * 1.0).toInt(),
                        "hp" to (p * 1.5).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )
            list.add(
                ItemDomain(
                    id = "eq_arm_${tier.name}_HELMET",
                    name = "Elmo de $material",
                    category = ItemCategory.HEAVY_ARMOR,
                    slot = EquipmentSlot.HELMET,
                    tier = tier,
                    stats = mapOf(
                        "defense" to (p * 0.8).toInt(),
                        "hp" to (p * 0.8).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )
            list.add(
                ItemDomain(
                    id = "eq_arm_${tier.name}_CHEST",
                    name = "Peitoral de $material",
                    category = ItemCategory.HEAVY_ARMOR,
                    slot = EquipmentSlot.CHEST,
                    tier = tier,
                    stats = mapOf(
                        "defense" to (p * 1.2).toInt(),
                        "hp" to (p * 1.2).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )
            list.add(
                ItemDomain(
                    id = "eq_arm_${tier.name}_LEGS",
                    name = "Perneiras de $material",
                    category = ItemCategory.HEAVY_ARMOR,
                    slot = EquipmentSlot.LEGS,
                    tier = tier,
                    stats = mapOf(
                        "defense" to (p * 1.0).toInt(),
                        "hp" to (p * 1.0).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )
            list.add(
                ItemDomain(
                    id = "eq_arm_${tier.name}_BOOTS",
                    name = "Botas de $material",
                    category = ItemCategory.HEAVY_ARMOR,
                    slot = EquipmentSlot.BOOTS,
                    tier = tier,
                    stats = mapOf(
                        "defense" to (p * 0.7).toInt(),
                        "hp" to (p * 0.6).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )
            list.add(
                ItemDomain(
                    id = "eq_arm_${tier.name}_GLOVES",
                    name = "Luvas de $material",
                    category = ItemCategory.HEAVY_ARMOR,
                    slot = EquipmentSlot.GLOVES,
                    tier = tier,
                    stats = mapOf(
                        "defense" to (p * 0.6).toInt(),
                        "hp" to (p * 0.5).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )
            list.add(
                ItemDomain(
                    id = "eq_acc_amu_${tier.name}",
                    name = "Amuleto de $material",
                    category = ItemCategory.SHIELDS_ACCESSORIES,
                    slot = EquipmentSlot.AMULET,
                    tier = tier,
                    stats = mapOf(
                        "defense" to (p * 0.5).toInt(),
                        "hp" to (p * 1.0).toInt(),
                        "attack" to (p * 0.2).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )
            list.add(
                ItemDomain(
                    id = "eq_acc_ring_${tier.name}",
                    name = "Anel de $material",
                    category = ItemCategory.SHIELDS_ACCESSORIES,
                    slot = EquipmentSlot.RING_1,
                    tier = tier,
                    stats = mapOf(
                        "defense" to (p * 0.5).toInt(),
                        "hp" to (p * 0.8).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )
            list.add(
                ItemDomain(
                    id = "eq_acc_cape_${tier.name}",
                    name = "Capa de $material",
                    category = ItemCategory.SHIELDS_ACCESSORIES,
                    slot = EquipmentSlot.CAPE,
                    tier = tier,
                    stats = mapOf(
                        "defense" to (p * 0.6).toInt(),
                        "hp" to (p * 2.0).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )

            // =================================================================
            // RANGED: Foco equilibrado entre Dano intermediário e Defesa intermediária
            // Slots: Arma (Arco), Munição (Flecha), Elmo, Peito, Pernas, Botas, Luvas, Amuleto, Anel, Capa
            // =================================================================
            list.add(
                ItemDomain(
                    id = "eq_ranged_wpn_${tier.name}",
                    name = "Arco de $material",
                    category = ItemCategory.RANGED_WEAPONS_AMMO,
                    slot = EquipmentSlot.WEAPON,
                    tier = tier,
                    stats = mapOf(
                        "attack" to (p * 1.1).toInt(),
                        "agility" to (p * 0.4).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )
            list.add(
                ItemDomain(
                    id = "eq_ranged_ammo_${tier.name}",
                    name = "Flecha de $material",
                    category = ItemCategory.RANGED_WEAPONS_AMMO,
                    slot = EquipmentSlot.AMMO,
                    tier = tier,
                    stats = mapOf(
                        "attack" to (p * 0.5).toInt(),
                        "agility" to (p * 0.3).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )
            list.add(
                ItemDomain(
                    id = "eq_arm_leather_${tier.name}_HELMET",
                    name = "Capuz de Couro de $material",
                    category = ItemCategory.LEATHER_ARMOR,
                    slot = EquipmentSlot.HELMET,
                    tier = tier,
                    stats = mapOf(
                        "attack" to (p * 0.2).toInt(),
                        "defense" to (p * 0.5).toInt(),
                        "agility" to (p * 0.3).toInt(),
                        "hp" to (p * 0.4).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )
            list.add(
                ItemDomain(
                    id = "eq_arm_leather_${tier.name}_CHEST",
                    name = "Armadura de Couro de $material",
                    category = ItemCategory.LEATHER_ARMOR,
                    slot = EquipmentSlot.CHEST,
                    tier = tier,
                    stats = mapOf(
                        "attack" to (p * 0.3).toInt(),
                        "defense" to (p * 0.7).toInt(),
                        "agility" to (p * 0.4).toInt(),
                        "hp" to (p * 0.6).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )
            list.add(
                ItemDomain(
                    id = "eq_arm_leather_${tier.name}_LEGS",
                    name = "Calças de Couro de $material",
                    category = ItemCategory.LEATHER_ARMOR,
                    slot = EquipmentSlot.LEGS,
                    tier = tier,
                    stats = mapOf(
                        "attack" to (p * 0.2).toInt(),
                        "defense" to (p * 0.6).toInt(),
                        "agility" to (p * 0.3).toInt(),
                        "hp" to (p * 0.5).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )
            list.add(
                ItemDomain(
                    id = "eq_arm_leather_${tier.name}_BOOTS",
                    name = "Botas de Couro de $material",
                    category = ItemCategory.LEATHER_ARMOR,
                    slot = EquipmentSlot.BOOTS,
                    tier = tier,
                    stats = mapOf(
                        "attack" to (p * 0.1).toInt(),
                        "defense" to (p * 0.4).toInt(),
                        "agility" to (p * 0.4).toInt(),
                        "hp" to (p * 0.3).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )
            list.add(
                ItemDomain(
                    id = "eq_arm_leather_${tier.name}_GLOVES",
                    name = "Luvas de Couro de $material",
                    category = ItemCategory.LEATHER_ARMOR,
                    slot = EquipmentSlot.GLOVES,
                    tier = tier,
                    stats = mapOf(
                        "attack" to (p * 0.2).toInt(),
                        "defense" to (p * 0.4).toInt(),
                        "agility" to (p * 0.3).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )
            list.add(
                ItemDomain(
                    id = "eq_ranged_amu_${tier.name}",
                    name = "Amuleto do Caçador de $material",
                    category = ItemCategory.SHIELDS_ACCESSORIES,
                    slot = EquipmentSlot.AMULET,
                    tier = tier,
                    stats = mapOf(
                        "attack" to (p * 0.4).toInt(),
                        "defense" to (p * 0.3).toInt(),
                        "agility" to (p * 0.3).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )
            list.add(
                ItemDomain(
                    id = "eq_ranged_ring_${tier.name}",
                    name = "Anel do Arqueiro de $material",
                    category = ItemCategory.SHIELDS_ACCESSORIES,
                    slot = EquipmentSlot.RING_1,
                    tier = tier,
                    stats = mapOf(
                        "attack" to (p * 0.3).toInt(),
                        "agility" to (p * 0.5).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )
            list.add(
                ItemDomain(
                    id = "eq_ranged_cape_${tier.name}",
                    name = "Capa Élfica de $material",
                    category = ItemCategory.SHIELDS_ACCESSORIES,
                    slot = EquipmentSlot.CAPE,
                    tier = tier,
                    stats = mapOf(
                        "defense" to (p * 0.4).toInt(),
                        "agility" to (p * 0.5).toInt(),
                        "hp" to (p * 0.8).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )

            // =================================================================
            // MAGIC: Foco em Dano alto (magic), com Defesa e HP baixos
            // Slots: Arma (Varinha/Cajado), Munição (Runa consumível), Elmo, Peito, Pernas, Botas, Luvas, Amuleto, Anel, Capa
            // =================================================================
            list.add(
                ItemDomain(
                    id = "eq_magic_wpn_${tier.name}",
                    name = "Cajado de $material",
                    category = ItemCategory.MAGIC_WEAPONS,
                    slot = EquipmentSlot.WEAPON,
                    tier = tier,
                    stats = mapOf(
                        "attack" to (p * 1.8).toInt(),
                        "magic" to (p * 1.8).toInt(),
                        "hp" to (p * 0.1).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )
            list.add(
                ItemDomain(
                    id = "eq_magic_ammo_${tier.name}",
                    name = "Runa de $material",
                    category = ItemCategory.MAGIC_WEAPONS,
                    slot = EquipmentSlot.AMMO,
                    tier = tier,
                    stats = mapOf(
                        "attack" to (p * 0.7).toInt(),
                        "magic" to (p * 0.7).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )
            list.add(
                ItemDomain(
                    id = "eq_arm_cloth_${tier.name}_HELMET",
                    name = "Capuz Arcano de $material",
                    category = ItemCategory.CLOTH_ARMOR,
                    slot = EquipmentSlot.HELMET,
                    tier = tier,
                    stats = mapOf(
                        "attack" to (p * 0.4).toInt(),
                        "magic" to (p * 0.4).toInt(),
                        "defense" to (p * 0.15).toInt(),
                        "hp" to (p * 0.1).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )
            list.add(
                ItemDomain(
                    id = "eq_arm_cloth_${tier.name}_CHEST",
                    name = "Veste Arcana de $material",
                    category = ItemCategory.CLOTH_ARMOR,
                    slot = EquipmentSlot.CHEST,
                    tier = tier,
                    stats = mapOf(
                        "attack" to (p * 0.6).toInt(),
                        "magic" to (p * 0.6).toInt(),
                        "defense" to (p * 0.25).toInt(),
                        "hp" to (p * 0.2).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )
            list.add(
                ItemDomain(
                    id = "eq_arm_cloth_${tier.name}_LEGS",
                    name = "Saia Arcana de $material",
                    category = ItemCategory.CLOTH_ARMOR,
                    slot = EquipmentSlot.LEGS,
                    tier = tier,
                    stats = mapOf(
                        "attack" to (p * 0.5).toInt(),
                        "magic" to (p * 0.5).toInt(),
                        "defense" to (p * 0.2).toInt(),
                        "hp" to (p * 0.15).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )
            list.add(
                ItemDomain(
                    id = "eq_arm_cloth_${tier.name}_BOOTS",
                    name = "Sapatos Arcanos de $material",
                    category = ItemCategory.CLOTH_ARMOR,
                    slot = EquipmentSlot.BOOTS,
                    tier = tier,
                    stats = mapOf(
                        "attack" to (p * 0.3).toInt(),
                        "magic" to (p * 0.3).toInt(),
                        "defense" to (p * 0.15).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )
            list.add(
                ItemDomain(
                    id = "eq_arm_cloth_${tier.name}_GLOVES",
                    name = "Braçadeiras Arcanas de $material",
                    category = ItemCategory.CLOTH_ARMOR,
                    slot = EquipmentSlot.GLOVES,
                    tier = tier,
                    stats = mapOf(
                        "attack" to (p * 0.3).toInt(),
                        "magic" to (p * 0.3).toInt(),
                        "defense" to (p * 0.1).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )
            list.add(
                ItemDomain(
                    id = "eq_magic_amu_${tier.name}",
                    name = "Amuleto Místico de $material",
                    category = ItemCategory.SHIELDS_ACCESSORIES,
                    slot = EquipmentSlot.AMULET,
                    tier = tier,
                    stats = mapOf(
                        "attack" to (p * 0.6).toInt(),
                        "magic" to (p * 0.6).toInt(),
                        "defense" to (p * 0.1).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )
            list.add(
                ItemDomain(
                    id = "eq_magic_ring_${tier.name}",
                    name = "Anel Mágico de $material",
                    category = ItemCategory.SHIELDS_ACCESSORIES,
                    slot = EquipmentSlot.RING_1,
                    tier = tier,
                    stats = mapOf(
                        "attack" to (p * 0.5).toInt(),
                        "magic" to (p * 0.5).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )
            list.add(
                ItemDomain(
                    id = "eq_magic_cape_${tier.name}",
                    name = "Manto Arcano de $material",
                    category = ItemCategory.SHIELDS_ACCESSORIES,
                    slot = EquipmentSlot.CAPE,
                    tier = tier,
                    stats = mapOf(
                        "attack" to (p * 0.4).toInt(),
                        "magic" to (p * 0.4).toInt(),
                        "defense" to (p * 0.1).toInt(),
                        "hp" to (p * 0.3).toInt()
                    ),
                    isDiscovered = false,
                    quantity = 0
                )
            )
        }

        // 5. Recursos e Produtos de Habilidades/Profissões
        // Minérios
        list.add(ItemDomain("res_copper_ore", "Minério de Cobre", ItemCategory.RESOURCES, null, ItemTier.COMMON, mapOf("gathering_xp" to 15), false, 0))
        list.add(ItemDomain("res_blank_rune", "Runa em Branco", ItemCategory.RESOURCES, null, ItemTier.COMMON, mapOf("magic" to 1), false, 0))
        list.add(ItemDomain("res_coal", "Carvão", ItemCategory.RESOURCES, null, ItemTier.UNCOMMON, mapOf("gathering_xp" to 35), false, 0))
        list.add(ItemDomain("res_iron_ore", "Minério de Ferro", ItemCategory.RESOURCES, null, ItemTier.UNCOMMON, mapOf("gathering_xp" to 45), false, 0))
        list.add(ItemDomain("res_gold_ore", "Minério de Ouro", ItemCategory.RESOURCES, null, ItemTier.RARE, mapOf("gathering_xp" to 65), false, 0))
        list.add(ItemDomain("res_mithril_ore", "Minério de Mithril", ItemCategory.RESOURCES, null, ItemTier.SUPER_RARE, mapOf("gathering_xp" to 90), false, 0))
        list.add(ItemDomain("res_adamantite_ore", "Minério de Adamantio", ItemCategory.RESOURCES, null, ItemTier.EPIC, mapOf("gathering_xp" to 120), false, 0))
        list.add(ItemDomain("res_runite_ore", "Minério Rúnico", ItemCategory.RESOURCES, null, ItemTier.LEGENDARY, mapOf("gathering_xp" to 160), false, 0))

        // Madeiras
        list.add(ItemDomain("res_normal_logs", "Troncos Comuns", ItemCategory.RESOURCES, null, ItemTier.COMMON, mapOf("gathering_xp" to 15), false, 0))
        list.add(ItemDomain("res_oak_logs", "Troncos de Carvalho", ItemCategory.RESOURCES, null, ItemTier.UNCOMMON, mapOf("gathering_xp" to 35), false, 0))
        list.add(ItemDomain("res_willow_logs", "Troncos de Salgueiro", ItemCategory.RESOURCES, null, ItemTier.RARE, mapOf("gathering_xp" to 60), false, 0))
        list.add(ItemDomain("res_yew_logs", "Troncos de Teixo", ItemCategory.RESOURCES, null, ItemTier.EPIC, mapOf("gathering_xp" to 105), false, 0))
        list.add(ItemDomain("res_magic_logs", "Troncos Mágicos", ItemCategory.RESOURCES, null, ItemTier.LEGENDARY, mapOf("gathering_xp" to 160), false, 0))

        // Peixes Crus
        list.add(ItemDomain("raw_shrimp", "Camarão Cru", ItemCategory.RESOURCES, null, ItemTier.COMMON, mapOf("gathering_xp" to 15), false, 0))
        list.add(ItemDomain("raw_trout", "Truta Crua", ItemCategory.RESOURCES, null, ItemTier.UNCOMMON, mapOf("gathering_xp" to 35), false, 0))
        list.add(ItemDomain("raw_salmon", "Salmão Cru", ItemCategory.RESOURCES, null, ItemTier.RARE, mapOf("gathering_xp" to 60), false, 0))
        list.add(ItemDomain("raw_lobster", "Lagosta Crua", ItemCategory.RESOURCES, null, ItemTier.SUPER_RARE, mapOf("gathering_xp" to 95), false, 0))
        list.add(ItemDomain("raw_shark", "Tubarão Cru", ItemCategory.RESOURCES, null, ItemTier.EPIC, mapOf("gathering_xp" to 140), false, 0))

        // Comidas Assadas / Preparadas (restauram HP)
        list.add(ItemDomain("res_wheat", "Trigo", ItemCategory.RESOURCES, null, ItemTier.COMMON, mapOf("gathering_xp" to 10), false, 0))
        list.add(ItemDomain("food_bread", "Pão Caseiro", ItemCategory.FOOD, null, ItemTier.COMMON, mapOf("heal" to 50, "hp" to 50), false, 0))
        list.add(ItemDomain("cooked_shrimp", "Camarão Assado", ItemCategory.FOOD, null, ItemTier.COMMON, mapOf("heal" to 30, "hp" to 30), false, 0))
        list.add(ItemDomain("cooked_trout", "Truta Assada", ItemCategory.FOOD, null, ItemTier.UNCOMMON, mapOf("heal" to 90, "hp" to 90), false, 0))
        list.add(ItemDomain("cooked_salmon", "Salmão Assado", ItemCategory.FOOD, null, ItemTier.RARE, mapOf("heal" to 150, "hp" to 150), false, 0))
        list.add(ItemDomain("cooked_lobster", "Lagosta Assada", ItemCategory.FOOD, null, ItemTier.SUPER_RARE, mapOf("heal" to 240, "hp" to 240), false, 0))
        list.add(ItemDomain("cooked_shark", "Tubarão Assado", ItemCategory.FOOD, null, ItemTier.EPIC, mapOf("heal" to 380, "hp" to 380), false, 0))

        // Barras de Metal (Ferraria)
        list.add(ItemDomain("bar_bronze", "Barra de Bronze", ItemCategory.RESOURCES, null, ItemTier.COMMON, mapOf("smithing_xp" to 20), false, 0))
        list.add(ItemDomain("bar_iron", "Barra de Ferro", ItemCategory.RESOURCES, null, ItemTier.UNCOMMON, mapOf("smithing_xp" to 40), false, 0))
        list.add(ItemDomain("bar_steel", "Barra de Aço", ItemCategory.RESOURCES, null, ItemTier.RARE, mapOf("smithing_xp" to 70), false, 0))
        list.add(ItemDomain("bar_gold", "Barra de Ouro", ItemCategory.RESOURCES, null, ItemTier.RARE, mapOf("smithing_xp" to 85), false, 0))
        list.add(ItemDomain("bar_mithril", "Barra de Mithril", ItemCategory.RESOURCES, null, ItemTier.SUPER_RARE, mapOf("smithing_xp" to 110), false, 0))
        list.add(ItemDomain("bar_runite", "Barra Rúnica", ItemCategory.RESOURCES, null, ItemTier.LEGENDARY, mapOf("smithing_xp" to 180), false, 0))

        // Runas Mágicas (Runecrafting / Munição Mágica)
        list.add(ItemDomain("rune_air", "Runa de Ar", ItemCategory.MAGIC_WEAPONS, EquipmentSlot.AMMO, ItemTier.COMMON, mapOf("magic" to 2, "attack" to 2), false, 0))
        list.add(ItemDomain("rune_fire", "Runa de Fogo", ItemCategory.MAGIC_WEAPONS, EquipmentSlot.AMMO, ItemTier.COMMON, mapOf("magic" to 5, "attack" to 5), false, 0))
        list.add(ItemDomain("rune_water", "Runa de Água", ItemCategory.MAGIC_WEAPONS, EquipmentSlot.AMMO, ItemTier.UNCOMMON, mapOf("magic" to 8, "attack" to 8), false, 0))
        list.add(ItemDomain("rune_earth", "Runa da Terra", ItemCategory.MAGIC_WEAPONS, EquipmentSlot.AMMO, ItemTier.RARE, mapOf("magic" to 12, "attack" to 12), false, 0))
        list.add(ItemDomain("rune_chaos", "Runa do Caos", ItemCategory.MAGIC_WEAPONS, EquipmentSlot.AMMO, ItemTier.SUPER_RARE, mapOf("magic" to 18, "attack" to 18), false, 0))
        list.add(ItemDomain("rune_death", "Runa da Morte", ItemCategory.MAGIC_WEAPONS, EquipmentSlot.AMMO, ItemTier.LEGENDARY, mapOf("magic" to 28, "attack" to 28), false, 0))

        return list
    }

    private fun generateSkillRecipes(): List<SkillRecipe> {
        val list = mutableListOf<SkillRecipe>()

        // =================================================================
        // 1. MINERAÇÃO (MINING)
        // =================================================================
        list.add(SkillRecipe("mine_copper", SkillType.MINING, "Minério de Cobre", requiredLevel = 1, xpReward = 15, inputs = emptyMap(), outputItemId = "res_copper_ore", outputQuantity = 1))
        list.add(SkillRecipe("mine_blank_rune", SkillType.MINING, "Runa em Branco", requiredLevel = 5, xpReward = 25, inputs = emptyMap(), outputItemId = "res_blank_rune", outputQuantity = 1))
        list.add(SkillRecipe("mine_coal", SkillType.MINING, "Carvão", requiredLevel = 15, xpReward = 35, inputs = emptyMap(), outputItemId = "res_coal", outputQuantity = 1))
        list.add(SkillRecipe("mine_iron", SkillType.MINING, "Minério de Ferro", requiredLevel = 20, xpReward = 45, inputs = emptyMap(), outputItemId = "res_iron_ore", outputQuantity = 1))
        list.add(SkillRecipe("mine_gold", SkillType.MINING, "Minério de Ouro", requiredLevel = 30, xpReward = 65, inputs = emptyMap(), outputItemId = "res_gold_ore", outputQuantity = 1))
        list.add(SkillRecipe("mine_mithril", SkillType.MINING, "Minério de Mithril", requiredLevel = 45, xpReward = 90, inputs = emptyMap(), outputItemId = "res_mithril_ore", outputQuantity = 1))
        list.add(SkillRecipe("mine_adamantite", SkillType.MINING, "Minério de Adamantio", requiredLevel = 60, xpReward = 120, inputs = emptyMap(), outputItemId = "res_adamantite_ore", outputQuantity = 1))
        list.add(SkillRecipe("mine_runite", SkillType.MINING, "Minério Rúnico", requiredLevel = 75, xpReward = 160, inputs = emptyMap(), outputItemId = "res_runite_ore", outputQuantity = 1))

        // =================================================================
        // 2. MADEIRA (WOODCUTTING)
        // =================================================================
        list.add(SkillRecipe("wood_normal", SkillType.WOODCUTTING, "Troncos Comuns", requiredLevel = 1, xpReward = 15, inputs = emptyMap(), outputItemId = "res_normal_logs", outputQuantity = 1))
        list.add(SkillRecipe("wood_oak", SkillType.WOODCUTTING, "Troncos de Carvalho", requiredLevel = 15, xpReward = 35, inputs = emptyMap(), outputItemId = "res_oak_logs", outputQuantity = 1))
        list.add(SkillRecipe("wood_willow", SkillType.WOODCUTTING, "Troncos de Salgueiro", requiredLevel = 30, xpReward = 60, inputs = emptyMap(), outputItemId = "res_willow_logs", outputQuantity = 1))
        list.add(SkillRecipe("wood_yew", SkillType.WOODCUTTING, "Troncos de Teixo", requiredLevel = 50, xpReward = 105, inputs = emptyMap(), outputItemId = "res_yew_logs", outputQuantity = 1))
        list.add(SkillRecipe("wood_magic", SkillType.WOODCUTTING, "Troncos Mágicos", requiredLevel = 70, xpReward = 160, inputs = emptyMap(), outputItemId = "res_magic_logs", outputQuantity = 1))

        // =================================================================
        // 3. PESCA (FISHING)
        // =================================================================
        list.add(SkillRecipe("fish_shrimp", SkillType.FISHING, "Pescar Camarão Cru", requiredLevel = 1, xpReward = 15, inputs = emptyMap(), outputItemId = "raw_shrimp", outputQuantity = 1))
        list.add(SkillRecipe("fish_trout", SkillType.FISHING, "Pescar Truta Crua", requiredLevel = 15, xpReward = 35, inputs = emptyMap(), outputItemId = "raw_trout", outputQuantity = 1))
        list.add(SkillRecipe("fish_salmon", SkillType.FISHING, "Pescar Salmão Cru", requiredLevel = 30, xpReward = 60, inputs = emptyMap(), outputItemId = "raw_salmon", outputQuantity = 1))
        list.add(SkillRecipe("fish_lobster", SkillType.FISHING, "Pescar Lagosta Crua", requiredLevel = 45, xpReward = 95, inputs = emptyMap(), outputItemId = "raw_lobster", outputQuantity = 1))
        list.add(SkillRecipe("fish_shark", SkillType.FISHING, "Pescar Tubarão Cru", requiredLevel = 65, xpReward = 140, inputs = emptyMap(), outputItemId = "raw_shark", outputQuantity = 1))

        // =================================================================
        // 4. CULINÁRIA (COOKING)
        // =================================================================
        list.add(SkillRecipe("cook_shrimp", SkillType.COOKING, "Assar Camarão", requiredLevel = 1, xpReward = 20, inputs = mapOf("raw_shrimp" to 1), outputItemId = "cooked_shrimp", outputQuantity = 1))
        list.add(SkillRecipe("cook_wheat", SkillType.COOKING, "Colher Trigo", requiredLevel = 1, xpReward = 10, inputs = emptyMap(), outputItemId = "res_wheat", outputQuantity = 1))
        list.add(SkillRecipe("cook_bread", SkillType.COOKING, "Assar Pão Caseiro", requiredLevel = 5, xpReward = 30, inputs = mapOf("res_wheat" to 1), outputItemId = "food_bread", outputQuantity = 1))
        list.add(SkillRecipe("cook_trout", SkillType.COOKING, "Assar Truta", requiredLevel = 15, xpReward = 45, inputs = mapOf("raw_trout" to 1), outputItemId = "cooked_trout", outputQuantity = 1))
        list.add(SkillRecipe("cook_salmon", SkillType.COOKING, "Assar Salmão", requiredLevel = 30, xpReward = 75, inputs = mapOf("raw_salmon" to 1), outputItemId = "cooked_salmon", outputQuantity = 1))
        list.add(SkillRecipe("cook_lobster", SkillType.COOKING, "Assar Lagosta", requiredLevel = 45, xpReward = 115, inputs = mapOf("raw_lobster" to 1), outputItemId = "cooked_lobster", outputQuantity = 1))
        list.add(SkillRecipe("cook_shark", SkillType.COOKING, "Assar Tubarão", requiredLevel = 65, xpReward = 170, inputs = mapOf("raw_shark" to 1), outputItemId = "cooked_shark", outputQuantity = 1))

        // =================================================================
        // 5. FERRARIA (SMITHING)
        // =================================================================
        list.add(SkillRecipe("smith_bronze_bar", SkillType.SMITHING, "Barra de Bronze", requiredLevel = 1, xpReward = 20, inputs = mapOf("res_copper_ore" to 1), outputItemId = "bar_bronze", outputQuantity = 1))
        list.add(SkillRecipe("smith_iron_bar", SkillType.SMITHING, "Barra de Ferro", requiredLevel = 15, xpReward = 40, inputs = mapOf("res_iron_ore" to 1, "res_coal" to 1), outputItemId = "bar_iron", outputQuantity = 1))
        list.add(SkillRecipe("smith_steel_bar", SkillType.SMITHING, "Barra de Aço", requiredLevel = 30, xpReward = 70, inputs = mapOf("res_iron_ore" to 1, "res_coal" to 2), outputItemId = "bar_steel", outputQuantity = 1))
        list.add(SkillRecipe("smith_gold_bar", SkillType.SMITHING, "Barra de Ouro", requiredLevel = 40, xpReward = 85, inputs = mapOf("res_gold_ore" to 1), outputItemId = "bar_gold", outputQuantity = 1))
        list.add(SkillRecipe("smith_mithril_bar", SkillType.SMITHING, "Barra de Mithril", requiredLevel = 50, xpReward = 110, inputs = mapOf("res_mithril_ore" to 1, "res_coal" to 4), outputItemId = "bar_mithril", outputQuantity = 1))
        list.add(SkillRecipe("smith_runite_bar", SkillType.SMITHING, "Barra Rúnica", requiredLevel = 75, xpReward = 180, inputs = mapOf("res_runite_ore" to 1, "res_coal" to 8), outputItemId = "bar_runite", outputQuantity = 1))
        list.add(SkillRecipe("smith_bronze_sword", SkillType.SMITHING, "Forjar Espada de Bronze", requiredLevel = 5, xpReward = 35, inputs = mapOf("bar_bronze" to 2), outputItemId = "eq_wpn_COMMON", outputQuantity = 1))
        list.add(SkillRecipe("smith_iron_sword", SkillType.SMITHING, "Forjar Espada de Ferro", requiredLevel = 20, xpReward = 60, inputs = mapOf("bar_iron" to 2), outputItemId = "eq_wpn_UNCOMMON", outputQuantity = 1))

        // =================================================================
        // 6. RUNECRAFTING
        // =================================================================
        list.add(SkillRecipe("rc_air", SkillType.RUNECRAFTING, "Runa de Ar", requiredLevel = 1, xpReward = 20, inputs = mapOf("res_blank_rune" to 1), outputItemId = "rune_air", outputQuantity = 5))
        list.add(SkillRecipe("rc_fire", SkillType.RUNECRAFTING, "Runa de Fogo", requiredLevel = 10, xpReward = 30, inputs = mapOf("res_blank_rune" to 1), outputItemId = "rune_fire", outputQuantity = 5))
        list.add(SkillRecipe("rc_water", SkillType.RUNECRAFTING, "Runa de Água", requiredLevel = 20, xpReward = 45, inputs = mapOf("res_blank_rune" to 1), outputItemId = "rune_water", outputQuantity = 5))
        list.add(SkillRecipe("rc_earth", SkillType.RUNECRAFTING, "Runa da Terra", requiredLevel = 30, xpReward = 60, inputs = mapOf("res_blank_rune" to 1), outputItemId = "rune_earth", outputQuantity = 5))
        list.add(SkillRecipe("rc_chaos", SkillType.RUNECRAFTING, "Runa do Caos", requiredLevel = 45, xpReward = 90, inputs = mapOf("res_blank_rune" to 1), outputItemId = "rune_chaos", outputQuantity = 3))
        list.add(SkillRecipe("rc_death", SkillType.RUNECRAFTING, "Runa da Morte", requiredLevel = 65, xpReward = 140, inputs = mapOf("res_blank_rune" to 1), outputItemId = "rune_death", outputQuantity = 2))

        // =================================================================
        // 7. FLETCHING
        // =================================================================
        list.add(SkillRecipe("fletch_wooden_arrows", SkillType.FLETCHING, "Flechas de Madeira", requiredLevel = 1, xpReward = 15, inputs = mapOf("res_normal_logs" to 1), outputItemId = "eq_ranged_ammo_COMMON", outputQuantity = 15))
        list.add(SkillRecipe("fletch_shortbow", SkillType.FLETCHING, "Arco Curto", requiredLevel = 5, xpReward = 25, inputs = mapOf("res_normal_logs" to 2), outputItemId = "eq_ranged_wpn_COMMON", outputQuantity = 1))
        list.add(SkillRecipe("fletch_oak_arrows", SkillType.FLETCHING, "Flechas de Carvalho", requiredLevel = 15, xpReward = 35, inputs = mapOf("res_oak_logs" to 1), outputItemId = "eq_ranged_ammo_UNCOMMON", outputQuantity = 15))
        list.add(SkillRecipe("fletch_oak_bow", SkillType.FLETCHING, "Arco de Carvalho", requiredLevel = 20, xpReward = 50, inputs = mapOf("res_oak_logs" to 2), outputItemId = "eq_ranged_wpn_UNCOMMON", outputQuantity = 1))
        list.add(SkillRecipe("fletch_willow_arrows", SkillType.FLETCHING, "Flechas de Salgueiro", requiredLevel = 30, xpReward = 60, inputs = mapOf("res_willow_logs" to 1), outputItemId = "eq_ranged_ammo_RARE", outputQuantity = 15))
        list.add(SkillRecipe("fletch_willow_bow", SkillType.FLETCHING, "Arco de Salgueiro", requiredLevel = 35, xpReward = 75, inputs = mapOf("res_willow_logs" to 2), outputItemId = "eq_ranged_wpn_RARE", outputQuantity = 1))
        list.add(SkillRecipe("fletch_yew_bow", SkillType.FLETCHING, "Arco de Teixo", requiredLevel = 50, xpReward = 110, inputs = mapOf("res_yew_logs" to 2), outputItemId = "eq_ranged_wpn_EPIC", outputQuantity = 1))
        list.add(SkillRecipe("fletch_magic_bow", SkillType.FLETCHING, "Arco Mágico", requiredLevel = 70, xpReward = 160, inputs = mapOf("res_magic_logs" to 2), outputItemId = "eq_ranged_wpn_LEGENDARY", outputQuantity = 1))

        // =================================================================
        // 8. PIROTECNIA (FIREMAKING)
        // =================================================================
        list.add(SkillRecipe("fm_normal", SkillType.FIREMAKING, "Fogueira de Madeira Comum", requiredLevel = 1, xpReward = 20, inputs = mapOf("res_normal_logs" to 1), outputItemId = "res_coal", outputQuantity = 1))
        list.add(SkillRecipe("fm_oak", SkillType.FIREMAKING, "Fogueira de Carvalho", requiredLevel = 15, xpReward = 45, inputs = mapOf("res_oak_logs" to 1), outputItemId = "res_coal", outputQuantity = 2))
        list.add(SkillRecipe("fm_willow", SkillType.FIREMAKING, "Fogueira de Salgueiro", requiredLevel = 30, xpReward = 80, inputs = mapOf("res_willow_logs" to 1), outputItemId = "res_coal", outputQuantity = 3))
        list.add(SkillRecipe("fm_yew", SkillType.FIREMAKING, "Fogueira de Teixo", requiredLevel = 50, xpReward = 135, inputs = mapOf("res_yew_logs" to 1), outputItemId = "res_coal", outputQuantity = 5))
        list.add(SkillRecipe("fm_magic", SkillType.FIREMAKING, "Fogueira Mágica", requiredLevel = 70, xpReward = 200, inputs = mapOf("res_magic_logs" to 1), outputItemId = "res_coal", outputQuantity = 8))

        // =================================================================
        // 9. ALQUIMIA (ALCHEMY)
        // =================================================================
        list.add(SkillRecipe("alch_potion_1", SkillType.ALCHEMY, "Poção de Cura Menor", requiredLevel = 1, xpReward = 25, inputs = mapOf("res_wheat" to 1), outputItemId = "cons_potion_1", outputQuantity = 1))
        list.add(SkillRecipe("alch_potion_2", SkillType.ALCHEMY, "Poção de Cura Média", requiredLevel = 15, xpReward = 55, inputs = mapOf("res_wheat" to 2), outputItemId = "cons_potion_2", outputQuantity = 1))
        list.add(SkillRecipe("alch_potion_3", SkillType.ALCHEMY, "Poção de Cura Forte", requiredLevel = 30, xpReward = 95, inputs = mapOf("res_wheat" to 3), outputItemId = "cons_potion_3", outputQuantity = 1))

        return list
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
}
