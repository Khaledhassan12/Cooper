package org.sketchware.daygreen.preview

import com.besome.sketch.beans.ViewBean

interface PreviewViewHandler {
    /** ViewBean.VIEW_TYPE_* / ViewBeans.VIEW_TYPE_* yang dihasilkan */
    val viewType: Int

    /** true jika handler ini menangani nama class (tanpa package) */
    fun matches(className: String): Boolean

    /** true jika nama class cocok secara exact */
    fun matchesExact(className: String): Boolean = false

    /** true jika nama class cocok berdasarkan suffix */
    fun matchesSuffix(className: String): Boolean = matches(className)

    /** Opsional: sesuaikan bean setelah atribut diterapkan */
    fun applyToBean(bean: ViewBean, attrs: Map<String, String>) {}
}