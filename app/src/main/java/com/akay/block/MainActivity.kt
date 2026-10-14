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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
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
    private var game: GameView? = null

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
        Sfx.init(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        try {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
            window.isNavigationBarContrastEnforced = false
        } catch (e: Throwable) {
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (screen) {
                    "home" -> finish()
                    "adv", "win", "lose" -> showMap()
                    else -> showHome()
                }
            }
        })
        showHome()
    }

    override fun onPause() {
        saveIfPlaying()
        super.onPause()
    }

    // keeps the running game (Classic or Adventure) so it can be continued later
    private fun saveIfPlaying() {
        val g = game ?: return
        if (screen == "classic") {
            val s = g.snapshot()
            if (s != null) Store.saveGame(s) else Store.clearGame()
        } else if (screen == "adv") {
            val s = g.snapshot()
            if (s != null) Store.saveAdv(Store.advLevel().toString() + "|" + s) else Store.clearAdv()
        }
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
        box.setOnClickListener {
            Sfx.tap()
            onClick()
        }
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

    // plain text button under the big buttons
    private fun link(text: String, onClick: () -> Unit): View {
        val t = label(text, 20f, Color.WHITE)
        t.setPadding(0, dp(14), 0, dp(14))
        t.setOnClickListener {
            Sfx.tap()
            onClick()
        }
        return t
    }

    // ---------- home ----------

    private fun showHome() {
        saveIfPlaying()
        game = null
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
        ) { showMap() }
        root.addView(adv, lp(MATCH, dp(84)))

        val classic = chunky(
            "Classic", 0xFF22C99A.toInt(), 0xFF139A75.toInt(),
            IconView(this, 1, 0xFF22C99A.toInt())
        ) { showClassic(true) }
        val cl = lp(MATCH, dp(84))
        cl.topMargin = dp(18)
        root.addView(classic, cl)

        val best = Store.classicBest()
        val saved = Store.loadGame() != null || Store.loadAdv() != null
        val info = "Level ${Store.advMax()}   \u2022   Best: $best" +
            (if (saved) "   \u2022   Game saved" else "")
        val infoView = label(info, 14f, 0xCCFFFFFF.toInt())
        infoView.setPadding(0, dp(18), 0, 0)
        root.addView(infoView, lp(MATCH, WRAP))

        root.addView(View(this), lp(MATCH, 0, 0.7f))
        root.addView(label("v1.0  \u2022  Stage 6", 12f, 0x99FFFFFF.toInt()), lp(WRAP, WRAP))

        val bg = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF1A3FB0.toInt(), 0xFF1485DE.toInt())
        )
        setScreen(root, bg, Color.TRANSPARENT, Color.TRANSPARENT, false)
    }

    // ---------- adventure level map ----------

    private fun showMap() {
        saveIfPlaying()
        game = null
        screen = "map"
        val maxL = Store.advMax()
        val sel = Store.advLevel()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(0xFF4F62D0.toInt())
        bar.setPadding(dp(8), dp(10), dp(8), dp(10))
        val back = BackView(this)
        back.setOnClickListener {
            Sfx.tap()
            showHome()
        }
        bar.addView(back, lp(dp(48), dp(48)))
        bar.addView(label("Adventure", 26f, Color.WHITE), lp(0, WRAP, 1f))
        bar.addView(View(this), lp(dp(48), dp(48)))
        root.addView(bar, lp(MATCH, WRAP))

        val map = MapView(this)
        map.maxLevel = maxL
        map.selected = sel
        map.showChapterOf(sel)
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(map)
        root.addView(sv, lp(MATCH, 0, 1f))

        val green = 0xFF2DBE12.toInt()
        val greenEdge = 0xFF1A8A07.toInt()
        val play = chunky("Level $sel", green, greenEdge, null) { showAdventure(true) } as LinearLayout
        val playText = play.getChildAt(play.childCount - 1) as TextView
        val footer = LinearLayout(this)
        footer.orientation = LinearLayout.VERTICAL
        footer.setBackgroundColor(0xFF4F62D0.toInt())
        footer.setPadding(dp(36), dp(12), dp(36), dp(16))
        footer.addView(play, lp(MATCH, dp(70)))
        root.addView(footer, lp(MATCH, WRAP))

        map.listener = object : MapView.Listener {
            override fun onPick(level: Int) {
                Sfx.tap()
                Store.setAdvLevel(level)
                map.selected = level
                map.invalidate()
                playText.text = "Level $level"
            }

            override fun onLocked(level: Int) {
                Sfx.back()
                toast("Level $level locked hai. Pehle Level ${Store.advMax()} clear karo")
            }

            override fun onChapter() {
                Sfx.tap()
                sv.post { sv.fullScroll(View.FOCUS_DOWN) }
            }
        }
        sv.postDelayed({
            val y = map.yOf(sel)
            if (y >= 0) sv.scrollTo(0, maxOf(0, y - sv.height / 2))
        }, 60)

        setScreen(root, ColorDrawable(0xFF3A4A8C.toInt()), 0xFF4F62D0.toInt(), 0xFF4F62D0.toInt(), false)
    }

    // ---------- classic mode ----------

    private fun showClassic(resume: Boolean) {
        val g = GameView(this)
        g.best = Store.classicBest()
        g.listener = this
        var loaded = false
        if (resume) {
            val s = Store.loadGame()
            if (s != null) loaded = g.loadState(s)
        }
        if (!loaded) {
            Store.clearGame()
            g.newGame()
        }
        game = g
        screen = "classic"
        setScreen(g, ColorDrawable(0xFF3B4F8F.toInt()), Color.TRANSPARENT, Color.TRANSPARENT, false)
    }

    // ---------- adventure mode ----------

    private fun showAdventure(resume: Boolean) {
        val num = Store.advLevel().coerceIn(1, Levels.COUNT)
        val lv = Levels.make(num)
        val g = GameView(this)
        g.listener = this
        g.startLevel(lv)
        if (resume) {
            val s = Store.loadAdv()
            if (s != null) {
                val i = s.indexOf('|')
                if (i > 0 && s.substring(0, i).toIntOrNull() == num) {
                    if (!g.loadState(s.substring(i + 1))) g.startLevel(lv)
                }
            }
        } else {
            Store.clearAdv()
        }
        game = g
        screen = "adv"
        setScreen(g, ColorDrawable(0xFF3B4F8F.toInt()), Color.TRANSPARENT, Color.TRANSPARENT, false)
    }

    override fun onGameOver(score: Int) {
        if (screen == "adv") {
            Store.clearAdv()
            game = null
            Sfx.over()
            val num = Store.advLevel()
            showLose(num, score, Levels.goal(num))
            return
        }
        Store.clearGame()
        game = null
        val old = Store.classicBest()
        val newBest = score > old
        if (newBest) Store.setClassicBest(score)
        if (newBest) Sfx.best() else Sfx.over()
        showGameOver(score, newBest, maxOf(score, old))
    }

    // clearing a level unlocks the next one
    override fun onLevelComplete(score: Int) {
        val lvNum = Store.advLevel()
        Store.clearAdv()
        game = null
        Sfx.best()
        val newlyUnlocked = lvNum >= Store.advMax() && lvNum < Levels.COUNT
        if (newlyUnlocked) Store.setAdvMax(lvNum + 1)
        if (lvNum < Levels.COUNT) Store.setAdvLevel(lvNum + 1)
        showWin(lvNum, score, Levels.goal(lvNum), newlyUnlocked)
    }

    override fun onSettings() {
        showSettings()
    }

    override fun onBack() {
        showMap()
    }

    // ---------- Adventure win screen ----------

    private fun showWin(lvNum: Int, score: Int, goal: Int, unlocked: Boolean) {
        screen = "win"
        val ratio = score.toFloat() / goal.toFloat()
        val stars = if (ratio >= 2f) 3 else if (ratio >= 1.4f) 2 else 1
        val allDone = lvNum >= Levels.COUNT
        val orange = 0xFFF5A31A.toInt()
        val orangeEdge = 0xFFC77D00.toInt()

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.gravity = Gravity.CENTER_HORIZONTAL
        content.setPadding(dp(40), 0, dp(40), 0)

        content.addView(View(this), lp(MATCH, 0, 1f))
        content.addView(
            label(if (allDone) "All levels done!" else "Level $lvNum", 24f, 0xFFFFD86B.toInt()),
            lp(MATCH, WRAP)
        )
        val title = label("Complete!", 44f, Color.WHITE)
        title.setPadding(0, dp(4), 0, dp(10))
        content.addView(title, lp(MATCH, WRAP))
        content.addView(StarsView(this, stars), lp(MATCH, dp(130)))
        content.addView(label("Score", 18f, 0xFFB9C7FF.toInt()), lp(MATCH, WRAP))
        content.addView(label(score.toString(), 60f, Color.WHITE), lp(MATCH, WRAP))
        content.addView(label("Goal: $goal", 15f, 0xCCFFFFFF.toInt()), lp(MATCH, WRAP))
        if (unlocked) {
            val u = label("Level ${lvNum + 1} unlocked", 16f, 0xFF9BFFB0.toInt())
            u.setPadding(0, dp(14), 0, 0)
            content.addView(u, lp(MATCH, WRAP))
        }
        val next = chunky(
            if (allDone) "Play Again" else "Next Level", orange, orangeEdge,
            IconView(this, 2, orange)
        ) { showAdventure(false) }
        val nl = lp(MATCH, dp(80))
        nl.topMargin = dp(30)
        content.addView(next, nl)
        content.addView(link("Levels") { showMap() }, lp(MATCH, WRAP))
        content.addView(link("Home") { showHome() }, lp(MATCH, WRAP))
        content.addView(View(this), lp(MATCH, 0, 1f))

        val frame = FrameLayout(this)
        frame.addView(ConfettiView(this), FrameLayout.LayoutParams(MATCH, MATCH))
        frame.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))

        val bg = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF2B2A9E.toInt(), 0xFF7A4BD8.toInt())
        )
        setScreen(frame, bg, Color.TRANSPARENT, Color.TRANSPARENT, false)
    }

    // ---------- Adventure lose screen ----------

    private fun showLose(lvNum: Int, score: Int, goal: Int) {
        screen = "lose"
        val orange = 0xFFF5A31A.toInt()
        val orangeEdge = 0xFFC77D00.toInt()
        val short = (goal - score).coerceAtLeast(0)

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.gravity = Gravity.CENTER_HORIZONTAL
        content.setPadding(dp(40), 0, dp(40), 0)

        content.addView(View(this), lp(MATCH, 0, 1f))
        content.addView(label("Level $lvNum", 24f, 0xFFFFD86B.toInt()), lp(MATCH, WRAP))
        val title = label("Out of moves!", 38f, Color.WHITE)
        title.setPadding(0, dp(4), 0, dp(24))
        content.addView(title, lp(MATCH, WRAP))
        content.addView(GoalBarView(this, score, goal), lp(MATCH, dp(90)))
        val sh = label("$short points short", 16f, 0xFFFFD86B.toInt())
        sh.setPadding(0, dp(6), 0, dp(4))
        content.addView(sh, lp(MATCH, WRAP))
        content.addView(label("Tip: clear lines to score more", 14f, 0xCCFFFFFF.toInt()), lp(MATCH, WRAP))

        val retry = chunky("Retry", orange, orangeEdge, IconView(this, 2, orange)) {
            showAdventure(false)
        }
        val rl = lp(MATCH, dp(80))
        rl.topMargin = dp(36)
        content.addView(retry, rl)
        content.addView(link("Levels") { showMap() }, lp(MATCH, WRAP))
        content.addView(link("Home") { showHome() }, lp(MATCH, WRAP))
        content.addView(View(this), lp(MATCH, 0, 1f))

        val bg = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF2A2F6B.toInt(), 0xFF5B3A9E.toInt())
        )
        setScreen(content, bg, Color.TRANSPARENT, Color.TRANSPARENT, false)
    }

    // ---------- Classic game over ----------

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
        ) { showClassic(false) }
        val pl = lp(MATCH, dp(84))
        pl.topMargin = dp(40)
        root.addView(play, pl)

        root.addView(link("Home") { showHome() }, lp(MATCH, WRAP))
        root.addView(View(this), lp(MATCH, 0, 1.2f))

        val bg = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF4E0FA3.toInt(), 0xFF8B58E3.toInt())
        )
        setScreen(root, bg, Color.TRANSPARENT, Color.TRANSPARENT, false)
    }

    // ---------- settings ----------

    // one sound / vibrate toggle tile
    private fun tile(kind: Int, text: String, on: Boolean, onToggle: (Boolean) -> Unit): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(dp(8), dp(14), dp(8), dp(14))
        val ic = ToggleIcon(this, kind)
        ic.off = !on
        box.addView(ic, lp(dp(54), dp(54)))
        val t = label(text, 15f, Color.WHITE)
        t.setPadding(0, dp(6), 0, 0)
        box.addView(t, lp(WRAP, WRAP))
        box.setOnClickListener {
            val nowOn = ic.off
            ic.off = !nowOn
            onToggle(nowOn)
        }
        return box
    }

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

        val tiles = LinearLayout(this)
        tiles.orientation = LinearLayout.HORIZONTAL
        val tbg = GradientDrawable()
        tbg.setColor(0xFF3A44A6.toInt())
        tbg.cornerRadius = dp(18).toFloat()
        tiles.background = tbg
        tiles.addView(tile(0, "Sound", Sfx.soundOn) { on ->
            Sfx.setSound(on)
            if (on) Sfx.tap()
        }, lp(0, WRAP, 1f))
        tiles.addView(tile(2, "Vibrate", Sfx.vibeOn) { on ->
            Sfx.setVibe(on)
            if (on) Sfx.vPlace()
        }, lp(0, WRAP, 1f))
        val tl = lp(MATCH, WRAP)
        tl.topMargin = dp(14)
        tl.bottomMargin = dp(16)
        panel.addView(tiles, tl)

        val green = 0xFF2DBE12.toInt()
        val greenEdge = 0xFF1A8A07.toInt()
        val b1 = chunky("Continue", green, greenEdge, null) { dlg.dismiss() }
        panel.addView(b1, lp(MATCH, dp(66)))
        val b2 = chunky("Replay", green, greenEdge, null) {
            dlg.dismiss()
            if (screen == "adv") showAdventure(false) else showClassic(false)
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
