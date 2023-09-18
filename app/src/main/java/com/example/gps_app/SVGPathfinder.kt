package com.example.gps_app

import android.util.Log

class SVGPathfinder(private val pathData: List<String>) {

    data class Point(val x: Float, val y: Float)
    data class Edge(val from: Point, val to: Point)

    private val graph: MutableList<Edge> = mutableListOf()

    fun SVGToGraph() {
        // Az elemzéshez először egyetlen stringgé kell alakítani az összes pathData-t
        val fullData = pathData.joinToString(" ")

        // Tárolja az utolsó ismert pontot
        var lastPoint: Point? = null

        // Szétszedjük a stringet az utasítások alapján
        val commands = fullData.split(" ").toMutableList()

        var index = 0
        while (index < commands.size) {
            val command = commands[index]
            when (command) {
                "M", "L" -> {
                    val x = commands[++index].toFloat()
                    val y = commands[++index].toFloat()
                    val newPoint = Point(x, y)
                    if (lastPoint != null) {
                        graph.add(Edge(lastPoint!!, newPoint))
                    }
                    lastPoint = newPoint
                }
            }
            index++
        }

        // Debug céljából logoljuk a gráfot
        Log.d("MyApp", "Graph: $graph")
    }

    companion object {
        private var instance: SVGPathfinder? = null
        fun getInstance(): SVGPathfinder? {
            return instance
        }
    }
}