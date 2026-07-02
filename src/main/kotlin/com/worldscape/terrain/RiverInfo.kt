package com.worldscape.terrain

data class RiverInfo(
    val isRiver: Boolean,
    val distSq: Double,
    val width: Double,
    val depth: Double,
    val flowDirection: Double
) {
    companion object {
        @JvmField
        val EMPTY = RiverInfo(false, 0.0, 0.0, 0.0, 0.0)
    }

    val normalizedDist: Double
        get() = if (width <= 0.0) 1.0
        else Math.min(Math.sqrt(distSq) / (width * 0.5), 1.0)

    fun isWithinRiver(): Boolean =
        isRiver && width > 0.0 && distSq <= width * 0.5 * (width * 0.5)

    fun getErosionIntensity(): Double =
        if (!isRiver) 0.0 else 1.0 - normalizedDist

    override fun toString(): String =
        "RiverInfo{isRiver=$isRiver, distSq=$distSq, width=$width, depth=$depth, flow=$flowDirection}"
}