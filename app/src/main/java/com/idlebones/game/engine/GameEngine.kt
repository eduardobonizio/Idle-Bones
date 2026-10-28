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
import com.idlebones.game.data.models.SkillRecipe
import com.idlebones.game.data.models.SkillType
import com.idlebones.game.data.save.GameState
import com.idlebones.game.data.save.GameStateMapper
import com.idlebones.game.data.save.ItemData
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

            state.activeRecipeId != null ->
                processRecipeTick(state.activeRecipeId)

            state.activeSkill != null ->
                runCatching { SkillType.valueOf(state.activeSkill) }.getOrNull()
                    ?.let { processSkillTick(it) }
        }
    }

    // =====================================================================
    // 1) LOOP DE COMBATE
    // =====================================================================
    /**
     * Verifica e consome comida do inventário se o HP atual estiver abaixo ou igual ao threshold configurado.
     * Retorna o GameState atualizado e o novo HP do jogador.
     */
    private fun checkAndAutoEat(state: GameState, curHp: Long, maxHp: Long): Pair<GameState, Long> {
        val threshold = maxHp * (state.autoEatHpPercentage / 100f)
        if (curHp > threshold) return state to curHp

        // Busca comidas disponíveis no inventário (categoria FOOD / COOKING ou CONSUMABLES com atributo de cura)
        val availableFoods = state.inventory.filter { invItem ->
            invItem.quantity > 0 && (
                invItem.category.equals("FOOD", ignoreCase = true) ||
                invItem.category.equals("COOKING", ignoreCase = true) ||
                (invItem.category.equals("CONSUMABLES", ignoreCase = true) && (invItem.stats["heal"] ?: 0) > 0)
            )
        }

        // Ordena da que cura mais HP para a que cura menos
        val bestFood = availableFoods.maxByOrNull { it.stats["heal"] ?: it.stats["hp"] ?: 0 }
            ?: return state to curHp

        val healAmount = (bestFood.stats["heal"] ?: bestFood.stats["hp"] ?: 0).toLong()
        val newHp = minOf(maxHp, curHp + healAmount)

        // Consome 1 unidade da melhor comida e atualiza o inventário
        val updatedInventory = state.inventory.toMutableList()
        val foodIndex = updatedInventory.indexOfFirst { it.id == bestFood.id }
        if (foodIndex >= 0) {
            val item = updatedInventory[foodIndex]
            if (item.quantity > 1) {
                updatedInventory[foodIndex] = item.copy(quantity = item.quantity - 1)
            } else {
                updatedInventory.removeAt(foodIndex)
            }
        }

        val updatedState = state.copy(
            inventory = updatedInventory,
            playerCurrentHp = newHp
        )
        return updatedState to newHp
    }

    /**
     * Resolve um turno de combate (1s). Regras:
     *  - Delay de respawn: se monsterCombatHp == 0, aguarda 1 tick sem atacar nem receber dano, reseta a vida e inicia o próximo combate.
     *  - Iniciativa: maior Agilidade ataca primeiro (empate = jogador).
     *  - Dano: max(1, Atk - Def), crítico (até 30%) aplicando +50%.
     *  - Morte instantânea: se o primeiro atacante mata o alvo, o alvo NÃO contra-ataca.
     *  - HP do monstro persiste entre ticks via monsterCombatHp.
     *  - Auto-eat: se HP <= threshold%, consome 1 unidade da melhor comida.
     *  - Registra lastDamageToMonster e lastDamageToPlayer a cada turno (inclusive 0).
     */
    private fun resolveCombatTurn(monster: Monster) {
        var state = _gameState.value

        // Delay de Respawn do Inimigo: se o HP zerou no ciclo anterior,
        // mantém o delay de 1s (1 ciclo de TICK_MS) onde não ataca nem recebe dano,
        // reseta a vida do monstro e deixa pronto para o próximo combate.
        if (state.monsterCombatHp == 0L) {
            _gameState.update {
                it.copy(
                    monsterCombatHp = monster.hp,
                    combatMonsterId = monster.id,
                    lastDamageToMonster = 0,
                    lastDamageToPlayer = 0
                )
            }
            return
        }

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

        // Auto-eat no início do turno se estiver abaixo do threshold
        val (stateAfterEatStart, hpAfterEatStart) = checkAndAutoEat(state, pCurHp, pMaxHp)
        state = stateAfterEatStart
        pCurHp = hpAfterEatStart

        val playerFirst = pAgi >= monster.agility
        var damageToMonster = 0
        var damageToPlayer = 0

        // --- Ataque 1 (iniciativa) ---
        if (playerFirst) {
            val dmg = rollDamage(pAtk, monster.defense, pAgi).toInt()
            damageToMonster += dmg
            monsterHp -= dmg
        } else {
            val dmg = rollDamage(monster.attack, pDef, monster.agility).toInt()
            damageToPlayer += dmg
            pCurHp -= dmg
        }

        // --- Morte instantânea: quem morreu primeiro não contra-ataca ---
        if (monsterHp <= 0) {
            handleVictory(monster, pCurHp, damageToMonster, damageToPlayer)
            return
        }
        if (pCurHp <= 0) {
            handlePlayerDefeat(damageToPlayer, damageToMonster)
            return
        }

        // Auto-eat se sofreu dano e sobreviveu
        if (!playerFirst && pCurHp <= pMaxHp * (state.autoEatHpPercentage / 100f)) {
            val (stateAfterDamage, hpAfterDamage) = checkAndAutoEat(state, pCurHp, pMaxHp)
            state = stateAfterDamage
            pCurHp = hpAfterDamage
        }

        // --- Contra-ataque ---
        if (playerFirst) {
            val dmg = rollDamage(monster.attack, pDef, monster.agility).toInt()
            damageToPlayer += dmg
            pCurHp -= dmg
        } else {
            val dmg = rollDamage(pAtk, monster.defense, pAgi).toInt()
            damageToMonster += dmg
            monsterHp -= dmg
        }

        if (monsterHp <= 0) {
            handleVictory(monster, pCurHp, damageToMonster, damageToPlayer)
            return
        }
        if (pCurHp <= 0) {
            handlePlayerDefeat(damageToPlayer, damageToMonster)
            return
        }

        // Auto-eat se sofreu contra-ataque e sobreviveu
        if (playerFirst && pCurHp <= pMaxHp * (state.autoEatHpPercentage / 100f)) {
            val (stateAfterDamage, hpAfterDamage) = checkAndAutoEat(state, pCurHp, pMaxHp)
            state = stateAfterDamage
            pCurHp = hpAfterDamage
        }

        // Ambos vivos: persiste HP e registros do último dano do turno para o próximo tick.
        state = state.copy(
            playerCurrentHp = pCurHp,
            monsterCombatHp = monsterHp,
            combatMonsterId = monster.id,
            lastDamageToMonster = damageToMonster,
            lastDamageToPlayer = damageToPlayer
        )
        _gameState.value = state
    }

    /**
     * Vitória: XP de combate + XP da arma, Gold, drops, pet e descobertas.
     * NÃO recupera a vida total do jogador automaticamente.
     * Define monsterCombatHp = 0L por 1s para o delay de respawn antes da próxima luta.
     */
    private fun handleVictory(
        monster: Monster,
        survivingHp: Long? = null,
        damageToMonster: Int = 0,
        damageToPlayer: Int = 0
    ) {
        var state = _gameState.value
        val xp = combatXpFor(monster)

        // 1) Ouro
        state = state.copy(gold = state.gold + monster.goldDrop)

        // 2) XP da skill de combate + XP da arma equipada
        state = grantXp(state, SkillType.ATTACK, xp)
        state = grantWeaponXp(state, xp / 2)

        // 3) Drops de itens e incremento de sessionLoot
        state = rollItemDrops(state, monster.drops)

        // 4) Pet de combate (chance <= 0.09%)
        GameDatabase.pets.firstOrNull { it.skillType == SkillType.ATTACK }?.let { pet ->
            if (rollPetDrop(pet)) state = unlockPet(state, pet)
        }

        // 5) Descoberta do monstro
        state = DiscoveryManager.discoverMonster(state, monster.id)

        // 6) Se for boss, desbloqueia a progressão de mapa
        if (monster.isBoss) {
            val zone = GameDatabase.zones.firstOrNull { z -> z.monsters.any { it.id == monster.id } }
            val zoneId = zone?.id ?: state.currentZoneId
            state = state.copy(defeatedBossZoneIds = state.defeatedBossZoneIds + zoneId)
        }

        // 7) Reset do combate: mantém o HP remanescente (sem cura total automática),
        // seta monsterCombatHp = 0L para acionar o delay de respawn de 1s e registra os danos.
        val finalHp = survivingHp ?: state.playerCurrentHp
        state = state.copy(
            monsterCombatHp = 0L,
            combatMonsterId = monster.id,
            playerCurrentHp = finalHp,
            sessionMonstersKilled = state.sessionMonstersKilled + 1,
            lastDamageToMonster = damageToMonster,
            lastDamageToPlayer = damageToPlayer
        )
        _gameState.value = state
    }

    /** Derrota do jogador: reseta HP cheio, limpa combate e grava último dano. */
    private fun handlePlayerDefeat(damageToPlayer: Int = 0, damageToMonster: Int = 0) {
        _gameState.update {
            it.copy(
                playerCurrentHp = playerMaxHp(it),
                monsterCombatHp = null,
                combatMonsterId = null,
                lastDamageToMonster = damageToMonster,
                lastDamageToPlayer = damageToPlayer
            )
        }
    }

    // =====================================================================
    // 2) LOOP DE PROFISSÕES / COLETA E ARTESANATO
    // =====================================================================

    /**
     * Processa um tick (1s) de uma receita de habilidade / recurso ativo.
     * Valida nível e insumos, consome materiais (craft), adiciona output e concede XP.
     */
    private fun processRecipeTick(recipeId: String) {
        val state = _gameState.value
        val recipe = GameDatabase.skillRecipes.firstOrNull { it.id == recipeId }
        if (recipe == null) {
            stopSkillAction()
            return
        }

        // 1. Valida se o jogador tem o nível requerido da skill
        val currentLvl = skillLevel(state, recipe.skillType)
        if (currentLvl < recipe.requiredLevel) {
            stopSkillAction()
            emitFeedback("Nível insuficiente para ${recipe.name}: requer ${recipe.skillType.name} nível ${recipe.requiredLevel}.", success = false)
            return
        }

        // 2. Se exigir inputs (crafting), valida se há itens no inventário
        if (recipe.inputs.isNotEmpty()) {
            for ((neededItemId, neededQty) in recipe.inputs) {
                val availableQty = state.inventory.firstOrNull { it.id == neededItemId }?.quantity ?: 0
                if (availableQty < neededQty) {
                    val neededItemName = GameDatabase.items.firstOrNull { it.id == neededItemId }?.name ?: neededItemId
                    stopSkillAction()
                    emitFeedback("Material insuficiente: faltam ${neededQty - availableQty}x $neededItemName para ${recipe.name}.", success = false)
                    return
                }
            }
        }

        // 3. Remove os insumos (se houver)
        var updatedState = state
        if (recipe.inputs.isNotEmpty()) {
            val newInventory = updatedState.inventory.toMutableList()
            for ((neededItemId, neededQty) in recipe.inputs) {
                val idx = newInventory.indexOfFirst { it.id == neededItemId }
                if (idx >= 0) {
                    val item = newInventory[idx]
                    if (item.quantity > neededQty) {
                        newInventory[idx] = item.copy(quantity = item.quantity - neededQty)
                    } else {
                        newInventory.removeAt(idx)
                    }
                }
            }
            updatedState = updatedState.copy(inventory = newInventory)
        }

        // 4. Adiciona o outputItem ao inventário
        updatedState = addItem(updatedState, recipe.outputItemId, recipe.outputQuantity)

        // 5. Concede xpReward na skill correspondente
        updatedState = grantXp(updatedState, recipe.skillType, recipe.xpReward.toLong())

        // 6. Chance de pet da skill correspondente (<= 0.09%)
        GameDatabase.pets.firstOrNull { it.skillType == recipe.skillType }?.let { pet ->
            if (rollPetDrop(pet)) updatedState = unlockPet(updatedState, pet)
        }

        _gameState.value = updatedState
    }

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

        // Retorno curto (ou ja processado): nada a simular, mas re-sincroniza o
        // timestamp para que o mesmo intervalo nunca seja contabilizado duas vezes.
        if (secondsOffline < MIN_OFFLINE_SECONDS) {
            _gameState.update { it.copy(lastSaveTimestamp = now) }
            return
        }
        val effectiveSeconds = min(secondsOffline, MAX_OFFLINE_SECONDS)

        scope.launch(Dispatchers.Default) {
            var state = _gameState.value

            when {
                state.currentMonsterId.isNotEmpty() -> {
                    monstersById[state.currentMonsterId]?.let { monster ->
                        val kills = effectiveSeconds / COMBAT_SECONDS_PER_KILL
                        val xp = combatXpFor(monster)

                        state = state.copy(
                            gold = state.gold + monster.goldDrop * kills,
                            sessionMonstersKilled = state.sessionMonstersKilled + kills.toInt()
                        )
                        state = grantXp(state, SkillType.ATTACK, xp * kills)
                        state = grantWeaponXp(state, xp / 2 * kills)
                        state = rollItemDropsBulk(state, monster.drops, kills)

                        if (monster.isBoss && kills > 0) {
                            val zone = GameDatabase.zones.firstOrNull { z -> z.monsters.any { it.id == monster.id } }
                            val zoneId = zone?.id ?: state.currentZoneId
                            state = state.copy(defeatedBossZoneIds = state.defeatedBossZoneIds + zoneId)
                        }

                        GameDatabase.pets.firstOrNull { it.skillType == SkillType.ATTACK }?.let { pet ->
                            if (!isPetOwned(state, pet) && rollBulk(pet.dropChance, kills)) {
                                state = unlockPet(state, pet)
                            }
                        }
                        state = DiscoveryManager.discoverMonster(state, monster.id)
                    }
                }

                state.activeRecipeId != null -> {
                    val recipeId = state.activeRecipeId ?: return@launch
                    val recipe = GameDatabase.skillRecipes.firstOrNull { it.id == recipeId }
                    if (recipe != null) {
                        val currentLvl = skillLevel(state, recipe.skillType)
                        if (currentLvl >= recipe.requiredLevel) {
                            val actions: Long = if (recipe.inputs.isEmpty()) {
                                effectiveSeconds
                            } else {
                                val maxPossible = recipe.inputs.minOfOrNull { (neededId, neededQty) ->
                                    val invQty = state.inventory.firstOrNull { it.id == neededId }?.quantity ?: 0
                                    invQty / neededQty
                                }?.toLong() ?: 0L
                                minOf(effectiveSeconds, maxPossible)
                            }

                            if (actions > 0) {
                                // Consome insumos
                                if (recipe.inputs.isNotEmpty()) {
                                    val newInventory = state.inventory.toMutableList()
                                    for ((neededId, neededQty) in recipe.inputs) {
                                        val totalNeeded = neededQty * actions.toInt()
                                        val idx = newInventory.indexOfFirst { it.id == neededId }
                                        if (idx >= 0) {
                                            val item = newInventory[idx]
                                            if (item.quantity > totalNeeded) {
                                                newInventory[idx] = item.copy(quantity = item.quantity - totalNeeded)
                                            } else {
                                                newInventory.removeAt(idx)
                                            }
                                        }
                                    }
                                    state = state.copy(inventory = newInventory)
                                }

                                // Adiciona itens produzidos e concede XP
                                state = addItem(state, recipe.outputItemId, (recipe.outputQuantity * actions).toInt())
                                state = grantXp(state, recipe.skillType, recipe.xpReward.toLong() * actions)

                                // Pet chance
                                GameDatabase.pets.firstOrNull { it.skillType == recipe.skillType }?.let { pet ->
                                    if (!isPetOwned(state, pet) && rollBulk(pet.dropChance, actions)) {
                                        state = unlockPet(state, pet)
                                    }
                                }
                            }

                            // Se faltou material durante o período offline, encerra a ação
                            if (recipe.inputs.isNotEmpty() && actions < effectiveSeconds) {
                                state = state.copy(activeRecipeId = null, activeSkill = null)
                            }
                        } else {
                            state = state.copy(activeRecipeId = null, activeSkill = null)
                        }
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

            // Atualiza timestamp EM RAM ao final, evitando duplo processamento.
            _gameState.value = state.copy(lastSaveTimestamp = now)
        }
    }
    // =====================================================================
    // 4) SMART SAVE
    // =====================================================================
    /**
     * Salva imediatamente (onPause/onStop/onDestroy).
     * Grava o lastSaveTimestamp EM RAM (via update) e em disco, de modo que um
     * futuro onResume calcule o tempo offline a partir do instante real da pausa.
     */
    fun forceSave() {
        val now = System.currentTimeMillis()
        _gameState.update { it.copy(lastSaveTimestamp = now) }
        val snapshot = _gameState.value
        scope.launch {
            SaveManager.saveGame(context, snapshot)
        }
    }
    /**
     * Chame em onResume: recalcula o tempo ausente e aplica o progresso offline
     * caso o processo tenha sobrevivido (minimizar -> retomar / tela travada).
     */
    fun resumeFromBackground() {
        processOfflineProgress()
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
    /**
     * Verifica se uma zona está liberada:
     * Uma zona N só fica liberada se N == 1 ou se o boss da zona N-1 foi derrotado.
     */
    fun isZoneUnlocked(zoneId: String): Boolean {
        val zones = GameDatabase.zones
        val index = zones.indexOfFirst { it.id == zoneId }
        if (index <= 0) return true
        val previousZone = zones[index - 1]
        return _gameState.value.defeatedBossZoneIds.contains(previousZone.id)
    }

    fun selectZone(zoneId: String) {
        if (!isZoneUnlocked(zoneId)) {
            emitFeedback("Zona bloqueada! Derrote o boss da zona anterior.", success = false)
            return
        }
        _gameState.update {
            it.copy(
                currentZoneId = zoneId,
                currentMonsterId = "",
                monsterCombatHp = null,
                combatMonsterId = null,
                lastDamageToMonster = null,
                lastDamageToPlayer = null
            )
        }
    }

    fun selectMonster(monsterId: String) {
        val zone = GameDatabase.zones.firstOrNull { z -> z.monsters.any { it.id == monsterId } }
        if (zone != null && !isZoneUnlocked(zone.id)) {
            emitFeedback("Zona bloqueada! Derrote o boss da zona anterior.", success = false)
            return
        }
        _gameState.update {
            it.copy(
                currentMonsterId = monsterId,
                currentZoneId = zone?.id ?: it.currentZoneId,
                activeSkill = null,
                activeRecipeId = null,
                monsterCombatHp = null,
                combatMonsterId = null,
                playerCurrentHp = it.playerCurrentHp ?: playerMaxHp(it),
                lastDamageToMonster = null,
                lastDamageToPlayer = null
            )
        }
    }

    /** Inicia o combate com o monstro especificado */
    fun startCombatWith(monsterId: String) = selectMonster(monsterId)

    fun setAutoEatHpPercentage(percentage: Int) {
        _gameState.update {
            it.copy(autoEatHpPercentage = percentage.coerceIn(1, 100))
        }
    }

    fun setAutoEatPercentage(percentage: Int) = setAutoEatHpPercentage(percentage)

    fun selectSkill(skillType: SkillType) {
        _gameState.update {
            it.copy(
                activeSkill = skillType.name,
                activeRecipeId = null,
                currentMonsterId = "",
                monsterCombatHp = null,
                combatMonsterId = null,
                lastDamageToMonster = null,
                lastDamageToPlayer = null
            )
        }
    }

    /**
     * Inicia a ação de coleta ou fabricação de uma receita de habilidade.
     * Cancela o combate atual (currentMonsterId = "") e define activeRecipeId e activeSkill.
     */
    fun startSkillAction(recipeId: String) {
        val recipe = GameDatabase.skillRecipes.firstOrNull { it.id == recipeId }
        if (recipe == null) {
            emitFeedback("Receita não encontrada.", success = false)
            return
        }

        val currentLvl = skillLevel(_gameState.value, recipe.skillType)
        if (currentLvl < recipe.requiredLevel) {
            emitFeedback("Nível insuficiente: requer ${recipe.skillType.name} nível ${recipe.requiredLevel}.", success = false)
            return
        }

        _gameState.update {
            it.copy(
                activeRecipeId = recipeId,
                activeSkill = recipe.skillType.name,
                currentMonsterId = "",
                monsterCombatHp = null,
                combatMonsterId = null,
                lastDamageToMonster = null,
                lastDamageToPlayer = null
            )
        }
    }

    /**
     * Interrompe a ação de skill ativa, resetando activeRecipeId e activeSkill para null.
     */
    fun stopSkillAction() {
        _gameState.update {
            it.copy(
                activeRecipeId = null,
                activeSkill = null
            )
        }
    }

    fun stopActions() {
        _gameState.update {
            it.copy(
                activeSkill = null,
                activeRecipeId = null,
                currentMonsterId = "",
                monsterCombatHp = null,
                combatMonsterId = null,
                lastDamageToMonster = null,
                lastDamageToPlayer = null
            )
        }
    }

    /**
     * Move um item entre o inventário e equippedItems[slot] atomicamente via _gameState.update.
     * Devolve o item anteriormente equipado no slot para o inventário (se houver).
     */
    fun equipItem(item: ItemData, slot: String) {
        _gameState.update { state ->
            val invIndex = state.inventory.indexOfFirst { it.id == item.id }
            if (invIndex < 0) return@update state

            val targetItem = state.inventory[invIndex]
            val updatedInventory = state.inventory.toMutableList()

            // 1. Remove 1 unidade do item do inventário
            if (targetItem.quantity > 1) {
                updatedInventory[invIndex] = targetItem.copy(quantity = targetItem.quantity - 1)
            } else {
                updatedInventory.removeAt(invIndex)
            }

            // 2. Se já havia item equipado no slot, devolve para o inventário
            val previousEquipped = state.equippedItems[slot]
            if (previousEquipped != null) {
                val existingIndex = updatedInventory.indexOfFirst { it.id == previousEquipped.id }
                if (existingIndex >= 0) {
                    val existing = updatedInventory[existingIndex]
                    updatedInventory[existingIndex] = existing.copy(quantity = existing.quantity + 1)
                } else {
                    updatedInventory.add(previousEquipped.copy(quantity = 1))
                }
            }

            // 3. Atualiza equippedItems
            val updatedEquipped = state.equippedItems.toMutableMap()
            updatedEquipped[slot] = targetItem.copy(quantity = 1, isDiscovered = true)

            state.copy(
                inventory = updatedInventory,
                equippedItems = updatedEquipped
            )
        }
    }

    /**
     * Sobrecarga para compatibilidade com chamadas legadas por itemId.
     */
    fun equipItem(itemId: String) {
        val state = _gameState.value
        val item = state.inventory.firstOrNull { it.id == itemId } ?: return
        val slot = item.slot ?: return
        equipItem(item, slot)
    }

    /** Remove o equipamento de um slot e devolve ao inventário. */
    fun unequipSlot(slotName: String) {
        _gameState.update { state ->
            val itemToUnequip = state.equippedItems[slotName] ?: return@update state
            val updatedEquipped = state.equippedItems.toMutableMap().apply { remove(slotName) }
            val updatedInventory = state.inventory.toMutableList()

            val existingIndex = updatedInventory.indexOfFirst { it.id == itemToUnequip.id }
            if (existingIndex >= 0) {
                val existing = updatedInventory[existingIndex]
                updatedInventory[existingIndex] = existing.copy(quantity = existing.quantity + 1)
            } else {
                updatedInventory.add(itemToUnequip.copy(quantity = 1))
            }

            state.copy(equippedItems = updatedEquipped, inventory = updatedInventory)
        }
    }

    /**
     * Para cada um dos 10 slots da classe especificada ("Melee", "Ranged", "Magic"),
     * equipa o melhor item disponível com base na soma de stats prioritários:
     * - Melee: DEF + HP
     * - Ranged: ATK + DEF
     * - Magic: ATK
     */
    fun autoEquipClass(cls: String) {
        val normalizedCls = cls.lowercase().trim()
        val targetSlots = when (normalizedCls) {
            "ranged", "magic" -> listOf("WEAPON", "AMMO", "HELMET", "CHEST", "LEGS", "BOOTS", "GLOVES", "AMULET", "RING_1", "CAPE")
            else -> listOf("WEAPON", "SHIELD", "HELMET", "CHEST", "LEGS", "BOOTS", "GLOVES", "AMULET", "RING_1", "CAPE")
        }

        _gameState.update { state ->
            val currentEquipped = state.equippedItems.toMutableMap()
            val currentInventory = state.inventory.toMutableList()

            fun score(stats: Map<String, Int>): Int = when (normalizedCls) {
                "melee" -> (stats["defense"] ?: 0) + (stats["hp"] ?: 0)
                "ranged" -> (stats["attack"] ?: 0) + (stats["defense"] ?: 0)
                "magic" -> (stats["attack"] ?: 0) + (stats["magic"] ?: 0)
                else -> (stats["defense"] ?: 0) + (stats["hp"] ?: 0)
            }

            // Desequipa slots mutuamente exclusivos entre classes
            if (normalizedCls in listOf("ranged", "magic") && currentEquipped.containsKey("SHIELD")) {
                val shield = currentEquipped.remove("SHIELD")
                if (shield != null) {
                    val idx = currentInventory.indexOfFirst { it.id == shield.id }
                    if (idx >= 0) {
                        currentInventory[idx] = currentInventory[idx].copy(quantity = currentInventory[idx].quantity + 1)
                    } else {
                        currentInventory.add(shield.copy(quantity = 1))
                    }
                }
            } else if (normalizedCls == "melee" && currentEquipped.containsKey("AMMO")) {
                val ammo = currentEquipped.remove("AMMO")
                if (ammo != null) {
                    val idx = currentInventory.indexOfFirst { it.id == ammo.id }
                    if (idx >= 0) {
                        currentInventory[idx] = currentInventory[idx].copy(quantity = currentInventory[idx].quantity + 1)
                    } else {
                        currentInventory.add(ammo.copy(quantity = 1))
                    }
                }
            }

            // Para cada um dos 10 slots, encontra e equipa o melhor item
            for (slot in targetSlots) {
                val compatibleIndices = currentInventory.indices.filter { idx ->
                    isSlotCompatible(currentInventory[idx].slot, slot)
                }

                val bestInvItem = compatibleIndices
                    .map { currentInventory[it] }
                    .maxByOrNull { score(it.stats) }

                val currentlyEquipped = currentEquipped[slot]
                val currentScore = currentlyEquipped?.let { score(it.stats) } ?: -1
                val bestInvScore = bestInvItem?.let { score(it.stats) } ?: -1

                if (bestInvItem != null && bestInvScore > currentScore) {
                    // Remove 1 unidade do inventário
                    val invIdx = currentInventory.indexOfFirst { it.id == bestInvItem.id }
                    if (invIdx >= 0) {
                        val item = currentInventory[invIdx]
                        if (item.quantity > 1) {
                            currentInventory[invIdx] = item.copy(quantity = item.quantity - 1)
                        } else {
                            currentInventory.removeAt(invIdx)
                        }
                    }

                    // Devolve o anterior ao inventário
                    if (currentlyEquipped != null) {
                        val existingIdx = currentInventory.indexOfFirst { it.id == currentlyEquipped.id }
                        if (existingIdx >= 0) {
                            currentInventory[existingIdx] = currentInventory[existingIdx].copy(
                                quantity = currentInventory[existingIdx].quantity + 1
                            )
                        } else {
                            currentInventory.add(currentlyEquipped.copy(quantity = 1))
                        }
                    }

                    // Equipa o item vencedor
                    currentEquipped[slot] = bestInvItem.copy(quantity = 1, isDiscovered = true)
                }
            }

            state.copy(
                inventory = currentInventory,
                equippedItems = currentEquipped
            )
        }
    }

    private fun isSlotCompatible(itemSlot: String?, targetSlot: String): Boolean {
        if (itemSlot == null) return false
        if (itemSlot.equals(targetSlot, ignoreCase = true)) return true
        if (targetSlot.equals("RING_1", ignoreCase = true) && (itemSlot.equals("RING", ignoreCase = true) || itemSlot.equals("RING_2", ignoreCase = true))) return true
        return false
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
    // ATRIBUTOS TOTAIS DE COMBATE & DANO
    // =====================================================================
    /**
     * Soma o bonus de um stat ("attack", "defense", "agility", "hp") de TODOS
     * os equipamentos equipados. Sem isso, comprar/forjar nao afeta o combate.
     */
    private fun equipmentBonus(s: GameState, stat: String): Int =
        s.equippedItems.values.sumOf { it.stats[stat] ?: 0 }

    /** Nível de Attack/Strength + bônus de equipamentos Melee. */
    fun totalMeleeAttack(state: GameState = _gameState.value): Int {
        val stats = state.playerStats
        val equipBonus = state.equippedItems.values
            .filter { item ->
                item.category in listOf("SWORDS", "AXES", "HEAVY_ARMOR") ||
                (item.category == "SHIELDS_ACCESSORIES" && !item.id.contains("ranged") && !item.id.contains("magic"))
            }
            .sumOf { (it.stats["attack"] ?: 0) + (it.stats["strength"] ?: 0) }
        return stats.attackLevel + stats.strengthLevel + equipBonus
    }

    /** Nível de Ranged + bônus de equipamentos Ranged. */
    fun totalRangedAttack(state: GameState = _gameState.value): Int {
        val stats = state.playerStats
        val equipBonus = state.equippedItems.values
            .filter { item ->
                item.category in listOf("RANGED_WEAPONS_AMMO", "LEATHER_ARMOR") ||
                item.id.contains("ranged")
            }
            .sumOf { (it.stats["ranged"] ?: it.stats["attack"]) ?: 0 }
        return stats.rangedLevel + equipBonus
    }

    /** Nível de Magic + bônus de equipamentos Magic. */
    fun totalMagicAttack(state: GameState = _gameState.value): Int {
        val stats = state.playerStats
        val equipBonus = state.equippedItems.values
            .filter { item ->
                item.category in listOf("MAGIC_WEAPONS", "CLOTH_ARMOR") ||
                item.id.contains("magic")
            }
            .sumOf { (it.stats["magic"] ?: it.stats["attack"]) ?: 0 }
        return stats.magicLevel + equipBonus
    }

    /** Nível de Defense + bônus de equipamentos de defesa. */
    fun totalDefense(state: GameState = _gameState.value): Int {
        val stats = state.playerStats
        val equipBonus = state.equippedItems.values.sumOf { it.stats["defense"] ?: 0 }
        return stats.defenseLevel + equipBonus
    }

    private fun playerAttack(s: GameState): Int {
        val weapon = s.equippedItems["WEAPON"]
        return when (weapon?.category) {
            "RANGED_WEAPONS_AMMO" -> totalRangedAttack(s)
            "MAGIC_WEAPONS" -> totalMagicAttack(s)
            else -> totalMeleeAttack(s)
        }
    }

    private fun playerDefense(s: GameState): Int = totalDefense(s)

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
            "MAGIC_WEAPONS" -> SkillType.MAGIC
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
                val dbItem = GameDatabase.items.firstOrNull { it.id == entry.itemId }
                val rarity = dbItem?.tier?.name ?: "COMMON"
                val lootKey = "${entry.itemId}_$rarity"
                val currentQty = state.sessionLoot[lootKey] ?: 0
                state = state.copy(sessionLoot = state.sessionLoot + (lootKey to (currentQty + qty)))
            }
        }
        return state
    }

    /** Versão em lote para offline: estimativa por expectativa (chance * n). */
    private fun rollItemDropsBulk(state0: GameState, drops: List<DropTableEntry>, kills: Long): GameState {
        var state = state0
        for (entry in drops) {
            val expectedQty = (entry.dropChance * kills).toLong()
            if (expectedQty > 0) {
                val qty = expectedQty.toInt()
                state = addItem(state, entry.itemId, qty)
                val dbItem = GameDatabase.items.firstOrNull { it.id == entry.itemId }
                val rarity = dbItem?.tier?.name ?: "COMMON"
                val lootKey = "${entry.itemId}_$rarity"
                val currentQty = state.sessionLoot[lootKey] ?: 0
                state = state.copy(sessionLoot = state.sessionLoot + (lootKey to (currentQty + qty)))
            }
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

