package com.worldscape.terrain

class TerrainControlPoint(
    @JvmField val x: Int,
    @JvmField val z: Int,
    @JvmField val terrainType: TerrainType,
    elevationOffset: Double,
    @JvmField val influenceRadius: Double
) {
    @JvmField val elevationOffset: Double

    init {
        require(influenceRadius > 0.0) { "influenceRadius must be positive, got: $influenceRadius" }
        this.elevationOffset = clampOffset(terrainType, elevationOffset)
    }

    private fun clampOffset(type: TerrainType, rawOffset: Double): Double {
        // Prefer JSON-defined offset range; fall back to legacy hardcoded mapping if no function def is loaded.
        // 优先使用 JSON 定义的偏移范围；未加载函数定义时回退到旧硬编码映射。
        val functionDef = type.getFunctionDef()
        val (minOffset, maxOffset) = if (functionDef != null) {
            functionDef.minOffset to functionDef.maxOffset
        } else {
            getLegacyOffsetRange(type)
        }
        return rawOffset.coerceIn(minOffset, maxOffset)
    }

    /**
     * Legacy hardcoded elevation offset ranges used when no JSON function definition is loaded.
     * Kept as a safe fallback to preserve behavior for any future path that constructs a
     * TerrainControlPoint before defaults.json is available.
     * 未加载 JSON 函数定义时使用的旧硬编码海拔偏移范围。保留为安全回退，以应对在 defaults.json 可用前构造 TerrainControlPoint 的未来路径。
     */
    private fun getLegacyOffsetRange(type: TerrainType): Pair<Double, Double> {
        return when (type) {
            TerrainType.PLAINS -> -5.0 to 5.0
            TerrainType.HILLS -> 10.0 to 30.0
            TerrainType.RIDGE -> 40.0 to 80.0
            TerrainType.HIGH_MOUNTAINS -> 80.0 to 150.0
            TerrainType.CANYON -> -80.0 to -30.0
            TerrainType.DUNE -> 0.0 to 15.0
            TerrainType.PLATEAU -> 30.0 to 60.0
            TerrainType.DOME -> 15.0 to 35.0
            TerrainType.CLIFF -> 0.0 to 0.0
            TerrainType.BASIN -> -30.0 to -10.0
            TerrainType.GLACIAL_VALLEY -> -40.0 to -10.0
            TerrainType.VALLEY -> -25.0 to -5.0
            TerrainType.CIRQUE -> -30.0 to -5.0
            TerrainType.FJORD -> -40.0 to -10.0
            TerrainType.SINKHOLE -> -30.0 to -10.0
            TerrainType.BEACH -> -2.0 to 6.0
            TerrainType.TRENCH -> -100.0 to -60.0
            TerrainType.SEA_PLATEAU -> -30.0 to -10.0
            TerrainType.DELTA -> -5.0 to 5.0
            else -> -10.0 to 10.0
        }
    }

    fun calculateInfluence(targetX: Int, targetZ: Int): Double {
        val dx = (targetX - x).toDouble()
        val dz = (targetZ - z).toDouble()
        val distanceSq = dx * dx + dz * dz
        val distance = Math.sqrt(distanceSq)
        if (distance > influenceRadius) return 0.0
        val normalizedDist = distance / influenceRadius
        return (1.0 - normalizedDist) * (1.0 - normalizedDist)
    }

    fun calculateInfluenceFromSquaredDist(distanceSq: Double): Double {
        val radiusSq = influenceRadius * influenceRadius
        if (distanceSq > radiusSq) return 0.0
        val normalizedDist = Math.sqrt(distanceSq) / influenceRadius
        return (1.0 - normalizedDist) * (1.0 - normalizedDist)
    }

    fun squaredDistanceTo(targetX: Int, targetZ: Int): Double {
        val dx = (targetX - x).toDouble()
        val dz = (targetZ - z).toDouble()
        return dx * dx + dz * dz
    }
}