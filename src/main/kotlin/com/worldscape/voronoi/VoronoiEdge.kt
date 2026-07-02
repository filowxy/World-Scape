package com.worldscape.voronoi

class VoronoiEdge(
    val id: String = "e_" + idCounter++,
    var start: VoronoiVertex?,
    var end: VoronoiVertex?
) {
    // Secondary constructor for Java interop (Java cannot use Kotlin default params)
    // 供 Java 互操作的辅助构造函数
    constructor(s: VoronoiVertex?, e: VoronoiVertex?) : this(start = s, end = e)

    companion object {
        @JvmField var idCounter: Int = 0
    }

    var leftCell: VoronoiCell? = null
    var rightCell: VoronoiCell? = null
    @get:JvmName("isVisible")
    var visible: Boolean = true

    val length: Double
        get() = if (start == null || end == null) Double.MAX_VALUE else start!!.distanceTo(end!!)

    val midX: Double
        get() = if (start == null || end == null) 0.0 else (start!!.x + end!!.x) / 2.0

    val midY: Double
        get() = if (start == null || end == null) 0.0 else (start!!.y + end!!.y) / 2.0

    fun isInfinite(): Boolean = start == null || end == null

    override fun equals(other: Any?): Boolean =
        this === other || (other is VoronoiEdge && start == other.start && end == other.end)

    override fun hashCode(): Int = java.util.Objects.hash(start, end)

    override fun toString(): String =
        "VoronoiEdge{id='$id', start=${start?.id ?: "null"}, end=${end?.id ?: "null"}}"
}