/*
 * Project Idle RPG - Idle Bones
 * ShopSystem: logica pura de economia (catalogo, precos e crafting).
 *
 * Objetivo de balanceamento (progressao ate nivel 130):
 *  - O preco de compra escala com (a) o multiplicador do tier e (b) o nivel para
 *    o qual aquele tier e "esperado". Assim, itens de tier baixo ficam baratos
 *    mesmo com jogador rico, e itens de tier alto sao um investimento real.
 *  - A venda paga uma fracao do preco de compra (evita loop compra->venda lucrativo).
 *  - O crafting custa recursos coletados + Gold, e gera o equipamento do tier alvo,
 *    substituindo a "sorte" de drop por esforco de coleta.
 */
package com.idlebones.game.data.economy

import com.idlebones.game.data.generator.GameDatabase
import com.idlebones.game.data.generator.GameDatabaseGenerator
import com.idlebones.game.data.models.EquipmentSlot
import com.idlebones.game.data.models.Item
import com.idlebones.game.data.models.ItemCategory
import com.idlebones.game.data.models.ItemTier
import com.idlebones.game.data.models.SkillType
import kotlin.math.pow
import kotlin.math.roundToLong

/** Tipo de mercador / secao da cidade. */
enum class ShopType(val displayName: String, val description: String) {
    BLACKSMITH("Ferreiro", "Armas e armaduras forjadas em metal."),
    ALCHEMIST("Alquimista", "Po\u00e7\u00f5es e reagentes."),
    FORGE("Forja", "Refina recursos em equipamentos de tier superior.")
}

/** Um item a venda na loja, com o preco ja calculado. */
data class ShopOffer(
    val item: Item,
    val buyPrice: Long,
    val sellPrice: Long
)

/** Receita de craft: recursos + gold para produzir um item resultante. */
data class CraftRecipe(
    val id: String,
    val resultItemId: String,
    val requiredTier: ItemTier,
    val goldCost: Long,
    val ingredients: Map<String, Int>
) {
    val resultItem: Item?
        get() = GameDatabase.items.firstOrNull { it.id == resultItemId }
}

object ShopSystem {

    /** Fracao do valor de compra devolvida na venda (60%). */
    private const val SELL_RATIO = 0.6

    /** Numero de tiers disponiveis. */
    private val tierCount = ItemTier.values().size

    /**
     * Nivel-equivalencia esperado para um tier (0..9 -> ~1..130).
     * Usado para agrupar itens por "momento da progressao".
     */
    private fun levelForTier(tier: ItemTier): Int {
        val t = tier.ordinal // 0..9
        return 1 + (t * 130 / (tierCount - 1))
    }

    /**
     * Zona representativa de um tier (1..20). E a primeira zona cujo
     * tierOf(zona) == tier. A renda dessa zona ancora o preco do equipamento.
     */
    private fun representativeZoneForTier(tier: ItemTier): Int {
        val t = tier.ordinal
        for (z in 1..20) {
            val zoneTier = minOf(z / 3, tierCount - 1)
            if (zoneTier == t) return z
        }
        return 20
    }

    /**
     * Quantas mortes no monstro-comum da zona do tier sao necessarias para
     * comprar 1 peca de equipamento daquele tier. Cresce suavemente (3 -> ~14)
     * para que tiers altos exijam mais esforco sem virar parede.
     */
    private fun killsNeededForTier(tier: ItemTier): Double = 3.0 + tier.ordinal * 1.25

    /** Ouro dropado por 1 monstro comum na zona ancorada ao tier. */
    private fun goldPerKillForTier(tier: ItemTier): Long {
        val zoneIndex = representativeZoneForTier(tier)
        val zone = GameDatabase.zones.getOrNull(zoneIndex - 1)
        val common = zone?.monsters?.firstOrNull { !it.isBoss }
        return common?.goldDrop ?: 10L
    }

    /**
     * Preco de compra de um item.
     *
     * Equipamentos: ancorados na renda da zona do tier, de modo que o tempo
     * de farm por peca fique estavel (~0.4min no COMMON ate ~18min no GOD).
     *   priceUnit = goldPerKill(tier) * killsNeeded(tier)
     * Recursos/consumiveis: fracao pequena do precoUnit (nao competem com equip).
     */
    fun buyPriceOf(item: Item): Long {
        val goldKill = goldPerKillForTier(item.tier)
        val priceUnit = goldKill * killsNeededForTier(item.tier)
        return when (item.category) {
            ItemCategory.RESOURCES -> {
                // ~18% do valor de uma peca de equipamento do mesmo tier.
                (priceUnit * 0.18).roundToLong().coerceAtLeast(1L)
            }
            ItemCategory.CONSUMABLES -> {
                // ~25% do valor de uma peca (pocoes de cura).
                (priceUnit * 0.25).roundToLong().coerceAtLeast(1L)
            }
            else -> {
                // Equipamentos: preco da peca, modulado pelo multiplicador do tier
                // (tiers mais raros custam um pouco mais DENTRO da mesma zona).
                val tierBias = item.tier.multiplier.pow(0.35)
                (priceUnit * tierBias).roundToLong().coerceAtLeast(5L)
            }
        }
    }

    /** Preco de venda (fragmento do preco de compra). */
    fun sellPriceOf(item: Item): Long =
        (buyPriceOf(item) * SELL_RATIO).roundToLong().coerceAtLeast(1L)

    /**
     * Ouro que um jogador ganha por luta em uma zona (para referencia de balanceamento).
     * Aproxima o goldDrop do monstro comum da zona.
     */
    fun goldPerKillAtZone(zoneIndex: Int): Long {
        val zone = GameDatabase.zones.getOrNull(zoneIndex - 1) ?: return 0L
        val common = zone.monsters.firstOrNull { !it.isBoss } ?: return 0L
        return common.goldDrop
    }

    /**
     * Tiers disponiveis para loja do jogador em seu nivel atual.
     * Sempre inclui o tier esperado e o anterior; nunca "vaza" tiers futuros.
     */
    fun availableTiersForLevel(level: Int): List<ItemTier> {
        val tiers = ItemTier.values()
        val expectedIndex = tiers.indexOfFirst { levelForTier(it) >= level }
            .let { if (it < 0) tiers.lastIndex else it }
            .coerceAtLeast(0)
        val maxIndex = (expectedIndex + 1).coerceAtMost(tiers.lastIndex)
        val minIndex = (expectedIndex - 1).coerceAtLeast(0)
        return tiers.slice(minIndex..maxIndex)
    }

    /**
     * Catalogo de itens de uma loja para o nivel do jogador.
     * - BLACKSMITH: armas + pecas de armadura dos tiers disponiveis.
     * - ALCHEMIST: consumiveis + recursos dos tiers disponiveis.
     */
    fun catalogForShop(shop: ShopType, playerLevel: Int): List<ShopOffer> {
        val tiers = availableTiersForLevel(playerLevel).toSet()

        when (shop) {
            ShopType.BLACKSMITH -> {
                return GameDatabase.items
                    .filter { it.slot != null }
                    .filter { it.tier in tiers }
                    .sortedWith(compareBy({ it.tier.ordinal }, { it.category.name }, { it.name }))
                    .map { offerOf(it) }
            }
            ShopType.ALCHEMIST -> {
                return GameDatabase.items
                    .filter { it.category == ItemCategory.CONSUMABLES || it.category == ItemCategory.RESOURCES }
                    .filter { it.tier in tiers }
                    .sortedWith(compareBy({ it.category.name }, { it.tier.ordinal }, { it.name }))
                    .take(40)
                    .map { offerOf(it) }
            }
            ShopType.FORGE -> return emptyList()
        }
    }

    private fun offerOf(item: Item): ShopOffer =
        ShopOffer(item = item, buyPrice = buyPriceOf(item), sellPrice = sellPriceOf(item))

    // ---------------------------------------------------------------------
    // CRAFTING
    // ---------------------------------------------------------------------

    /**
     * Receita principal de craft da Forja para o nivel atual:
     * converte recursos coletados em um equipamento do tier alvo.
     */
    fun craftRecipesForLevel(playerLevel: Int): List<CraftRecipe> {
        val tiers = availableTiersForLevel(playerLevel)
        val targetTier = tiers.last() // foco no tier mais alto liberado
        val t = targetTier.ordinal
        // Recursos exigidos: converter N de cada material do tier-base em 1 do tier alvo.
        val oreId = "res_ore_tier${(t * 2 + 1).coerceIn(1, 20)}"
        val woodId = "res_wood_tier${(t * 2 + 1).coerceIn(1, 20)}"
        val fishId = "res_fish_tier${(t * 2 + 1).coerceIn(1, 20)}"

        val resultIds = listOf(
            "eq_wpn_${targetTier.name}",
            "eq_arm_${targetTier.name}_${EquipmentSlot.CHEST.name}",
            "eq_acc_amu_${targetTier.name}"
        ).filter { id -> GameDatabase.items.any { it.id == id } }

        return resultIds.mapIndexed { idx, resultId ->
            val item = GameDatabase.items.first { it.id == resultId }
            val goldCost = (buyPriceOf(item) * 0.5).roundToLong().coerceAtLeast(10L)
            CraftRecipe(
                id = "recipe_${t}_${idx}",
                resultItemId = resultId,
                requiredTier = targetTier,
                goldCost = goldCost,
                ingredients = mapOf(oreId to 10, woodId to 5, fishId to 5)
            )
        }
    }

    /** Nome do recurso legivel para exibir na receita. */
    fun resourceDisplayName(itemId: String): String =
        GameDatabase.items.firstOrNull { it.id == itemId }?.name ?: itemId

    /** XP de craft concedida por forja bem-sucedida (skill de Ferraria). */
    fun craftXpReward(tier: ItemTier): Long = (tier.multiplier * 50).roundToLong()

    /** Skill beneficiada pelo craft (Ferraria). Util para o engine. */
    val craftSkill: SkillType = SkillType.SMITHING

    /** XP por nivel (reexport para a UI nao depender de generator diretamente). */
    fun xpForLevel(level: Int): Long = GameDatabaseGenerator.xpForLevel(level)
}
