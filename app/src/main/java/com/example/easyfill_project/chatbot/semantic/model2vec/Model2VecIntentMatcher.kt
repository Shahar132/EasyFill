package com.example.easyfill_project.chatbot.semantic.model2vec

import android.content.Context
import com.example.easyfill_project.chatbot.model.BotIntent
import com.example.easyfill_project.chatbot.semantic.AppIntentCatalog
import com.example.easyfill_project.chatbot.semantic.AppIntentExample

data class Model2VecIntentMatch(
    val intent: BotIntent,
    val score: Float,
    val matchedText: String
)

class Model2VecIntentMatcher(
    context: Context
) {
    private val service = Model2VecService(
        tokenizer = Model2VecTokenizer(context),
        embeddingReader = Model2VecEmbeddingReader(context)
    )

    private var exampleEmbeddings: List<Pair<AppIntentExample, FloatArray>> = emptyList()
    private var loaded = false

    fun load() {
        if (loaded) return

        service.load()

        exampleEmbeddings = AppIntentCatalog.examples.map { example ->
            example to service.embed(example.text)
        }

        loaded = true
    }

    fun findBestIntent(userText: String): Model2VecIntentMatch? {
        if (!loaded) {
            throw IllegalStateException("Model2VecIntentMatcher is not loaded. Call load() first.")
        }

        val userEmbedding = service.embed(userText)

        var bestExample: AppIntentExample? = null
        var bestScore = -1f

        exampleEmbeddings.forEach { (example, exampleEmbedding) ->
            val score = cosineSimilarity(userEmbedding, exampleEmbedding)

            if (score > bestScore) {
                bestScore = score
                bestExample = example
            }
        }

        val match = bestExample ?: return null

        return Model2VecIntentMatch(
            intent = match.intent,
            score = bestScore,
            matchedText = match.text
        )
    }

    private fun cosineSimilarity(
        first: FloatArray,
        second: FloatArray
    ): Float {
        val size = minOf(first.size, second.size)

        var dot = 0f

        for (i in 0 until size) {
            dot += first[i] * second[i]
        }

        return dot
    }
}