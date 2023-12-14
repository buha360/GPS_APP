package com.example.gps_app

import android.util.Log
import kotlin.math.abs
import kotlin.math.pow

class ShapeContext(originalGraph: CanvasView.Graph, transformedGraph: CanvasView.Graph) {

    data class LogPolarCoordinate(val radius: Double, val angle: Double)

    private var logPolarCoordinatesOriginal: List<LogPolarCoordinate>
    private var logPolarCoordinatesTransformed: List<LogPolarCoordinate>

    init {
        Log.d("ShapeContext-SC-init", "Initializing ShapeContext")
        logPolarCoordinatesOriginal = calculateLogPolarCoordinates(originalGraph)
        logPolarCoordinatesTransformed = calculateLogPolarCoordinates(transformedGraph)

        /*
        Log.d("ShapeContext-SC-init", "orifinalGraph: $originalGraph")
        Log.d("ShapeContext-SC-init", "transformedGraph: $transformedGraph")
        Log.d("ShapeContext-SC-init", "Original Graph Log-Polar Coordinates: $logPolarCoordinatesOriginal")
        Log.d("ShapeContext-SC-init", "Transformed Graph Log-Polar Coordinates: $logPolarCoordinatesTransformed")

         */
    }

    fun compareGraphs(): Double {
        Log.d("ShapeContext-SC", "Comparing graphs")
        // 1. Histogramok készítése
        val histogramOriginal = createHistograms(logPolarCoordinatesOriginal)
        val histogramTransformed = createHistograms(logPolarCoordinatesTransformed)

        // 2. Hasonlósági pontszám kiszámítása
        val similarityScore = calculateGlobalSimilarity(histogramOriginal, histogramTransformed)
        Log.d("ShapeContext-SC", "Similarity score: $similarityScore")
        return similarityScore
    }

    private fun calculateLogPolarCoordinates(graph: CanvasView.Graph): List<LogPolarCoordinate> {
        val logPolarCoordinates = mutableListOf<LogPolarCoordinate>()
        graph.edges.forEach { edge ->
            logPolarCoordinates.add(cartesianToLogPolar(edge.start))
            logPolarCoordinates.add(cartesianToLogPolar(edge.end))
        }
        return logPolarCoordinates
    }

    private fun createHistograms(logPolarCoordinates: List<LogPolarCoordinate>): List<Histogram> {
        return logPolarCoordinates.mapIndexed { index, coordinate ->
            val histogram = Histogram(9, 3)
            logPolarCoordinates.forEach { otherCoordinate ->
                val binRadius = determineBinRadius(coordinate.radius, otherCoordinate.radius)
                val binAngle = determineBinAngle(coordinate.angle, otherCoordinate.angle)
                histogram.increment(binRadius, binAngle)
            }
            Log.d("ShapeContext-SC", "Histogram for element $index: ${histogram.bins.contentDeepToString()}")
            histogram
        }
    }

    private fun calculateGlobalSimilarity(histogramOriginal: List<Histogram>, histogramTransformed: List<Histogram>): Double {
        if (histogramOriginal.isEmpty() || histogramTransformed.isEmpty()) {
            Log.d("ShapeContext-SC", "One of the histograms is empty. $histogramOriginal $histogramTransformed")
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

        return similarityScore
    }

    private fun determineBinRadius(radius1: Double, radius2: Double, numBinsRadius: Int = 10): Int {
        val maxRadius = 0.1 // vagy egy másik megfelelő érték
        val minRadius = 0.0

        val distance = abs(radius1 - radius2)
        val normalizedDistance = (distance - minRadius) / (maxRadius - minRadius)

        return (normalizedDistance * numBinsRadius).toInt().coerceIn(0, numBinsRadius - 1)
    }

    private fun determineBinAngle(angle1: Double, angle2: Double, numBinsAngle: Int = 10): Int {
        val diffAngle = abs(angle1 - angle2)
        val normalizedAngle = diffAngle / (2 * Math.PI)

        return (normalizedAngle * numBinsAngle).toInt().coerceIn(0, numBinsAngle - 1)
    }

    /*
    private fun calculateLogPolarCoordinates(graph: CanvasView.Graph): List<LogPolarCoordinate> {
        return graph.vertices.map { vertex -> val coordinate = cartesianToLogPolar(vertex)
            coordinate}
    }

     */

    private fun cartesianToLogPolar(vertex: CanvasView.Vertex): LogPolarCoordinate {
        val radius = kotlin.math.sqrt(vertex.x.pow(2) + vertex.y.pow(2))
        val angle = kotlin.math.atan2(vertex.y, vertex.x)
        val logRadius = if (radius > 0) kotlin.math.ln(radius) else 0.0
        return LogPolarCoordinate(logRadius, angle)
    }
}
