package com.example.easyfill_project.chatbot.model

enum class BotAction {
    NONE,

    // הקראה חד פעמית למסך הנוכחי
    READ_ALOUD,
    STOP_READING,

    // הקראה אוטומטית לכל מסך
    ENABLE_AUTO_READ,
    DISABLE_AUTO_READ,

    // ניווט למסכי התאמה אישית
    OPEN_PERSONAL_SETTINGS,
    OPEN_CONTRAST_SETTINGS,
    OPEN_FONT_SIZE_SETTINGS,
    OPEN_BACKGROUND_SOUNDS,

    // מוזיקת רקע
    PLAY_NATURE_SOUND,
    PLAY_CALM_MUSIC,
    PLAY_INSTRUMENT_SOUND,
    STOP_BACKGROUND_MUSIC,

    // צבעים / ניגודיות
    SET_CONTRAST_DEFAULT,
    SET_CONTRAST_HIGH,
    SET_CONTRAST_LOW,

    // גודל טקסט
    SET_FONT_SMALL,
    SET_FONT_NORMAL,
    SET_FONT_LARGE
}