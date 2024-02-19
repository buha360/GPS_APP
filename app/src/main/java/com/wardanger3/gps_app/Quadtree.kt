package com.wardanger3.gps_app

class Quadtree(private val boundary: Rectangle, private val capacity: Int) {
    private var points: MutableList<CanvasViewSC.Vertex> = mutableListOf()
    private var divided = false
    private var northWest: Quadtree? = null
    private var northEast: Quadtree? = null
    private var southWest: Quadtree? = null
    private var southEast: Quadtree? = null

    fun insert(point: CanvasViewSC.Vertex): Boolean {
        if (!boundary.contains(point)) {
            return false
        }

        return if (points.size < capacity) {
            points.add(point)
            true
        } else {
            if (!divided) {
                subdivide()
            }
            (northWest?.insert(point) ?: false) ||
                    (northEast?.insert(point) ?: false) ||
                    (southWest?.insert(point) ?: false) ||
                    (southEast?.insert(point) ?: false)
        }
    }

    private fun subdivide() {
        val x = boundary.x
        val y = boundary.y
        val w = boundary.width / 2
        val h = boundary.height / 2

        northWest = Quadtree(Rectangle(x, y, w, h), capacity)
        northEast = Quadtree(Rectangle(x + w, y, w, h), capacity)
        southWest = Quadtree(Rectangle(x, y + h, w, h), capacity)
        southEast = Quadtree(Rectangle(x + w, y + h, w, h), capacity)

        divided = true
    }

    fun query(range: Rectangle, found: MutableList<CanvasViewSC.Vertex> = mutableListOf()): MutableList<CanvasViewSC.Vertex> {
        if (!boundary.intersects(range)) {
            return found
        }

        for (p in points) {
            if (range.contains(p)) {
                found.add(p)
            }
        }

        if (divided) {
            northWest?.query(range, found)
            northEast?.query(range, found)
            southWest?.query(range, found)
            southEast?.query(range, found)
        }

        return found
    }
}

data class Rectangle(val x: Double, val y: Double, val width: Double, val height: Double) {
    fun contains(point: CanvasViewSC.Vertex): Boolean =
        point.x >= x && point.x <= x + width && point.y >= y && point.y <= y + height

    fun intersects(range: Rectangle): Boolean =
        x < range.x + range.width && x + width > range.x && y < range.y + range.height && y + height > range.y
}
