/*
 * Project Idle RPG - Idle Bones
 * Helpers de UI compartilhados para habilidades.
 */
package com.idlebones.game.ui.screens

import com.idlebones.game.data.models.SkillType
import com.idlebones.game.data.save.PlayerStatsData

/** Nome de exibição em PT-BR para cada SkillType. */
internal fun skillDisplayName(skill: SkillType): String = when (skill) {
    SkillType.WOODCUTTING -> "Lenhador"
    SkillType.MINING -> "Minera\u00e7\u00e3o"
    SkillType.FISHING -> "Pesca"
    SkillType.FIREMAKING -> "Pirotecnia"
    SkillType.SMITHING -> "Ferraria"
    SkillType.FLETCHING -> "Flecharia"
    SkillType.RUNECRAFTING -> "Runecraft"
    SkillType.COOKING -> "Culin\u00e1ria"
    SkillType.ALCHEMY -> "Alquimia"
    SkillType.PRAYER -> "Ora\u00e7\u00e3o"
    SkillType.AGILITY -> "Agilidade"
    SkillType.ATTACK -> "Ataque"
    SkillType.STRENGTH -> "For\u00e7a"
    SkillType.DEFENSE -> "Defesa"
    SkillType.RANGED -> "Arquearia"
    SkillType.MAGIC -> "Magia"
}

/** (level, xp) de uma skill dentro do PlayerStatsData. */
internal fun levelXp(stats: PlayerStatsData, skill: SkillType): Pair<Int, Long> = when (skill) {
    SkillType.WOODCUTTING -> stats.woodcuttingLevel to stats.woodcuttingXp
    SkillType.MINING -> stats.miningLevel to stats.miningXp
    SkillType.FISHING -> stats.fishingLevel to stats.fishingXp
    SkillType.FIREMAKING -> stats.firemakingLevel to stats.firemakingXp
    SkillType.SMITHING -> stats.smithingLevel to stats.smithingXp
    SkillType.FLETCHING -> stats.fletchingLevel to stats.fletchingXp
    SkillType.RUNECRAFTING -> stats.runecraftingLevel to stats.runecraftingXp
    SkillType.COOKING -> stats.cookingLevel to stats.cookingXp
    SkillType.ALCHEMY -> stats.alchemyLevel to stats.alchemyXp
    SkillType.PRAYER -> stats.prayerLevel to stats.prayerXp
    SkillType.AGILITY -> stats.agilityLevel to stats.agilityXp
    SkillType.ATTACK -> stats.attackLevel to stats.attackXp
    SkillType.STRENGTH -> stats.strengthLevel to stats.strengthXp
    SkillType.DEFENSE -> stats.defenseLevel to stats.defenseXp
    SkillType.RANGED -> stats.rangedLevel to stats.rangedXp
    SkillType.MAGIC -> stats.magicLevel to stats.magicXp
}

/** Ícone temático para cada SkillType. */
internal fun skillIcon(skill: SkillType): String = when (skill) {
    SkillType.ATTACK -> "⚔️"
    SkillType.STRENGTH -> "💪"
    SkillType.DEFENSE -> "🛡️"
    SkillType.RANGED -> "🏹"
    SkillType.MAGIC -> "🔮"
    SkillType.PRAYER -> "✨"
    SkillType.AGILITY -> "⚡"
    SkillType.MINING -> "⛏️"
    SkillType.WOODCUTTING -> "🪓"
    SkillType.FISHING -> "🎣"
    SkillType.COOKING -> "🍳"
    SkillType.SMITHING -> "🔨"
    SkillType.RUNECRAFTING -> "📜"
    SkillType.FLETCHING -> "🎯"
    SkillType.FIREMAKING -> "🔥"
    SkillType.ALCHEMY -> "🧪"
}
