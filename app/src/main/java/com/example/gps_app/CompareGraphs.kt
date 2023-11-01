package com.example.gps_app

import android.util.Log
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class CompareGraph {
    data class Point(val x: Double, val y: Double)

    object DataHolder {
        val selectedPoints = mutableListOf<Point>()
    }

    private fun scalePoints(points: List<Point>, scaleFactor: Double, center: Point): List<Point> {
        return points.map {
            Point(
                center.x + (it.x - center.x) * scaleFactor,
                center.y + (it.y - center.y) * scaleFactor
            )
        }
    }

    fun findSubgraph(largeGraph: MutableMap<Point, MutableList<Point>>, smallGraph: CanvasView.Graph): List<Point> {
        val matchedPoints = mutableListOf<Point>()
        val convertedIntersectionPoints = MainActivity.DataHolder.intersectionPoints.map { convertGeoPointToGraphPoint(it) }.toSet()
        for (vertex in smallGraph.vertices) {
            val targetPoint = Point(vertex.x, vertex.y)
            val matchedPoint = findClosestPoint(targetPoint, largeGraph, convertedIntersectionPoints)
            if (matchedPoint != null) {
                matchedPoints.add(matchedPoint)
                DataHolder.selectedPoints.add(matchedPoint)
            }
        }

        val pathBetweenMatchedPoints = mutableListOf<Point>()
        for (i in 1 until matchedPoints.size) {
            val path = aStarSearch(matchedPoints[i - 1], matchedPoints[i], largeGraph, convertedIntersectionPoints)
            if (path != null) {
                pathBetweenMatchedPoints.addAll(path)
            }
        }

        return pathBetweenMatchedPoints
    }

    fun findBestRotationMatch(largeGraph: MutableMap<Point, MutableList<Point>>, smallGraph: CanvasView.Graph): Map<Double, List<Point>> {
        val allMatches = mutableMapOf<Double, List<Point>>()
        val scores = mutableMapOf<Double, Double>()

        val originalVertices = smallGraph.vertices.map { Point(it.x, it.y) }
        val centroidSmallGraph = computeCentroid(originalVertices)
        val centroidLargeGraph = computeCentroid(largeGraph.keys.toList())

        val scalingFactors = listOf(0.8, 0.9, 1.0, 1.1, 1.2, 1.3, 1.4)

        for (scale in scalingFactors) {
            val scaledVertices = scalePoints(originalVertices, scale, centroidSmallGraph)
            val maxDistance = computeMaxDistanceFromCentroid(centroidLargeGraph, largeGraph)

            for (radius in 0..maxDistance.toInt() step 8) {
                for (i in 0..45) {
                    val angle = i * 8.0

                    val dx = centroidLargeGraph.x + radius * cos(Math.toRadians(angle)) - centroidSmallGraph.x
                    val dy = centroidLargeGraph.y + radius * sin(Math.toRadians(angle)) - centroidSmallGraph.y

                    val translatedVertices = scaledVertices.map { Point(it.x + dx, it.y + dy) }

                    val rotatedVertices = translatedVertices.map { rotatePoint(it, centroidLargeGraph, angle) }
                    val rotatedSmallGraph = CanvasView.Graph().apply {
                        vertices.addAll(rotatedVertices.map { CanvasView.Vertex(it.x, it.y) })
                    }

                    val currentMatch = findSubgraph(largeGraph, rotatedSmallGraph)

                    val score = currentMatch.sumOf { matchPoint ->
                        translatedVertices.minOf { vertex ->
                            heuristicCostEstimate(vertex, matchPoint)
                        }
                    }

                    scores[angle] = score
                    Log.d("gps_app-", "Scale: $scale, Angle: $angle, Match: $currentMatch, Score: $score")

                    if (currentMatch.isNotEmpty()) {
                        allMatches[angle] = currentMatch
                    }
                }
            }
        }

        val bestAngle = scores.minByOrNull { it.value }?.key
        val bestMatch = allMatches[bestAngle]

        return mapOf(bestAngle!! to bestMatch!!)
    }



    private fun rotatePoint(p: Point, center: Point, angle: Double): Point {
        val rad = Math.toRadians(angle)
        val sin = sin(rad)
        val cos = cos(rad)

        // Translate point to origin
        val x = p.x - center.x
        val y = p.y - center.y

        // Rotate point
        val xNew = x * cos - y * sin
        val yNew = x * sin + y * cos

        // Translate point back
        return Point(xNew + center.x, yNew + center.y)
    }

    private fun computeCentroid(points: List<Point>): Point {
        val x = points.sumOf { it.x } / points.size
        val y = points.sumOf { it.y } / points.size
        return Point(x, y)
    }

    private fun findClosestPoint(target: Point, largeGraph: MutableMap<Point, MutableList<Point>>, intersectionPoints: Set<Point>): Point? {
        var closestPoint: Point? = null
        var minDistance = Double.MAX_VALUE

        val allPoints = largeGraph.keys + intersectionPoints

        for (point in allPoints) {
            val distance = sqrt((target.x - point.x).pow(2) + (target.y - point.y).pow(2))
            if (distance < minDistance) {
                minDistance = distance
                closestPoint = point
            }
        }

        return closestPoint
    }

    private fun aStarSearch(start: Point, goal: Point, largeGraph: MutableMap<Point, MutableList<Point>>, intersectionPoints: Set<Point>): List<Point>? {
        val openSet = mutableListOf(start)
        val cameFrom = mutableMapOf<Point, Point>()
        val gScore = mutableMapOf<Point, Double>().withDefault { Double.MAX_VALUE }
        val fScore = mutableMapOf<Point, Double>().withDefault { Double.MAX_VALUE }
        val intersectionsOnPath = mutableListOf<Point>()

        gScore[start] = 0.0
        fScore[start] = heuristicCostEstimate(start, goal)

        while (openSet.isNotEmpty()) {
            val current = openSet.minByOrNull { fScore.getValue(it) } ?: return null
            if (current == goal) {
                return if (intersectionsOnPath.isNotEmpty()) {
                    val firstIntersection = intersectionsOnPath.first()
                    val lastIntersection = intersectionsOnPath.last()
                    reconstructPath(cameFrom, current).filter {
                        isBetween(it, start, firstIntersection) || isBetween(it, lastIntersection, goal)
                    }
                } else {
                    reconstructPath(cameFrom, current)
                }
            }
            openSet.remove(current)
            for (neighbor in largeGraph[current] ?: emptyList()) {
                if (neighbor in intersectionPoints) {
                    intersectionsOnPath.add(neighbor)
                }
                val tentativeGScore = gScore.getValue(current) + distanceBetween(current, neighbor)
                if (tentativeGScore < gScore.getValue(neighbor)) {
                    cameFrom[neighbor] = current
                    gScore[neighbor] = tentativeGScore
                    fScore[neighbor] = gScore.getValue(neighbor) + heuristicCostEstimate(neighbor, goal)
                    if (neighbor !in openSet) {
                        openSet.add(neighbor)
                    }
                }
            }
        }
        return null
    }

    private fun reconstructPath(cameFrom: MutableMap<Point, Point>, current: Point): List<Point> {
        val totalPath = mutableListOf(current)
        var tempCurrent = current
        while (cameFrom.keys.contains(tempCurrent)) {
            tempCurrent = cameFrom[tempCurrent]!!
            totalPath.add(tempCurrent)
        }
        return totalPath.reversed()
    }

    private fun heuristicCostEstimate(start: Point, goal: Point): Double {
        return sqrt((start.x - goal.x).pow(2) + (start.y - goal.y).pow(2))
    }

    private fun distanceBetween(a: Point, b: Point): Double {
        return sqrt((a.x - b.x).pow(2) + (a.y - b.y).pow(2))
    }

    private fun convertGeoPointToGraphPoint(geoPoint: MainActivity.GeoPoint): Point {
        return Point(geoPoint.lat, geoPoint.lon)
    }

    private fun computeMaxDistanceFromCentroid(centroid: Point, graph: MutableMap<Point, MutableList<Point>>): Double {
        return graph.keys.maxOfOrNull { distanceBetween(it, centroid) } ?: 0.0
    }

    private fun isBetween(point: Point, start: Point, end: Point): Boolean {
        val crossProduct = (point.y - start.y) * (end.x - start.x) - (point.x - start.x) * (end.y - start.y)
        if (abs(crossProduct) > 0.0001) return false  // a pont nem az egyenesen van

        val dotProduct = (point.x - start.x) * (end.x - start.x) + (point.y - start.y) * (end.y - start.y)
        if (dotProduct < 0) return false  // a pont a start pont mögött van

        val squaredLength = (end.x - start.x) * (end.x - start.x) + (end.y - start.y) * (end.y - start.y)
        if (dotProduct > squaredLength) return false  // a pont az end ponton túl van

        return true
    }

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