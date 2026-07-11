package com.example.easyfill_project.pdf_export

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseFormReader {

    fun loadSavedFormFields(
        onResult: (Result<Map<String, String>>) -> Unit
    ) {
        val uid = FirebaseAuth
            .getInstance()
            .currentUser
            ?.uid

        if (uid == null) {
            onResult(
                Result.failure(
                    IllegalStateException("לא נמצא משתמש מחובר")
                )
            )
            return
        }

        FirebaseFirestore
            .getInstance()
            .collection("users")
            .document(uid)
            .collection("savedUpdatedData")
            .document("allFields")
            .get()
            .addOnSuccessListener { document ->

                if (!document.exists()) {
                    onResult(
                        Result.failure(
                            IllegalStateException("לא נמצאו נתונים שמורים לטופס")
                        )
                    )
                    return@addOnSuccessListener
                }

                val fields = document.data
                    .orEmpty()
                    .mapValues { (_, value) ->
                        value?.toString().orEmpty()
                    }
                    .filterValues { value ->
                        value.isNotBlank()
                    }

                onResult(Result.success(fields))
            }
            .addOnFailureListener { exception ->
                onResult(Result.failure(exception))
            }
    }
}