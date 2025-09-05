package com.github.ruaconytl.plugins.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.github.ruaconytl.plugins.model.ImageRow
import com.github.ruaconytl.plugins.model.ImageTableModel
import com.github.ruaconytl.plugins.services.MyProjectService
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JProgressBar
import javax.swing.JScrollPane
import javax.swing.JTextArea


class MyToolWindowFactory : ToolWindowFactory {

    init {
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(toolWindow)
        val contentTinyTab = ContentFactory.getInstance().createContent(myToolWindow.getContentTinyTab(), "Tiny", false)
        val contentOtherTab = ContentFactory.getInstance().createContent(myToolWindow.getContentOtherTab(), "Other", false)
        toolWindow.contentManager.addContent(contentTinyTab)
        toolWindow.contentManager.addContent(contentOtherTab)
    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow(toolWindow: ToolWindow) {


        private val service = toolWindow.project.service<MyProjectService>()

        val consoleView: ConsoleView = ConsoleViewImpl(toolWindow.project, true).apply {
            minimumSize = Dimension(0, 200)
        }
        val consoleComponent = consoleView.component

        private val logArea = JTextArea().apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
        }

        private val tableModel = ImageTableModel(mutableListOf())
        private val table = JBTable(tableModel).apply {
            setShowGrid(true)
            autoCreateRowSorter = true
            columnModel.getColumn(0).preferredWidth = 40
            preferredScrollableViewportSize = Dimension(
                preferredScrollableViewportSize.width,
                rowHeight * 10 // luôn hiển thị cao như 10 dòng
            )
            // double click mở file
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {
                        val viewRow = selectedRow
                        if (viewRow >= 0) {
                            val modelRow = convertRowIndexToModel(viewRow)
                            val rowObj = tableModel.getRow(modelRow)
                            val vf = LocalFileSystem.getInstance().findFileByPath(rowObj.path)
                            if (vf != null) {
                                FileEditorManager.getInstance(toolWindow.project).openFile(vf, true)
                            }
                        }
                    }
                }
            })
        }
        private val imageListModel = DefaultListModel<ImageRow>()
//        private val imageList = JList(imageListModel)

        // giữ tham chiếu tới các control để enable/disable

        private lateinit var totalLabel: JLabel
        private lateinit var scanButton: JButton
        private lateinit var optimizeButton: JButton
        private lateinit var selectAllCheckBox: JCheckBox
        private  val progressBar: JProgressBar = object : JProgressBar() {
            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                val g2 = g.create() as Graphics2D
                g2.color = JBColor.YELLOW   // đổi sang đỏ
                val text = this.string
                if (!text.isNullOrEmpty()) {
                    val fm = g2.fontMetrics
                    val x = (width - fm.stringWidth(text)) / 2
                    val y = (height + fm.ascent - fm.descent) / 2
                    g2.drawString(text, x, y)
                }
                g2.dispose()
            }
        }.apply {
            isStringPainted = true
        }


        fun getContentTinyTab() = panel {
            row {
                scanButton = button("Scan Images") {
                    val results = service.scanImages()
                    val rows = results.map {
                        ImageRow(
                            selected = false,
                            module = it.module,
                            folder = it.folder,
                            name = it.name,
                            sizeKb = it.sizeKb,
                            bitDepth = it.bitDepth,
                            path = it.path
                        )
                    }
                    tableModel.setData(rows)
                    imageListModel.clear()
                    imageListModel.addAll(rows)
                    totalLabel.text = "Total : ${imageListModel.size} files"

                }.component

                optimizeButton = button("Optimize Selected") {
                    val selected = tableModel.getSelectedRows()
                    if (selected.isEmpty()) {
                        log("⚠ No image selected.")
                    } else {
                        log("Optimizing ${selected.size} file(s)...")
                        setUiEnabled(false)
                        service.optimizeImages(
                            selected.map { it.path },
                            logger = { msg -> log(msg) },
                            { current, total ->
                                updateProgress(current, total)
                            }) {
                            // Khi optimize xong, mở lại
                            setUiEnabled(true)
                            scanButton.doClick()
                        }
                    }
                }.component
            }
            row {
                selectAllCheckBox = checkBox("Select All").applyToComponent {
                    addActionListener {
                        tableModel.setAllSelected(isSelected)
                    }
                }.component
            }

            row {
                totalLabel = label("").component
            }
            row {
                progressBar.isVisible = false
                cell(progressBar).align(Align.FILL)
            }

            row {
                cell(JScrollPane(table)).align(Align.FILL)
            }
            row {
                cell(consoleView.component).align(Align.FILL)
                    .comment("Logs")
            }
//            row {
//                cell(JScrollPane(logArea)).align(Align.FILL).comment("Logs")
//            }
        }

        fun getContentOtherTab() = panel {
            row {
                label("Chưa có gì")
            }
        }

        private fun updateProgress(current: Int, total: Int) {
            progressBar.maximum = total
            progressBar.value = current
            progressBar.string = "Optimized $current / $total"
            progressBar.isVisible = true
        }

        private fun setUiEnabled(enabled: Boolean) {
            scanButton.isEnabled = enabled
            optimizeButton.isEnabled = enabled
            selectAllCheckBox.isEnabled = enabled
            tableModel.editable = enabled
            tableModel.fireTableDataChanged()
        }

        private fun log(message: String) {
            consoleView.print(message +"\n", ConsoleViewContentType.LOG_WARNING_OUTPUT)

//            logArea.append("$message\n")
//            logArea.caretPosition = logArea.document.length
        }
    }


}
