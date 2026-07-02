package com.worldscape.terrain

import java.util.Collections
import kotlin.jvm.JvmOverloads

data class FunctionDef @JvmOverloads constructor(
    @JvmField val id: String,
    @JvmField val minHeight: Int,
    @JvmField val maxHeight: Int,
    @JvmField val tierWhitelist: IntArray?,
    @JvmField val heightCap: Double,
    @JvmField val coordinateTransform: CoordinateTransform?,
    @JvmField val functions: List<NoisePrimitive>,
    @JvmField val combinator: Combinator?,
    @JvmField val finalExpr: String,
    @JvmField val climate: Climate?,
    // Elevation offset clamp range for control points of this terrain type.
    // Externalized from TerrainControlPoint so pack authors can tune them without recompiling.
    // 该地形类型控制点的海拔偏移钳制范围。从 TerrainControlPoint 外部化，使整合包作者无需重新编译即可调整。
    @JvmField val minOffset: Double = -10.0,
    @JvmField val maxOffset: Double = 10.0
) {
    fun isAllowedOnTier(tier: Int): Boolean {
        if (tierWhitelist == null || tierWhitelist.isEmpty()) return true
        return tier in tierWhitelist
    }

    override fun toString(): String =
        "FunctionDef{id='$id', functions=${functions.size}, combinator=${combinator?.type ?: "none"}}"
}