package com.example.gps_app

import org.osmdroid.util.GeoPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.math.tan

class CompareGraph {
    data class Point(val x: Double, val y: Double)

    fun findSubgraph(largeGraph: MutableMap<MainActivity.GeoPoint, MutableList<MainActivity.GeoPoint>>, smallGraph: CanvasView.Graph?): List<Point>? {
        var bestMatch: List<Point>? = null
        var bestScore = Double.MIN_VALUE
        /*
        for (startNode in largeGraph.keys) {
            val score = matchScore(largeGraph, startNode, smallGraph)
            if (score > bestScore) {
                bestScore = score
                bestMatch = smallGraph
            }
        }
         */
        return bestMatch
    }
/*
    private fun euklideanDistance(p1: Point, p2: Point): Double {
        return sqrt(((p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y)))
    }

    private fun matchScore(largeGraph: MutableMap<MainActivity.GeoPoint, MutableList<MainActivity.GeoPoint>>, startNode: MainActivity.GeoPoint, smallGraph: List<Point>): Double {
        val e1From = convertGeoPointToXY(startNode)
        val e1To = convertGeoPointToXY(largeGraph[startNode]!![0])
        val e2From = smallGraph[0]
        val e2To = smallGraph[1]

        val length1 = euklideanDistance(e1From, e1To)
        val length2 = euklideanDistance(e2From, e2To)
        val lengthDiff = abs(length1 - length2) / maxOf(length1, length2)

        val angle1 = atan2((e1To.y - e1From.y), (e1To.x - e1From.x))
        val angle2 = atan2((e2To.y - e2From.y), (e2To.x - e2From.x))
        val angleDiff = abs(angle1 - angle2)

        val startDistance = euklideanDistance(e1From, e2From)
        val endDistance = euklideanDistance(e1To, e2To)

        val score = 0.3 * lengthDiff + 0.3 * angleDiff + 0.3 * startDistance + 0.3 * endDistance
        return 1.0 - minOf(score, 0.4)
    }


 */
    companion object {
        private var instance: CompareGraph? = null
        fun getInstance(): CompareGraph {
            if (instance == null) {
                instance = CompareGraph()
            }
            return instance!!
        }
    }
}