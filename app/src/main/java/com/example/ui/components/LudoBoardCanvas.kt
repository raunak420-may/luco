package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.BoardGeometry
import com.example.model.Move
import com.example.model.PlayerColor
import com.example.model.Token
import com.example.ui.theme.RoyalAmberYellow
import com.example.ui.theme.RoyalEmeraldGreen
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.RoyalIvory
import com.example.ui.theme.RoyalNavyDark
import com.example.ui.theme.RoyalParchment
import com.example.ui.theme.RoyalRubyRed
import com.example.ui.theme.RoyalSapphireBlue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

fun PlayerColor.toComposeColor(): Color = when (this) {
    PlayerColor.RED -> RoyalRubyRed
    PlayerColor.GREEN -> RoyalEmeraldGreen
    PlayerColor.YELLOW -> RoyalAmberYellow
    PlayerColor.BLUE -> RoyalSapphireBlue
}

fun PlayerColor.toDarkAccentColor(): Color = when (this) {
    PlayerColor.RED -> Color(0xFF8E0000)
    PlayerColor.GREEN -> Color(0xFF00600F)
    PlayerColor.YELLOW -> Color(0xFF996500)
    PlayerColor.BLUE -> Color(0xFF004C8C)
}

@Composable
fun LudoBoardCanvas(
    tokens: List<Token>,
    validMoves: List<Move>,
    safeCellsEnabled: Boolean,
    reducedMotion: Boolean,
    onTokenSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val validTokenIds = validMoves.map { it.playerIndex to it.tokenId }.toSet()

    val infiniteTransition = rememberInfiniteTransition(label = "tokenPulse")
    val pulseFraction by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (reducedMotion) 2000 else 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Pre-calculate animated grid coordinates for each token
    val animatedPositions = tokens.map { token ->
        val rawCenter = BoardGeometry.getTokenGridCenter(token)
        val animDuration = if (reducedMotion) 60 else 280
        val animCol by animateFloatAsState(
            targetValue = rawCenter.col,
            animationSpec = tween(durationMillis = animDuration, easing = FastOutSlowInEasing),
            label = "col_${token.playerIndex}_${token.id}"
        )
        val animRow by animateFloatAsState(
            targetValue = rawCenter.row,
            animationSpec = tween(durationMillis = animDuration, easing = FastOutSlowInEasing),
            label = "row_${token.playerIndex}_${token.id}"
        )
        Triple(token, animCol, animRow)
    }

    // Group tokens sharing the same cell so they fan out cleanly
    val cellGroups = tokens.groupBy { token ->
        val pt = BoardGeometry.getTokenGridCenter(token)
        "${(pt.col * 10).toInt()}_${(pt.row * 10).toInt()}"
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .shadow(12.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(RoyalIvory)
            .border(3.dp, RoyalGold, RoundedCornerShape(20.dp))
            .testTag("ludo_board_canvas")
            .semantics { contentDescription = "Royal Dice Ludo Board" }
            .pointerInput(tokens, validMoves) {
                detectTapGestures { tapOffset ->
                    if (validMoves.isEmpty()) return@detectTapGestures
                    val cellSize = size.width / 15f
                    // Find closest movable token within generous tap radius (1.35 cells)
                    val tappedMove = validMoves.minByOrNull { move ->
                        val token = tokens.firstOrNull {
                            it.playerIndex == move.playerIndex && it.id == move.tokenId
                        }
                        if (token == null) {
                            Float.MAX_VALUE
                        } else {
                            val pt = BoardGeometry.getTokenGridCenter(token)
                            val cx = pt.col * cellSize
                            val cy = pt.row * cellSize
                            hypot(tapOffset.x - cx, tapOffset.y - cy)
                        }
                    }
                    if (tappedMove != null) {
                        val token = tokens.firstOrNull {
                            it.playerIndex == tappedMove.playerIndex && it.id == tappedMove.tokenId
                        }
                        if (token != null) {
                            val pt = BoardGeometry.getTokenGridCenter(token)
                            val dist = hypot(tapOffset.x - pt.col * cellSize, tapOffset.y - pt.row * cellSize)
                            if (dist <= cellSize * 1.45f) {
                                onTokenSelected(tappedMove.tokenId)
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cellSize = size.width / 15f

            // 1. Parchment background
            drawRect(color = RoyalParchment)

            // 2. Draw 4 Corner Base Yards (6x6 each)
            drawPlayerYard(0, 0, cellSize, PlayerColor.RED)
            drawPlayerYard(9, 0, cellSize, PlayerColor.GREEN)
            drawPlayerYard(9, 9, cellSize, PlayerColor.YELLOW)
            drawPlayerYard(0, 9, cellSize, PlayerColor.BLUE)

            // 3. Draw 52 Main Track Cells
            BoardGeometry.MAIN_TRACK_COORDS.forEachIndexed { trackIdx, (col, row) ->
                val topLeft = Offset(col * cellSize, row * cellSize)
                val cellRectSize = Size(cellSize, cellSize)

                val startColor = BoardGeometry.START_TRACK_INDICES.entries
                    .firstOrNull { it.value == trackIdx }?.key

                val fill = when {
                    startColor != null -> startColor.toComposeColor()
                    trackIdx in BoardGeometry.STAR_TRACK_INDICES && safeCellsEnabled -> Color(0xFFFFF3CD)
                    else -> RoyalIvory
                }

                drawRect(color = fill, topLeft = topLeft, size = cellRectSize)
                drawRect(
                    color = Color(0xFF4A3E6D).copy(alpha = 0.38f),
                    topLeft = topLeft,
                    size = cellRectSize,
                    style = Stroke(width = 1.4f)
                )

                // Draw star icon on all 8 safe cells
                if (safeCellsEnabled && trackIdx in BoardGeometry.SAFE_TRACK_INDICES) {
                    val starColor = if (startColor != null) RoyalIvory else RoyalGold
                    drawFivePointStar(
                        center = Offset((col + 0.5f) * cellSize, (row + 0.5f) * cellSize),
                        outerRadius = cellSize * 0.34f,
                        innerRadius = cellSize * 0.15f,
                        color = starColor
                    )
                }
            }

            // 4. Draw 4 Colored Home Lanes (5 cells each)
            BoardGeometry.HOME_LANE_COORDS.forEach { (playerColor, coords) ->
                val laneColor = playerColor.toComposeColor()
                coords.forEach { (col, row) ->
                    val topLeft = Offset(col * cellSize, row * cellSize)
                    drawRect(
                        color = laneColor.copy(alpha = 0.88f),
                        topLeft = topLeft,
                        size = Size(cellSize, cellSize)
                    )
                    drawRect(
                        color = RoyalIvory.copy(alpha = 0.65f),
                        topLeft = topLeft,
                        size = Size(cellSize, cellSize),
                        style = Stroke(width = 1.6f)
                    )
                }
            }

            // 5. Draw Central 3x3 Finishing Crown Area (cols 6..9, rows 6..9)
            drawCentralFinishingArea(cellSize)

            // 6. Draw All 16 Tokens (with micro-offset if stacked and pulsing ring if movable)
            animatedPositions.forEach { (token, animCol, animRow) ->
                val rawPt = BoardGeometry.getTokenGridCenter(token)
                val groupKey = "${(rawPt.col * 10).toInt()}_${(rawPt.row * 10).toInt()}"
                val siblings = cellGroups[groupKey] ?: listOf(token)
                val indexInStack = siblings.indexOfFirst {
                    it.playerIndex == token.playerIndex && it.id == token.id
                }.coerceAtLeast(0)

                val (offsetX, offsetY) = if (siblings.size > 1) {
                    val angle = (2.0 * PI * indexInStack) / siblings.size
                    val spread = cellSize * 0.16f
                    (cos(angle).toFloat() * spread) to (sin(angle).toFloat() * spread)
                } else {
                    0f to 0f
                }

                val center = Offset(animCol * cellSize + offsetX, animRow * cellSize + offsetY)
                val isMovable = (token.playerIndex to token.id) in validTokenIds
                val tokenRadius = if (siblings.size > 1) cellSize * 0.33f else cellSize * 0.38f

                if (isMovable) {
                    val haloRadius = tokenRadius * (1.28f + 0.32f * pulseFraction)
                    drawCircle(
                        color = RoyalGold.copy(alpha = 0.55f - 0.25f * pulseFraction),
                        radius = haloRadius,
                        center = center
                    )
                    drawCircle(
                        color = RoyalGold,
                        radius = haloRadius,
                        center = center,
                        style = Stroke(width = 3.2f)
                    )
                }

                // Token shadow
                drawCircle(
                    color = Color.Black.copy(alpha = 0.35f),
                    radius = tokenRadius,
                    center = Offset(center.x + 1.5f, center.y + 2.5f)
                )

                // Outer metallic gold rim
                drawCircle(
                    color = if (isMovable) RoyalGold else RoyalIvory,
                    radius = tokenRadius,
                    center = center
                )

                // Jewel body
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            token.color.toComposeColor(),
                            token.color.toDarkAccentColor()
                        ),
                        center = Offset(center.x - tokenRadius * 0.25f, center.y - tokenRadius * 0.25f),
                        radius = tokenRadius * 1.1f
                    ),
                    radius = tokenRadius * 0.80f,
                    center = center
                )

                // Inner specular highlight
                drawCircle(
                    color = Color.White.copy(alpha = 0.55f),
                    radius = tokenRadius * 0.26f,
                    center = Offset(center.x - tokenRadius * 0.22f, center.y - tokenRadius * 0.22f)
                )
            }
        }
    }
}

private fun DrawScope.drawPlayerYard(
    startCol: Int,
    startRow: Int,
    cellSize: Float,
    color: PlayerColor
) {
    val yardLeft = startCol * cellSize
    val yardTop = startRow * cellSize
    val yardSize = 6f * cellSize

    // Outer colored yard
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(color.toComposeColor(), color.toDarkAccentColor()),
            start = Offset(yardLeft, yardTop),
            end = Offset(yardLeft + yardSize, yardTop + yardSize)
        ),
        topLeft = Offset(yardLeft, yardTop),
        size = Size(yardSize, yardSize)
    )

    // Inner white royal court
    val pad = cellSize * 0.85f
    drawRoundRect(
        color = RoyalIvory,
        topLeft = Offset(yardLeft + pad, yardTop + pad),
        size = Size(yardSize - pad * 2, yardSize - pad * 2),
        cornerRadius = CornerRadius(cellSize * 0.45f, cellSize * 0.45f)
    )
    drawRoundRect(
        color = RoyalGold,
        topLeft = Offset(yardLeft + pad, yardTop + pad),
        size = Size(yardSize - pad * 2, yardSize - pad * 2),
        cornerRadius = CornerRadius(cellSize * 0.45f, cellSize * 0.45f),
        style = Stroke(width = 2.5f)
    )

    // 4 circular token pedestals
    val slots = BoardGeometry.BASE_SLOT_COORDS[color] ?: emptyList()
    slots.forEach { slot ->
        val cx = slot.col * cellSize
        val cy = slot.row * cellSize
        drawCircle(
            color = color.toComposeColor().copy(alpha = 0.22f),
            radius = cellSize * 0.46f,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = color.toComposeColor(),
            radius = cellSize * 0.46f,
            center = Offset(cx, cy),
            style = Stroke(width = 2.2f)
        )
    }
}

private fun DrawScope.drawCentralFinishingArea(cellSize: Float) {
    val left = 6f * cellSize
    val top = 6f * cellSize
    val right = 9f * cellSize
    val bottom = 9f * cellSize
    val center = Offset(7.5f * cellSize, 7.5f * cellSize)

    // Left triangle (RED)
    val redPath = Path().apply {
        moveTo(left, top)
        lineTo(center.x, center.y)
        lineTo(left, bottom)
        close()
    }
    drawPath(redPath, color = PlayerColor.RED.toComposeColor())

    // Top triangle (GREEN)
    val greenPath = Path().apply {
        moveTo(left, top)
        lineTo(right, top)
        lineTo(center.x, center.y)
        close()
    }
    drawPath(greenPath, color = PlayerColor.GREEN.toComposeColor())

    // Right triangle (YELLOW)
    val yellowPath = Path().apply {
        moveTo(right, top)
        lineTo(right, bottom)
        lineTo(center.x, center.y)
        close()
    }
    drawPath(yellowPath, color = PlayerColor.YELLOW.toComposeColor())

    // Bottom triangle (BLUE)
    val bluePath = Path().apply {
        moveTo(left, bottom)
        lineTo(center.x, center.y)
        lineTo(right, bottom)
        close()
    }
    drawPath(bluePath, color = PlayerColor.BLUE.toComposeColor())

    // Diagonal & outer gold borders
    drawRect(
        color = RoyalGold,
        topLeft = Offset(left, top),
        size = Size(3f * cellSize, 3f * cellSize),
        style = Stroke(width = 3f)
    )

    // Central golden medallion star
    drawCircle(
        color = RoyalNavyDark,
        radius = cellSize * 0.55f,
        center = center
    )
    drawCircle(
        color = RoyalGold,
        radius = cellSize * 0.55f,
        center = center,
        style = Stroke(width = 2.5f)
    )
    drawFivePointStar(
        center = center,
        outerRadius = cellSize * 0.36f,
        innerRadius = cellSize * 0.16f,
        color = RoyalGold
    )
}

private fun DrawScope.drawFivePointStar(
    center: Offset,
    outerRadius: Float,
    innerRadius: Float,
    color: Color
) {
    val path = Path()
    val startAngle = -PI / 2.0
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outerRadius else innerRadius
        val angle = startAngle + i * (PI / 5.0)
        val x = center.x + (r * cos(angle)).toFloat()
        val y = center.y + (r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color = color)
}

@Composable
fun AnimatedRoyalDice(
    diceValue: Int,
    isRolling: Boolean,
    canRoll: Boolean,
    activeColor: PlayerColor,
    rollLabel: String,
    rollingLabel: String,
    tapTokenLabel: String,
    isWaitingForMove: Boolean,
    reducedMotion: Boolean,
    onRollClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (isRolling && !reducedMotion) 360f else 0f,
        animationSpec = tween(durationMillis = if (reducedMotion) 80 else 380),
        label = "diceRotation"
    )
    val scale by animateFloatAsState(
        targetValue = if (isRolling && !reducedMotion) 1.15f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "diceScale"
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = canRoll && !isRolling) { onRollClicked() }
            .testTag("roll_dice_button"),
        color = if (canRoll) activeColor.toComposeColor().copy(alpha = 0.22f) else Color(0xFF1F1640),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (canRoll) 2.dp else 1.dp,
            color = if (canRoll) RoyalGold else Color.White.copy(alpha = 0.18f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Six-sided die face
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .scale(scale)
                    .rotate(rotation)
                    .shadow(6.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(RoyalIvory, Color(0xFFEAE0C8))
                        )
                    )
                    .border(2.5.dp, RoyalGold, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    val w = size.width
                    val h = size.height
                    val pipColor = if (diceValue == 6) RoyalRubyRed else RoyalNavyDark
                    val r = w * 0.105f

                    val tl = Offset(w * 0.22f, h * 0.22f)
                    val tr = Offset(w * 0.78f, h * 0.22f)
                    val ml = Offset(w * 0.22f, h * 0.50f)
                    val mc = Offset(w * 0.50f, h * 0.50f)
                    val mr = Offset(w * 0.78f, h * 0.50f)
                    val bl = Offset(w * 0.22f, h * 0.78f)
                    val br = Offset(w * 0.78f, h * 0.78f)

                    val pips = when (diceValue.coerceIn(1, 6)) {
                        1 -> listOf(mc)
                        2 -> listOf(tl, br)
                        3 -> listOf(tl, mc, br)
                        4 -> listOf(tl, tr, bl, br)
                        5 -> listOf(tl, tr, mc, bl, br)
                        6 -> listOf(tl, tr, ml, mr, bl, br)
                        else -> listOf(mc)
                    }
                    pips.forEach { pt ->
                        drawCircle(color = pipColor, radius = r, center = pt)
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = when {
                        isRolling -> rollingLabel
                        isWaitingForMove -> tapTokenLabel
                        else -> rollLabel
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (canRoll || isWaitingForMove) RoyalGold else Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(activeColor.toComposeColor())
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (diceValue in 1..6) "Rolled: $diceValue" else "Ready",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
