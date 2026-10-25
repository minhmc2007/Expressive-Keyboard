package com.expressive.keyboard

/** Search по настройкам на главном экране: пункт → раздел, где он находится. */
object SettingsSearch {
    private val index: List<Pair<String, SettingsPage>> = buildList {
        fun add(page: SettingsPage, vararg titles: String) = titles.forEach { add(it to page) }
        add(SettingsPage.LOOK, "Mode", "Accent shape", "Number of waves", "Wave depth", "Typeface", "Font weight",
            "Letter size", "Load custom font", "Key press response", "Panel animations")
        add(SettingsPage.COLORS, "Material You palette", "Keyboard background", "Letter keys", "Letter text", "Function keys",
            "Accent (Enter, pressed)", "Key opacity", "Blur под клавиатурой", "Blur strength", "Restore theme colors", "Choose image", "Dim amount", "Blur")
        add(SettingsPage.KEYS, "Key height", "Key corner radius", "Space between keys", "Space between rows",
            "Side margins", "Bottom margin", "Mode одной руки", "Keyboard width", "Number row", "Key hints",
            "Key pop-up on press", "Key borders", "Кнопка смены языка", "Кнопка эмодзи", "Кнопка микрофона",
            "Строка подсказок", "Editing toolbar", "Удержание кнопки языка", "Запятая на основной клавиатуре", "Точка на основной клавиатуре",
            "Своя клавиша слева от пробела", "Своя клавиша справа от пробела")
        add(SettingsPage.LAYOUT, "Поменять местами", "Restore defaults", "Key symbol")
        add(SettingsPage.ICONS, "Стиль эмодзи", "Свой шрифт эмодзи", "Вернуть все иконки")
        add(SettingsPage.TOOLBAR, "Кнопки", "Порядок по умолчанию")
        add(SettingsPage.LANGUAGES, "Onючённые языки", "Добавить язык", "Скачивать словарь автоматически", "Китайский (пиньинь)", "Японский (ромадзи → кана)")
        add(SettingsPage.TYPING, "Autoисправление", "Насколько смело исправлять", "Системная проверка правописания",
            "Знаки после пробела", "Ключ Claude API", "Ключ Gemini", "Ключ OpenAI", "Ключ Groq", "Кто исправляет", "Исправление текста ИИ", "Заглавная в начале предложения", "Точка двойным пробелом")
        add(SettingsPage.GESTURES, "Мгновенный ввод", "Свайп-набор", "След за пальцем", "Свайп вверх по клавише", "Удержание пробела",
            "Парные скобки", "Incognito", "Свайп по пробелу", "Свайп влево по ⌫", "Задержка удержания")
        add(SettingsPage.CLIPBOARD, "История буфера", "Сколько хранить", "Ключ GIPHY API", "Ключ KLIPY")
        add(SettingsPage.FEEDBACK, "Вибрация при нажатии", "Характер отклика", "Длительность", "Сила", "Звук нажатия", "Громкость", "Свой звук нажатия")
        add(SettingsPage.PLUGINS, "Импорт из файла", "Импорт из буфера обмена", "Сохранить пример мода (.js)", "Сохранить пример плагина")
        add(SettingsPage.BACKUP, "Экспорт настроек", "Импорт настроек", "Сбросить настройки", "Clear выученные слова", "Clear недавние эмодзи")
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
