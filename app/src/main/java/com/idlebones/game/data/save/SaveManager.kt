/*

 * Project Idle RPG - Idle Bones
 */
package com.idlebones.game.data.save

import android.content.Context
import com.idlebones.game.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * SaveManager singleton para salvar e carregar o estado do jogo.
 * Salva em arquivo JSON local: savegame.json dentro de context.filesDir
 * Versão: 1
 * Tratamento robusto de exceções para nunca quebrar o app.
 */
object SaveManager {
    
    private const val SAVE_FILE_NAME = "savegame.json"
    private const val SAVE_VERSION = 1
    
    // Configuração JSON com pretty print para debug, ignoreUnknownKeys para compatibilidade
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Salva o GameState de forma assíncrona e segura.
     * @param context Contexto Android para acessar filesDir
     * @param gameState Estado atual do jogo
     * @return true se salvou com sucesso, false caso contrário
     */
    suspend fun saveGame(context: Context, gameState: GameState): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Atualiza timestamp antes de salvar
                val gameStateToSave = gameState.copy(
                    version = SAVE_VERSION,
                    lastSaveTimestamp = System.currentTimeMillis()
                )
                
                // Serializa para JSON
                val jsonString = json.encodeToString(gameStateToSave)
                
                // Salva no arquivo
                val saveFile = File(context.filesDir, SAVE_FILE_NAME)
                saveFile.writeText(jsonString)
                
                true
            } catch (e: Exception) {
                // Nunca quebra o app, apenas loga a exceção
                e.printStackTrace()
                false
            }
        }
    }
    
    /**
     * Carrega o GameState de forma assíncrona e segura.
     * @param context Contexto Android para acessar filesDir
     * @return GameState carregado ou GameState padrão se não existir/corrompido
     */
    suspend fun loadGame(context: Context): GameState {
        return withContext(Dispatchers.IO) {
            try {
                val saveFile = File(context.filesDir, SAVE_FILE_NAME)
                
                // Se arquivo não existe, retorna estado padrão
                if (!saveFile.exists()) {
                    return@withContext createDefaultGameState()
                }
                
                // Lê o arquivo
                val jsonString = saveFile.readText()
                
                // Deserializa
                val loadedState = json.decodeFromString<GameState>(jsonString)
                
                // Verifica versão para futuras migrações
                if (loadedState.version != SAVE_VERSION) {
                    // Por enquanto, retorna estado padrão em caso de versão diferente
                    // Futuramente implementar migração
                    return@withContext createDefaultGameState()
                }
                
                loadedState
            } catch (e: Exception) {
                // Nunca quebra o app, retorna estado padrão em caso de corrupção
                e.printStackTrace()
                createDefaultGameState()
            }
        }
    }
    
    /**
     * Verifica se existe um save válido
     * @param context Contexto Android
     * @return true se arquivo de save existe
     */
    fun hasSave(context: Context): Boolean {
        return try {
            val saveFile = File(context.filesDir, SAVE_FILE_NAME)
            saveFile.exists() && saveFile.length() > 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Deleta o arquivo de save (útil para reset)
     * @param context Contexto Android
     * @return true se deletado com sucesso
     */
    fun deleteSave(context: Context): Boolean {
        return try {
            val saveFile = File(context.filesDir, SAVE_FILE_NAME)
            if (saveFile.exists()) {
                saveFile.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Cria um GameState padrão para novo jogo
     */
    private fun createDefaultGameState(): GameState {
        return GameState(
            version = SAVE_VERSION,
            playerStats = PlayerStatsData(),
            inventory = emptyList(),
            equippedItems = emptyMap(),
            activePet = null,
            currentZoneId = "zone_1",
            currentMonsterId = "",
            lastSaveTimestamp = System.currentTimeMillis(),
            activeSkill = null,
            gold = 0L,
            discoveredMonsterIds = emptySet(),
            discoveredZoneIds = setOf("zone_1"),
            pets = emptyList()
        )
    }
}

/**
 * Extensions para conversão entre modelos de domínio e dados serializáveis
 */
object GameStateMapper {
    
    /**
     * Converte PlayerStats (domínio) para PlayerStatsData (serializável)
     */
    fun toData(stats: PlayerStats): PlayerStatsData {
        return PlayerStatsData(
            woodcuttingLevel = stats.woodcutting.level,
            woodcuttingXp = stats.woodcutting.currentXp,
            miningLevel = stats.mining.level,
            miningXp = stats.mining.currentXp,
            fishingLevel = stats.fishing.level,
            fishingXp = stats.fishing.currentXp,
            firemakingLevel = stats.firemaking.level,
            firemakingXp = stats.firemaking.currentXp,
            smithingLevel = stats.smithing.level,
            smithingXp = stats.smithing.currentXp,
            fletchingLevel = stats.fletching.level,
            fletchingXp = stats.fletching.currentXp,
            runecraftingLevel = stats.runecrafting.level,
            runecraftingXp = stats.runecrafting.currentXp,
            cookingLevel = stats.cooking.level,
            cookingXp = stats.cooking.currentXp,
            alchemyLevel = stats.alchemy.level,
            alchemyXp = stats.alchemy.currentXp,
            prayerLevel = stats.prayer.level,
            prayerXp = stats.prayer.currentXp,
            agilityLevel = stats.agility.level,
            agilityXp = stats.agility.currentXp,
            attackLevel = stats.attack.level,
            attackXp = stats.attack.currentXp,
            strengthLevel = stats.strength.level,
            strengthXp = stats.strength.currentXp,
            defenseLevel = stats.defense.level,
            defenseXp = stats.defense.currentXp,
            rangedLevel = stats.ranged.level,
            rangedXp = stats.ranged.currentXp,
            magicLevel = stats.magic.level,
            magicXp = stats.magic.currentXp
        )
    }
    
    /**
     * Converte PlayerStatsData (serializável) para PlayerStats (domínio)
     */
    fun toDomain(data: PlayerStatsData): PlayerStats {
        return PlayerStats(
            woodcutting = SkillData(data.woodcuttingLevel, data.woodcuttingXp),
            mining = SkillData(data.miningLevel, data.miningXp),
            fishing = SkillData(data.fishingLevel, data.fishingXp),
            firemaking = SkillData(data.firemakingLevel, data.firemakingXp),
            smithing = SkillData(data.smithingLevel, data.smithingXp),
            fletching = SkillData(data.fletchingLevel, data.fletchingXp),
            runecrafting = SkillData(data.runecraftingLevel, data.runecraftingXp),
            cooking = SkillData(data.cookingLevel, data.cookingXp),
            alchemy = SkillData(data.alchemyLevel, data.alchemyXp),
            prayer = SkillData(data.prayerLevel, data.prayerXp),
            agility = SkillData(data.agilityLevel, data.agilityXp),
            attack = SkillData(data.attackLevel, data.attackXp),
            strength = SkillData(data.strengthLevel, data.strengthXp),
            defense = SkillData(data.defenseLevel, data.defenseXp),
            ranged = SkillData(data.rangedLevel, data.rangedXp),
            magic = SkillData(data.magicLevel, data.magicXp)
        )
    }
    
    /**
     * Converte Item (domínio) para ItemData (serializável)
     */
    fun toItemData(item: Item): ItemData {
        return ItemData(
            id = item.id,
            name = item.name,
            category = item.category.name,
            slot = item.slot?.name,
            tier = item.tier.name,
            stats = item.stats,
            isDiscovered = item.isDiscovered,
            quantity = item.quantity
        )
    }
    
    /**
     * Converte ItemData (serializável) para Item (domínio)
     */
    fun toItemDomain(itemData: ItemData): Item {
        return Item(
            id = itemData.id,
            name = itemData.name,
            category = ItemCategory.valueOf(itemData.category),
            slot = itemData.slot?.let { EquipmentSlot.valueOf(it) },
            tier = ItemTier.valueOf(itemData.tier),
            stats = itemData.stats,
            isDiscovered = itemData.isDiscovered,
            quantity = itemData.quantity
        )
    }
    
    /**
     * Converte Pet (domínio) para PetData (serializável)
     */
    fun toPetData(pet: Pet): PetData {
        return PetData(
            id = pet.id,
            skillType = pet.skillType.name,
            name = pet.name,
            bonusDescription = pet.bonusDescription,
            isDiscovered = pet.isDiscovered,
            dropChance = pet.dropChance
        )
    }
    
    /**
     * Converte PetData (serializável) para Pet (domínio)
     */
    fun toPetDomain(petData: PetData): Pet {
        return Pet(
            id = petData.id,
            skillType = SkillType.valueOf(petData.skillType),
            name = petData.name,
            bonusDescription = petData.bonusDescription,
            isDiscovered = petData.isDiscovered,
            dropChance = petData.dropChance
        )
    }
}
