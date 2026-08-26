package org.multisharp.bench

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.PowerManager
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class BenchActivity : Activity() {

    private lateinit var log: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var status: TextView
    private lateinit var argsField: EditText
    private val buttons = mutableListOf<Button>()
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val pm = getSystemService(PowerManager::class.java)
        val sustained = pm != null && pm.isSustainedPerformanceModeSupported
        if (sustained) {
            window.setSustainedPerformanceMode(true)
        }

        Bench.installPrecompSource(assets)

        setContentView(buildUi())
        appendLine(Bench.describeEnvironment())
        appendLine("sustained performance mode: " +
            if (sustained) "on" else "NOT SUPPORTED -- expect thermal drift over long runs")
        appendLine("output directory: ${getExternalFilesDir(null)}")
        appendLine("")
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        status = TextView(this).apply {
            text = "idle"
            setTypeface(Typeface.DEFAULT_BOLD)
        }
        root.addView(status)

        argsField = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "arguments"
            textSize = 12f
        }
        root.addView(argsField)

        val note = TextView(this).apply {
            setTextColor(Color.DKGRAY)
            textSize = 11f
            text = "Tap to run with the arguments shown. Long-press to load them " +
                "into the field above and read what the harness does."
        }
        root.addView(note)

        for (h in Bench.harnesses) {
            val b = Button(this).apply {
                text = "${h.label}  ${h.defaultArgs}".trim()
                isAllCaps = false
                textSize = 12f
                minHeight = 0
                minimumHeight = 0
                setPadding(16, 8, 16, 8)
                setOnClickListener { launch(h) }
                setOnLongClickListener {
                    argsField.setText(h.defaultArgs)
                    note.text = h.note
                    true
                }
            }
            buttons += b
            root.addView(b)
        }

        log = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 9f
            setTextIsSelectable(true)
        }
        logScroll = ScrollView(this).apply {
            addView(log)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        root.addView(logScroll)

        return root
    }

    private fun launch(h: Bench.Harness) {
        if (running) {
            appendLine("!! a run is already in progress")
            return
        }
        val argLine = argsField.text.toString().ifBlank { h.defaultArgs }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val out = File(getExternalFilesDir(null), "${h.label}-$stamp.txt")

        running = true
        buttons.forEach { it.isEnabled = false }
        status.text = "running ${h.label} $argLine"
        appendLine("=== ${h.label} $argLine")
        appendLine("=== writing to ${out.name}")

        thread(name = "bench") {
            val started = System.nanoTime()
            val saved = System.out
            try {
                FileOutputStream(out).use { fos ->
                    PrintStream(Tee(fos), true).use { ps ->
                        System.setOut(ps)
                        ps.println(Bench.describeEnvironment())
                        ps.println("harness: ${h.label} $argLine")
                        ps.println()
                        Bench.run(h, argLine)
                    }
                }
            } catch (t: Throwable) {
                System.setOut(saved)
                appendLine("!! ${t::class.java.simpleName}: ${t.message}")
                t.printStackTrace()
            } finally {
                System.setOut(saved)
                val secs = (System.nanoTime() - started) / 1e9
                runOnUiThread {
                    running = false
                    buttons.forEach { it.isEnabled = true }
                    status.text = String.format(Locale.US, "done in %.1f s -> %s", secs, out.name)
                }
            }
        }
    }

    private inner class Tee(private val file: OutputStream) : OutputStream() {
        private val line = StringBuilder()

        override fun write(b: Int) {
            file.write(b)
            if (b == '\n'.code) {
                val s = line.toString()
                line.setLength(0)
                appendLine(s)
            } else if (b != '\r'.code) {
                line.append(b.toChar())
            }
        }

        override fun flush() = file.flush()
    }

    private fun appendLine(s: String) {
        runOnUiThread {
            log.append(s + "\n")
            logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }
}
