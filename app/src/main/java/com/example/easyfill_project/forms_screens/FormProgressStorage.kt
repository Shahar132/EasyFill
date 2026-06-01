package com.example.easyfill_project.forms_screens

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

//Stores:
//- current step
//- completed status
//- last updated
enum class FormStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED
}

object FormProgressStorage {

    private const val PREFS_NAME = "form_progress"

    private fun currentStepKey(formId: String): String {
        return "${formId}_current_step"
    }

    private fun completedKey(formId: String): String {
        return "${formId}_completed"
    }

    private fun lastUpdatedKey(formId: String): String {
        return "${formId}_last_updated"
    }

    fun getCurrentStep(context: Context, formId: String): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(currentStepKey(formId), 0)
    }

    fun saveCurrentStep(context: Context, formId: String, currentStep: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        prefs.edit()
            .putInt(currentStepKey(formId), currentStep)
            .putBoolean(completedKey(formId), false)
            .putLong(lastUpdatedKey(formId), System.currentTimeMillis())
            .apply()
    }

    fun markCompleted(context: Context, formId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        prefs.edit()
            .putBoolean(completedKey(formId), true)
            .putLong(lastUpdatedKey(formId), System.currentTimeMillis())
            .apply()
    }

    fun isCompleted(context: Context, formId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(completedKey(formId), false)
    }

    fun getLastUpdatedMillis(context: Context, formId: String): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(lastUpdatedKey(formId), 0L)
    }

    fun getStatus(context: Context, formId: String): FormStatus {
        val lastUpdated = getLastUpdatedMillis(context, formId)

        if (lastUpdated == 0L) return FormStatus.NOT_STARTED
        if (isCompleted(context, formId)) return FormStatus.COMPLETED

        return FormStatus.IN_PROGRESS
    }

    fun getLastUpdatedText(context: Context, formId: String): String {
        val lastUpdated = getLastUpdatedMillis(context, formId)

        if (lastUpdated == 0L) {
            return "עדיין לא התחלת למלא את הטופס"
        }

        val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("he", "IL"))
        return "עודכן לאחרונה: ${formatter.format(Date(lastUpdated))}"
    }
}