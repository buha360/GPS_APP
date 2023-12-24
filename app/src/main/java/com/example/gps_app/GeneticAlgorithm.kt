package com.example.gps_app

import android.util.Log

class GeneticAlgorithm(private val populationSize: Int, private val geneticCodeLength: Int, private val mutationRate: Double,
                       private val largeGraph: CanvasView.Graph, private val smallGraph: CanvasView.Graph) {

    var population: MutableList<Individual> = initialize()

    private fun initialize(): MutableList<Individual> {
        val neuralNetwork = initializeNeuralNetwork()
        return MutableList(populationSize) { Individual(neuralNetwork) }
    }

    fun evolve() {
        for (generation in 1..5) {
            Log.d("NeuralNetwork - GeneticAlgorithm", "Generation: $generation")
        }

        for (individual in population) {
            individual.calculateFitness(largeGraph, smallGraph)  // Frissítve
        }

        selection()
        crossover()
        mutation()
    }

    private fun selection() {
        // Rendezd a populációt a fitnessz értékek alapján csökkenő sorrendben
        population.sortByDescending { it.fitness }

        // Válaszd ki a legjobb 3 egyedet
        population = population.take(5).toMutableList()
    }

    private fun crossover() {
        val newPopulation = mutableListOf<Individual>()

        // Amíg el nem éred a populáció méretét, addig hozz létre új egyedeket a kiválasztott szülők genetikai kódjának kombinálásával
        while (newPopulation.size < populationSize) {
            for (i in 0 until population.size) {
                for (j in 0 until population.size) {
                    if (i != j) {
                        val parent1 = population[i]
                        val parent2 = population[j]

                        // Új genetikai kód létrehozása a szülők kombinálásával
                        val childGeneticCode = DoubleArray(parent1.geneticCode.size)
                        for (k in childGeneticCode.indices) {
                            childGeneticCode[k] = if (Math.random() < 0.5) parent1.geneticCode[k] else parent2.geneticCode[k]
                        }

                        // Új egyed létrehozása az új genetikai kóddal
                        val child = Individual(parent1.neuralNetwork)  // Egy szülő neurális hálózatának használata
                        child.updateGeneticCode(childGeneticCode)
                        newPopulation.add(child)
                    }
                }
            }
        }

        // Frissítjük a populációt az új egyedekkel
        population = newPopulation
    }

    private fun mutation() {
        population.forEach { individuum ->
            individuum.mutate(mutationRate)
        }
    }

    private fun initializeNeuralNetwork(): NeuralNetwork {
        // 3 réteg: bemeneti réteg, 1 rejtett réteg és a kimeneti réteg
        val inputLayer = NeuralLayer(geneticCodeLength, 12) // 12 neuron a bemeneti rétegben
        val hiddenLayer = NeuralLayer(12, 12) // 12 neuron a rejtett rétegben
        val outputLayer = NeuralLayer(12, 1) // 1 neuron a kimeneti rétegben
        return NeuralNetwork(arrayOf(inputLayer, hiddenLayer, outputLayer))
    }
}

class Individual(val neuralNetwork: NeuralNetwork) {
    var geneticCode: DoubleArray = neuralNetwork.getWeightsAndBiases().flatMap { (weights, biases) ->
        weights.map { it.toList() }.flatten() + biases.toList()
    }.toDoubleArray()

    var fitness: Double = 0.0

    fun mutate(mutationRate: Double) {
        for (i in geneticCode.indices) {
            if (Math.random() < mutationRate) {
                geneticCode[i] += (Math.random() - 0.6) * 0.2 // Small random change
            }
        }

        // Update the neural network weights and biases
        neuralNetwork.setWeightsAndBiases(decodeGeneticCode())
    }

    fun calculateFitness(largeGraph: CanvasView.Graph, smallGraph: CanvasView.Graph) {
        val transformedGraph = applyNeuralNetworkToGraph(smallGraph)  // Az egyed által generált gráf
        val shapeContext = ShapeContext(transformedGraph, largeGraph)
        fitness = shapeContext.compareGraphs()  // Minél kisebb az érték, annál jobb az illeszkedés
        Log.d("NeuralNetwork - GeneticAlgorithm","ShapeContext number: $fitness")
    }

    fun applyNeuralNetworkToGraph(graph: CanvasView.Graph): CanvasView.Graph {
        // 1. lépés: Gráf átalakítása bemenetivé
        val inputVector = graphToInputVector(graph)

        // 2. lépés: Neurális hálózat alkalmazása és kimenet értelmezése
        val outputVector = neuralNetwork.forward(inputVector)
        val selectedVertices = outputVectorToVertices(outputVector)

        // 3. lépés: Új gráf létrehozása a kiválasztott csúcspontokkal
        val transformedGraph = CanvasView.Graph()
        transformedGraph.vertices = selectedVertices

        return transformedGraph
    }

    private fun outputVectorToVertices(outputVector: DoubleArray): MutableList<CanvasView.Vertex> {
        val selectedVertices = mutableListOf<CanvasView.Vertex>()

        // Feltételezzük, hogy a kimeneti vektor minden két elem egy csúcspontot jelöl (x, y koordináták)
        for (i in outputVector.indices step 2) {
            val x = outputVector[i]
            val y = outputVector.getOrNull(i + 1) ?: 0.0  // Biztosítjuk, hogy ne lépjünk túl a vektoron
            selectedVertices.add(CanvasView.Vertex(x, y))
        }

        return selectedVertices
    }

    private fun graphToInputVector(graph: CanvasView.Graph): DoubleArray {
        val vertexFeatures = graph.vertices.flatMap { listOf(it.x, it.y) } // Minden csúcs x és y koordinátája
        val edgeFeatures = graph.edges.flatMap { edge ->
            listOf(edge.start.x, edge.start.y, edge.end.x, edge.end.y) // Minden él kezdő és végpontjának koordinátái
        }

        return (vertexFeatures + edgeFeatures).toDoubleArray()
    }

    fun updateGeneticCode(newGeneticCode: DoubleArray) {
        this.geneticCode = newGeneticCode
        neuralNetwork.setWeightsAndBiases(decodeGeneticCode())
    }

    private fun decodeGeneticCode(): Array<Pair<Array<DoubleArray>, DoubleArray>> {
        val decodedWeightsAndBiases = Array(neuralNetwork.layers.size) { Pair(emptyArray<DoubleArray>(), DoubleArray(0)) }
        var index = 0

        for ((layerIndex, layer) in neuralNetwork.layers.withIndex()) {
            val weights = Array(layer.outputSize) { DoubleArray(layer.inputSize) }
            val biases = DoubleArray(layer.outputSize)

            // Súlyok kinyerése
            for (i in weights.indices) {
                for (j in weights[i].indices) {
                    if (index < geneticCode.size) {
                        weights[i][j] = geneticCode[index++]
                    }
                }
            }

            // Biasok kinyerése
            for (i in biases.indices) {
                if (index < geneticCode.size) {
                    biases[i] = geneticCode[index++]
                }
            }

            decodedWeightsAndBiases[layerIndex] = Pair(weights, biases)
        }

        return decodedWeightsAndBiases
    }
}