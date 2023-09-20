package com.example.gps_app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.sqrt

class CompareGraphs(private val context: Context, private val graphDraw: MutableList<SVGtoGraph.Edge>, private val graphMap: MutableList<SVGtoGraph.Edge>) {

    private fun distance(p1: SVGtoGraph.Point, p2: SVGtoGraph.Point): Float {
        return sqrt((p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y))
    }

    fun alignGraphs(): MutableList<SVGtoGraph.Edge> {
        val alignedGraph = mutableListOf<SVGtoGraph.Edge>()

        for (e1 in graphDraw) {
            var bestMatch: SVGtoGraph.Edge? = null
            var bestScore = Float.MAX_VALUE

            for (e2 in graphMap) {
                val score = distance(e1.from, e2.from) + distance(e1.to, e2.to)
                if (score < bestScore) {
                    bestScore = score
                    bestMatch = e2
                }
            }

            if (bestMatch != null) {
                val dx = bestMatch.from.x - e1.from.x
                val dy = bestMatch.from.y - e1.from.y

                val newFrom = SVGtoGraph.Point(e1.from.x + dx, e1.from.y + dy)
                val newTo = SVGtoGraph.Point(e1.to.x + dx, e1.to.y + dy)

                alignedGraph.add(SVGtoGraph.Edge(newFrom, newTo))
            }
        }

        return alignedGraph
    }

    fun drawTransformedGraph(alignedGraph: MutableList<SVGtoGraph.Edge>, canvas: Canvas) {
        val paint = Paint().apply {
            color = Color.RED
            strokeWidth = 4f
            style = Paint.Style.STROKE
        }

        // Kirajzoljuk az igazított gráfot
        for (edge in alignedGraph) {
            val from = edge.from
            val to = edge.to
            canvas.drawLine(from.x, from.y, to.x, to.y, paint)
        }
    }

    companion object {
        private var instance: CompareGraphs? = null

        fun getInstance(): CompareGraphs? {
            return instance
        }
    }
}