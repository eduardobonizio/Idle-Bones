/*
 * Project Idle RPG - Idle Bones
 * Componentes reutilizáveis de UI.
 */
package com.idlebones.game.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idlebones.game.data.models.Item
import com.idlebones.game.data.models.ItemTier
import com.idlebones.game.ui.theme.GameColors

/** Converte o colorHex de um ItemTier em Color. */
fun ItemTier.toColor(): Color = Color(android.graphics.Color.parseColor(colorHex))

/**
 * Selo de item: nome (ou "Item Sombrio" se não descoberto) + tier colorido + multiplicador.
 */
@Composable
fun TierBadge(
    item: Item,
    modifier: Modifier = Modifier,
    showMultiplier: Boolean = true
) {
    val tierColor = item.tier.toColor()
    val label = if (item.isDiscovered) item.name else "[Item Sombrio]"
    val suffix = if (showMultiplier) " · ${item.tier.multiplier}x" else ""

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(tierColor.copy(alpha = 0.15f))
            .border(1.dp, tierColor.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "$label$suffix",
            color = tierColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/** Barra de progresso genérica com rótulo opcional e cores customizadas. */
@Composable
fun StatBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    label: String? = null,
    valueText: String? = null,
    color: Color = GameColors.Gold,
    height: Int = 14
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null || valueText != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label ?: "", color = GameColors.TextSecondary, fontSize = 11.sp)
                Text(valueText ?: "", color = GameColors.TextSecondary, fontSize = 11.sp)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(GameColors.IronDark)
                .border(1.dp, GameColors.Iron.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(height.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(color.copy(alpha = 0.7f), color)
                        )
                    )
            )
        }
    }
}

/** Cabeçalho de seção estilo pergaminho/ferro. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(GameColors.GoldDim.copy(alpha = 0.5f))
        )
        Text(
            text = text.uppercase(),
            color = GameColors.Gold,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(GameColors.GoldDim.copy(alpha = 0.5f))
        )
    }
}

/** Card padrão do tema. */
@Composable
fun GameCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(GameColors.Surface)
            .border(1.dp, GameColors.IronDark, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        content()
    }
}
