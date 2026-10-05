package com.example.engine

import com.example.model.PlayerColor
import com.example.model.Token
import com.example.model.TokenState

data class GridPoint(val col: Float, val row: Float)

object BoardGeometry {
    // 8 safe cells on the 52-position main track: 4 start cells + 4 star cells
    val SAFE_TRACK_INDICES: Set<Int> = setOf(0, 8, 13, 21, 26, 34, 39, 47)
    val STAR_TRACK_INDICES: Set<Int> = setOf(8, 21, 34, 47)
    val START_TRACK_INDICES: Map<PlayerColor, Int> = mapOf(
        PlayerColor.RED to 0,
        PlayerColor.GREEN to 13,
        PlayerColor.YELLOW to 26,
        PlayerColor.BLUE to 39
    )

    // 52-cell clockwise outer track coordinates on a 15x15 Ludo grid (col 0..14, row 0..14)
    val MAIN_TRACK_COORDS: List<Pair<Int, Int>> = listOf(
        // Red bottom-of-top-left arm moving right (0..4)
        1 to 6, 2 to 6, 3 to 6, 4 to 6, 5 to 6,
        // Top arm left column moving up (5..10)
        6 to 5, 6 to 4, 6 to 3, 6 to 2, 6 to 1, 6 to 0,
        // Top edge turn (11..12)
        7 to 0, 8 to 0,
        // Top arm right column moving down (13..17)
        8 to 1, 8 to 2, 8 to 3, 8 to 4, 8 to 5,
        // Right arm top row moving right (18..23)
        9 to 6, 10 to 6, 11 to 6, 12 to 6, 13 to 6, 14 to 6,
        // Right edge turn (24..25)
        14 to 7, 14 to 8,
        // Right arm bottom row moving left (26..30)
        13 to 8, 12 to 8, 11 to 8, 10 to 8, 9 to 8,
        // Bottom arm right column moving down (31..36)
        8 to 9, 8 to 10, 8 to 11, 8 to 12, 8 to 13, 8 to 14,
        // Bottom edge turn (37..38)
        7 to 14, 6 to 14,
        // Bottom arm left column moving up (39..43)
        6 to 13, 6 to 12, 6 to 11, 6 to 10, 6 to 9,
        // Left arm bottom row moving left (44..49)
        5 to 8, 4 to 8, 3 to 8, 2 to 8, 1 to 8, 0 to 8,
        // Left edge turn (50..51)
        0 to 7, 0 to 6
    )

    // Home lanes (steps 51..55 -> index 0..4)
    val HOME_LANE_COORDS: Map<PlayerColor, List<Pair<Int, Int>>> = mapOf(
        PlayerColor.RED to listOf(1 to 7, 2 to 7, 3 to 7, 4 to 7, 5 to 7),
        PlayerColor.GREEN to listOf(7 to 1, 7 to 2, 7 to 3, 7 to 4, 7 to 5),
        PlayerColor.YELLOW to listOf(13 to 7, 12 to 7, 11 to 7, 10 to 7, 9 to 7),
        PlayerColor.BLUE to listOf(7 to 13, 7 to 12, 7 to 11, 7 to 10, 7 to 9)
    )

    // Base yard token slots (4 per color inside the 6x6 corner yards)
    val BASE_SLOT_COORDS: Map<PlayerColor, List<GridPoint>> = mapOf(
        PlayerColor.RED to listOf(
            GridPoint(1.8f, 1.8f),
            GridPoint(4.2f, 1.8f),
            GridPoint(1.8f, 4.2f),
            GridPoint(4.2f, 4.2f)
        ),
        PlayerColor.GREEN to listOf(
            GridPoint(10.8f, 1.8f),
            GridPoint(13.2f, 1.8f),
            GridPoint(10.8f, 4.2f),
            GridPoint(13.2f, 4.2f)
        ),
        PlayerColor.YELLOW to listOf(
            GridPoint(10.8f, 10.8f),
            GridPoint(13.2f, 10.8f),
            GridPoint(10.8f, 13.2f),
            GridPoint(13.2f, 13.2f)
        ),
        PlayerColor.BLUE to listOf(
            GridPoint(1.8f, 10.8f),
            GridPoint(4.2f, 10.8f),
            GridPoint(1.8f, 13.2f),
            GridPoint(4.2f, 13.2f)
        )
    )

    // Finished token positions inside the central 3x3 finishing area (col 6..8, row 6..8)
    val FINISHED_SLOT_COORDS: Map<PlayerColor, List<GridPoint>> = mapOf(
        PlayerColor.RED to listOf(
            GridPoint(6.35f, 7.20f),
            GridPoint(6.35f, 7.80f),
            GridPoint(6.65f, 7.35f),
            GridPoint(6.65f, 7.65f)
        ),
        PlayerColor.GREEN to listOf(
            GridPoint(7.20f, 6.35f),
            GridPoint(7.80f, 6.35f),
            GridPoint(7.35f, 6.65f),
            GridPoint(7.65f, 6.65f)
        ),
        PlayerColor.YELLOW to listOf(
            GridPoint(8.65f, 7.20f),
            GridPoint(8.65f, 7.80f),
            GridPoint(8.35f, 7.35f),
            GridPoint(8.35f, 7.65f)
        ),
        PlayerColor.BLUE to listOf(
            GridPoint(7.20f, 8.65f),
            GridPoint(7.80f, 8.65f),
            GridPoint(7.35f, 8.35f),
            GridPoint(7.65f, 8.35f)
        )
    )

    fun isSafeTrackIndex(trackIndex: Int, safeCellsEnabled: Boolean = true): Boolean {
        if (!safeCellsEnabled) return false
        return trackIndex in SAFE_TRACK_INDICES
    }

    fun getTokenGridCenter(token: Token): GridPoint {
        return when (token.state) {
            TokenState.BASE -> {
                val slots = BASE_SLOT_COORDS[token.color] ?: BASE_SLOT_COORDS.getValue(PlayerColor.RED)
                slots[token.id.coerceIn(0, 3)]
            }
            TokenState.ACTIVE -> {
                val trackIdx = ((token.color.startOffset + token.stepsFromStart.coerceIn(0, 50)) % 52)
                val (c, r) = MAIN_TRACK_COORDS[trackIdx]
                GridPoint(c + 0.5f, r + 0.5f)
            }
            TokenState.HOME -> {
                val laneIdx = (token.stepsFromStart - 51).coerceIn(0, 4)
                val lane = HOME_LANE_COORDS[token.color] ?: HOME_LANE_COORDS.getValue(PlayerColor.RED)
                val (c, r) = lane[laneIdx]
                GridPoint(c + 0.5f, r + 0.5f)
            }
            TokenState.FINISHED -> {
                val slots = FINISHED_SLOT_COORDS[token.color] ?: FINISHED_SLOT_COORDS.getValue(PlayerColor.RED)
                slots[token.id.coerceIn(0, 3)]
            }
        }
    }
}
