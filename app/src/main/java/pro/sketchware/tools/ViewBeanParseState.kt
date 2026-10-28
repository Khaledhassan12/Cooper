package pro.sketchware.tools

import com.besome.sketch.beans.ViewBean
import a.a.a.wq
import java.io.IOException

/**
 * Per-parse state for ViewBeanParser.
 *
 * Keeping counters and child positions scoped to one parse prevents opening or reparsing
 * another layout from changing generated IDs or child ordering in this layout.
 */
internal class ViewBeanParseState {
    private val viewCounts = IntArray(256)
    private val childCounts = HashMap<String, Int>()

    fun generateUniqueId(ids: Set<String>, type: Int, className: String): String {
        var prefix = wq.b(type)
        val typeName = ViewBean.getViewTypeName(type)

        // Preserve Sketchware's existing ID prefixes for scroll containers and custom linear layouts.
        if (type != ViewBean.VIEW_TYPE_LAYOUT_VSCROLLVIEW ||
            type != ViewBean.VIEW_TYPE_LAYOUT_HSCROLLVIEW
        ) {
            if (prefix == "linear" &&
                type == ViewBean.VIEW_TYPE_LAYOUT_LINEAR &&
                typeName != className
            ) {
                prefix = className.toSnakeCaseId()
            }
        }

        val counterIndex = type.coerceIn(0, viewCounts.lastIndex)
        var count = ++viewCounts[counterIndex]
        var id = prefix + count
        while (ids.contains(id)) {
            count = ++viewCounts[counterIndex]
            id = prefix + count
        }
        return id
    }

    @Throws(IOException::class)
    fun validateExplicitId(id: String?, tag: String, ids: Set<String>): String {
        if (id.isNullOrBlank()) {
            throw IOException("Invalid android:id on <$tag>.")
        }
        if (ids.contains(id)) {
            throw IOException(
                "Duplicate android:id '$id' on <$tag>. Layout parsing was stopped to preserve existing widget identities."
            )
        }
        return id
    }

    fun nextChildIndex(parentId: String): Int {
        val position = childCounts[parentId] ?: 0
        childCounts[parentId] = position + 1
        return position
    }

    private fun String.toSnakeCaseId(): String {
        val result = StringBuilder(length + 8)
        forEachIndexed { index, char ->
            if (char.isUpperCase()) {
                if (index != 0) result.append('_')
                result.append(char.lowercaseChar())
            } else {
                result.append(char)
            }
        }
        return result.toString()
    }
}
