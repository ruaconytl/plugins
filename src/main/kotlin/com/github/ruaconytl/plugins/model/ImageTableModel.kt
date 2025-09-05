package com.github.ruaconytl.plugins.model

import javax.swing.table.AbstractTableModel

class ImageTableModel(private val rows: MutableList<ImageRow>) : AbstractTableModel() {
    private val columns = arrayOf("✔", "Module", "Folder", "Name", "Size (KB)", "BitDepth")

    var editable: Boolean = true

    override fun getRowCount(): Int = rows.size
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(column: Int): String = columns[column]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val row = rows[rowIndex]
        return when (columnIndex) {
            0 -> row.selected
            1 -> row.module
            2 -> row.folder
            3 -> row.name
            4 -> row.sizeKb
            5 -> row.bitDepth
            else -> ""
        }
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        return editable && columnIndex == 0  // chỉ editable khi flag = true
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        if (editable && columnIndex == 0 && aValue is Boolean) {
            rows[rowIndex].selected = aValue
            fireTableCellUpdated(rowIndex, columnIndex)
        }
    }

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return when (columnIndex) {
            0 -> java.lang.Boolean::class.java
            4 -> java.lang.Long::class.java
            5 -> java.lang.Integer::class.java
            else -> String::class.java
        }
    }

    fun getSelectedRows(): List<ImageRow> = rows.filter { it.selected }
    fun setAllSelected(value: Boolean) {
        rows.forEach { it.selected = value }
        fireTableDataChanged()
    }
    fun setData(newRows: List<ImageRow>) {
        rows.clear()
        rows.addAll(newRows)
        fireTableDataChanged()
    }
    fun getRow(index: Int): ImageRow = rows[index]
}
