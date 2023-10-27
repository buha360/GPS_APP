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

    fun findSubgraph(largeGraph: MutableMap<Point, MutableList<Point>>, smallGraph: CanvasView.Graph): List<Point> {
        val matchedPoints = mutableListOf<Point>()
        val convertedIntersectionPoints = MainActivity.DataHolder.intersectionPoints.map { convertGeoPointToGraphPoint(it) }.toSet()
        for (vertex in smallGraph.vertices) {
            val targetPoint = Point(vertex.x, vertex.y)
            val matchedPoint = findClosestPoint(targetPoint, largeGraph, convertedIntersectionPoints)
            if (matchedPoint != null) {
                matchedPoints.add(matchedPoint)
                DataHolder.selectedPoints.add(matchedPoint)  // Save the matched points to the DataHolder
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
        val centroid = computeCentroid(originalVertices)

        for (i in 0..36) {
            val angle = i * 10.0
            val rotatedVertices = originalVertices.map { rotatePoint(it, centroid, angle) }
            val rotatedSmallGraph = CanvasView.Graph().apply {
                vertices.addAll(rotatedVertices.map { CanvasView.Vertex(it.x, it.y) })
            }

            val currentMatch = findSubgraph(largeGraph, rotatedSmallGraph)

            // Compute score for this match
            val score = currentMatch.sumOf { matchPoint ->
                originalVertices.minOf { vertex ->
                    heuristicCostEstimate(vertex, matchPoint)
                }
            }

            scores[angle] = score

            // Log the match and its score
            Log.d("gps_app-", "Angle: $angle, Match: $currentMatch, Score: $score")

            if (currentMatch.isNotEmpty()) {
                allMatches[angle] = currentMatch
            }
        }

        // Now, you can get the best match based on the scores
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
            val current = openSet.minByOrNull { fScore.getValue(it) }
            if (current == null) {
                return null
            }
            if (current == goal) {
                if (intersectionsOnPath.isNotEmpty()) {
                    val firstIntersection = intersectionsOnPath.first()
                    val lastIntersection = intersectionsOnPath.last()
                    return reconstructPath(cameFrom, current).filter {
                        isBetween(it, start, firstIntersection) || isBetween(it, lastIntersection, goal)
                    }
                } else {
                    return reconstructPath(cameFrom, current)
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

    private fun convertGeoPointToGraphPoint(geoPoint: MainActivity.GeoPoint): CompareGraph.Point {
        return CompareGraph.Point(geoPoint.lat, geoPoint.lon)
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