package com.example.gps_app

import android.util.Log
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class CompareGraphs(private val graphDraw: List<SVGtoGraph.Edge>, private val graphMap: List<SVGtoGraph.Edge>) {

    data class Node(val point: SVGtoGraph.Point) {
        var distance = Double.MAX_VALUE
        var visited = false
        var previous: Node? = null
    }

    private fun distance(p1: SVGtoGraph.Point, p2: SVGtoGraph.Point): Double {
        return sqrt(((p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y)).toDouble())
    }

    private fun edgeWeight(drawEdge: SVGtoGraph.Edge, mapEdge: SVGtoGraph.Edge): Double {
        return 1.0 / matchScore(drawEdge, mapEdge)
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

        val score = 0.3 * lengthDiff + 0.3 * angleDiff + 0.4 * startDistance + 0.4 * endDistance
        return 1.0 - minOf(score, 0.5)
    }

    fun dijkstra(start: SVGtoGraph.Point, end: SVGtoGraph.Point): List<SVGtoGraph.Edge> {
        val nodes = graphMap.flatMap { listOf(it.from, it.to) }
            .distinct()
            .map { Node(it) }
            .associateBy { it.point }

        nodes[start]?.distance = 0.0

        while (true) {
            val currentNode = nodes.values
                .filterNot { it.visited }
                .minByOrNull { it.distance } ?: break

            currentNode.visited = true

            val neighbors = graphMap
                .filter { it.from == currentNode.point || it.to == currentNode.point }
                .mapNotNull { edge ->
                    val neighborPoint = if (edge.from == currentNode.point) edge.to else edge.from
                    val drawEdge = SVGtoGraph.Edge(currentNode.point, neighborPoint)
                    nodes[neighborPoint]?.let { node -> Pair(node, edgeWeight(drawEdge, edge)) }
                }

            for ((neighbor, weight) in neighbors) {
                if (currentNode.distance + weight < neighbor.distance) {
                    neighbor.distance = currentNode.distance + weight
                    neighbor.previous = currentNode
                }
            }
        }

        val path = mutableListOf<SVGtoGraph.Edge>()
        var current = nodes[end]
        while (current?.previous != null) {
            val previous = current.previous!!
            path.add(SVGtoGraph.Edge(current.point, previous.point))
            current = previous
        }

        return path.reversed()
    }

    fun findPath(): List<SVGtoGraph.Edge> {
        val start = graphDraw.first().from
        Log.d("MyApp: - start: ", start.toString())
        val end = graphDraw.last().to
        Log.d("MyApp: - end: ", end.toString())

        return dijkstra(start, end)
    }
}