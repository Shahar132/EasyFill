package com.example.easyfill_project.chatbot.semantic.model2vec

class Model2VecService(
    private val tokenizer: Model2VecTokenizer,
    private val embeddingReader: Model2VecEmbeddingReader
) {
    fun load() {
        tokenizer.load()
        embeddingReader.load()
    }

    fun embed(text: String): FloatArray {
        val tokenIds = tokenizer.tokenize(text)
        return embeddingReader.embedTokenIds(tokenIds)
    }
}