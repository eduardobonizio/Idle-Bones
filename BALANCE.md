# BALANCE.md — Modelo Matemático e Balanceamento Econômico

> **Project Idle RPG — Idle Bones**
> Documento de referência das fórmulas, curvas e decisões de balanceamento do jogo.
> Última atualização: alinhado ao estado atual de `GameDatabaseGenerator.kt`, `ShopSystem.kt` e `GameEngine.kt`.

---

## 1. Visão Geral do Balanceamento

O jogo fecha um ciclo econômico auto-sustentável:

```
Combate (TTK) ──► Ouro/min ──► Compra/Forja de equipamento ──► Stats maiores ──► TTK menor ──► Ouro/min maior
```

O objetivo de design é que o **tempo de farm por peça** cresça de forma **suave e previsível** (idle saudável), sem paredes que travem a progressão e sem upgrades triviais.

### 1.1 Níveis-chave (medidos por simulação)

Valores simulados com o jogador **equipado no tier da zona atual** (cenário esperado de jogo), monstro comum representativo da zona.

| Nível | Zona | Tier | TTK | Ouro/min | Farm por peça | Set completo (7 peças) |
|------:|-----:|------|----:|---------:|--------------:|-----------------------:|
| 1   | 1  | COMMON (1.0x)     | 7 s  | 171 | 0.4 min  | ~2 min   |
| 30  | 5  | UNCOMMON (1.3x)   | 91 s | 204 | 3.4 min  | ~24 min  |
| 65  | 10 | SUPER_RARE (2.2x) | 87 s | 448 | 10.7 min | ~75 min  |
| 100 | 16 | LEGENDARY (3.5x)  | 77 s | 787 | 16.9 min | ~119 min |
| 130 | 20 | MYTHIC (4.3x)     | 81 s | 963 | 20.3 min | ~142 min |

**Leitura dos números:**

- **TTK estável (7–91 s):** o combate nunca "trava". O pico de 91 s no nível 30 é a transição para UNCOMMON (defesa do monstro cresce um pouco mais rápido que o ataque básico); equipar peças da zona reduz esse tempo ativamente.
- **Ouro/min crescente (171 → 963):** a renda acompanha a progressão — nunca fica para trás do custo.
- **Farm por peça (0.4 → 20 min):** crescimento monotônico e previsível; o jogador nunca espera horas por uma única peça.
- **Set completo (2 → 142 min):** um upgrade completo é investimento de longo prazo, feito **peça a peça**, nunca "tudo de uma vez".

> **Faixa-alvo de design:** peça única entre ~0.5 min (early) e ~20 min (endgame); set completo entre ~2 min e ~2.5 h. Qualquer conteúdo novo deve permanecer dentro (ou próximo) dessa faixa.

---

## 2. Fórmulas Principais

### 2.1 Curva de XP (estilo RuneScape, adaptada até o nível 130)

A XP total necessária para alcançar um nível `L` é a soma do custo por nível, dividida por 4:

```
XP_total(L) = floor( ( Σ_{i=1}^{L-1} floor( i + 300 · 2^(i/7) ) ) / 4 )

xpForLevel(L):
    se L ≤ 1      → 0
    se L > 130    → xpForLevel(130)   (cap)
    senão         → floor( Σ ... / 4 )
```

- Crescimento aproximadamente **exponencial** (base `2^(1/7)` por nível).
- `xpToNextLevel(L) = xpForLevel(L+1) − xpForLevel(L)`; no nível 130 retorna um valor-sentinela.
- **Uso:** todas as 16 skills usam a mesma curva; o combate concede `XP = monsterLevel × 10`.

### 2.2 Multiplicadores de Tier de Itens (1.0x → 8.0x)

Cada `ItemTier` define um multiplicador aplicado a stats e (parcialmente) a preços:

| Tier         | Multiplicador | Cor (hex)  |
|--------------|--------------:|-----------|
| COMMON       | 1.0x          | `#9E9E9E` |
| UNCOMMON     | 1.3x          | `#4CAF50` |
| RARE         | 1.7x          | `#2196F3` |
| SUPER_RARE   | 2.2x          | `#9C27B0` |
| EPIC         | 2.8x          | `#FF9800` |
| LEGENDARY    | 3.5x          | `#F44336` |
| MYTHIC       | 4.3x          | `#E91E63` |
| ANCESTRAL    | 5.2x          | `#00BCD4` |
| DIVINE       | 6.5x          | `#ECEFF1` |
| GOD          | 8.0x          | `#FFD700` |

- `stat_do_item = basePower × tier.multiplier`, com `basePower = (tier.ordinal + 1) × 10`.
- Na UI, o `TierBadge` coloriza pelo `colorHex` e exibe o multiplicador (modo detalhado).

### 2.3 Cálculo de preços de compra e venda

O preço é **ancorado na renda da zona equivalente ao tier** (e não num nível global abstrato). Isso mantém o tempo de farm estável por design.

```
representativeZoneForTier(tier) = primeira zona z tal que tierOf(z) == tier
goldPerKillForTier(tier)        = goldDrop do monstro comum da zona representativa
killsNeededForTier(tier)        = 3.0 + tier.ordinal × 1.25        // 3.0 → ~14.25

priceUnit = goldPerKillForTier(tier) × killsNeededForTier(tier)

buyPriceOf(item):
    RESOURCES     → round(priceUnit × 0.18)          // ~18% da peça
    CONSUMABLES   → round(priceUnit × 0.25)          // ~25% da peça
    EQUIPAMENTOS  → round(priceUnit × tier.multiplier^0.35)   // viés suave por tier

sellPriceOf(item) = round(buyPriceOf(item) × 0.60)    // 60% de recompra
```

- **Por que `tier.multiplier^0.35`?** Tiers mais raros custam um pouco mais dentro da mesma zona, mas sem explosão exponencial (o expoente suaviza).
- **Por que vender a 60%?** Impede o loop lucrativo `comprar → vender` e mantém o ouro fluindo pelo combate.

#### Tabela de preços por tier

| Tier       | Mult | Zona | Ouro/kill | Kills necessárias | Preço da peça |
|------------|-----:|-----:|----------:|------------------:|--------------:|
| COMMON     | 1.0x | 1    | 20        | 3.00              | 60            |
| UNCOMMON   | 1.3x | 3    | 150       | 4.25              | 699           |
| RARE       | 1.7x | 6    | 340       | 5.50              | 2.252         |
| SUPER_RARE | 2.2x | 9    | 540       | 6.75              | 4.803         |
| EPIC       | 2.8x | 12   | 730       | 8.00              | 8.374         |
| LEGENDARY  | 3.5x | 15   | 930       | 9.25              | 13.337        |
| MYTHIC     | 4.3x | 18   | 1.120     | 10.50             | 19.594        |
| ANCESTRAL  | 5.2x | 20   | 1.250     | 11.75             | 26.155        |
| DIVINE     | 6.5x | 20   | 1.250     | 13.00             | 31.288        |
| GOD        | 8.0x | 20   | 1.250     | 14.25             | 36.881        |

### 2.4 Stats de combate e cálculo de dano

```
playerAttack(s)  = attackLevel  + 10 + Σ(equip.stats["attack"])
playerDefense(s) = defenseLevel + 5  + Σ(equip.stats["defense"])
playerAgility(s) = agilityLevel + 5  + Σ(equip.stats["agility"])
playerMaxHp(s)   = strengthLevel×10 + 100 + Σ(equip.stats["hp"])

rollDamage(atk, def, agi):
    base = max(1, atk − def)
    critChance = min(0.30, agi / 40.0)          // cap 30%
    dano = base × (critical ? 1.5 : 1.0)         // +50% no crítico

TTK ≈ ceil(monsterHp / avgDamage)
Ouro/min ≈ (60 / TTK) × monsterGoldDrop
```

> **Regra crítica:** equipamento **soma** aos atributos (`equipmentBonus`). Sem isso, comprar/forjar não afeta o combate e a economia inteira vira cosmética.

### 2.5 Recursos e crafting

- Recursos de coleta: `res_ore_tierN`, `res_wood_tierN`, `res_fish_tierN` (N: 1..20), onde `N` deriva do nível da skill: `tierIndexForLevel(level) = ((level − 1) / 7 + 1)`.
- Craft (Forja) converte recursos + Gold no equipamento do tier alvo:
  - `goldCost = round(buyPriceOf(item) × 0.5)`
  - Ingredientes: `{ore: 10, wood: 5, fish: 5}` do tier-base correspondente.
  - Concede XP de **Ferraria** (`SMITHING`): `craftXpReward = round(tier.multiplier × 50)`.

---

## 3. Diretrizes para Expansões Futuras

### 3.1 Adicionar novas Zonas

1. **Mantenha o espaçamento de ~6.5 níveis por zona** (atual: 20 zonas cobrem 1–130). Uma nova zona `zone_21` deve começar no nível `(21−1)×6.5+1 ≈ 131`.
2. **Derive o tier da zona** por `tierOf(zone) = min(zone / 3, numTiers − 1)`. Ao adicionar zonas *além* do tier GOD, **adicione um novo Tier** (ver 3.2), senão várias zonas colapsam no mesmo tier e a progressão "achata".
3. **Stats do monstro** já escalam por `level` e `zoneIndex`:
   - `hp = 50 × level × mult × (1 + zone×0.15)`
   - `attack = 5 + level×1.5×mult`, `defense = 3 + level×1.2×mult`, `agility = 2 + level×0.8×mult`
   - `goldDrop = level × 10 × mult` (boss = `mult 3.5`)
   - **Verifique após adicionar:** TTK da zona nova deve cair na faixa **~30 s–3 min**; se passar disso, o ataque básico está defasado → reforce o `equip` daquele tier.
4. **Drop tables:** mantenha 1 equipamento comum + drops de boss alinhados ao `equipmentDropIdFor(tier, kind)` para não quebrar IDs.

### 3.2 Adicionar novos Tiers

1. **Multiplicador:** siga a curva suave atual (~**×1.2–1.3 por tier**). Ex.: após `GOD (8.0x)`, algo como `CELESTIAL (9.5x)` mantém a cadência sem saltos. Evite saltos > ×1.5.
2. **Não altere `killsNeededForTier` retroativamente** de forma abrupta: a fórmula é `3 + ordinal×1.25`. Um novo ordinal **herda automaticamente** o custo crescente — o que é o comportamento desejado.
3. **`levelForTier` e `representativeZoneForTier`** distribuem ~130 níveis entre os tiers. Ao adicionar um tier, garanta que exista pelo menos **uma zona** cuja `tierOf(z)` retorne o novo ordinal, senão ele nunca terá `goldPerKill` e o preço cairá para o fallback (zona 20).
4. **Revalide a tabela do item 1** com o mesmo modelo de simulação após qualquer novo tier.

### 3.3 Checklist de balanceamento (para cada mudança)

- [ ] **TTK** de cada zona-alvo dentro de **~30 s–3 min** (com jogador equipado).
- [ ] **Ouro/min** monotonicamente crescente ao longo dos níveis.
- [ ] **Farm por peça** entre **~0.5 min** (early) e **~20 min** (endgame); nunca > ~30 min por peça única.
- [ ] **Set completo** ≤ ~2.5 h de jogo ativo no endgame.
- [ ] **Venda = 60%** da compra (nunca ≥ 100% — evita loop infinito de ouro).
- [ ] Equipamento **afeta stats** (`equipmentBonus` intacto em `attack/defense/agility/hp`).
- [ ] Recursos/consumíveis continuam **mais baratos** que equipamento do mesmo tier (18% / 25%).
- [ ] XP: nenhuma skill passa do cap 130 indevidamente (`xpForLevel` protegido).

### 3.4 Anti-padrões a evitar

- ❌ Ancorar preço só no "nível global" (a renda real depende da zona e do TTK). **Ancore na renda da zona.**
- ❌ Fazer TTK crescer sem limite (foi o bug histórico: defesa do monstro `+1.2/level` vs. ataque `+10` fixo). **Garanta que o dano escale com equipamento.**
- ❌ Preços exponenciais fortes em tiers altos (`mult^k`, k>1) — geram paredes. Use expoente ≤ ~0.4.
- ❌ Venda próxima de 100% do preço — permite arbitragem infinita.
- ❌ Introduzir zonas repetindo o mesmo tier sem novo multiplicador (perda de sensação de progresso).

---

## Anexo — Constantes principais

| Constante                    | Valor            | Origem                          |
|------------------------------|------------------|---------------------------------|
| `TICK_MS`                    | 1.000 ms         | `GameEngine` (turno = 1 s)      |
| `CRIT_CHANCE_CAP`            | 0.30 (30%)       | `GameEngine`                    |
| `CRIT_DAMAGE_MULT`           | 1.5 (+50%)       | `GameEngine`                    |
| `AGI_PER_CRIT`               | 40.0             | `GameEngine`                    |
| `SELL_RATIO`                 | 0.60             | `ShopSystem`                    |
| `killsNeededForTier`         | `3 + ordinal×1.25` | `ShopSystem`                  |
| `tierBias (equip)`           | `mult^0.35`      | `ShopSystem`                    |
| Recursos / Consumíveis       | 18% / 25% da peça| `ShopSystem`                    |
| Craft (goldCost)             | `buyPrice×0.5`   | `ShopSystem`                    |
| Craft (XP Ferraria)          | `mult×50`        | `ShopSystem`                    |
| Zonas / Níveis               | 20 zonas, 1–130  | `GameDatabaseGenerator`         |
| Curva de XP                  | RuneScape, 1–130 | `GameDatabaseGenerator`         |
