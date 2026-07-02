package com.worldscape.voronoi

class VoronoiVertex(
    val id: String = "v_" + idCounter++,
    val x: Double,
    val y: Double
) {
    // Secondary constructor for Java interop (Java cannot use Kotlin default params)
    // 供 Java 互操作的辅助构造函数
    constructor(px: Double, py: Double) : this(x = px, y = py)

    companion object {
        @JvmField var idCounter: Int = 0
    }

    private val adjacentEdges: MutableList<VoronoiEdge> = ArrayList()

    fun addAdjacentEdge(edge: VoronoiEdge) {
        if (edge !in adjacentEdges) adjacentEdges.add(edge)
    }

    fun getAdjacentEdges(): List<VoronoiEdge> = ArrayList(adjacentEdges)

    fun distanceTo(other: VoronoiVertex): Double = distanceTo(other.x, other.y)

    fun distanceTo(ox: Double, oy: Double): Double {
        val dx = x - ox
        val dy = y - oy
        return Math.sqrt(dx * dx + dy * dy)
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is VoronoiVertex && x == other.x && y == other.y)

    override fun hashCode(): Int = java.util.Objects.hash(x, y)

    override fun toString(): String = "VoronoiVertex{id='$id', x=$x, y=$y}"
}