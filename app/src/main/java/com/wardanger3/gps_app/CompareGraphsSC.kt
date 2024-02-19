package com.wardanger3.gps_app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.PriorityQueue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class CompareGraphsSC {

    private class AStarNode(val vertex: CanvasViewSC.Vertex, val fValue: Double) :
        Comparable<AStarNode> {
        override fun compareTo(other: AStarNode): Int {
            return this.fValue.compareTo(other.fValue)
        }
    }

    var progressListener: ProgressListener? = null

    interface ProgressListener {
        fun onProgressUpdate(progress: Int)
    }

    private fun scaleGraph(graph: CanvasViewSC.Graph, scaleFactor: Double): CanvasViewSC.Graph {
        val scaledGraph = CanvasViewSC.Graph()
        val scaledVertices = mutableMapOf<CanvasViewSC.Vertex, CanvasViewSC.Vertex>()

        // Skálázás a csúcsokon
        for (vertex in graph.vertices) {
            val scaledX = vertex.x * scaleFactor
            val scaledY = vertex.y * scaleFactor
            val scaledVertex = CanvasViewSC.Vertex(scaledX, scaledY)
            scaledVertices[vertex] = scaledVertex
            scaledGraph.vertices.add(scaledVertex)
        }

        // Skálázás az éleken
        for (edge in graph.edges) {
            val scaledStart = scaledVertices[edge.start]
            val scaledEnd = scaledVertices[edge.end]
            if (scaledStart != null && scaledEnd != null) {
                scaledGraph.edges.add(CanvasViewSC.Edge(scaledStart, scaledEnd))
            }
        }

        return scaledGraph
    }

    private fun calculateCentroid(vertices: List<CanvasViewSC.Vertex>): CanvasViewSC.Vertex {
        val sumX = vertices.sumOf { it.x }
        val sumY = vertices.sumOf { it.y }
        return CanvasViewSC.Vertex(sumX / vertices.size, sumY / vertices.size)
    }

    private fun translateGraph(graph: CanvasViewSC.Graph, translationX: Double, translationY: Double): CanvasViewSC.Graph {
        val translatedGraph = CanvasViewSC.Graph()
        val translatedVertices = mutableMapOf<CanvasViewSC.Vertex, CanvasViewSC.Vertex>()

        for (vertex in graph.vertices) {
            val translatedX = vertex.x + translationX
            val translatedY = vertex.y + translationY
            val translatedVertex = CanvasViewSC.Vertex(translatedX, translatedY)
            translatedVertices[vertex] = translatedVertex
            translatedGraph.vertices.add(translatedVertex)
        }

        for (edge in graph.edges) {
            val translatedStart = translatedVertices[edge.start]!!
            val translatedEnd = translatedVertices[edge.end]!!
            translatedGraph.edges.add(CanvasViewSC.Edge(translatedStart, translatedEnd))
        }

        return translatedGraph
    }

    suspend fun findBestRotationMatch(largeGraph: MutableMap<CanvasViewSC.Vertex, MutableList<CanvasViewSC.Vertex>>, transformedGraph: CanvasViewSC.Graph, canvasWidth: Int, canvasHeight: Int): CanvasViewSC.Graph? {
        val scaleFactors = listOf(1.0)
        val spiralPoints = generatePoissonDiscPointsWithQuadtree(canvasWidth, canvasHeight)
        var bestMatch: CanvasViewSC.Graph? = null
        var bestMatchScore = Double.MAX_VALUE

        coroutineScope {
            val totalIterations = spiralPoints.size * scaleFactors.size * 24
            var currentIteration = 0
            val jobs = spiralPoints.flatMap { spiralPoint ->
                scaleFactors.flatMap { scaleFactor ->
                    (0 until 360 step 15).map { angle ->
                        async(Dispatchers.Default) {
                            val scaledGraph = scaleGraph(transformedGraph, scaleFactor)
                            val rotatedGraph = rotateGraph(scaledGraph, angle.toDouble(), calculateCentroid(scaledGraph.vertices))
                            val transformed = translateGraph(rotatedGraph, spiralPoint.x - calculateCentroid(rotatedGraph.vertices).x, spiralPoint.y - calculateCentroid(rotatedGraph.vertices).y)

                            val currentMatches = findClosestPoints(largeGraph, transformed)
                            val pathSegment = buildCompletePath(currentMatches, largeGraph)

                            val shapeContext = ShapeContext(transformed, pathSegment)
                            val similarityScore = shapeContext.compareGraphs()

                            if (similarityScore < bestMatchScore) {
                                bestMatchScore = similarityScore
                                bestMatch = pathSegment
                                // Log.d("ShapeContext-CG", "BestMatchScore: $bestMatchScore BestMatch: $bestMatch")
                            }

                            currentIteration++
                            val progress = (currentIteration.toDouble() / totalIterations * 100).toInt()
                            progressListener?.onProgressUpdate(progress)
                        }
                    }
                }
            }

            for (job in jobs) {
                job.await()
            }
        }

        return bestMatch
    }

    private fun buildCompletePath(matches: List<Pair<CanvasViewSC.Vertex, CanvasViewSC.Vertex>>, largeGraph: Map<CanvasViewSC.Vertex, MutableList<CanvasViewSC.Vertex>>): CanvasViewSC.Graph {
        val completeGraph = CanvasViewSC.Graph()

        for (i in 0 until matches.size - 1) {
            val start = matches[i].second
            val end = matches[i + 1].second

            // Log.d("CompareGraphsSC", "Finding path between points: Start: $start, End: $end")

            var pathSegment = findBestPathInLargeGraph(largeGraph, start, end)

            if (pathSegment.isEmpty()) {
                // Log.d("CompareGraphsSC", "No path found between: $start and $end, looking for alternatives.")
                val alternatives = findAlternativeEndpoints(largeGraph, end)

                for (alternativeEnd in alternatives) {
                    pathSegment = findBestPathInLargeGraph(largeGraph, start, alternativeEnd)
                    if (pathSegment.isNotEmpty()) {
                        // Log.d("CompareGraphsSC", "Alternative path found. Segment size: ${pathSegment.size}")
                    }
                }

                if (pathSegment.isEmpty()) {
                    // Log.d("CompareGraphsSC", "No alternative path found either.")
                    continue // Skip to the next segment if no path found
                }
            }

            pathSegment.forEach { vertex ->
                completeGraph.vertices.add(vertex)
            }

            for (j in 0 until pathSegment.size - 1) {
                val pathStart = pathSegment[j]
                val pathEnd = pathSegment[j + 1]
                completeGraph.edges.add(CanvasViewSC.Edge(pathStart, pathEnd))
            }
        }

        // Log.d("CompareGraphsSC", "Complete graph built successfully. Vertices: ${completeGraph.vertices.size}, Edges: ${completeGraph.edges.size}")

        return completeGraph
    }

    private fun findBestPathInLargeGraph(largeGraph: Map<CanvasViewSC.Vertex, MutableList<CanvasViewSC.Vertex>>, start: CanvasViewSC.Vertex, goal: CanvasViewSC.Vertex): List<CanvasViewSC.Vertex> {
        val heuristic = { _: CanvasViewSC.Vertex -> 0.0 }
        val openList = PriorityQueue<AStarNode>()
        val closedSet = mutableSetOf<CanvasViewSC.Vertex>()
        val gValues = largeGraph.keys.associateWith { Double.POSITIVE_INFINITY }.toMutableMap()
        gValues[start] = 0.0

        val parentNodes = mutableMapOf<CanvasViewSC.Vertex, CanvasViewSC.Vertex?>()
        openList.add(AStarNode(start, heuristic(start)))

        while (openList.isNotEmpty()) {
            val currentNode = openList.poll()

            if (currentNode != null) {
                if (currentNode.vertex == goal) {
                    return constructPath(parentNodes, goal)
                }

                closedSet.add(currentNode.vertex)

                largeGraph[currentNode.vertex]?.forEach { neighbor ->
                    if (closedSet.contains(neighbor)) return@forEach

                    val tentativeGValue = gValues[currentNode.vertex]!! + calculateDistance(
                        currentNode.vertex,
                        neighbor
                    )
                    if (tentativeGValue < gValues[neighbor]!!) {
                        gValues[neighbor] = tentativeGValue
                        parentNodes[neighbor] = currentNode.vertex

                        // Az 'AStarNode' létrehozása a 'vertex' és az F érték alapján
                        openList.add(AStarNode(start, heuristic(start)))

                        // Egy másik 'AStarNode' létrehozása egy másik csúcs és F érték alapján
                        openList.add(AStarNode(neighbor, tentativeGValue + heuristic(neighbor)))
                    }
                }
            }
        }

        return emptyList() // Nem talált útvonal
    }

    private fun findAlternativeEndpoints(largeGraph: Map<CanvasViewSC.Vertex, MutableList<CanvasViewSC.Vertex>>, originalGoal: CanvasViewSC.Vertex): List<CanvasViewSC.Vertex> {
        // Távolság alapján rendezett csúcsok és távolságuk listája
        val distances = largeGraph.keys.map { vertex ->
            vertex to calculateDistance(vertex, originalGoal)
        }.sortedBy { it.second }

        // Visszaadjuk a legközelebbi három pontot, kihagyva az eredeti célpontot, ha szerepel a listában
        return distances.filter { it.first != originalGoal }.map { it.first }.take(4)
    }

    private fun constructPath(parentNodes: Map<CanvasViewSC.Vertex, CanvasViewSC.Vertex?>, goal: CanvasViewSC.Vertex): List<CanvasViewSC.Vertex> {
        val path = mutableListOf<CanvasViewSC.Vertex>()
        var current: CanvasViewSC.Vertex? = goal
        while (current != null) {
            path.add(current)
            current = parentNodes[current]
        }
        return path.reversed()
    }

    private fun generatePoissonDiscPointsWithQuadtree(canvasWidth: Int, canvasHeight: Int, minDist: Double = 60.0, maxDist: Double = 100.0, numberOfPoints: Int = 10): List<CanvasViewSC.Vertex> {
        val samplePoints = mutableListOf<CanvasViewSC.Vertex>()
        val activeList = mutableListOf<CanvasViewSC.Vertex>()
        val random = Random.Default
        val quadtree = Quadtree(Rectangle(0.0, 0.0, canvasWidth.toDouble(), canvasHeight.toDouble()), 4)

        // Kezdőpont hozzáadása és Quadtree-hez adása
        val initialPoint = CanvasViewSC.Vertex(canvasWidth / 2.0, canvasHeight / 2.0)
        quadtree.insert(initialPoint)
        samplePoints.add(initialPoint)
        activeList.add(initialPoint)

        val centerX = canvasWidth / 2.0
        val centerY = canvasHeight / 2.0
        val maxRadius = min(canvasWidth, canvasHeight) / 2.8

        while (activeList.isNotEmpty() && samplePoints.size < numberOfPoints) {
            val pointIndex = random.nextInt(activeList.size)
            val point = activeList[pointIndex]

            var found = false
            for (i in 0 until numberOfPoints) {
                val angle = random.nextDouble(2 * PI)
                val radius = random.nextDouble(minDist, maxDist)
                val newX = point.x + radius * cos(angle)
                val newY = point.y + radius * sin(angle)
                val newPoint = CanvasViewSC.Vertex(newX, newY)

                val isInsideCircle = (newX - centerX).pow(2) + (newY - centerY).pow(2) <= maxRadius.pow(2)

                if (isInsideCircle && isPointFarEnough(newPoint, quadtree, minDist)) {
                    quadtree.insert(newPoint)
                    activeList.add(newPoint)
                    samplePoints.add(newPoint)
                    found = true
                    break
                }
            }

            if (!found) {
                activeList.removeAt(pointIndex)
            }
        }

        Log.d("Poisson","Generated ${samplePoints.size} points")
        return samplePoints
    }

    private fun isPointFarEnough(point: CanvasViewSC.Vertex, quadtree: Quadtree, minDist: Double): Boolean {
        val searchArea = Rectangle(point.x - minDist, point.y - minDist, 2 * minDist, 2 * minDist)
        val nearbyPoints = quadtree.query(searchArea)
        return nearbyPoints.none { existingPoint ->
            val dx = existingPoint.x - point.x
            val dy = existingPoint.y - point.y
            sqrt(dx * dx + dy * dy) < minDist
        }
    }

    private fun findClosestPoints(largeGraph: Map<CanvasViewSC.Vertex, MutableList<CanvasViewSC.Vertex>>, transformedGraph: CanvasViewSC.Graph): MutableList<Pair<CanvasViewSC.Vertex, CanvasViewSC.Vertex>> {
        val matches = mutableListOf<Pair<CanvasViewSC.Vertex, CanvasViewSC.Vertex>>()
        transformedGraph.vertices.forEach { transformedVertex ->
            val closest = largeGraph.keys.minByOrNull { calculateDistance(it, transformedVertex) }
                ?: transformedVertex
            matches.add(Pair(transformedVertex, closest))
        }
        return matches
    }

    private fun calculateDistance(point1: CanvasViewSC.Vertex, point2: CanvasViewSC.Vertex): Double {
        val dx = point2.x - point1.x
        val dy = point2.y - point1.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun rotateGraph(graph: CanvasViewSC.Graph, angleDegrees: Double, centroid: CanvasViewSC.Vertex): CanvasViewSC.Graph {
        val rotatedGraph = CanvasViewSC.Graph()
        val rotatedVertices = mutableMapOf<CanvasViewSC.Vertex, CanvasViewSC.Vertex>()
        val angleRadians = Math.toRadians(angleDegrees)

        for (vertex in graph.vertices) {
            val shiftedX = vertex.x - centroid.x
            val shiftedY = vertex.y - centroid.y

            val rotatedX = shiftedX * cos(angleRadians) - shiftedY * sin(angleRadians)
            val rotatedY = shiftedX * sin(angleRadians) + shiftedY * cos(angleRadians)

            val rotatedVertex = CanvasViewSC.Vertex(rotatedX + centroid.x, rotatedY + centroid.y)
            rotatedVertices[vertex] = rotatedVertex
            rotatedGraph.vertices.add(rotatedVertex)
        }

        for (edge in graph.edges) {
            val rotatedStart = rotatedVertices[edge.start]!!
            val rotatedEnd = rotatedVertices[edge.end]!!
            rotatedGraph.edges.add(CanvasViewSC.Edge(rotatedStart, rotatedEnd))
        }

        return rotatedGraph
    }

    companion object {
        private var instance: CompareGraphsSC? = null
        fun getInstance(): CompareGraphsSC {
            if (instance == null) {
                instance = CompareGraphsSC()
            }
            return instance!!
        }
    }
}