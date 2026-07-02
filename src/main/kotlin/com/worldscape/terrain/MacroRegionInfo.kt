package com.worldscape.terrain

data class MacroRegionInfo(
    @JvmField val elevationTier: Int,
    @JvmField val secondElevationTier: Int,
    @JvmField val blendedBaseHeight: Double,
    @JvmField val tectonic: TectonicType,
    @JvmField val climate: ClimateZone,
    @JvmField val blendWeight: Double,
    @JvmField val transitionWidth: Int,
    @JvmField val primaryCellX: Int,
    @JvmField val primaryCellZ: Int
) {
    enum class TectonicType { OROGENIC_BELT, SUBDUCTION_ZONE, RIFT_ZONE, CRATON, FAULT_ZONE }

    enum class ClimateZone { ARID, GLACIAL, TEMPERATE, TROPICAL }

    fun getBaseHeight(): Double = blendedBaseHeight
    fun getElevationTier(): Int = elevationTier
    fun getSecondElevationTier(): Int = secondElevationTier
    fun getBlendWeight(): Double = blendWeight
}