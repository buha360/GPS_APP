package com.example.gps_app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class CompareGraph {
    data class Point(val x: Double, val y: Double) {
        fun rotate(angle: Double, about: Point): Point {
            val sinA = sin(Math.toRadians(angle))
            val cosA = cos(Math.toRadians(angle))
            val dx = x - about.x
            val dy = y - about.y
            val newX = cosA * dx - sinA * dy + about.x
            val newY = sinA * dx + cosA * dy + about.y
            return Point(newX, newY)
        }

        fun scale(factor: Double, about: Point): Point {
            val dx = x - about.x
            val dy = y - about.y
            val newX = dx * factor + about.x
            val newY = dy * factor + about.y
            return Point(newX, newY)
        }
    }

    object Dataholder {
        var totalAttempts: Int = 0
        var currentAttempt: Int = 0
    }

    data class PriorityQueueNode<T>(val data: T, val priority: Double)

    class PriorityQueue<T> {
        val elements: MutableList<PriorityQueueNode<T>> = mutableListOf()

        fun isEmpty(): Boolean = elements.isEmpty()

        fun add(data: T, priority: Double) {
            elements.add(PriorityQueueNode(data, priority))
            elements.sortBy { it.priority }
        }

        fun poll(): T? {
            if (isEmpty()) return null
            return elements.removeAt(0).data
        }
    }

    private fun closestPointInLargeGraph(
        point: Point,
        graph: Map<Point, MutableList<Point>>
    ): Point {
        return graph.keys.minByOrNull { euklideanDistance(it, point) } ?: point
    }

    private fun aStarSearch(
        start: Point,
        goal: Point,
        graph: Map<Point, MutableList<Point>>
    ): List<Point> {
        val openSet = PriorityQueue<Point>()
        openSet.add(start, 0.0)

        val cameFrom = mutableMapOf<Point, Point>()
        val gScore = mutableMapOf<Point, Double>().withDefault { Double.MAX_VALUE }
        gScore[start] = 0.0

        val fScore = mutableMapOf<Point, Double>().withDefault { Double.MAX_VALUE }
        fScore[start] = euklideanDistance(start, goal)

        while (!openSet.isEmpty()) {
            val current = openSet.poll()!!

            if (current == goal) {
                return reconstructPath(cameFrom, current)
            }

            for (neighbor in graph[current] ?: emptyList()) {
                val tentativeGScore =
                    gScore.getValue(current) + euklideanDistance(current, neighbor)

                if (tentativeGScore < gScore.getValue(neighbor)) {
                    cameFrom[neighbor] = current
                    gScore[neighbor] = tentativeGScore
                    fScore[neighbor] = tentativeGScore + euklideanDistance(neighbor, goal)
                    if (neighbor !in openSet.elements.map { it.data }) {
                        openSet.add(neighbor, fScore.getValue(neighbor))
                    }
                }
            }
        }

        return emptyList()
    }

    private fun reconstructPath(cameFrom: Map<Point, Point>, current: Point): List<Point> {
        val path = mutableListOf(current)
        var temp = current
        while (cameFrom.containsKey(temp)) {
            path.add(cameFrom[temp]!!)
            temp = cameFrom[temp]!!
        }
        return path.reversed()
    }

    private fun shortestPathInLargeGraph(
        start: Point,
        end: Point,
        graph: Map<Point, MutableList<Point>>
    ): List<Point> {
        return aStarSearch(start, end, graph)
    }

    suspend fun findSubgraph(
        largeGraph: MutableMap<Point, MutableList<Point>>,
        smallGraph: CanvasView.Graph
    ): List<List<Point>> {
        val iterations = 1
        val allResults = mutableListOf<Pair<List<Point>, Double>>()

        val rotations = (0 until 360 step 12).map { it.toDouble() }
        val scales = listOf(0.8, 0.9, 1.0, 1.1, 1.2)
        val centroid = Point(
            smallGraph.vertices.map { it.x }.average(),
            smallGraph.vertices.map { it.y }.average()
        )

        Dataholder.totalAttempts = largeGraph.size * rotations.size * scales.size

        // Új külső ciklus: Végighaladunk a nagy gráf minden pontján
        for (largeGraphStartPoint in largeGraph.keys) {
            for (i in 0 until iterations) {
                val results = coroutineScope {
                    rotations.flatMap { rotation ->
                        scales.map { scale ->
                            async(Dispatchers.Default) {
                                Dataholder.currentAttempt++
                                Log.d("gps_app-", "Attempt ${Dataholder.currentAttempt}/${Dataholder.totalAttempts}")
                                val transformedSmallGraph = smallGraph.vertices.map {
                                    Point(it.x, it.y).rotate(rotation, centroid).scale(scale, centroid)
                                        .let {
                                            CanvasView.Vertex(it.x, it.y)
                                        }
                                }

                                if (transformedSmallGraph.isEmpty()) {
                                    return@async null
                                }

                                val potentialMatches = mutableListOf<List<Point>>()

                                // Használjuk a nagy gráf aktuális pontját a keresés indításához
                                val startPointInLargeGraph = largeGraphStartPoint
                                for (j in 1 until transformedSmallGraph.size) {
                                    val endPoint = Point(transformedSmallGraph[j].x, transformedSmallGraph[j].y)
                                    val closestEndPointInLargeGraph = closestPointInLargeGraph(endPoint, largeGraph)
                                    val pathInLargeGraph = shortestPathInLargeGraph(
                                        startPointInLargeGraph,
                                        closestEndPointInLargeGraph,
                                        largeGraph
                                    )
                                    potentialMatches.add(pathInLargeGraph)
                                }

                                val lastPointInTransformed = Point(transformedSmallGraph.last().x, transformedSmallGraph.last().y)
                                val pathToStart = shortestPathInLargeGraph(
                                    closestPointInLargeGraph(lastPointInTransformed, largeGraph),
                                    startPointInLargeGraph,
                                    largeGraph
                                )
                                potentialMatches.add(pathToStart)

                                potentialMatches.maxByOrNull { match ->
                                    matchScore(match, transformedSmallGraph)
                                }
                            }
                        }
                    }.awaitAll()
                }

                val bestResult = results.filterNotNull()
                    .maxByOrNull { match -> matchScore(match, smallGraph.vertices) }
                if (bestResult != null) {
                    allResults.add(Pair(bestResult, matchScore(bestResult, smallGraph.vertices)))
                }
            }
        }

        allResults.sortByDescending { it.second } // Rendezzük csökkenő sorrendben az illeszkedési pontszámok alapján
        val top10Results = allResults.take(10).map { it.first } // Vegyük az első 10 eredményt

        return top10Results
    }

    private fun euklideanDistance(p1: Point, p2: Point): Double {
        return sqrt((p2.x - p1.x).pow(2) + (p2.y - p1.y).pow(2))
    }

    private fun matchScore(largeGraph: List<Point>, smallGraphVertices: List<CanvasView.Vertex>): Double {
        if (smallGraphVertices.isEmpty() || largeGraph.size < smallGraphVertices.size + 1) return 0.0  // +1 for the loopback to the start

        var totalScore = 0.0
        for (i in smallGraphVertices.indices) {
            val e1From = largeGraph[i]
            val e1To = largeGraph[(i + 1) % largeGraph.size]

            val e2From = Point(smallGraphVertices[i].x, smallGraphVertices[i].y)
            val e2To = Point(smallGraphVertices[(i + 1) % smallGraphVertices.size].x, smallGraphVertices[(i + 1) % smallGraphVertices.size].y)

            val length1 = euklideanDistance(e1From, e1To)
            val length2 = euklideanDistance(e2From, e2To)
            val lengthDiff = abs(length1 - length2) / maxOf(length1, length2)

            val angle1 = atan2((e1To.y - e1From.y), (e1To.x - e1From.x))
            val angle2 = atan2((e2To.y - e2From.y), (e2To.x - e2From.x))
            val angleDiff = abs(angle1 - angle2)

            val score = 0.5 * lengthDiff + 0.5 * angleDiff
            totalScore += (1.0 - minOf(score, 0.85))
        }

        return totalScore / smallGraphVertices.size
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