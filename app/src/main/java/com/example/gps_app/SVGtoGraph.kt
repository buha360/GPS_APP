package com.example.gps_app

class SVGtoGraph(private val pathData: List<String>) {

    data class Point(val x: Float, val y: Float)
    data class Edge(val from: Point, val to: Point)

    private val graph: MutableList<Edge> = mutableListOf()

    fun SVGToGraph(): MutableList<Edge> {
        val fullData = pathData.joinToString(" ")
        var lastPoint: Point? = null
        val commands = fullData.split(Regex("[ ,]")).filter { it.isNotEmpty() }

        var index = 0
        while (index < commands.size) {
            when (commands[index]) {
                "M", "L" -> {
                    val x = commands[++index].toFloat()
                    val y = commands[++index].toFloat()
                    val newPoint = Point(x, y)
                    if (lastPoint != null) {
                        graph.add(Edge(lastPoint, newPoint))
                    }
                    lastPoint = newPoint
                }
            }
            index++
        }
        return graph
    }
}