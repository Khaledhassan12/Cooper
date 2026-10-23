package org.sketchware.daygreen.preview

import com.besome.sketch.beans.ViewBean
import mod.agus.jcoderz.beans.ViewBeans

class PreviewLayoutHandler(
    override val viewType: Int,
    private val exactNames: Set<String> = emptySet(),
    private val suffixes: List<String> = emptyList()
) : PreviewViewHandler {

    override fun matchesExact(className: String): Boolean {
        return className in exactNames
    }

    override fun matchesSuffix(className: String): Boolean {
        return suffixes.any { suffix -> className.endsWith(suffix) }
    }

    override fun matches(className: String): Boolean {
        return matchesExact(className) || matchesSuffix(className)
    }

    companion object {
        @JvmStatic
        fun createDefaultHandlers(): List<PreviewLayoutHandler> = listOf(
            // 14. CardView
            PreviewLayoutHandler(
                viewType = ViewBeans.VIEW_TYPE_LAYOUT_CARDVIEW,
                exactNames = setOf("CardView", "MaterialCardView"),
                suffixes = listOf("CardView")
            ),
            // 15. ViewPager
            PreviewLayoutHandler(
                viewType = ViewBeans.VIEW_TYPE_LAYOUT_VIEWPAGER,
                exactNames = setOf("ViewPager", "ViewPager2"),
                suffixes = listOf("ViewPager")
            ),
            // 16. TabLayout
            PreviewLayoutHandler(
                viewType = ViewBeans.VIEW_TYPE_LAYOUT_TABLAYOUT,
                exactNames = setOf("TabLayout"),
                suffixes = listOf("TabLayout")
            ),
            // 17. ScrollView / NestedScrollView (Paling akhir; HorizontalScrollView dikecualikan)
            PreviewLayoutHandler(
                viewType = ViewBean.VIEW_TYPE_LAYOUT_VSCROLLVIEW,
                exactNames = setOf("ScrollView", "NestedScrollView"),
                suffixes = listOf("ScrollView")
            )
        )
    }
}