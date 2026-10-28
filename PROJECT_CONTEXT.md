# PROJECT_CONTEXT.md — Arquitetura Técnica (Idle Bones)

> Resumo compacto do estado atual. Para balanceamento numérico, ver `BALANCE.md`.

## 1. Visão Geral

- **Linguagem/UI:** Kotlin + Jetpack Compose (Material3).
- **Concorrência:** Kotlin Coroutines; estado reativo via `StateFlow`.
- **Persistência:** JSON (kotlinx.serialization) em `SaveManager` (arquivo interno).
- **Arquitetura:** engine único (`GameEngine`) expõe `StateFlow<GameState>`; UI é reativa e sem lógica de jogo.
- **Estrutura de pastas:** `data/models` (domínio), `data/generator` (`GameDatabaseGenerator`, `GameDatabase`, `DiscoveryManager`), `data/economy` (`ShopSystem`), `data/save` (`GameState`, `SaveManager`), `engine` (`GameEngine`), `ui/screens` + `ui/components` + `ui/theme`.

## 2. Fluxo do Ciclo de Vida (`lastSaveTimestamp`)

`GameState.lastSaveTimestamp` = instante (ms) da última gravação de tempo válida. É a âncora de todo o cálculo offline.

- **`bootstrap()`** (chamado no init do `GameEngine`, ex. `onCreate`):
  `loadGame()` → applica `processOfflineProgress()` → `startLoops()` (combate/skills + autosave).
- **`forceSave()`** (`onPause`/`onStop`/`onDestroy`):
  1. `_gameState.update { copy(lastSaveTimestamp = now) }` (**RAM**);
  2. `SaveManager.saveGame(snapshot)` (**disco**).
  Gravar o timestamp em RAM é essencial: no retorno sem recriação, o cálculo parte do instante real da pausa.
- **`resumeFromBackground()`** (`onResume`):
  chama `processOfflineProgress()`. Aplica catch-up se o processo sobreviveu (minimizar/travar tela).
- **Anti-duplicação:** `processOfflineProgress()` sempre atualiza `lastSaveTimestamp = now` ao final (e também em retornos curtos), garantindo que o mesmo intervalo nunca seja contabilizado 2×. `bootstrap` (processo morto) e `resumeFromBackground` (processo vivo) usam a **mesma lógica** e o **mesmo timestamp**.
- **Autosave:** loop a cada `AUTO_SAVE_MS` (30s).

## 3. Estruturas de Dados Chave

**`GameState`** (raiz serializável):
`playerStats: PlayerStatsData`, `inventory: List<ItemData>`, `equippedItems: Map<String, ItemData>` (chave = nome do slot), `activePet: PetData?`, `currentZoneId`, `currentMonsterId`, `activeSkill: String?`, `gold: Long`, `discoveredMonsterIds/ZoneIds: Set<String>`, `pets: List<PetData>`, `lastSaveTimestamp: Long`.
Transientes de combate: `playerCurrentHp`, `monsterCombatHp`, `combatMonsterId` (persistidos para continuidade entre ticks/resume).

**`PlayerStatsData`** (16 skills, cada uma com `level` + `xp`):
WOODCUTTING, MINING, FISHING, FIREMAKING, SMITHING, FLETCHING, RUNECRAFTING, COOKING, ALCHEMY, PRAYER, AGILITY, ATTACK, STRENGTH, DEFENSE, RANGED, MAGIC.

**Versões serializáveis:** `ItemData` (id, name, category, slot?, tier, stats: Map<String,Int>, isDiscovered, quantity), `PetData` (id, skillType, name, bonusDescription, isDiscovered, dropChance).

**`GameDatabase`** (em `data/generator/DiscoveryManager.kt`), objetos `lazy` gerados por `GameDatabaseGenerator`:
`zones: List<Zone>` (20 zonas, 6 monstros + 1 boss cada), `items: List<Item>`, `pets: List<Pet>` (16, um por skill).

## 4. Regras do Engine

- **Loop de tick:** `TICK_MS = 1000L`. A cada tick:
  - `currentMonsterId` setado → resolve combate (1 turno);
  - senão `activeSkill` setado → processa coleta da skill.
- **Combate:** dano `max(1, atk−def)`, crítico até `CRIT_CHANCE_CAP=0.30` aplicando `CRIT_DAMAGE_MULT=1.5` (`+1%`/`AGI_PER_CRIT=40`). Stats = nível + base + **bônus de equipamento** (`attack/defense/agility/hp`).
- **XP:** combate concede `monsterLevel × 10`; coleta `SKILL_XP_PER_ACTION=20` por ação. Curva RuneScape até `MAX_LEVEL=130`.
- **Offline (batch):** simula em `Dispatchers.Default` sem loop por segundo — aplica ouro, XP, drops (`rollItemDropsBulk`), pets e descobertas de uma vez, publicando um snapshot único.
- **Limites offline:** `MIN_OFFLINE_SECONDS=30` (abaixo disso só re-sincroniza) e `MAX_OFFLINE_SECONDS=12h` (cap). Estimativa de combate: `COMBAT_SECONDS_PER_KILL`.

## 5. Convenções de IDs e Tiers

- **Recursos** (coleta): `res_ore_tierN`, `res_wood_tierN`, `res_fish_tierN` — `N` em **1..20**. `N` deriva do nível da skill: `tierIndexForLevel(level)=((level−1)/7+1)`.
- **Consumíveis:** `cons_potion_N` (N: 1..20).
- **Equipamentos** (IDs determinísticos por tier):
  - Arma: `eq_wpn_<TIER>`
  - Armadura: `eq_arm_<TIER>_<SLOT>` (HELMET, CHEST, LEGS, BOOTS, GLOVES)
  - Acessórios: `eq_acc_amu_<TIER>`, `eq_acc_ring_<TIER>`, `eq_acc_cape_<TIER>`
- **Zona→Tier:** `tierOf(zone) = min(zone/3, numTiers−1)`.
- **Tiers (10):** COMMON 1.0x, UNCOMMON 1.3x, RARE 1.7x, SUPER_RARE 2.2x, EPIC 2.8x, LEGENDARY 3.5x, MYTHIC 4.3x, ANCESTRAL 5.2x, DIVINE 6.5x, GOD 8.0x.
- **Pets:** `pet_<skill>`.
- **Zonas/Monstros:** `zone_1..20`; `monster_zoneZ_boss` / `monster_zoneZ_commonM`.
