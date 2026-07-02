package com.worldscape.voronoi

class VoronoiCell(
    val controlPointId: String,
    var controlPoint: VoronoiControlPoint? = null
) {
    private val vertices: MutableList<VoronoiVertex> = ArrayList()
    private val edges: MutableList<VoronoiEdge> = ArrayList()

    fun addVertex(vertex: VoronoiVertex) {
        if (vertex !in vertices) vertices.add(vertex)
    }

    fun addEdge(edge: VoronoiEdge) {
        if (edge !in edges) edges.add(edge)
    }

    fun getVertices(): List<VoronoiVertex> = ArrayList(vertices)
    fun getEdges(): List<VoronoiEdge> = ArrayList(edges)
    fun getVertexCount(): Int = vertices.size
    fun getEdgeCount(): Int = edges.size

    fun getArea(): Double {
        if (vertices.size < 3) return 0.0
        var area = 0.0
        val n = vertices.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += vertices[i].x * vertices[j].y
            area -= vertices[j].x * vertices[i].y
        }
        return Math.abs(area) / 2.0
    }

    fun getPerimeter(): Double {
        var perimeter = 0.0
        val n = vertices.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            perimeter += vertices[i].distanceTo(vertices[j])
        }
        return perimeter
    }

    fun getAverageElevation(): Double =
        if (controlPoint == null) 0.0 else controlPoint!!.weight * 100.0f.toDouble()

    fun getAverageSlope(): Double {
        if (vertices.size < 3) return 0.0
        return Math.min(1.0, getPerimeter() / Math.sqrt(getArea()) * 0.1)
    }

    fun sortVerticesCounterclockwise() {
        if (vertices.size < 3) return
        var centerX = 0.0
        var centerY = 0.0
        for (v in vertices) {
            centerX += v.x
            centerY += v.y
        }
        val cx = centerX / vertices.size
        val cy = centerY / vertices.size
        vertices.sortWith(Comparator { v1, v2 ->
            val angle1 = Math.atan2(v1.y - cy, v1.x - cx)
            val angle2 = Math.atan2(v2.y - cy, v2.x - cx)
            java.lang.Double.compare(angle1, angle2)
        })
    }

    fun containsPoint(x: Double, y: Double): Boolean {
        if (vertices.size < 3) return false
        val n = vertices.size
        var inside = false
        var j = n - 1
        for (i in 0 until n) {
            val xi = vertices[i].x
            val yi = vertices[i].y
            val xj = vertices[j].x
            val yj = vertices[j].y
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    override fun toString(): String =
        "VoronoiCell{controlPointId='$controlPointId', vertices=${vertices.size}, area=${getArea()}}"
}