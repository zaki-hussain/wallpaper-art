package dev.wallpaperart

import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Outline
import android.os.Bundle
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import dev.wallpaperart.art.ArtRenderer
import dev.wallpaperart.art.ArtSpec
import dev.wallpaperart.art.Generator
import dev.wallpaperart.art.PaletteGen
import dev.wallpaperart.art.Patterns
import dev.wallpaperart.art.Rng
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
        findViewById<Button>(R.id.btnHide).setOnClickListener { hideCurrentPattern() }

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
        // The auto-refresh alarm changes the current art behind our back.
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
        Wallpaper.applyAsync(this)
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
            .setNeutralButton(R.string.btn_hidden) { _, _ -> showHiddenDialog() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Bans the current art's pattern from future refreshes and moves to another one. */
    private fun hideCurrentPattern() {
        val cur = current()
        val remaining = store.enabledPatterns().filter { it.id != cur.pattern }
        if (remaining.isEmpty()) {
            toast(getString(R.string.last_pattern))
            return
        }
        store.setDisabledIds(store.disabledIds() + cur.pattern)
        setCurrent(Generator.switchPattern(cur, remaining))
        toast(getString(R.string.pattern_hidden))
    }

    /** Hidden patterns with rendered previews, each restorable with a tap. */
    private fun showHiddenDialog() {
        val hidden = Patterns.all.filter { it.id in store.disabledIds() }
        if (hidden.isEmpty()) {
            toast(getString(R.string.none_hidden))
            return
        }
        val density = resources.displayMetrics.density
        val pad = (20 * density).toInt()
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.hidden_title)
            .setView(ScrollView(this).apply { addView(list) })
            .setNegativeButton(R.string.close, null)
            .create()

        val size = (64 * density).toInt()
        hidden.forEach { pattern ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, pad / 3, 0, pad / 3)
            }
            val thumb = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size)
                scaleType = ImageView.ScaleType.FIT_XY
            }
            roundOutline(thumb, 12f)
            thumbExecutor.execute {
                val rng = Rng(pattern.id.hashCode().toLong())
                val sample = ArtSpec(
                    pattern.id, rng.nextLong(),
                    PaletteGen.generate(pattern.colorRange.last, rng)
                )
                val bmp = ArtRenderer.render(sample, 192, 192)
                thumb.post { thumb.setImageBitmap(bmp) }
            }
            val name = TextView(this).apply {
                text = pattern.label
                textSize = 15f
                setTextColor(getColor(R.color.text))
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                ).apply { marginStart = (12 * density).toInt() }
            }
            val restore = Button(this).apply {
                text = getString(R.string.restore)
                textSize = 13f
                isAllCaps = false
                setTextColor(getColor(R.color.text_dim))
                background = getDrawable(R.drawable.btn_quiet_bg)
                stateListAnimator = null
                setPadding((16 * density).toInt(), 0, (16 * density).toInt(), 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, (36 * density).toInt()
                )
                setOnClickListener {
                    store.setDisabledIds(store.disabledIds() - pattern.id)
                    list.removeView(row)
                    if (list.childCount == 0) dialog.dismiss()
                }
            }
            row.addView(thumb)
            row.addView(name)
            row.addView(restore)
            list.addView(row)
        }
        dialog.show()
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
        val blur = view.findViewById<Switch>(R.id.switchBlur)
        val label = view.findViewById<TextView>(R.id.blurLabel)
        val seek = view.findViewById<SeekBar>(R.id.blurSeek)

        enable.isChecked = store.wallpaperEnabled
        blur.isChecked = store.blurEnabled
        seek.progress = store.blurPct
        seek.isEnabled = blur.isChecked
        label.text = getString(R.string.blur_pct_label, seek.progress)
        blur.setOnCheckedChangeListener { _, on -> seek.isEnabled = on }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                label.text = getString(R.string.blur_pct_label, p)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        AlertDialog.Builder(this)
            .setTitle(R.string.wallpaper_title)
            .setView(view)
            .setPositiveButton(R.string.done) { _, _ ->
                store.wallpaperEnabled = enable.isChecked
                store.blurEnabled = blur.isChecked
                store.blurPct = seek.progress
                if (enable.isChecked) {
                    Wallpaper.applyAsync(this)
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
