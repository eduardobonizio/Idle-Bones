/*
 * Project Idle RPG - Idle Bones
 */
package com.idlebones.game.data.models

/**
 * Estatísticas de habilidades do jogador.
 * Nível máximo: 130 por habilidade.
 * @param level Nível atual da habilidade
 * @param currentXp XP acumulado atual
 */
data class SkillData(
    val level: Int = 1,
    val currentXp: Long = 0L
)

/**
 * Dados completos do jogador para todas as 16 habilidades.
 */
data class PlayerStats(
    val woodcutting: SkillData = SkillData(),
    val mining: SkillData = SkillData(),
    val fishing: SkillData = SkillData(),
    val firemaking: SkillData = SkillData(),
    val smithing: SkillData = SkillData(),
    val fletching: SkillData = SkillData(),
    val runecrafting: SkillData = SkillData(),
    val cooking: SkillData = SkillData(),
    val alchemy: SkillData = SkillData(),
    val prayer: SkillData = SkillData(),
    val agility: SkillData = SkillData(),
    val attack: SkillData = SkillData(),
    val strength: SkillData = SkillData(),
    val defense: SkillData = SkillData(),
    val ranged: SkillData = SkillData(),
    val magic: SkillData = SkillData()
)
