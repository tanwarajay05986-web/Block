package com.akay.block

import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity(), GameView.Listener {

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private var screen = "home"

    private fun lp(w: Int, h: Int, wt: Float = 0f): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h, wt)

    // shows a screen and keeps the status / navigation bar areas coloured (edge-to-edge)
    private fun setScreen(content: View, bg: Drawable, top: Int, bottom: Int, lightBars: Boolean) {
        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.background = bg
        val tp = View(this)
        tp.setBackgroundColor(top)
        val bp = View(this)
        bp.setBackgroundColor(bottom)
        wrap.addView(tp, lp(MATCH, 0))
        wrap.addView(content, lp(MATCH, 0, 1f))
        wrap.addView(bp, lp(MATCH, 0))
        ViewCompat.setOnApplyWindowInsetsListener(wrap) { _, ins ->
            val sb = ins.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            (tp.layoutParams as LinearLayout.LayoutParams).height = sb.top
            (bp.layoutParams as LinearLayout.LayoutParams).height = sb.bottom
            tp.requestLayout()
            bp.requestLayout()
            WindowInsetsCompat.CONSUMED
        }
        setContentView(wrap)
        val c = WindowInsetsControllerCompat(window, wrap)
        c.isAppearanceLightStatusBars = lightBars
        c.isAppearanceLightNavigationBars = lightBars
        ViewCompat.requestApplyInsets(wrap)
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        try {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
            window.isNavigationBarContrastEnforced = false
        } catch (e: Throwable) {
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (screen == "home") finish() else showHome()
            }
        })
        showHome()
    }

    // big chunky button with a darker bottom edge
    private fun chunky(text: String, face: Int, edge: Int, icon: View?, onClick: () -> Unit): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.HORIZONTAL
        box.gravity = Gravity.CENTER
        val r = dp(22).toFloat()
        val bottom = GradientDrawable()
        bottom.setColor(edge)
        bottom.cornerRadius = r
        val top = GradientDrawable()
        top.setColor(face)
        top.cornerRadius = r
        val ld = LayerDrawable(arrayOf<Drawable>(bottom, top))
        ld.setLayerInset(1, 0, 0, 0, dp(7))
        box.background = ld
        box.setPadding(dp(18), 0, dp(18), dp(7))
        if (icon != null) box.addView(icon, lp(dp(38), dp(38)))
        val t = TextView(this)
        t.text = text
        t.textSize = 26f
        t.setTextColor(Color.WHITE)
        t.setTypeface(Typeface.DEFAULT_BOLD, Typeface.BOLD_ITALIC)
        t.setPadding(if (icon != null) dp(14) else 0, 0, 0, 0)
        box.addView(t)
        box.setOnClickListener { onClick() }
        return box
    }

    private fun label(text: String, sizeSp: Float, color: Int): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = sizeSp
        t.setTextColor(color)
        t.setTypeface(Typeface.DEFAULT_BOLD)
        t.gravity = Gravity.CENTER
        return t
    }

    // ---------- home ----------

    private fun showHome() {
        screen = "home"
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setPadding(dp(44), dp(36), dp(44), dp(24))

        root.addView(LogoView(this), lp(MATCH, WRAP))
        root.addView(View(this), lp(MATCH, 0, 1f))

        val adv = chunky(
            "Adventure", 0xFFF5A31A.toInt(), 0xFFC77D00.toInt(),
            IconView(this, 0, 0xFFF5A31A.toInt())
        ) { toast("Adventure mode Stage 4 mein aayega") }
        root.addView(adv, lp(MATCH, dp(84)))

        val classic = chunky(
            "Classic", 0xFF22C99A.toInt(), 0xFF139A75.toInt(),
            IconView(this, 1, 0xFF22C99A.toInt())
        ) { showClassic() }
        val cl = lp(MATCH, dp(84))
        cl.topMargin = dp(18)
        root.addView(classic, cl)

        root.addView(View(this), lp(MATCH, 0, 0.7f))
        root.addView(label("v1.0  \u2022  Stage 1", 12f, 0x99FFFFFF.toInt()), lp(WRAP, WRAP))

        val bg = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF1A3FB0.toInt(), 0xFF1485DE.toInt())
        )
        setScreen(root, bg, Color.TRANSPARENT, Color.TRANSPARENT, false)
    }

    // ---------- classic mode ----------

    private fun showClassic() {
        screen = "classic"
        val g = GameView(this)
        g.best = Store.classicBest()
        g.listener = this
        g.newGame()
        setScreen(g, ColorDrawable(0xFF3B4F8F.toInt()), Color.TRANSPARENT, Color.TRANSPARENT, false)
    }

    override fun onGameOver(score: Int) {
        val old = Store.classicBest()
        val newBest = score > old
        if (newBest) Store.setClassicBest(score)
        showGameOver(score, newBest, maxOf(score, old))
    }

    override fun onSettings() {
        showSettings()
    }

    private fun showGameOver(score: Int, newBest: Boolean, best: Int) {
        screen = "over"
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setPadding(dp(44), 0, dp(44), 0)

        root.addView(View(this), lp(MATCH, 0, 1f))
        root.addView(CrownView(this), lp(dp(90), dp(70)))
        val title = label(
            if (newBest) "Best Score!" else "Game Over", 42f,
            if (newBest) 0xFFFFB400.toInt() else Color.WHITE
        )
        title.setPadding(0, dp(10), 0, dp(36))
        root.addView(title, lp(MATCH, WRAP))
        root.addView(label("Score", 22f, 0xFFB57BFF.toInt()), lp(MATCH, WRAP))
        val sc = label(score.toString(), 80f, Color.WHITE)
        sc.setPadding(0, dp(10), 0, dp(10))
        root.addView(sc, lp(MATCH, WRAP))
        if (!newBest) root.addView(label("Best: $best", 18f, 0xFFFFC21A.toInt()), lp(MATCH, WRAP))

        val play = chunky(
            "Play Again", 0xFFF5A31A.toInt(), 0xFFC77D00.toInt(),
            IconView(this, 2, 0xFFF5A31A.toInt())
        ) { showClassic() }
        val pl = lp(MATCH, dp(84))
        pl.topMargin = dp(40)
        root.addView(play, pl)

        val home = label("Home", 20f, Color.WHITE)
        home.setPadding(0, dp(24), 0, dp(24))
        home.setOnClickListener { showHome() }
        root.addView(home, lp(MATCH, WRAP))
        root.addView(View(this), lp(MATCH, 0, 1.2f))

        val bg = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF4E0FA3.toInt(), 0xFF8B58E3.toInt())
        )
        setScreen(root, bg, Color.TRANSPARENT, Color.TRANSPARENT, false)
    }

    // ---------- settings (sound options come in Stage 3) ----------

    private fun showSettings() {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(dp(22), dp(20), dp(22), dp(22))
        val pbg = GradientDrawable()
        pbg.setColor(0xFF4F6FD6.toInt())
        pbg.cornerRadius = dp(26).toFloat()
        panel.background = pbg

        panel.addView(label("Settings", 26f, Color.WHITE), lp(MATCH, WRAP))
        val note = label("Sound aur music options Stage 3 mein aayenge", 13f, 0xCCFFFFFF.toInt())
        note.setPadding(0, dp(6), 0, dp(14))
        panel.addView(note, lp(MATCH, WRAP))

        val green = 0xFF2DBE12.toInt()
        val greenEdge = 0xFF1A8A07.toInt()
        val b1 = chunky("Continue", green, greenEdge, null) { dlg.dismiss() }
        panel.addView(b1, lp(MATCH, dp(66)))
        val b2 = chunky("Replay", green, greenEdge, null) {
            dlg.dismiss()
            showClassic()
        }
        val l2 = lp(MATCH, dp(66))
        l2.topMargin = dp(12)
        panel.addView(b2, l2)
        val b3 = chunky("Home", green, greenEdge, null) {
            dlg.dismiss()
            showHome()
        }
        val l3 = lp(MATCH, dp(66))
        l3.topMargin = dp(12)
        panel.addView(b3, l3)

        dlg.setContentView(panel)
        val w = dlg.window
        if (w != null) {
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setLayout((resources.displayMetrics.widthPixels * 0.86f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
        }
        dlg.show()
    }
}
