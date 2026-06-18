package com.example.easyfill_project.chatbot.semantic.model2vec

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Model2VecTokenizer(
    private val context: Context
) {
    private val tokenToId = mutableMapOf<String, Int>()
    private var maxTokenLength = 0
    private var unkTokenId: Int? = null
    private var loaded = false

    fun load() {
        if (loaded) return

        val jsonText = context.assets.open("model2vec/tokenizer.json")
            .bufferedReader()
            .use { it.readText() }

        val json = JSONObject(jsonText)
        val model = json.getJSONObject("model")
        val vocab = model.getJSONArray("vocab")

        for (i in 0 until vocab.length()) {
            val item = vocab.getJSONArray(i)

            val token = item.getString(0)

            tokenToId[token] = i

            if (token.length > maxTokenLength) {
                maxTokenLength = token.length
            }

            if (token == "<unk>" || token == "[UNK]") {
                unkTokenId = i
            }
        }

        loaded = true
    }

    fun tokenize(text: String): List<Int> {
        if (!loaded) {
            throw IllegalStateException("Tokenizer is not loaded. Call load() first.")
        }

        val normalizedText = normalizeForSentencePiece(text)

        val ids = mutableListOf<Int>()

        var index = 0

        while (index < normalizedText.length) {
            var foundTokenId: Int? = null
            var foundLength = 0

            val maxLengthForPosition = minOf(
                maxTokenLength,
                normalizedText.length - index
            )

            for (length in maxLengthForPosition downTo 1) {
                val candidate = normalizedText.substring(index, index + length)
                val tokenId = tokenToId[candidate]

                if (tokenId != null) {
                    foundTokenId = tokenId
                    foundLength = length
                    break
                }
            }

            if (foundTokenId != null) {
                ids.add(foundTokenId)
                index += foundLength
            } else {
                val fallbackId = unkTokenId

                if (fallbackId != null) {
                    ids.add(fallbackId)
                }

                index += 1
            }
        }

        return ids
    }

    fun isLoaded(): Boolean {
        return loaded
    }

    private fun normalizeForSentencePiece(text: String): String {
        val cleanText = text
            .trim()
            .replace(Regex("\\s+"), " ")

        if (cleanText.isEmpty()) {
            return ""
        }

        return "▁" + cleanText.replace(" ", "▁")
    }
}