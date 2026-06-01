package com.example.easyfill_project.forms_screens

import android.content.Context
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FormStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED
}

object FormProgressStorage {

    private const val PREFS_NAME = "form_progress"

    private fun currentStepKey(uid: String, formId: String) =
        "${uid}_${formId}_current_step"

    private fun completedKey(uid: String, formId: String) =
        "${uid}_${formId}_completed"

    private fun lastUpdatedKey(uid: String, formId: String) =
        "${uid}_${formId}_last_updated"

    fun getCurrentStep(context: Context, uid: String, formId: String): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(currentStepKey(uid, formId), 0)
    }

    fun saveCurrentStep(context: Context, uid: String, formId: String, currentStep: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        prefs.edit {
            putInt(currentStepKey(uid, formId), currentStep)
            putBoolean(completedKey(uid, formId), false)
            putLong(lastUpdatedKey(uid, formId), System.currentTimeMillis())
        }
    }

    fun markCompleted(context: Context, uid: String, formId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        prefs.edit {
            putBoolean(completedKey(uid, formId), true)
            putLong(lastUpdatedKey(uid, formId), System.currentTimeMillis())
        }
    }

    fun isCompleted(context: Context, uid: String, formId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(completedKey(uid, formId), false)
    }

    fun getLastUpdatedMillis(context: Context, uid: String, formId: String): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(lastUpdatedKey(uid, formId), 0L)
    }

    fun getStatus(context: Context, uid: String, formId: String): FormStatus {
        val lastUpdated = getLastUpdatedMillis(context, uid, formId)

        if (lastUpdated == 0L) return FormStatus.NOT_STARTED
        if (isCompleted(context, uid, formId)) return FormStatus.COMPLETED

        return FormStatus.IN_PROGRESS
    }

    fun getLastUpdatedText(context: Context, uid: String, formId: String): String {
        val lastUpdated = getLastUpdatedMillis(context, uid, formId)

        if (lastUpdated == 0L) {
            return "עדיין לא התחלת למלא את הטופס"
        }

        val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("he", "IL"))
        return "עודכן לאחרונה: ${formatter.format(Date(lastUpdated))}"
    }
}