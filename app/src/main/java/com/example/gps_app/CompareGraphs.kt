package com.example.gps_app

import kotlin.math.min
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.PriorityQueue
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class CompareGraph {

    object DataHolder {
        var matchedEndPoints: MutableList<Pair<CanvasView.Vertex, CanvasView.Vertex>> = mutableListOf()
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

    suspend fun findBestRotationMatch(largeGraph: MutableMap<CanvasView.Vertex, MutableList<CanvasView.Vertex>>, transformedGraph: CanvasView.Graph, canvasWidth: Int, canvasHeight: Int): CanvasView.Graph {
        var bestMatch: CanvasView.Graph? = null
        var bestMatchScore = Double.MAX_VALUE

        // Ha a rajzolt gráf éleinek száma >= 5, közvetlenül a képre helyezzük
        if (transformedGraph.edges.size > 5) {

            // Megkeresi a legközelebbi pontokat a nagy gráfon
            val matches = findClosestPoints(largeGraph, transformedGraph)

            // Összeköti a pontokat az A* algoritmus segítségével a nagy gráfon
            bestMatch = buildCompletePath(matches, largeGraph)
        } else {
            // Poisson-disk pontgenerálás és shape context alkalmazása, ha kevesebb mint 5 él van
            val scaleFactors = listOf(1.0)
            val spiralPoints = generatePoissonDiscPoints(canvasWidth, canvasHeight)
            Log.d("spiralPoints", "Generált pontok száma: ${spiralPoints.size}")

            coroutineScope {
                val totalIterations = spiralPoints.size * 72
                var currentIteration = 0
                val jobs = spiralPoints.flatMap { spiralPoint ->
                    scaleFactors.flatMap { scaleFactor ->
                        (0 until 360 step 5).map { angle ->
                            async(Dispatchers.Default) {
                                val scaledGraph = scaleGraph(transformedGraph, scaleFactor)
                                val rotatedGraph = rotateGraph(
                                    scaledGraph,
                                    angle.toDouble(),
                                    calculateCentroid(scaledGraph.vertices)
                                )
                                val transformed = translateGraph(
                                    rotatedGraph,
                                    spiralPoint.x - calculateCentroid(rotatedGraph.vertices).x,
                                    spiralPoint.y - calculateCentroid(rotatedGraph.vertices).y
                                )

                                val currentMatches = findClosestPoints(largeGraph, transformed)
                                val pathSegment = buildCompletePath4(currentMatches, largeGraph)

                                val shapeContext = ShapeContext(transformed, pathSegment)
                                val similarityScore = shapeContext.compareGraphs()

                                if (similarityScore < bestMatchScore) {
                                    bestMatchScore = similarityScore
                                    bestMatch = pathSegment
                                    Log.d(
                                        "ShapeContext-CG",
                                        "BestMatchScore: $bestMatchScore BestMatch: $bestMatch"
                                    )
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
        }

        // A bestMatch értéke vagy a legjobb megtalált egyezés vagy a transzformált gráf
        val finalBestMatch = bestMatch ?: transformedGraph

        // A DFS alapú bejárás használata a bestMatch finomítására
        val refinedGraph = dfsBasedTraversal(finalBestMatch)

        // Túllógó élek visszavágása a finomított gráfról
        return trimExcessEdges(refinedGraph)
    }

    private fun dfsBasedTraversal(transformedGraph: CanvasView.Graph): CanvasView.Graph {
        val visited = mutableSetOf<CanvasView.Vertex>()
        val refinedGraph = CanvasView.Graph()

        // Induló csúcs kiválasztása
        val startingVertex = selectStartingVertex(transformedGraph)

        // DFS bejárás kezdete
        dfs(startingVertex, visited, transformedGraph, refinedGraph)

        return refinedGraph
    }

    private fun dfs(current: CanvasView.Vertex, visited: MutableSet<CanvasView.Vertex>, originalGraph: CanvasView.Graph, refinedGraph: CanvasView.Graph) {
        visited.add(current)
        refinedGraph.vertices.add(current)

        // Végigiterálás az aktuális csúcs szomszédain
        for (neighbor in originalGraph.getNeighbors(current)) {
            if (neighbor !in visited && isDesiredEdge(current, neighbor, refinedGraph)) {
                refinedGraph.edges.add(CanvasView.Edge(current, neighbor))
                dfs(neighbor, visited, originalGraph, refinedGraph)
            }
        }
    }

    private fun selectStartingVertex(graph: CanvasView.Graph): CanvasView.Vertex {
        return graph.vertices.first()
    }

    private fun isDesiredEdge(start: CanvasView.Vertex, end: CanvasView.Vertex, transformedGraph: CanvasView.Graph): Boolean {
        val edgeLength = calculateDistance(start, end) // Az él hossza

        val correspondingEdge = findCorrespondingEdge(start, end, transformedGraph)

        if (correspondingEdge != null) {
            val correspondingLength = calculateDistance(correspondingEdge.start, correspondingEdge.end)
            if (edgeLength < 0.9 * correspondingLength || edgeLength > 1.1 * correspondingLength) {
                return false // Az él hossza nem felel meg a kritériumoknak
            }

            // Ellenőrizze a szögeltérést
            val angleDeviation = calculateAngleDeviation(start, end, correspondingEdge.start, correspondingEdge.end)
            if (angleDeviation > 20) { // Megengedett eltérés: 20 fok
                return false // A szögeltérés nem felel meg a kritériumoknak
            }
        }

        return true // Az él megfelel a kritériumoknak
    }

    private fun findCorrespondingEdge(start: CanvasView.Vertex, end: CanvasView.Vertex, graph: CanvasView.Graph): CanvasView.Edge? {
        for (edge in graph.edges) {
            if (isSimilarEdge(edge, start, end)) {
                return edge
            }
        }
        return null
    }

    private fun isSimilarEdge(edge: CanvasView.Edge, start: CanvasView.Vertex, end: CanvasView.Vertex): Boolean {
        return (edge.start == start && edge.end == end) || (edge.start == end && edge.end == start)
    }

    private fun calculateAngleDeviation(start1: CanvasView.Vertex, end1: CanvasView.Vertex, start2: CanvasView.Vertex, end2: CanvasView.Vertex): Double {
        // Vektorok kiszámítása az élekhez
        val vector1 = Pair(end1.x - start1.x, end1.y - start1.y)
        val vector2 = Pair(end2.x - start2.x, end2.y - start2.y)

        // Vektorok közötti szögek
        val dotProduct = vector1.first * vector2.first + vector1.second * vector2.second
        val magnitude1 = sqrt(vector1.first * vector1.first + vector1.second * vector1.second)
        val magnitude2 = sqrt(vector2.first * vector2.first + vector2.second * vector2.second)

        // Képletek a szög kiszámításához
        val cosineOfAngle = dotProduct / (magnitude1 * magnitude2)
        val angleInRadians = acos(cosineOfAngle)

        // Átváltás radiánból fokba
        return Math.toDegrees(angleInRadians)
    }

    // Új függvény a túllógó élek kezelésére
    private fun trimExcessEdges(graph: CanvasView.Graph, threshold: Double = 0.1): CanvasView.Graph {
        val trimmedGraph = CanvasView.Graph()
        trimmedGraph.vertices.addAll(graph.vertices)

        for (edge in graph.edges) {
            val start = edge.start
            val end = edge.end
            var closestPoint = start // Kezdetben a start pont a legközelebbi

            // Keressük meg a legközelebbi pontot az él végpontjaihoz képest
            for (vertex in graph.vertices) {
                if (vertex != start && vertex != end) {
                    val distanceToStart = calculateDistance(vertex, start)
                    val distanceToEnd = calculateDistance(vertex, end)
                    val edgeLength = calculateDistance(start, end)

                    // Ha a pont közelebb van a végpontokhoz, mint az él hossza, és a távolság kisebb, mint a küszöbérték
                    if ((distanceToStart < edgeLength || distanceToEnd < edgeLength) && (distanceToStart < threshold || distanceToEnd < threshold)) {
                        closestPoint = vertex
                        break
                    }
                }
            }

            // Ha a legközelebbi pont nem a kezdőpont, hozzuk létre az új éleket
            if (closestPoint != start) {
                trimmedGraph.edges.add(CanvasView.Edge(start, closestPoint))
                trimmedGraph.edges.add(CanvasView.Edge(closestPoint, end))
            } else {
                trimmedGraph.edges.add(edge)
            }
        }

        return trimmedGraph
    }

    private fun buildCompletePath(matches: List<Pair<CanvasView.Vertex, CanvasView.Vertex>>, largeGraph: Map<CanvasView.Vertex, MutableList<CanvasView.Vertex>>): CanvasView.Graph {
        val completeGraph = CanvasView.Graph()
        val currentPathSegment = mutableListOf<CanvasView.Vertex>()

        for (i in matches.indices) {
            val start = matches[i].second
            val isStartMatched = DataHolder.matchedEndPoints.any { it.second == start }

            if (isStartMatched && currentPathSegment.isNotEmpty()) {
                // Zárja le az aktuális szegmentumot, ha új objektum kezdődik
                addPathSegmentToGraph(currentPathSegment, completeGraph)
                currentPathSegment.clear()
            }
            currentPathSegment.add(start)

            if (i < matches.size - 1 && !isStartMatched) {
                // Csak akkor folytatja az útvonalat, ha nem egy új objektum kezdete
                val end = matches[i + 1].second
                val pathSegment = findBestPathInLargeGraph(largeGraph, start, end)
                currentPathSegment.addAll(pathSegment)
            }
        }

        if (currentPathSegment.isNotEmpty()) {
            addPathSegmentToGraph(currentPathSegment, completeGraph)
        }

        return removeUnwantedEdges(completeGraph)
    }

    private fun buildCompletePath4(matches: List<Pair<CanvasView.Vertex, CanvasView.Vertex>>, largeGraph: Map<CanvasView.Vertex, MutableList<CanvasView.Vertex>>): CanvasView.Graph {
        val completeGraph = CanvasView.Graph()

        // Végig iterálunk a párosításokon és összekötjük az illesztett pontokat a térképen
        for (i in 0 until matches.size - 1) {
            val start = matches[i].second // Az aktuális pont a nagy gráfon
            val end = matches[i + 1].second // A következő pont a nagy gráfon

            // Az A* algoritmus segítségével megtaláljuk a legjobb utat a két pont között
            val pathSegment = findBestPathInLargeGraph4(largeGraph, start, end)

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

    private fun findBestPathInLargeGraph4(largeGraph: Map<CanvasView.Vertex, MutableList<CanvasView.Vertex>>, start: CanvasView.Vertex, goal: CanvasView.Vertex): List<CanvasView.Vertex> {
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

    private fun removeUnwantedEdges(graph: CanvasView.Graph): CanvasView.Graph {
        val edgesToRemove = mutableListOf<CanvasView.Edge>()

        for (edge in graph.edges) {
            // Ellenőrizzük, hogy az él kezdő- vagy végpontja szerepel-e az endPoints listában
            if (CanvasView.DataHolder.endPoints.contains(edge.start) || CanvasView.DataHolder.endPoints.contains(edge.end)) {
                edgesToRemove.add(edge)
            }
        }

        graph.edges.removeAll(edgesToRemove)
        return graph
    }

    private fun addPathSegmentToGraph(pathSegment: List<CanvasView.Vertex>, graph: CanvasView.Graph) {
        pathSegment.forEach { vertex ->
            graph.vertices.add(vertex)
        }

        for (j in 0 until pathSegment.size - 1) {
            val pathStart = pathSegment[j]
            val pathEnd = pathSegment[j + 1]
            graph.edges.add(CanvasView.Edge(pathStart, pathEnd))
        }
    }

    private fun findBestPathInLargeGraph(largeGraph: Map<CanvasView.Vertex, MutableList<CanvasView.Vertex>>, start: CanvasView.Vertex, goal: CanvasView.Vertex): List<CanvasView.Vertex> {
        // Ellenőrzés, hogy a start pont szerepel-e a matchedEndPoints listában
        val isStartMatched = DataHolder.matchedEndPoints.any { it.second == start }

        // Ha a start pont szerepel a matchedEndPoints listában, térjünk vissza üres listával
        if (isStartMatched) {
            return emptyList() // Ne csináljon semmit, mivel a start pont már össze van kötve
        }

        // Heurisztika: Euklideszi távolság a célcsúcshoz
        val heuristic = { vertex: CanvasView.Vertex -> calculateDistance(vertex, goal) }
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
                    if (!closedSet.contains(neighbor)) {
                        val tentativeGValue = gValues[currentNode.vertex]!! + calculateDistance(currentNode.vertex, neighbor)
                        if (tentativeGValue < gValues[neighbor]!!) {
                            parentNodes[neighbor] = currentNode.vertex
                            gValues[neighbor] = tentativeGValue
                            openList.add(AStarNode(neighbor, tentativeGValue + heuristic(neighbor)))
                        }
                    }
                }
            }
        }

        return emptyList() // Nem talált útvonal, ha a start pont nem volt matched
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

    private fun generatePoissonDiscPoints(canvasWidth: Int, canvasHeight: Int, minDist: Double = 45.0, numberOfPoints: Int = 165): List<CanvasView.Vertex> {
        val samplePoints = mutableListOf<CanvasView.Vertex>()
        val activeList = mutableListOf<CanvasView.Vertex>()

        // Kezdőpont hozzáadása
        val initialPoint = CanvasView.Vertex(canvasWidth / 2.0, canvasHeight / 2.0)
        samplePoints.add(initialPoint)
        activeList.add(initialPoint)

        val centerX = canvasWidth / 2.0
        val centerY = canvasHeight / 2.0
        val maxRadius = min(canvasWidth, canvasHeight) / 2.8

        while (activeList.isNotEmpty() && samplePoints.size < numberOfPoints) {
            val pointIndex = (Math.random() * activeList.size).toInt()
            val point = activeList[pointIndex]
            var found = false

            for (i in 0 until numberOfPoints) {
                val angle = Math.random() * 2 * Math.PI
                val radius = minDist * (Math.random() + 1)
                val newX = point.x + radius * cos(angle)
                val newY = point.y + radius * sin(angle)
                val newPoint = CanvasView.Vertex(newX, newY)

                // Ellenőrizzük, hogy a pont a körön belül van-e és elegendő távolságra van-e a többi ponttól
                val isInsideCircle = (newX - centerX) * (newX - centerX) + (newY - centerY) * (newY - centerY) <= maxRadius * maxRadius
                val isFarEnough = samplePoints.none { existingPoint ->
                    val dx = existingPoint.x - newX
                    val dy = existingPoint.y - newY
                    sqrt(dx * dx + dy * dy) < minDist
                }

                if (isInsideCircle && isFarEnough) {
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

        return samplePoints
    }

    private fun findClosestPoints(largeGraph: Map<CanvasView.Vertex, MutableList<CanvasView.Vertex>>, transformedGraph: CanvasView.Graph): MutableList<Pair<CanvasView.Vertex, CanvasView.Vertex>> {
        val matches = mutableListOf<Pair<CanvasView.Vertex, CanvasView.Vertex>>()

        transformedGraph.vertices.forEach { transformedVertex ->
            val closest = largeGraph.keys.minByOrNull { calculateDistance(it, transformedVertex) } ?: transformedVertex
            matches.add(Pair(transformedVertex, closest))
        }

        CanvasView.DataHolder.endPoints.forEach { endPoint ->
            val closestMatch = matches.minByOrNull { calculateDistance(it.second, endPoint) }
            closestMatch?.let {
                DataHolder.matchedEndPoints.add(Pair(endPoint, it.second))
            }
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