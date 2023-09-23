package com.example.gps_app

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/*
    Valószínű, hogy adjustolni kell még és jobb stratégiát kitalálni erre a problémára
    Lehetne az, hogy ahol talál matchScore()-t, ott rögtön elkezdhetnénk az útbejárást a graphMap alapján
    Végigmegyünk tranformációs szabályokkal és ha sikeres a bejárás, akkor elmentjük egy listába az egyezési arányával együtt, ha nem
    Akkor eldobjuk az útvonalat és ugrunk a következőre ?
 */

class CompareGraphs(private val graphDraw: MutableList<SVGtoGraph.Edge>, private val graphMap: MutableList<SVGtoGraph.Edge>) {
    class Node(val point: SVGtoGraph.Point) {
        val edges = mutableListOf<Edge>()
    }

    class Edge(val from: Node, val to: Node, val weight: Double)

    private fun distance(p1: SVGtoGraph.Point, p2: SVGtoGraph.Point): Double {
        return sqrt(((p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y)).toDouble())
    }

    private fun matchScore(e1: SVGtoGraph.Edge, e2: SVGtoGraph.Edge): Double {
        val length1 = distance(e1.from, e1.to)
        val length2 = distance(e2.from, e2.to)
        val lengthDiff = abs(length1 - length2) / maxOf(length1, length2)

        val angle1 = atan2(e1.to.y - e1.from.y, e1.to.x - e1.from.x)
        val angle2 = atan2(e2.to.y - e2.from.y, e2.to.x - e2.from.x)
        val angleDiff = abs(angle1 - angle2)

        val startDistance = distance(e1.from, e2.from)
        val endDistance = distance(e1.to, e2.to)

        val score = 0.3 * lengthDiff + 0.3 * angleDiff + 0.3 * startDistance + 0.3 * endDistance
        return 1.0 - minOf(score, 0.6)
    }

    fun findMatchingEdges(): MutableList<SVGtoGraph.Edge> {
        val matchedEdges = mutableListOf<SVGtoGraph.Edge>()

        for (edgeDraw in graphDraw) {
            for (edgeMap in graphMap) {
                if (matchScore(edgeDraw, edgeMap) >= 0.3) {
                    matchedEdges.add(edgeMap)
                }
            }
        }

        return matchedEdges
    }

    fun constructGraph(): Pair<List<Node>, List<Edge>> {
        val nodes = graphMap.flatMap { listOf(it.from, it.to) }
            .distinctBy { it.x to it.y }
            .map { Node(it) }

        val edges = graphMap.map { edge ->
            val fromNode = nodes.first { node -> node.point == edge.from }
            val toNode = nodes.first { node -> node.point == edge.to }
            Edge(fromNode, toNode, distance(edge.from, edge.to))
        }

        for (edge in edges) {
            edge.from.edges.add(edge)
            edge.to.edges.add(edge)
        }

        return Pair(nodes, edges)
    }

    fun dijkstra(start: SVGtoGraph.Point, nodes: List<Node>): Map<SVGtoGraph.Point, Double> {
        val distances = nodes.associateBy({ it.point }, { Double.POSITIVE_INFINITY }).toMutableMap()
        val visited = nodes.associateBy({ it.point }, { false }).toMutableMap()
        distances[start] = 0.0

        while (true) {
            val current = distances.filterNot { visited[it.key] == true }.minByOrNull { it.value }?.key ?: break

            val currentDist = distances[current] ?: continue
            val neighbors = nodes.first { it.point == current }.edges.map { it.to.point }

            for (neighbor in neighbors) {
                val newDist = currentDist + (distances[neighbor] ?: Double.POSITIVE_INFINITY)
                distances[neighbor] = minOf(distances[neighbor] ?: Double.POSITIVE_INFINITY, newDist)
            }

            visited[current] = true
        }

        return distances
    }
}