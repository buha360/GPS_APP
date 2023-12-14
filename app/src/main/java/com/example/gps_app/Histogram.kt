package com.example.gps_app

class Histogram(numBinsRadius: Int, numBinsAngle: Int) {
    val bins = Array(numBinsRadius) { IntArray(numBinsAngle) }

    fun increment(binRadius: Int, binAngle: Int) {
        if (binRadius in bins.indices && binAngle in bins[binRadius].indices) {
            bins[binRadius][binAngle]++
        }
    }
}