package com.example.easyfill_project.chatbot.intent

import android.content.Context
import com.example.easyfill_project.chatbot.model.BotIntent
import com.example.easyfill_project.chatbot.semantic.model2vec.Model2VecIntentMatcher

class Model2VecIntentDetector(
    context: Context,
    private val ruleBasedIntentDetector: IntentDetector = RuleBasedIntentDetector(),
    private val confidenceThreshold: Float = 0.6f
) : IntentDetector {

    private val matcher = Model2VecIntentMatcher(context.applicationContext)

    private var loaded = false
    private var failed = false

    fun load() {
        if (loaded || failed) return

        try {
            matcher.load()
            loaded = true
        } catch (t: Throwable) {
            failed = true
        }
    }

    fun isLoaded(): Boolean {
        return loaded
    }

    override fun detectIntent(userMessage: String): BotIntent {

        // קודם ננסה את המודל הסמנטי
        if (loaded && !failed) {
            val semanticMatch = matcher.findBestIntent(userMessage)

            if (semanticMatch != null && semanticMatch.score >= confidenceThreshold) {
                return semanticMatch.intent
            }
        }

        // אם המודל לא בטוח — חוזרים לחוקים הרגילים
        return ruleBasedIntentDetector.detectIntent(userMessage)
    }
}