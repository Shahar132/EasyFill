package com.example.easyfill_project.distress_scoring

// Defines which type of interaction is currently happening.
// The active mode determines which analyses contribute to the final distress score and the chosen weights.
enum class DistressMode {
    FORM_FILLING,
    VOICE_RECORDING
}