package com.wardanger3.gps_app

import android.util.Log
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

class ShapeContext(originalGraph: CanvasViewSC.Graph, transformedGraph: CanvasViewSC.Graph) {

    data class LogPolarCoordinate(val radius: Double, val angle: Double)

    private var logPolarCoordinatesOriginal: MutableList<LogPolarCoordinate>
    private var logPolarCoordinatesTransformed: MutableList<LogPolarCoordinate>

    init {
        val (origin, maxSize) = adjustCoordinateSystem(originalGraph)
        logPolarCoordinatesOriginal = calculateLogPolarCoordinates(originalGraph, origin, maxSize).toMutableList()
        logPolarCoordinatesTransformed = calculateLogPolarCoordinates(transformedGraph, origin, maxSize).toMutableList()
    }

    fun compareGraphs(): Double {
        // 1. Histogramok készítése
        val histogramOriginal = createHistograms(logPolarCoordinatesOriginal)
        val histogramTransformed = createHistograms(logPolarCoordinatesTransformed)

        // 2. Hasonlósági pontszám kiszámítása
        return calculateGlobalSimilarity(histogramOriginal, histogramTransformed)
    }

    private fun calculateLogPolarCoordinates(graph: CanvasViewSC.Graph, origin: Pair<Double, Double>, maxSize: Double): List<LogPolarCoordinate> {
        val logPolarCoordinates = mutableListOf<LogPolarCoordinate>()
        graph.edges.forEach { edge ->
            // Mindkét végpont log-polar koordinátáinak számítása
            logPolarCoordinates.add(cartesianToLogPolar(edge.start, origin, maxSize))
            logPolarCoordinates.add(cartesianToLogPolar(edge.end, origin, maxSize))
        }
        return logPolarCoordinates
    }

    private fun createHistograms(logPolarCoordinates: List<LogPolarCoordinate>): List<Histogram> {
        val numRadiusBins = 4
        val numAngleBins = 8
        return logPolarCoordinates.mapIndexed { index, coordinate ->
            val histogram = Histogram(numRadiusBins, numAngleBins)
            logPolarCoordinates.forEach { otherCoordinate ->
                val binRadius = determineBinRadius(coordinate.radius, otherCoordinate.radius, numRadiusBins)
                val binAngle = determineBinAngle(coordinate.angle, otherCoordinate.angle, numAngleBins)
                histogram.increment(binRadius, binAngle)
            }
            //Log.d("ShapeContext-SC", "Histogram for element $index: ${histogram.bins.contentDeepToString()}")
            histogram
        }
    }

    private fun calculateGlobalSimilarity(histogramOriginal: List<Histogram>, histogramTransformed: List<Histogram>): Double {
        if (histogramOriginal.isEmpty() || histogramTransformed.isEmpty()) {
            return 0.0
        }

        var similarityScore = 0.0
        try {
            for (i in histogramOriginal.indices) {
                var localSimilarity = 0.0

                for (j in 0 until histogramOriginal[i].bins.size) {
                    for (k in 0 until histogramOriginal[i].bins[j].size) {
                        val originalValue = histogramOriginal[i].bins[j][k]
                        val transformedValue = histogramTransformed[i].bins[j][k]

                        if (originalValue + transformedValue > 0) {
                            // Chi-négyzet számítás
                            val diff = originalValue - transformedValue
                            localSimilarity += diff * diff / (originalValue + transformedValue).toDouble()
                        }
                    }
                }

                similarityScore += localSimilarity
            }
        } catch (e: IndexOutOfBoundsException) {
            Log.e("ShapeContext", "IndexOutOfBoundsException in calculateGlobalSimilarity", e)
        }

        //Log.d("ShapeContext-SC","Similarity score: $similarityScore")
        return similarityScore
    }

    private fun determineBinRadius(radius1: Double, radius2: Double, numBinsRadius: Int): Int {
        val logBase = 1.0
        val maxRadius = ln(1.0 + 1, logBase)
        val adjustedRadius1 = ln(radius1 + 1, logBase)
        val adjustedRadius2 = ln(radius2 + 1, logBase)
        val distance = abs(adjustedRadius1 - adjustedRadius2)
        val normalizedDistance = distance / maxRadius // Normalizálás a legnagyobb lehetséges logaritmusos távolsághoz
        return (normalizedDistance * numBinsRadius).toInt().coerceIn(0, numBinsRadius - 1)
    }

    private fun determineBinAngle(angle1: Double, angle2: Double, numBinsAngle: Int): Int {
        val diffAngle = abs(angle1 - angle2)
        val normalizedAngle = diffAngle / (2 * kotlin.math.PI)
        return (normalizedAngle * numBinsAngle).toInt().coerceIn(0, numBinsAngle - 1)
    }

    private fun ln(value: Double, base: Double = kotlin.math.E): Double {
        // Logaritmus számítás tetszőleges alappal
        return kotlin.math.ln(value) / kotlin.math.ln(base)
    }

    private fun adjustCoordinateSystem(graph: CanvasViewSC.Graph): Pair<Pair<Double, Double>, Double> {
        val maxX = graph.vertices.maxOf { it.x }
        val minX = graph.vertices.minOf { it.x }
        val maxY = graph.vertices.maxOf { it.y }
        val minY = graph.vertices.minOf { it.y }

        val padding = 1.05

        val width = maxX - minX + 2 * padding
        val height = maxY - minY + 2 * padding
        val maxSize = maxOf(width, height)

        val origin = Pair(minX - padding, minY - padding)

        return Pair(origin, maxSize)
    }

    private fun cartesianToLogPolar(vertex: CanvasViewSC.Vertex, origin: Pair<Double, Double>, maxSize: Double): LogPolarCoordinate {
        val adjustedX = (vertex.x - origin.first) / maxSize
        val adjustedY = (vertex.y - origin.second) / maxSize

        val radius = sqrt(adjustedX.pow(2) + adjustedY.pow(2))
        val angle = atan2(adjustedY, adjustedX)
        val logRadius = if (radius > 0) ln(radius) else 0.0

        return LogPolarCoordinate(logRadius, angle)
    }
}