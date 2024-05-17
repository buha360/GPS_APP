package com.wardanger3.gps_app

import android.util.Log
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt
import kotlin.random.Random

class CompareGraph {

    object DataHolder {
        var matchedEndPoints: MutableList<Pair<CanvasView.Vertex, CanvasView.Vertex>> = mutableListOf()

        fun clearData() {
            matchedEndPoints.clear()
        }
    }

    private class AStarNode(val vertex: CanvasView.Vertex, val fValue: Double) :
        Comparable<AStarNode> {
        override fun compareTo(other: AStarNode): Int {
            return this.fValue.compareTo(other.fValue)
        }
    }

    fun findBestRotationMatch(largeGraph: MutableMap<CanvasView.Vertex, MutableList<CanvasView.Vertex>>, transformedGraph: CanvasView.Graph): CanvasView.Graph {
        val bestMatch: CanvasView.Graph?

        // Megkeresi a legközelebbi pontokat a nagy gráfon
        val matches = findClosestPoints(largeGraph, transformedGraph)

        // Összeköti a pontokat az A* algoritmus segítségével a nagy gráfon
        bestMatch = buildCompletePath(matches, largeGraph)

        // A DFS alapú bejárás használata a bestMatch finomítására
        val refinedGraph = dfsBasedTraversal(bestMatch)

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
        Log.d("CompareGraph-DEBUG" ,"DFS at vertex: $current")
        visited.add(current)
        refinedGraph.vertices.add(current)

        for (neighbor in originalGraph.getNeighbors(current)) {
            if (neighbor !in visited) {
                Log.d("CompareGraph-DEBUG", "Checking edge from $current to $neighbor")
                if (isDesiredEdge(current, neighbor, refinedGraph)) {
                    Log.d("CompareGraph-DEBUG", "Adding edge from $current to $neighbor")
                    refinedGraph.edges.add(CanvasView.Edge(current, neighbor))
                    dfs(neighbor, visited, originalGraph, refinedGraph)
                }
            }
        }
    }

    private fun selectStartingVertex(graph: CanvasView.Graph): CanvasView.Vertex {
        return graph.vertices.first()
    }

    private fun isDesiredEdge(start: CanvasView.Vertex, end: CanvasView.Vertex, transformedGraph: CanvasView.Graph): Boolean {
        val edgeLength = calculateManhattanDistance(start, end) // Az él hossza

        val correspondingEdge = findCorrespondingEdge(start, end, transformedGraph)

        if (correspondingEdge != null) {
            val correspondingLength = calculateManhattanDistance(correspondingEdge.start, correspondingEdge.end)
            if (edgeLength < 0.8 * correspondingLength || edgeLength > 1.2 * correspondingLength) {
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
                    val distanceToStart = calculateManhattanDistance(vertex, start)
                    val distanceToEnd = calculateManhattanDistance(vertex, end)
                    val edgeLength = calculateManhattanDistance(start, end)

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
                var shortestPathSegment: List<CanvasView.Vertex> = emptyList()
                var shortestPathLength = Double.POSITIVE_INFINITY

                for (attempt in 1..5) {
                    var pathSegment = findBestPathInLargeGraph(largeGraph, start, end)

                    if (pathSegment.isEmpty()) {
                        // Nincs közvetlen útvonal, alternatívák keresése
                        val alternatives = findAlternativeEndpoints(largeGraph, end)
                        for (alternativeEnd in alternatives) {
                            pathSegment = findBestPathInLargeGraph(largeGraph, start, alternativeEnd)
                            if (pathSegment.isNotEmpty()) break // Megtaláltuk az alternatív útvonalat
                        }
                    }

                    if (pathSegment.isNotEmpty()) {
                        val pathLength = calculatePathLength(pathSegment)
                        if (pathLength < shortestPathLength) {
                            shortestPathSegment = pathSegment
                            shortestPathLength = pathLength
                        }
                    }
                }

                currentPathSegment.addAll(shortestPathSegment)
            }
        }

        if (currentPathSegment.isNotEmpty()) {
            addPathSegmentToGraph(currentPathSegment, completeGraph)
        }

        return removeUnwantedEdges(completeGraph)
    }

    private fun findBestPathInLargeGraph(largeGraph: Map<CanvasView.Vertex, MutableList<CanvasView.Vertex>>, start: CanvasView.Vertex, goal: CanvasView.Vertex): List<CanvasView.Vertex> {
        // Ellenőrzés, hogy a start pont szerepel-e a matchedEndPoints listában
        Log.d("CompareGraph-DEBUG", "Finding best path from $start to $goal")
        val isStartMatched = DataHolder.matchedEndPoints.any { it.second == start }

        // Ha a start pont szerepel a matchedEndPoints listában, térjünk vissza üres listával
        if (isStartMatched) {
            Log.d("CompareGraph-DEBUG", "Start point $start is already matched, returning empty path")
            return emptyList()
        }

        // Heurisztika: Manhatta távolság a célcsúcshoz + véletlenszerű érték
        val heuristic = { vertex: CanvasView.Vertex -> calculateManhattanDistance(vertex, goal) + Random.nextDouble(0.0, 1.0) }
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
                        val tentativeGValue = gValues[currentNode.vertex]!! + calculateManhattanDistance(currentNode.vertex, neighbor)
                        if (tentativeGValue < gValues[neighbor]!!) {
                            parentNodes[neighbor] = currentNode.vertex
                            gValues[neighbor] = tentativeGValue
                            openList.add(AStarNode(neighbor, tentativeGValue + heuristic(neighbor)))
                        }
                    }
                }
            }
        }

        return emptyList() // Nem talált útvonal
    }

    private fun calculatePathLength(pathSegment: List<CanvasView.Vertex>): Double {
        var length = 0.0
        for (i in 0 until pathSegment.size - 1) {
            length += calculateManhattanDistance(pathSegment[i], pathSegment[i + 1])
        }
        return length
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

    private fun findAlternativeEndpoints(largeGraph: Map<CanvasView.Vertex, MutableList<CanvasView.Vertex>>, originalGoal: CanvasView.Vertex): List<CanvasView.Vertex> {
        // Távolság alapján rendezett csúcsok és távolságuk listája
        val distances = largeGraph.keys.map { vertex ->
            vertex to calculateDistance(vertex, originalGoal)
        }.sortedBy { it.second }

        // Visszaadjuk a legközelebbi 15 pontot, kihagyva az eredeti célpontot, ha szerepel a listában
        return distances.filter { it.first != originalGoal }.map { it.first }.take(10)
    }

    private fun calculateDistance(point1: CanvasView.Vertex, point2: CanvasView.Vertex): Double {
        val dx = point2.x - point1.x
        val dy = point2.y - point1.y
        return sqrt(dx * dx + dy * dy)
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

    private fun findClosestPoints(largeGraph: Map<CanvasView.Vertex, MutableList<CanvasView.Vertex>>, transformedGraph: CanvasView.Graph): MutableList<Pair<CanvasView.Vertex, CanvasView.Vertex>> {
        val matches = mutableListOf<Pair<CanvasView.Vertex, CanvasView.Vertex>>()

        transformedGraph.vertices.forEach { transformedVertex ->
            val closest = largeGraph.keys.minByOrNull { calculateManhattanDistance(it, transformedVertex) } ?: transformedVertex
            matches.add(Pair(transformedVertex, closest))
        }

        DataHolder.clearData()
        CanvasView.DataHolder.endPoints.forEach { endPoint ->
            val closestMatch = matches.minByOrNull { calculateManhattanDistance(it.second, endPoint) }
            closestMatch?.let {
                DataHolder.matchedEndPoints.add(Pair(endPoint, it.second))
            }
        }

        return matches
    }

    private fun calculateManhattanDistance(point1: CanvasView.Vertex, point2: CanvasView.Vertex): Double {
        return abs(point2.x - point1.x) + abs(point2.y - point1.y)
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
