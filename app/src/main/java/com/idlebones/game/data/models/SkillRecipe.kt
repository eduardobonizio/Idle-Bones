/*
 * Project Idle RPG - Idle Bones
 * Modelo de Receita / Recurso de Habilidades
 */
package com.idlebones.game.data.models

/**
 * Modelo de Recurso ou Receita de Skill para coleta e artesanato/crafting.
 *
 * @param id Identificador único da receita/recurso (ex: "mine_copper", "cook_shrimp")
 * @param skillType Habilidade necessária (ex: MINING, COOKING, WOODCUTTING, etc.)
 * @param name Nome exibido do recurso ou receita
 * @param requiredLevel Nível necessário na habilidade para executar a ação
 * @param xpReward Quantidade de XP concedida por ciclo concluído
 * @param inputs Insumos/ingredientes necessários do inventário (vazio se for coleta pura)
 * @param outputItemId ID do item produzido/coletado
 * @param outputQuantity Quantidade produzida por ação (default 1)
 */
data class SkillRecipe(
    val id: String,
    val skillType: SkillType,
    val name: String,
    val requiredLevel: Int,
    val xpReward: Int,
    val inputs: Map<String, Int> = emptyMap(),
    val outputItemId: String,
    val outputQuantity: Int = 1
)

/**
 * Typealias para compatibilidade semântica com recursos de coleta.
 */
typealias SkillResource = SkillRecipe
