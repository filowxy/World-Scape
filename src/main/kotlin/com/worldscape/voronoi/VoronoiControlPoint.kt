package com.worldscape.voronoi

class VoronoiControlPoint(
    val id: String,
    var x: Int,
    var z: Int,
    @get:JvmName("getRawColor")
    var color: Int,
    var size: Float,
    var weight: Float,
    var label: String,
    var terrainType: String,
    @get:JvmName("isSelected")
    var selected: Boolean = false,
    @get:JvmName("isVisible")
    var visible: Boolean = true
) {
    companion object {
        @JvmField val DEFAULT_COLOR = -11886849
        @JvmField val DEFAULT_SIZE = 8.0f
        @JvmField val DEFAULT_WEIGHT = 1.0f
        private val SELECTED_COLOR = -43691
    }

    constructor(id: String, x: Int, z: Int, color: Int) :
        this(id, x, z, color, DEFAULT_SIZE, DEFAULT_WEIGHT, "", "")

    constructor(x: Int, z: Int, terrainType: String) :
        this("auto_${x}_$z", x, z, DEFAULT_COLOR, DEFAULT_SIZE, DEFAULT_WEIGHT, "", terrainType)

    constructor(other: VoronoiControlPoint) :
        this(other.id, other.x, other.z, other.color, other.size, other.weight,
             other.label, other.terrainType, other.selected, other.visible)

    fun getColor(): Int = if (selected) SELECTED_COLOR else color

    fun getOriginalColor(): Int = color

    fun distanceTo(px: Int, pz: Int): Double =
        Math.sqrt(squaredDistanceTo(px.toLong(), pz.toLong()).toDouble())

    fun squaredDistanceTo(px: Long, pz: Long): Long {
        val dx = x.toLong() - px
        val dz = z.toLong() - pz
        return dx * dx + dz * dz
    }

    fun isWithinRadius(px: Int, pz: Int, radiusSq: Long): Boolean =
        squaredDistanceTo(px.toLong(), pz.toLong()) <= radiusSq

    fun toggleSelection() { selected = !selected }

    override fun equals(other: Any?): Boolean =
        this === other || (other is VoronoiControlPoint && id == other.id)

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "VoronoiControlPoint[id=$id, x=$x, z=$z, type=$terrainType, weight=$weight]"
}