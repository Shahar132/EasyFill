package com.example.easyfill_project.chatbot.semantic.model2vec

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

data class Model2VecEmbeddingInfo(
    val tensorName: String,
    val dtype: String,
    val tokenCount: Int,
    val embeddingSize: Int,
    val dataStartOffset: Long,
    val tensorDataOffset: Long
)

class Model2VecEmbeddingReader(
    private val context: Context
) {
    private var modelFile: File? = null
    private var info: Model2VecEmbeddingInfo? = null

    fun load() {
        if (info != null) return

        val file = copyAssetToInternalStorage(
            assetPath = "model2vec/model.safetensors",
            outputFileName = "model2vec_model.safetensors"
        )

        modelFile = file

        RandomAccessFile(file, "r").use { raf ->
            val headerLengthBytes = ByteArray(8)
            raf.readFully(headerLengthBytes)

            val headerLength = ByteBuffer
                .wrap(headerLengthBytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .long

            val headerBytes = ByteArray(headerLength.toInt())
            raf.readFully(headerBytes)

            val headerText = String(headerBytes, Charsets.UTF_8)
            val json = JSONObject(headerText)

            val tensorName = "embeddings"
            val tensorJson = json.getJSONObject(tensorName)

            val dtype = tensorJson.getString("dtype")

            val shapeArray = tensorJson.getJSONArray("shape")
            val tokenCount = shapeArray.getInt(0)
            val embeddingSize = shapeArray.getInt(1)

            val offsetsArray = tensorJson.getJSONArray("data_offsets")
            val tensorDataOffset = offsetsArray.getLong(0)

            info = Model2VecEmbeddingInfo(
                tensorName = tensorName,
                dtype = dtype,
                tokenCount = tokenCount,
                embeddingSize = embeddingSize,
                dataStartOffset = 8L + headerLength,
                tensorDataOffset = tensorDataOffset
            )
        }
    }

    fun readTokenVector(tokenId: Int): FloatArray {
        val currentInfo = info
            ?: throw IllegalStateException("Embedding reader is not loaded. Call load() first.")

        val currentFile = modelFile
            ?: throw IllegalStateException("Model file is not available.")

        if (currentInfo.dtype != "F16") {
            throw IllegalStateException("Only F16 is supported for now. Found: ${currentInfo.dtype}")
        }

        if (tokenId < 0 || tokenId >= currentInfo.tokenCount) {
            throw IllegalArgumentException("Token id out of range: $tokenId")
        }

        val bytesPerValue = 2
        val vectorByteSize = currentInfo.embeddingSize * bytesPerValue

        val vectorOffset =
            currentInfo.dataStartOffset +
                    currentInfo.tensorDataOffset +
                    tokenId.toLong() * vectorByteSize.toLong()

        val vectorBytes = ByteArray(vectorByteSize)

        RandomAccessFile(currentFile, "r").use { raf ->
            raf.seek(vectorOffset)
            raf.readFully(vectorBytes)
        }

        val buffer = ByteBuffer
            .wrap(vectorBytes)
            .order(ByteOrder.LITTLE_ENDIAN)

        val vector = FloatArray(currentInfo.embeddingSize)

        for (i in 0 until currentInfo.embeddingSize) {
            val halfBits = buffer.short.toInt() and 0xFFFF
            vector[i] = halfToFloat(halfBits)
        }

        return vector
    }

    fun embedTokenIds(tokenIds: List<Int>): FloatArray {
        val currentInfo = info
            ?: throw IllegalStateException("Embedding reader is not loaded. Call load() first.")

        val validTokenIds = tokenIds.filter { tokenId ->
            tokenId >= 0 && tokenId < currentInfo.tokenCount
        }

        if (validTokenIds.isEmpty()) {
            return FloatArray(currentInfo.embeddingSize)
        }

        val sum = FloatArray(currentInfo.embeddingSize)

        validTokenIds.forEach { tokenId ->
            val vector = readTokenVector(tokenId)

            for (i in sum.indices) {
                sum[i] += vector[i]
            }
        }

        for (i in sum.indices) {
            sum[i] /= validTokenIds.size.toFloat()
        }

        return normalize(sum)
    }

    fun getInfo(): Model2VecEmbeddingInfo {
        return info ?: throw IllegalStateException("Embedding reader is not loaded. Call load() first.")
    }

    private fun normalize(vector: FloatArray): FloatArray {
        var sumSquares = 0.0

        for (value in vector) {
            sumSquares += value * value
        }

        val norm = sqrt(sumSquares).toFloat()

        if (norm == 0f) {
            return vector
        }

        for (i in vector.indices) {
            vector[i] /= norm
        }

        return vector
    }

    private fun copyAssetToInternalStorage(
        assetPath: String,
        outputFileName: String
    ): File {
        val outputFile = File(context.filesDir, outputFileName)

        if (outputFile.exists() && outputFile.length() > 0) {
            return outputFile
        }

        context.assets.open(assetPath).use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return outputFile
    }

    private fun halfToFloat(half: Int): Float {
        val sign = (half ushr 15) and 0x00000001
        val exponent = (half ushr 10) and 0x0000001F
        val fraction = half and 0x000003FF

        val floatBits = when (exponent) {
            0 -> {
                if (fraction == 0) {
                    sign shl 31
                } else {
                    var exp = -14
                    var frac = fraction

                    while ((frac and 0x00000400) == 0) {
                        frac = frac shl 1
                        exp--
                    }

                    frac = frac and 0x000003FF

                    (sign shl 31) or
                            ((exp + 127) shl 23) or
                            (frac shl 13)
                }
            }

            31 -> {
                (sign shl 31) or
                        0x7F800000 or
                        (fraction shl 13)
            }

            else -> {
                (sign shl 31) or
                        ((exponent - 15 + 127) shl 23) or
                        (fraction shl 13)
            }
        }

        return Float.fromBits(floatBits)
    }
}