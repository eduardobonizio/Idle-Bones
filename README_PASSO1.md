# Project Idle RPG - Idle Bones
# Passo 1: Estrutura de Dados e Persistência

## Estrutura Criada

### Data Models
- `SkillType.kt` - Enum com 16 habilidades
- `EquipmentSlot.kt` - Enum com slots de equipamento
- `ItemCategory.kt` - Enum com categorias de itens
- `ItemTier.kt` - Enum com 10 tiers e multiplicadores
- `Item.kt` - Data class para itens
- `Pet.kt` - Data class para pets
- `PlayerStats.kt` - Data class para stats e níveis

### Save System
- `GameState.kt` - GameState e classes serializáveis (PlayerStatsData, ItemData, PetData)
- `SaveManager.kt` - Singleton para salvar/carregar em JSON (savegame.json), tratamento de exceções, mapeadores

## Características
- 100% Kotlin + Jetpack Compose
- Sem bibliotecas externas pesadas
- Usa kotlinx.serialization (padrão moderno Android)
- Salva em `context.filesDir/savegame.json`
- Campo `version: 1` para versionamento
- Métodos assíncronos com coroutines
- Tratamento robusto de erros
