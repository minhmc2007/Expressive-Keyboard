package com.expressive.keyboard

/** Search по настройкам на главном экране: пункт → раздел, где он находится. */
object SettingsSearch {
    private val index: List<Pair<String, SettingsPage>> = buildList {
        fun add(page: SettingsPage, vararg titles: String) = titles.forEach { add(it to page) }
        add(SettingsPage.LOOK, "Mode", "Accent shape", "Number of waves", "Wave depth", "Typeface", "Font weight",
            "Letter size", "Load custom font", "Key press response", "Panel animations")
        add(SettingsPage.COLORS, "Material You palette", "Keyboard background", "Letter keys", "Letter text", "Function keys",
            "Accent (Enter, pressed)", "Key opacity", "Blur behind keyboard", "Blur strength", "Restore theme colors", "Choose image", "Dim amount", "Blur")
        add(SettingsPage.KEYS, "Key height", "Key corner radius", "Space between keys", "Space between rows",
            "Side margins", "Bottom margin", "One-handed mode", "Keyboard width", "Number row", "Key hints",
            "Key pop-up on press", "Key borders", "Language switch key", "Emoji key", "Microphone button",
            "Suggestion strip", "Editing toolbar", "Long-press language key", "Comma on the main keyboard", "Period on the main keyboard",
            "Custom key left of the spacebar", "Custom key right of the spacebar")
        add(SettingsPage.LAYOUT, "Swap keys", "Restore defaults", "Key symbol")
        add(SettingsPage.ICONS, "Emoji style", "Custom emoji font", "Restore all icons")
        add(SettingsPage.TOOLBAR, "Кнопки", "Порядок по умолчанию")
        add(SettingsPage.LANGUAGES, "Enabled languages", "Add language", "Download dictionaries automatically", "Chinese (pinyin)", "Japanese (romaji → kana)")
        add(SettingsPage.TYPING, "Autocorrect", "Correction strength", "System spell checker",
            "Smart punctuation", "Claude API key", "Gemini API key", "OpenAI API key", "Groq API key", "Correction provider", "AI text correction", "Capitalize sentence starts", "Period on double-space")
        add(SettingsPage.GESTURES, "Instant input", "Glide typing", "Finger trail", "Swipe up on a key", "Spacebar long-press",
            "Paired brackets", "Incognito", "Swipe on spacebar", "Swipe left on backspace", "Long-press delay")
        add(SettingsPage.CLIPBOARD, "Clipboard history", "Retention period", "Ключ GIPHY API", "Ключ KLIPY")
        add(SettingsPage.FEEDBACK, "Вибрация при нажатии", "Характер отклика", "Длительность", "Сила", "Звук нажатия", "Громкость", "Свой звук нажатия")
        add(SettingsPage.PLUGINS, "Import from file", "Import from clipboard", "Save example mod (.js)", "Save example plugin")
        add(SettingsPage.BACKUP, "Export settings", "Import settings", "Reset settings", "Clear learned words", "Clear recent emoji")
    }

    fun find(query: String): List<Pair<String, SettingsPage>> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val pages = SettingsPage.entries.filter { it != SettingsPage.HOME }
            .filter { tr(it.title).lowercase().contains(q) || tr(it.subtitle).lowercase().contains(q) || it.title.lowercase().contains(q) }
            .map { tr(it.title) to it }
        val items = index.filter { (t, _) -> tr(t).lowercase().contains(q) || t.lowercase().contains(q) }.map { (t, p) -> tr(t) to p }
        return (pages + items).distinct().take(30)
    }
}
