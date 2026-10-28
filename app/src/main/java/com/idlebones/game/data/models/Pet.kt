/*
 * Project Idle RPG - Idle Bones
 */
package com.idlebones.game.data.models

/**
 * Representa um pet obtível no jogo.
 * @param id Identificador único do pet
 * @param skillType Habilidade à qual o pet está vinculado
 * @param name Nome do pet
 * @param bonusDescription Descrição do bônus fornecido
 * @param isDiscovered Se o pet já foi descoberto pelo jogador
 * @param dropChance Chance de drop (padrão: 0.0009 = 0.09%)
 */
data class Pet(
    val id: String = "",
    val skillType: SkillType = SkillType.WOODCUTTING,
    val name: String = "",
    val bonusDescription: String = "",
    val isDiscovered: Boolean = false,
    val dropChance: Double = 0.0009
)
