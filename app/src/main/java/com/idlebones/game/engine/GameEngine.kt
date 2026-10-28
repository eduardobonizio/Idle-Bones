/*
 * Project Idle RPG - Idle Bones
 * Engine Principal do Jogo
 *
 * Responsabilidades:
 *  - Loop de combate em turnos de 1 segundo (iniciativa por Agilidade, crítico, morte instantânea).
 *  - Loop de profissões/coleta (XP, recurso, pet <= 0.09%).
 *  - Progresso offline (Idle Catch-up em lote, sem travar a main thread).
 *  - Smart Save a cada 30s e em onPause/onDestroy, com atualização instantânea em RAM via StateFlow.
 *
 * Padrão: StateHolder consumível pela UI Compose (StateFlow<GameState>).
 * O GameState é imutável: cada ação produz um novo snapshot (copy) e publica no _gameState.
 */
package com.idlebones.game.engine

import android.content.Context
import com.idlebones.game.data.generator.DiscoveryManager
import com.idlebones.game.data.generator.DropTableEntry
import com.idlebones.game.data.generator.GameDatabase
import com.idlebones.game.data.generator.GameDatabaseGenerator
import com.idlebones.game.data.generator.Monster
import com.idlebones.game.data.models.Pet
import com.idlebones.game.data.models.SkillType
import com.idlebones.game.data.save.GameState
import com.idlebones.game.data.save.GameStateMapper
import com.idlebones.game.data.save.PlayerStatsData
import com.idlebones.game.data.economy.ShopSystem
import com.idlebones.game.data.save.SaveManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class GameEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private companion object {
        const val TICK_MS = 1_000L                 // Turno de combate = 1s
        const val AUTO_SAVE_MS = 30_000L           // Smart save = 30s
        const val MAX_LEVEL = 130
        const val CRIT_CHANCE_CAP = 0.30           // Crítico máximo 30%
        const val CRIT_DAMAGE_MULT = 1.5           // +50% de dano no crítico
        const val AGI_PER_CRIT = 40.0              // +1% de crítico a cada 40 de Agilidade
        const val PET_DROP_CAP = 0.0009            // 0.09% máximo (clamp de segurança)
        const val SKILL_XP_PER_ACTION = 20L        // XP base por ação de coleta
        const val MAX_OFFLINE_SECONDS = 12 * 3600L // Cap de 12h de progresso offline
        const val MIN_OFFLINE_SECONDS = 30L        // Ignora ausências muito curtas
        const val COMBAT_SECONDS_PER_KILL = 2L     // Estimativa de segundos por luta (offline)
    }

    // ---------- Estado exposto à UI ----------
    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    // Feedback transiente para a UI (toast inline). id incremental dispara recompose.
    private val _shopFeedback = MutableStateFlow<ShopFeedback?>(null)
    val shopFeedback: StateFlow<ShopFeedback?> = _shopFeedback.asStateFlow()
    private var feedbackCounter = 0L

    private fun emitFeedback(message: String, success: Boolean) {
        _shopFeedback.value = ShopFeedback(++feedbackCounter, message, success)
    }

    fun clearFeedback() {
        _shopFeedback.value = null
    }

    private var gameLoopJob: Job? = null
    private var autoSaveJob: Job? = null

    // Cache de busca de monstros por id (evita flatten a cada tick)
    private val monstersById: Map<String, Monster> by lazy {
        GameDatabase.zones.flatMap { it.monsters }.associateBy { it.id }
    }

    init {
        bootstrap()
    }

    /** Carrega o save, aplica o catch-up offline e então inicia os loops. */
    private fun bootstrap() {
        scope.launch {
            _gameState.value = SaveManager.loadGame(context)
            processOfflineProgress()
            startLoops()
        }
    }

    private fun startLoops() {
        gameLoopJob?.cancel()
        gameLoopJob = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                processTick()
            }
        }

        autoSaveJob?.cancel()
        autoSaveJob = scope.launch {
            while (isActive) {
                delay(AUTO_SAVE_MS)
                SaveManager.saveGame(
                    context,
                    _gameState.value.copy(lastSaveTimestamp = System.currentTimeMillis())
                )
            }
        }
    }

    // ---------- Dispatcher de tick ----------
    private fun processTick() {
        val state = _gameState.value
        when {
            state.currentMonsterId.isNotEmpty() ->
                monstersById[state.currentMonsterId]?.let { resolveCombatTurn(it) }

            state.activeSkill != null ->
                runCatching { SkillType.valueOf(state.activeSkill) }.getOrNull()
                    ?.let { processSkillTick(it) }
        }
    }

    // =====================================================================
    // 1) LOOP DE COMBATE
    // =====================================================================
    /**
     * Resolve um turno de combate (1s). Regras:
     *  - Iniciativa: maior Agilidade ataca primeiro (empate = jogador).
     *  - Dano: max(1, Atk - Def), crítico (até 30%) aplicando +50%.
     *  - Morte instantânea: se o primeiro atacante mata o alvo, o alvo NÃO contra-ataca.
     *  - HP do monstro persiste entre ticks via monsterCombatHp.
     */
    private fun resolveCombatTurn(monster: Monster) {
        var state = _gameState.value

        var monsterHp: Long = if (state.combatMonsterId == monster.id) {
            state.monsterCombatHp ?: monster.hp
        } else {
            monster.hp
        }

        val pAtk = playerAttack(state)
        val pDef = playerDefense(state)
        val pAgi = playerAgility(state)
        val pMaxHp = playerMaxHp(state)
        var pCurHp = state.playerCurrentHp ?: pMaxHp

        val playerFirst = pAgi >= monster.agility

        // --- Ataque 1 (iniciativa) ---
        if (playerFirst) {
            monsterHp -= rollDamage(pAtk, monster.defense, pAgi)
        } else {
            pCurHp -= rollDamage(monster.attack, pDef, monster.agility)
        }

        // --- Morte instantânea: quem morreu primeiro não contra-ataca ---
        if (monsterHp <= 0) { handleVictory(monster); return }
        if (pCurHp <= 0) { handlePlayerDefeat(); return }

        // --- Contra-ataque ---
        if (playerFirst) {
            pCurHp -= rollDamage(monster.attack, pDef, monster.agility)
        } else {
            monsterHp -= rollDamage(pAtk, monster.defense, pAgi)
        }

        if (monsterHp <= 0) { handleVictory(monster); return }
        if (pCurHp <= 0) { handlePlayerDefeat(); return }

        // Ambos vivos: persiste HP e continua no próximo tick.
        state = state.copy(
            playerCurrentHp = pCurHp,
            monsterCombatHp = monsterHp,
            combatMonsterId = monster.id
        )
        _gameState.value = state
    }

    /**
     * Vitória: XP de combate + XP da arma, Gold, drops, pet e descobertas.
     * Reseta o HP do monstro para que o próximo tick inicie uma nova luta.
     */
    private fun handleVictory(monster: Monster) {
        var state = _gameState.value
        val xp = combatXpFor(monster)

        // 1) Ouro
        state = state.copy(gold = state.gold + monster.goldDrop)

        // 2) XP da skill de combate + XP da arma equipada
        state = grantXp(state, SkillType.ATTACK, xp)
        state = grantWeaponXp(state, xp / 2)

        // 3) Drops de itens
        state = rollItemDrops(state, monster.drops)

        // 4) Pet de combate (chance <= 0.09%)
        GameDatabase.pets.firstOrNull { it.skillType == SkillType.ATTACK }?.let { pet ->
            if (rollPetDrop(pet)) state = unlockPet(state, pet)
        }

        // 5) Descoberta do monstro
        state = DiscoveryManager.discoverMonster(state, monster.id)

        // 6) Reset do combate
        state = state.copy(
            monsterCombatHp = null,
            combatMonsterId = null,
            playerCurrentHp = playerMaxHp(state)
        )
        _gameState.value = state
    }

    /** Derrota do jogador: reseta HP cheio e encerra o combate (sem punição destrutiva). */
    private fun handlePlayerDefeat() {
        _gameState.update {
            it.copy(
                playerCurrentHp = playerMaxHp(it),
                monsterCombatHp = null,
                combatMonsterId = null
            )
        }
    }

    // =====================================================================
    // 2) LOOP DE PROFISSÕES / COLETA
    // =====================================================================
    private fun processSkillTick(skillType: SkillType) {
        var state = _gameState.value

        // 1) XP da habilidade
        state = grantXp(state, skillType, SKILL_XP_PER_ACTION)

        // 2) Recurso correspondente ao nível atual
        resourceItemIdFor(skillType, state)?.let { state = addItem(state, it, 1) }

        // 3) Pet da skill (chance <= 0.09%)
        GameDatabase.pets.firstOrNull { it.skillType == skillType }?.let { pet ->
            if (rollPetDrop(pet)) state = unlockPet(state, pet)
        }

        _gameState.value = state
    }

    /**
     * Mapeia (skill, nível) -> id de recurso EXISTENTE no GameDatabase.
     * Recursos do Passo 2: res_ore_tierN, res_wood_tierN, res_fish_tierN (N: 1..20).
     */
    private fun resourceItemIdFor(skill: SkillType, state: GameState): String? {
        val tier = tierIndexForLevel(skillLevel(state, skill))
        val candidates = when (skill) {
            SkillType.MINING -> listOf("res_ore_tier$tier")
            SkillType.WOODCUTTING, SkillType.FLETCHING, SkillType.FIREMAKING -> listOf("res_wood_tier$tier")
            SkillType.FISHING, SkillType.COOKING -> listOf("res_fish_tier$tier")
            else -> listOf("res_ore_tier$tier", "res_wood_tier$tier", "res_fish_tier$tier")
        }
        return candidates.firstOrNull { id -> GameDatabase.items.any { it.id == id } }
    }

    // =====================================================================
    // 3) PROGRESSO OFFLINE (IDLE CATCH-UP)
    // =====================================================================
    /**
     * Calcula o tempo ausente e simula os ganhos em LOTE (batch), sem loop por segundo.
     * A lógica pesada roda em Dispatchers.Default e publica um único snapshot final.
     */
    private fun processOfflineProgress() {
        val now = System.currentTimeMillis()
        val secondsOffline = (now - _gameState.value.lastSaveTimestamp) / 1000
        if (secondsOffline < MIN_OFFLINE_SECONDS) return
        val effectiveSeconds = min(secondsOffline, MAX_OFFLINE_SECONDS)

        scope.launch(Dispatchers.Default) {
            var state = _gameState.value

            when {
                state.currentMonsterId.isNotEmpty() -> {
                    monstersById[state.currentMonsterId]?.let { monster ->
                        val kills = effectiveSeconds / COMBAT_SECONDS_PER_KILL
                        val xp = combatXpFor(monster)

                        state = state.copy(gold = state.gold + monster.goldDrop * kills)
                        state = grantXp(state, SkillType.ATTACK, xp * kills)
                        state = grantWeaponXp(state, xp / 2 * kills)
                        state = rollItemDropsBulk(state, monster.drops, kills)

                        GameDatabase.pets.firstOrNull { it.skillType == SkillType.ATTACK }?.let { pet ->
                            if (!isPetOwned(state, pet) && rollBulk(pet.dropChance, kills)) {
                                state = unlockPet(state, pet)
                            }
                        }
                        state = DiscoveryManager.discoverMonster(state, monster.id)
                    }
                }

                state.activeSkill != null -> {
                    val activeSkillName = state.activeSkill ?: return@launch
                    runCatching { SkillType.valueOf(activeSkillName) }.getOrNull()?.let { skill ->
                        val actions = effectiveSeconds
                        state = grantXp(state, skill, SKILL_XP_PER_ACTION * actions)
                        resourceItemIdFor(skill, state)?.let { state = addItem(state, it, actions.toInt()) }

                        GameDatabase.pets.firstOrNull { it.skillType == skill }?.let { pet ->
                            if (!isPetOwned(state, pet) && rollBulk(pet.dropChance, actions)) {
                                state = unlockPet(state, pet)
                            }
                        }
                    }
                }
            }

            _gameState.value = state.copy(lastSaveTimestamp = now)
        }
    }

    // =====================================================================
    // 4) SMART SAVE
    // =====================================================================
    /** Salva imediatamente (chamar em onPause/onDestroy da Activity). */
    fun forceSave() {
        scope.launch {
            SaveManager.saveGame(
                context,
                _gameState.value.copy(lastSaveTimestamp = System.currentTimeMillis())
            )
        }
    }

    /** Encerra loops (onDestroy). Faz um save final. */
    fun shutdown() {
        gameLoopJob?.cancel()
        autoSaveJob?.cancel()
        forceSave()
    }

    // =====================================================================
    // API PARA A UI
    // =====================================================================
    fun selectMonster(monsterId: String) {
        _gameState.update {
            it.copy(
                currentMonsterId = monsterId,
                activeSkill = null,
                monsterCombatHp = null,
                combatMonsterId = null,
                playerCurrentHp = playerMaxHp(it)
            )
        }
    }

    fun selectSkill(skillType: SkillType) {
        _gameState.update {
            it.copy(
                activeSkill = skillType.name,
                currentMonsterId = "",
                monsterCombatHp = null,
                combatMonsterId = null
            )
        }
    }

    fun stopActions() {
        _gameState.update {
            it.copy(
                activeSkill = null,
                currentMonsterId = "",
                monsterCombatHp = null,
                combatMonsterId = null
            )
        }
    }

    /**
     * Equipa um item do inventario no slot correspondente.
     * Itens sem slot (recursos/consumiveis) sao ignorados.
     */
    fun equipItem(itemId: String) {
        _gameState.update { state ->
            val item = state.inventory.firstOrNull { it.id == itemId } ?: return@update state
            val slot = item.slot ?: return@update state
            val newEquipped = state.equippedItems.toMutableMap()
            newEquipped[slot] = item.copy(isDiscovered = true)
            state.copy(equippedItems = newEquipped)
        }
    }

    /** Remove o equipamento de um slot. */
    fun unequipSlot(slotName: String) {
        _gameState.update { state ->
            val newEquipped = state.equippedItems.toMutableMap()
            newEquipped.remove(slotName)
            state.copy(equippedItems = newEquipped)
        }
    }

    /** Define o pet ativo (deve estar entre os pets obtidos). */
    fun setActivePet(petId: String) {
        _gameState.update { state ->
            val pet = state.pets.firstOrNull { it.id == petId } ?: return@update state
            state.copy(activePet = pet)
        }
    }

    /** Monstro atualmente selecionado (ou null). */
    fun currentMonster(): Monster? {
        val id = _gameState.value.currentMonsterId
        return if (id.isNotEmpty()) monstersById[id] else null
    }

    /** XP necessario para o proximo nivel da habilidade informada. */
    fun xpForNextLevel(level: Int): Long = GameDatabaseGenerator.xpForLevel(level + 1)

    // =====================================================================
    // ECONOMIA: LOJA / VENDA / CRAFT
    // =====================================================================

    /**
     * Compra uma unidade de um item da loja. Valida Gold. Em caso de sucesso,
     * adiciona ao inventario e debita o preco. Sempre emite feedback.
     */
    fun buyItem(itemId: String) {
        val state = _gameState.value
        val item = GameDatabase.items.firstOrNull { it.id == itemId }
        if (item == null) {
            emitFeedback("Item indisponivel no comercio.", success = false)
            return
        }
        val price = ShopSystem.buyPriceOf(item)
        if (state.gold < price) {
            emitFeedback("Gold insuficiente: faltam ${price - state.gold}.", success = false)
            return
        }
        var newState = state.copy(gold = state.gold - price)
        newState = addItem(newState, itemId, 1)
        _gameState.value = newState
        emitFeedback("Comprado: ${item.name} por $price Gold.", success = true)
    }

    /**
     * Vende uma unidade de um item do inventario. Valida quantidade > 0.
     * Remove 1 unidade (ou o item inteiro) e credita o preco de venda.
     */
    fun sellItem(itemId: String) {
        val state = _gameState.value
        val owned = state.inventory.firstOrNull { it.id == itemId }
        if (owned == null || owned.quantity <= 0) {
            emitFeedback("Voce nao possui esse item.", success = false)
            return
        }
        val domainItem = GameDatabase.items.firstOrNull { it.id == itemId }
        if (domainItem == null) {
            emitFeedback("Item nao negociavel.", success = false)
            return
        }
        val price = ShopSystem.sellPriceOf(domainItem)
        val updatedInventory = if (owned.quantity <= 1) {
            state.inventory.filterNot { it.id == itemId }
        } else {
            state.inventory.map {
                if (it.id == itemId) it.copy(quantity = it.quantity - 1) else it
            }
        }
        _gameState.value = state.copy(
            gold = state.gold + price,
            inventory = updatedInventory
        )
        emitFeedback("Vendido: ${domainItem.name} por $price Gold.", success = true)
    }

    /**
     * Executa uma receita de craft: valida recursos + Gold, consome e entrega o item.
     * Concede XP de Ferraria. Sempre emite feedback.
     */
    fun craftRecipe(recipe: com.idlebones.game.data.economy.CraftRecipe) {
        val state = _gameState.value
        val result = recipe.resultItem
        if (result == null) {
            emitFeedback("Receita invalida.", success = false)
            return
        }
        if (state.gold < recipe.goldCost) {
            emitFeedback("Gold insuficiente para forjar: faltam ${recipe.goldCost - state.gold}.", success = false)
            return
        }
        // Valida ingredientes
        for ((ingredientId, needed) in recipe.ingredients) {
            val have = state.inventory.firstOrNull { it.id == ingredientId }?.quantity ?: 0
            if (have < needed) {
                val name = ShopSystem.resourceDisplayName(ingredientId)
                emitFeedback("Recursos insuficientes: faltam ${needed - have}x $name.", success = false)
                return
            }
        }
        // Consome ingredientes
        var newInventory = state.inventory
        for ((ingredientId, needed) in recipe.ingredients) {
            newInventory = newInventory.mapNotNull { invItem ->
                if (invItem.id == ingredientId) {
                    val remaining = invItem.quantity - needed
                    if (remaining > 0) invItem.copy(quantity = remaining) else null
                } else invItem
            }
        }
        var newState = state.copy(
            gold = state.gold - recipe.goldCost,
            inventory = newInventory
        )
        newState = addItem(newState, result.id, 1)
        // XP de craft
        newState = grantXp(newState, ShopSystem.craftSkill, ShopSystem.craftXpReward(recipe.requiredTier))
        _gameState.value = newState
        emitFeedback("Forjado: ${result.name}!", success = true)
    }

    /** Adiciona Gold (usado por features futuras / debug). */
    fun addGold(amount: Long) {
        if (amount <= 0) return
        _gameState.update { it.copy(gold = it.gold + amount) }
    }

    // =====================================================================
    // ATRIBUTOS & DANO
    // =====================================================================
    /**
     * Soma o bonus de um stat ("attack", "defense", "agility", "hp") de TODOS
     * os equipamentos equipados. Sem isso, comprar/forjar nao afeta o combate.
     */
    private fun equipmentBonus(s: GameState, stat: String): Int =
        s.equippedItems.values.sumOf { it.stats[stat] ?: 0 }

    private fun playerAttack(s: GameState) =
        s.playerStats.attackLevel + 10 + equipmentBonus(s, "attack")

    private fun playerDefense(s: GameState) =
        s.playerStats.defenseLevel + 5 + equipmentBonus(s, "defense")

    private fun playerAgility(s: GameState) =
        s.playerStats.agilityLevel + 5 + equipmentBonus(s, "agility")

    private fun playerMaxHp(s: GameState) =
        (s.playerStats.strengthLevel * 10L + 100L + equipmentBonus(s, "hp"))

    /** Dano: max(1, atk - def) com crítico (até 30%) aplicando +50%. */
    private fun rollDamage(atk: Int, def: Int, agi: Int): Long {
        val base = max(1, atk - def).toLong()
        val critChance = min(CRIT_CHANCE_CAP, agi / AGI_PER_CRIT)
        return if (Random.nextDouble() < critChance) (base * CRIT_DAMAGE_MULT).toLong() else base
    }

    private fun combatXpFor(monster: Monster): Long = monster.level * 10L

    // =====================================================================
    // XP / LEVEL (todas as 16 skills)
    // =====================================================================
    private fun grantXp(state: GameState, skill: SkillType, amount: Long): GameState {
        if (amount <= 0L) return state
        val (level, xp) = skillLevelXp(state, skill)
        var newLevel = level
        var newXp = xp + amount

        while (newLevel < MAX_LEVEL && newXp >= GameDatabaseGenerator.xpForLevel(newLevel + 1)) {
            newLevel++
        }
        if (newLevel >= MAX_LEVEL) {
            newXp = min(newXp, GameDatabaseGenerator.xpForLevel(MAX_LEVEL))
        }
        return state.copy(playerStats = writeSkill(state.playerStats, skill, newLevel, newXp))
    }

    /** XP da arma equipada (skill conforme categoria da arma; fallback Attack). */
    private fun grantWeaponXp(state: GameState, amount: Long): GameState {
        val weapon = state.equippedItems["WEAPON"] ?: return state
        val weaponSkill = when (weapon.category) {
            "RANGED_WEAPONS_AMMO" -> SkillType.RANGED
            else -> SkillType.ATTACK
        }
        return grantXp(state, weaponSkill, amount)
    }

    private fun skillLevel(state: GameState, skill: SkillType): Int = skillLevelXp(state, skill).first

    private fun skillLevelXp(state: GameState, skill: SkillType): Pair<Int, Long> {
        val s = state.playerStats
        return when (skill) {
            SkillType.WOODCUTTING -> s.woodcuttingLevel to s.woodcuttingXp
            SkillType.MINING -> s.miningLevel to s.miningXp
            SkillType.FISHING -> s.fishingLevel to s.fishingXp
            SkillType.FIREMAKING -> s.firemakingLevel to s.firemakingXp
            SkillType.SMITHING -> s.smithingLevel to s.smithingXp
            SkillType.FLETCHING -> s.fletchingLevel to s.fletchingXp
            SkillType.RUNECRAFTING -> s.runecraftingLevel to s.runecraftingXp
            SkillType.COOKING -> s.cookingLevel to s.cookingXp
            SkillType.ALCHEMY -> s.alchemyLevel to s.alchemyXp
            SkillType.PRAYER -> s.prayerLevel to s.prayerXp
            SkillType.AGILITY -> s.agilityLevel to s.agilityXp
            SkillType.ATTACK -> s.attackLevel to s.attackXp
            SkillType.STRENGTH -> s.strengthLevel to s.strengthXp
            SkillType.DEFENSE -> s.defenseLevel to s.defenseXp
            SkillType.RANGED -> s.rangedLevel to s.rangedXp
            SkillType.MAGIC -> s.magicLevel to s.magicXp
        }
    }

    private fun writeSkill(data: PlayerStatsData, skill: SkillType, level: Int, xp: Long): PlayerStatsData =
        when (skill) {
            SkillType.WOODCUTTING -> data.copy(woodcuttingLevel = level, woodcuttingXp = xp)
            SkillType.MINING -> data.copy(miningLevel = level, miningXp = xp)
            SkillType.FISHING -> data.copy(fishingLevel = level, fishingXp = xp)
            SkillType.FIREMAKING -> data.copy(firemakingLevel = level, firemakingXp = xp)
            SkillType.SMITHING -> data.copy(smithingLevel = level, smithingXp = xp)
            SkillType.FLETCHING -> data.copy(fletchingLevel = level, fletchingXp = xp)
            SkillType.RUNECRAFTING -> data.copy(runecraftingLevel = level, runecraftingXp = xp)
            SkillType.COOKING -> data.copy(cookingLevel = level, cookingXp = xp)
            SkillType.ALCHEMY -> data.copy(alchemyLevel = level, alchemyXp = xp)
            SkillType.PRAYER -> data.copy(prayerLevel = level, prayerXp = xp)
            SkillType.AGILITY -> data.copy(agilityLevel = level, agilityXp = xp)
            SkillType.ATTACK -> data.copy(attackLevel = level, attackXp = xp)
            SkillType.STRENGTH -> data.copy(strengthLevel = level, strengthXp = xp)
            SkillType.DEFENSE -> data.copy(defenseLevel = level, defenseXp = xp)
            SkillType.RANGED -> data.copy(rangedLevel = level, rangedXp = xp)
            SkillType.MAGIC -> data.copy(magicLevel = level, magicXp = xp)
        }

    // =====================================================================
    // INVENTÁRIO / DROPS / PETS
    // =====================================================================
    private fun addItem(state: GameState, itemId: String, quantity: Int): GameState {
        if (quantity <= 0) return state
        val existing = state.inventory.firstOrNull { it.id == itemId }
        return if (existing != null) {
            val updated = state.inventory.map {
                if (it.id == itemId) it.copy(quantity = it.quantity + quantity, isDiscovered = true) else it
            }
            state.copy(inventory = updated)
        } else {
            val dbItem = GameDatabase.items.firstOrNull { it.id == itemId } ?: return state
            state.copy(
                inventory = state.inventory +
                    GameStateMapper.toItemData(dbItem).copy(quantity = quantity, isDiscovered = true)
            )
        }
    }

    private fun rollItemDrops(state0: GameState, drops: List<DropTableEntry>): GameState {
        var state = state0
        for (entry in drops) {
            if (Random.nextDouble() < entry.dropChance) {
                val qty = Random.nextInt(entry.quantityRange.first, entry.quantityRange.last + 1)
                state = addItem(state, entry.itemId, qty)
            }
        }
        return state
    }

    /** Versão em lote para offline: estimativa por expectativa (chance * n). */
    private fun rollItemDropsBulk(state0: GameState, drops: List<DropTableEntry>, kills: Long): GameState {
        var state = state0
        for (entry in drops) {
            val expectedQty = (entry.dropChance * kills).toLong()
            if (expectedQty > 0) state = addItem(state, entry.itemId, expectedQty.toInt())
        }
        return state
    }

    private fun rollPetDrop(pet: Pet): Boolean =
        Random.nextDouble() < min(PET_DROP_CAP, pet.dropChance)

    /** Probabilidade acumulada de ao menos 1 sucesso em N tentativas independentes. */
    private fun rollBulk(p: Double, n: Long): Boolean {
        val chance = 1.0 - Math.pow(1.0 - min(PET_DROP_CAP, p), n.toDouble())
        return Random.nextDouble() < chance
    }

    private fun isPetOwned(state: GameState, pet: Pet): Boolean =
        state.pets.any { it.id == pet.id && it.isDiscovered }

    private fun unlockPet(state: GameState, pet: Pet): GameState {
        if (isPetOwned(state, pet)) return state
        val petData = GameStateMapper.toPetData(pet).copy(isDiscovered = true)
        return DiscoveryManager.discoverPet(state.copy(pets = state.pets + petData), pet.id)
    }

    /** Índice de tier de recurso (1..20) baseado no nível da skill. */
    private fun tierIndexForLevel(level: Int): Int = ((level - 1) / 7 + 1).coerceIn(1, 20)
}

/**
 * Mensagem transiente de feedback do comercio para a UI.
 * O campo id garante recomposicao mesmo com mensagens identicas.
 */
data class ShopFeedback(
    val id: Long,
    val message: String,
    val success: Boolean
)
