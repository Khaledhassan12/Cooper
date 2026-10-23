package org.sketchware.daygreen.preview

object PreviewViewRegistry {

    /**
     * Urutan handler spesifik lebih dulu:
     * Switch, CheckBox, RadioButton, ImageButton/ImageView, EditText,
     * SeekBar, ProgressBar, Spinner, TextView, Button, WebView,
     * ListView, RecyclerView, CardView, ViewPager, TabLayout, ScrollView.
     */
    private val handlers: List<PreviewViewHandler> =
        PreviewWidgetHandler.createDefaultHandlers() +
        PreviewLayoutHandler.createDefaultHandlers()

    @JvmStatic
    fun findHandler(className: String): PreviewViewHandler? {
        val simpleName = className.substringAfterLast('.')
        if (simpleName.isEmpty()) return null

        // Jangan tangkap HorizontalScrollView atau FloatingActionButton di sini
        // karena sudah ditangani secara spesifik oleh sistem ViewBean bawaan.
        if (simpleName == "HorizontalScrollView" ||
            simpleName == "HScrollView" ||
            simpleName.endsWith("FloatingActionButton")
        ) {
            return null
        }

        // a. Prioritas 1: Exact match nama class
        handlers.firstOrNull { it.matchesExact(simpleName) }?.let { return it }

        // b. Prioritas 2: Suffix match (endsWith), BUKAN contains
        return handlers.firstOrNull { it.matchesSuffix(simpleName) }
    }

    @JvmStatic
    fun typeOf(className: String, def: Int): Int {
        // Jika tipe sudah terdeteksi secara spesifik (bukan jatuh ke default LinearLayout)
        // kecuali memang perlu di-override oleh registry:
        val handler = findHandler(className)
        return handler?.viewType ?: def
    }
}