package com.example.gps_app

import kotlin.math.exp

class NeuralLayer(inputSize: Int, outputSize: Int) {
    var weights: Array<DoubleArray> = Array(outputSize) { DoubleArray(inputSize) { Math.random() } }
    var biases: DoubleArray = DoubleArray(outputSize) { Math.random() }

    fun forward(input: DoubleArray): DoubleArray {
        val output = DoubleArray(weights.size)
        for (i in weights.indices) {
            output[i] = input.zip(weights[i]) { a, b -> a * b }.sum() + biases[i]
            output[i] = sigmoid(output[i])
        }
        return output
    }

    private fun sigmoid(x: Double): Double = 1.0 / (1 + exp(-x))
}

class NeuralNetwork(private val layers: Array<NeuralLayer>) {

    fun forward(input: DoubleArray): DoubleArray {
        var currentOutput = input
        for (layer in layers) {
            currentOutput = layer.forward(currentOutput)
        }
        return currentOutput
    }

    // Getters and setters for weights and biases of each layer
    fun getWeightsAndBiases(): Array<Pair<Array<DoubleArray>, DoubleArray>> {
        return layers.map { layer -> Pair(layer.weights, layer.biases) }.toTypedArray()
    }

    fun setWeightsAndBiases(newWeightsAndBiases: Array<Pair<Array<DoubleArray>, DoubleArray>>) {
        for (i in layers.indices) {
            layers[i].weights = newWeightsAndBiases[i].first
            layers[i].biases = newWeightsAndBiases[i].second
        }
    }
}
