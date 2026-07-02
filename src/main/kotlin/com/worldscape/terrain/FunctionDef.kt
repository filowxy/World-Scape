package com.worldscape.terrain

import java.util.Collections

data class FunctionDef(
    @JvmField val id: String,
    @JvmField val minHeight: Int,
    @JvmField val maxHeight: Int,
    @JvmField val tierWhitelist: IntArray?,
    @JvmField val heightCap: Double,
    @JvmField val coordinateTransform: CoordinateTransform?,
    @JvmField val functions: List<NoisePrimitive>,
    @JvmField val combinator: Combinator?,
    @JvmField val finalExpr: String,
    @JvmField val climate: Climate?
) {
    fun isAllowedOnTier(tier: Int): Boolean {
        if (tierWhitelist == null || tierWhitelist.isEmpty()) return true
        return tier in tierWhitelist
    }

    override fun toString(): String =
        "FunctionDef{id='$id', functions=${functions.size}, combinator=${combinator?.type ?: "none"}}"
}