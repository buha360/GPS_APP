package com.example.gps_app

import kotlin.math.min
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.PriorityQueue
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class CompareGraph {

    object DataHolder {
        var closestMatches: MutableList<Pair<CanvasView.Vertex, CanvasView.Vertex>> =
            mutableListOf()
    }

    private class AStarNode(val vertex: CanvasView.Vertex, val fValue: Double) :
        Comparable<AStarNode> {
        override fun compareTo(other: AStarNode): Int {
            return this.fValue.compareTo(other.fValue)
        }
    }

    var progressListener: ProgressListener? = null

    interface ProgressListener {
        fun onProgressUpdate(progress: Int)
    }

    private fun scaleGraph(graph: CanvasView.Graph, scaleFactor: Double): CanvasView.Graph {
        val scaledGraph = CanvasView.Graph()
        val scaledVertices = mutableMapOf<CanvasView.Vertex, CanvasView.Vertex>()

        // Skálázás a csúcsokon
        for (vertex in graph.vertices) {
            val scaledX = vertex.x * scaleFactor
            val scaledY = vertex.y * scaleFactor
            val scaledVertex = CanvasView.Vertex(scaledX, scaledY)
            scaledVertices[vertex] = scaledVertex
            scaledGraph.vertices.add(scaledVertex)
        }

        // Skálázás az éleken
        for (edge in graph.edges) {
            val scaledStart = scaledVertices[edge.start]
            val scaledEnd = scaledVertices[edge.end]
            if (scaledStart != null && scaledEnd != null) {
                scaledGraph.edges.add(CanvasView.Edge(scaledStart, scaledEnd))
            }
        }

        return scaledGraph
    }

    private fun calculateCentroid(vertices: List<CanvasView.Vertex>): CanvasView.Vertex {
        val sumX = vertices.sumOf { it.x }
        val sumY = vertices.sumOf { it.y }
        return CanvasView.Vertex(sumX / vertices.size, sumY / vertices.size)
    }

    private fun translateGraph(
        graph: CanvasView.Graph,
        translationX: Double,
        translationY: Double
    ): CanvasView.Graph {
        val translatedGraph = CanvasView.Graph()
        val translatedVertices = mutableMapOf<CanvasView.Vertex, CanvasView.Vertex>()

        for (vertex in graph.vertices) {
            val translatedX = vertex.x + translationX
            val translatedY = vertex.y + translationY
            val translatedVertex = CanvasView.Vertex(translatedX, translatedY)
            translatedVertices[vertex] = translatedVertex
            translatedGraph.vertices.add(translatedVertex)
        }

        for (edge in graph.edges) {
            val translatedStart = translatedVertices[edge.start]!!
            val translatedEnd = translatedVertices[edge.end]!!
            translatedGraph.edges.add(CanvasView.Edge(translatedStart, translatedEnd))
        }

        return translatedGraph
    }

    /*
    suspend fun findBestRotationMatch(largeGraph: MutableMap<CanvasView.Vertex, MutableList<CanvasView.Vertex>>, transformedGraph: CanvasView.Graph, canvasWidth: Int, canvasHeight: Int): CanvasView.Graph {
        val scaleFactors = listOf(1.0)
        val spiralPoints = generateSpiralPoints(canvasWidth, canvasHeight)
        Log.d("spiralPoints","Ennyi pont keletkezik: $spiralPoints")
        var bestMatch: CanvasView.Graph? = null
        var bestMatchScore = Double.MAX_VALUE

        coroutineScope {
            val totalIterations = spiralPoints.size * scaleFactors.size * 72
            var currentIteration = 0
            val jobs = spiralPoints.flatMap { spiralPoint ->
                scaleFactors.flatMap { scaleFactor ->
                    (0 until 360 step 5).map { angle ->
                        async(Dispatchers.Default) {
                            val scaledGraph = scaleGraph(transformedGraph, scaleFactor)
                            val rotatedGraph = rotateGraph(scaledGraph, angle.toDouble(), calculateCentroid(scaledGraph.vertices))
                            val transformedGraph = translateGraph(rotatedGraph, spiralPoint.x - calculateCentroid(rotatedGraph.vertices).x, spiralPoint.y - calculateCentroid(rotatedGraph.vertices).y)

                            val currentMatches = findClosestPoints(largeGraph, transformedGraph)
                            val pathSegment = buildCompletePath(currentMatches, largeGraph)

                            val shapeContext = ShapeContext(transformedGraph, pathSegment)
                            val similarityScore = shapeContext.compareGraphs()

                            if (similarityScore < bestMatchScore) {
                                bestMatchScore = similarityScore
                                bestMatch = pathSegment
                                Log.d("ShapeContext-CG", "BestMatchScore: $bestMatchScore BestMatch: $bestMatch")
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

        return bestMatch ?: transformedGraph
    }
     */

    suspend fun findBestRotationMatch(largeGraph: MutableMap<CanvasView.Vertex, MutableList<CanvasView.Vertex>>, originalGraph: CanvasView.Graph, geneticAlgorithm: GeneticAlgorithm): CanvasView.Graph {
        var bestMatch: CanvasView.Graph? = null
        var bestMatchScore = Double.MAX_VALUE

        coroutineScope {
            // Az egyedek inicializálása és tesztelése
            geneticAlgorithm.evolve()

            for (individual in geneticAlgorithm.population) {
                // Az egyed által generált gráf
                val transformedGraph = individual.applyNeuralNetworkToGraph(originalGraph)

                // Az A* algoritmus használata az összekötéshez
                val currentMatches = findClosestPoints(largeGraph, transformedGraph)
                val pathSegment = buildCompletePath(currentMatches, largeGraph)

                // Shape Context használata az illeszkedés értékelésére
                val shapeContext = ShapeContext(transformedGraph, pathSegment)
                val similarityScore = shapeContext.compareGraphs()

                // A legjobb illeszkedés frissítése
                if (similarityScore < bestMatchScore) {
                    bestMatchScore = similarityScore
                    bestMatch = pathSegment
                    Log.d("GeneticAlgorithm-CG", "BestMatchScore: $bestMatchScore BestMatch: $bestMatch")
                }
            }
        }

        return bestMatch ?: originalGraph // Ha nincs találat, az eredeti gráfot adja vissza
    }

    private fun buildCompletePath(matches: List<Pair<CanvasView.Vertex, CanvasView.Vertex>>, largeGraph: Map<CanvasView.Vertex, MutableList<CanvasView.Vertex>>): CanvasView.Graph {
        val completeGraph = CanvasView.Graph()

        // Végig iterálunk a párosításokon és összekötjük az illesztett pontokat a térképen
        for (i in 0 until matches.size - 1) {
            val start = matches[i].second // Az aktuális pont a nagy gráfon
            val end = matches[i + 1].second // A következő pont a nagy gráfon

            // Az A* algoritmus segítségével megtaláljuk a legjobb utat a két pont között
            val pathSegment = findBestPathInLargeGraph(largeGraph, start, end)

            // Hozzáadjuk az útvonal pontjait a teljes gráfhoz csúcsként
            pathSegment.forEach { vertex ->
                completeGraph.vertices.add(vertex)
            }

            // Hozzáadjuk az útvonalat a teljes gráfhoz élként
            for (j in 0 until pathSegment.size - 1) {
                val pathStart = pathSegment[j]
                val pathEnd = pathSegment[j + 1]
                completeGraph.edges.add(CanvasView.Edge(pathStart, pathEnd))
            }
        }

        return completeGraph
    }

    private fun generateSpiralPoints(canvasWidth: Int, canvasHeight: Int): List<CanvasView.Vertex> {
        val centerX = canvasWidth / 2.0
        val centerY = canvasHeight / 2.0
        var angle = 0.0
        var radius = 0.0
        val maxRadius = min(canvasWidth, canvasHeight) / 2.8
        val angleIncrement = Math.PI / 8 // Állandó szög növekedés
        val spiralPoints = mutableListOf<CanvasView.Vertex>()
        var count = 0

        while (radius < maxRadius) {
            val x = centerX + radius * cos(angle)
            val y = centerY + radius * sin(angle)
            spiralPoints.add(CanvasView.Vertex(x, y))

            angle += angleIncrement
            radius += 0.75 + 0.08 * angle / Math.PI // A sugár növekedése arányos az elfordulás szögével

            // Ellenőrzés a túl nagy lépések elkerülésére
            if (radius > maxRadius) {
                radius = maxRadius
            }

            count++
        }

        Log.d("count","$count")
        return spiralPoints
    }

    private fun findClosestPoints(largeGraph: Map<CanvasView.Vertex, MutableList<CanvasView.Vertex>>, transformedGraph: CanvasView.Graph): MutableList<Pair<CanvasView.Vertex, CanvasView.Vertex>> {
        val matches = mutableListOf<Pair<CanvasView.Vertex, CanvasView.Vertex>>()
        transformedGraph.vertices.forEach { transformedVertex ->
            val closest = largeGraph.keys.minByOrNull { calculateDistance(it, transformedVertex) }
                ?: transformedVertex
            matches.add(Pair(transformedVertex, closest))
        }
        return matches
    }

    private fun calculateDistance(point1: CanvasView.Vertex, point2: CanvasView.Vertex): Double {
        val dx = point2.x - point1.x
        val dy = point2.y - point1.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun rotateGraph(graph: CanvasView.Graph, angleDegrees: Double, centroid: CanvasView.Vertex): CanvasView.Graph {
        val rotatedGraph = CanvasView.Graph()
        val rotatedVertices = mutableMapOf<CanvasView.Vertex, CanvasView.Vertex>()
        val angleRadians = Math.toRadians(angleDegrees)

        for (vertex in graph.vertices) {
            val shiftedX = vertex.x - centroid.x
            val shiftedY = vertex.y - centroid.y

            val rotatedX = shiftedX * cos(angleRadians) - shiftedY * sin(angleRadians)
            val rotatedY = shiftedX * sin(angleRadians) + shiftedY * cos(angleRadians)

            val rotatedVertex = CanvasView.Vertex(rotatedX + centroid.x, rotatedY + centroid.y)
            rotatedVertices[vertex] = rotatedVertex
            rotatedGraph.vertices.add(rotatedVertex)
        }

        for (edge in graph.edges) {
            val rotatedStart = rotatedVertices[edge.start]!!
            val rotatedEnd = rotatedVertices[edge.end]!!
            rotatedGraph.edges.add(CanvasView.Edge(rotatedStart, rotatedEnd))
        }

        return rotatedGraph
    }

    private fun findBestPathInLargeGraph(largeGraph: Map<CanvasView.Vertex, MutableList<CanvasView.Vertex>>, start: CanvasView.Vertex, goal: CanvasView.Vertex): List<CanvasView.Vertex> {
        val heuristic = { _: CanvasView.Vertex -> 0.0 }
        val openList = PriorityQueue<AStarNode>()
        val closedSet = mutableSetOf<CanvasView.Vertex>()
        val gValues = largeGraph.keys.associateWith { Double.POSITIVE_INFINITY }.toMutableMap()
        gValues[start] = 0.0

        val parentNodes = mutableMapOf<CanvasView.Vertex, CanvasView.Vertex?>()
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

    private fun constructPath(parentNodes: Map<CanvasView.Vertex, CanvasView.Vertex?>, goal: CanvasView.Vertex): List<CanvasView.Vertex> {
        val path = mutableListOf<CanvasView.Vertex>()
        var current: CanvasView.Vertex? = goal
        while (current != null) {
            path.add(current)
            current = parentNodes[current]
        }
        return path.reversed()
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