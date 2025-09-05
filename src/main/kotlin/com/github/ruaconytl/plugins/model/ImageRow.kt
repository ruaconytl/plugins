package com.github.ruaconytl.plugins.model

data class ImageRow(
    var selected: Boolean,
    val module: String,
    val folder: String,
    val name: String,
    val sizeKb: Long,
    val bitDepth: Int,
    val path: String
) {
    override fun toString(): String {
        // Hiển thị gọn trong list
        val mark = if (bitDepth == 32) " (32bit!)" else ""
        return "[$module/$folder] $name - ${sizeKb}KB - $mark"
    }
}
