package dev.artwidget

import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Outline
import android.os.Bundle
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import dev.artwidget.art.ArtRenderer
import dev.artwidget.art.ArtSpec
import dev.artwidget.art.Generator
import dev.artwidget.art.Patterns
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var store: ArtStore
    private lateinit var artView: ArtView
    private lateinit var savedRow: LinearLayout
    private lateinit var savedLabel: TextView
    private val thumbExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = ArtStore(this)

        artView = findViewById(R.id.artView)
        savedRow = findViewById(R.id.savedRow)
        savedLabel = findViewById(R.id.savedLabel)
        roundOutline(artView, 24f)

        findViewById<Button>(R.id.btnNew).setOnClickListener {
            setCurrent(store.refreshArt(isSystemDark()))
        }
        findViewById<Button>(R.id.btnPattern).setOnClickListener {
            setCurrent(Generator.switchPattern(current(), store.enabledPatterns()))
        }
        findViewById<Button>(R.id.btnColours).setOnClickListener {
            setCurrent(Generator.switchColors(current(), isSystemDark()))
        }
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            if (store.addSaved(current())) {
                refreshSaved()
                toast(getString(R.string.saved))
            } else {
                toast(getString(R.string.already_saved))
            }
        }
        findViewById<Button>(R.id.btnPatterns).setOnClickListener { showPatternsDialog() }
        findViewById<Button>(R.id.btnShare).setOnClickListener { showShareDialog() }
        findViewById<Button>(R.id.btnAuto).setOnClickListener { showAutoRefreshDialog() }
        findViewById<Button>(R.id.btnWallpaper).setOnClickListener { showWallpaperDialog() }

        findViewById<CheckBox>(R.id.lockPattern).apply {
            isChecked = store.patternLocked
            setOnCheckedChangeListener { _, b -> store.patternLocked = b }
        }
        findViewById<CheckBox>(R.id.lockColours).apply {
            isChecked = store.colorsLocked
            setOnCheckedChangeListener { _, b -> store.colorsLocked = b }
        }

        refreshSaved()
    }

    override fun onResume() {
        super.onResume()
        // The widget tap and the auto-refresh alarm change the current art behind our back.
        val spec = store.currentOrCreate()
        if (artView.spec != spec) artView.spec = spec
    }

    override fun onDestroy() {
        super.onDestroy()
        thumbExecutor.shutdown()
    }

    private fun current(): ArtSpec = store.currentOrCreate()

    private fun setCurrent(spec: ArtSpec) {
        store.current = spec
        artView.spec = spec
        ArtWidgetProvider.pushAsync(this)
    }

    private fun refreshSaved() {
        savedRow.removeAllViews()
        val list = store.saved()
        savedLabel.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        val size = (64 * resources.displayMetrics.density).toInt()
        val margin = (10 * resources.displayMetrics.density).toInt()
        list.forEach { spec ->
            val thumb = ImageView(this)
            val lp = LinearLayout.LayoutParams(size, size)
            lp.marginEnd = margin
            thumb.layoutParams = lp
            thumb.scaleType = ImageView.ScaleType.FIT_XY
            roundOutline(thumb, 14f)
            thumbExecutor.execute {
                val bmp = ArtRenderer.render(spec, 192, 192)
                thumb.post { thumb.setImageBitmap(bmp) }
            }
            thumb.setOnClickListener {
                setCurrent(spec)
            }
            thumb.setOnLongClickListener {
                AlertDialog.Builder(this)
                    .setTitle(R.string.delete_title)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        store.removeSaved(spec)
                        refreshSaved()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
            savedRow.addView(thumb)
        }
    }

    private fun showPatternsDialog() {
        val patterns = Patterns.all
        val disabled = store.disabledIds()
        val checked = BooleanArray(patterns.size) { patterns[it].id !in disabled }
        AlertDialog.Builder(this)
            .setTitle(R.string.patterns_title)
            .setMultiChoiceItems(
                patterns.map { it.label }.toTypedArray(), checked
            ) { _, i, b -> checked[i] = b }
            .setPositiveButton(R.string.done) { _, _ ->
                store.setDisabledIds(
                    patterns.filterIndexed { i, _ -> !checked[i] }.map { it.id }.toSet()
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAutoRefreshDialog() {
        val options = listOf(
            0L to getString(R.string.interval_off),
            AlarmManager.INTERVAL_HALF_HOUR to getString(R.string.interval_30m),
            AlarmManager.INTERVAL_HOUR to getString(R.string.interval_hourly),
            AlarmManager.INTERVAL_HOUR * 6 to getString(R.string.interval_6h),
            AlarmManager.INTERVAL_DAY to getString(R.string.interval_daily)
        )
        val checked = options.indexOfFirst { it.first == store.refreshIntervalMillis }
        AlertDialog.Builder(this)
            .setTitle(R.string.auto_refresh_title)
            .setSingleChoiceItems(options.map { it.second }.toTypedArray(), checked) { d, i ->
                store.refreshIntervalMillis = options[i].first
                RefreshReceiver.schedule(this)
                d.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showWallpaperDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_wallpaper, null)
        val enable = view.findViewById<Switch>(R.id.switchWallpaper)
        val label = view.findViewById<TextView>(R.id.solidLabel)
        val seek = view.findViewById<SeekBar>(R.id.solidSeek)
        val divide = view.findViewById<Spinner>(R.id.divideSpinner)

        enable.isChecked = store.wallpaperEnabled
        seek.progress = store.wallpaperSolidPct
        label.text = getString(R.string.wallpaper_solid_label, seek.progress)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                label.text = getString(R.string.wallpaper_solid_label, p)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        divide.adapter = ArrayAdapter.createFromResource(
            this, R.array.divide_options, android.R.layout.simple_spinner_item
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        divide.setSelection(
            if (!store.divideFrozen) 0
            else Wallpaper.STYLES.indexOf(store.divideStyle).coerceAtLeast(0) + 1
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.wallpaper_title)
            .setView(view)
            .setPositiveButton(R.string.done) { _, _ ->
                store.wallpaperEnabled = enable.isChecked
                store.wallpaperSolidPct = seek.progress
                val pick = divide.selectedItemPosition
                if (pick in 1..Wallpaper.STYLES.size) {
                    store.divideFrozen = true
                    store.divideStyle = Wallpaper.STYLES[pick - 1]
                } else {
                    store.divideFrozen = false
                }
                if (enable.isChecked) {
                    ArtWidgetProvider.pushAsync(this)
                    toast(getString(R.string.wallpaper_applied))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showShareDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_share, null)
        val codeView = view.findViewById<TextView>(R.id.codeText)
        val input = view.findViewById<EditText>(R.id.importInput)
        codeView.text = current().toJson()

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.share_title)
            .setView(view)
            .setNegativeButton(R.string.close, null)
            .create()

        view.findViewById<Button>(R.id.btnCopy).setOnClickListener {
            val clipboard = getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("art", codeView.text))
            toast(getString(R.string.copied))
        }
        view.findViewById<Button>(R.id.btnImport).setOnClickListener {
            val spec = ArtSpec.fromJson(input.text.toString())
            when {
                spec == null -> toast(getString(R.string.invalid_code))
                Patterns.byId(spec.pattern) == null -> toast(getString(R.string.unknown_pattern))
                else -> {
                    setCurrent(spec)
                    dialog.dismiss()
                    toast(getString(R.string.imported))
                }
            }
        }
        dialog.show()
    }

    private fun roundOutline(v: View, radiusDp: Float) {
        val r = radiusDp * resources.displayMetrics.density
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, r)
            }
        }
        v.clipToOutline = true
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
