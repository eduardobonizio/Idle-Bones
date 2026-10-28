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
    val items: List<Item> by lazy { GameDatabaseGenerator.generateItems() }
    val pets: List<Pet> by lazy { GameDatabaseGenerator.generatePets() }
}
