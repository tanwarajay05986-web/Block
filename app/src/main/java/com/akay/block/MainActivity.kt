package com.akay.block

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
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
    private var curWrap: LinearLayout? = null

    // put your privacy policy link here in Stage 10 (it opens from Settings > More)
    private val PRIVACY_URL = ""

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
        curWrap = wrap
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
        Ads.init(this)
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

    // adds the numbers of the running game to the medal counters
    private fun flushStats() {
        val g = game ?: return
        val s = g.takeStats()
        if (s[0] > 0 || s[1] > 0 || s[2] > 0) Store.addStats(s[0], s[1], s[2])
    }

    private fun announceMedals() {
        val fresh = Medals.checkNew()
        if (fresh.isNotEmpty()) toast("Medal mila: " + fresh.joinToString(", ") { it.title })
    }

    // keeps the running game (Classic or Adventure) so it can be continued later
    private fun saveIfPlaying() {
        val g = game ?: return
        flushStats()
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

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(color)
        d.cornerRadius = dp(radiusDp).toFloat()
        return d
    }

    // ---------- home ----------

    private fun medalButton(): View {
        val f = FrameLayout(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.addView(MedalView(this, 0xFFFFC21A.toInt(), true), lp(dp(48), dp(48)))
        col.addView(label("Medal", 13f, Color.WHITE), lp(WRAP, WRAP))
        f.addView(col, FrameLayout.LayoutParams(WRAP, WRAP))
        if (Store.earnedMedals().size > Store.seenMedals().size) {
            val dot = View(this)
            val d = GradientDrawable()
            d.shape = GradientDrawable.OVAL
            d.setColor(0xFFFF3B30.toInt())
            dot.background = d
            f.addView(dot, FrameLayout.LayoutParams(dp(14), dp(14), Gravity.TOP or Gravity.END))
        }
        f.setOnClickListener {
            Sfx.tap()
            showMedals()
        }
        return f
    }

    private fun streakCard(): View {
        val n = Store.currentStreak()
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = rounded(0xFFEDE9F6.toInt(), 14)
        card.clipToOutline = true

        val head = label("Consecutive Daily Victories", 14f, 0xFF2B2F6B.toInt())
        head.setBackgroundColor(0xFFA5ACEB.toInt())
        head.setPadding(dp(10), dp(6), dp(10), dp(6))
        card.addView(head, lp(MATCH, WRAP))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(18), dp(8), dp(18), dp(8))
        row.addView(WinBadgeView(this), lp(dp(54), dp(54)))
        val x = label("\u00D7$n", 30f, 0xFF2B2F6B.toInt())
        x.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        x.setPadding(dp(18), 0, 0, 0)
        row.addView(x, lp(0, WRAP, 1f))
        row.addView(CheckView(this, Store.wonToday()), lp(dp(28), dp(28)))
        card.addView(row, lp(MATCH, WRAP))
        return card
    }

    private fun showHome() {
        saveIfPlaying()
        Medals.checkNew()
        game = null
        screen = "home"
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setPadding(dp(30), dp(14), dp(30), dp(18))

        val topRow = LinearLayout(this)
        topRow.orientation = LinearLayout.HORIZONTAL
        topRow.addView(View(this), lp(0, 1, 1f))
        topRow.addView(medalButton(), lp(WRAP, WRAP))
        root.addView(topRow, lp(MATCH, WRAP))

        root.addView(LogoView(this), lp(MATCH, WRAP))

        val sc = lp(MATCH, WRAP)
        sc.topMargin = dp(6)
        sc.leftMargin = dp(14)
        sc.rightMargin = dp(14)
        root.addView(streakCard(), sc)

        root.addView(View(this), lp(MATCH, 0, 1f))

        val adv = chunky(
            "Adventure", 0xFFF5A31A.toInt(), 0xFFC77D00.toInt(),
            IconView(this, 0, 0xFFF5A31A.toInt())
        ) { showMap() }
        val al = lp(MATCH, dp(84))
        al.topMargin = dp(18)
        al.leftMargin = dp(14)
        al.rightMargin = dp(14)
        root.addView(adv, al)

        val classic = chunky(
            "Classic", 0xFF22C99A.toInt(), 0xFF139A75.toInt(),
            IconView(this, 1, 0xFF22C99A.toInt())
        ) { showClassic(true) }
        val cl = lp(MATCH, dp(84))
        cl.topMargin = dp(16)
        cl.leftMargin = dp(14)
        cl.rightMargin = dp(14)
        root.addView(classic, cl)

        val best = Store.classicBest()
        val saved = Store.loadGame() != null || Store.loadAdv() != null
        val info = "Level ${Store.advMax()}   \u2022   Best: $best" +
            (if (saved) "   \u2022   Game saved" else "")
        val infoView = label(info, 14f, 0xCCFFFFFF.toInt())
        infoView.setPadding(0, dp(16), 0, 0)
        root.addView(infoView, lp(MATCH, WRAP))

        root.addView(View(this), lp(MATCH, 0, 0.5f))
        root.addView(label("v1.1  \u2022  Stage 9", 12f, 0x99FFFFFF.toInt()), lp(WRAP, WRAP))

        val sv = ScrollView(this)
        sv.isFillViewport = true
        sv.isVerticalScrollBarEnabled = false
        sv.addView(root)

        val bg = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF1A3FB0.toInt(), 0xFF1485DE.toInt())
        )
        setScreen(sv, bg, Color.TRANSPARENT, Color.TRANSPARENT, false)
    }

    // ---------- medals ----------

    private fun showMedals() {
        screen = "medals"
        Medals.checkNew()
        val earned = Store.earnedMedals()
        Store.setSeenMedals(earned)

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
        bar.addView(label("Medals", 26f, Color.WHITE), lp(0, WRAP, 1f))
        val cnt = label("${earned.size}/${Medals.all.size}", 16f, 0xFFFFD86B.toInt())
        bar.addView(cnt, lp(dp(60), WRAP))
        root.addView(bar, lp(MATCH, WRAP))

        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(dp(16), dp(14), dp(16), dp(20))
        for (m in Medals.all) {
            val have = earned.contains(m.id)
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(14), dp(10), dp(14), dp(10))
            row.background = rounded(if (have) 0xFF4A5CC0.toInt() else 0xFF2F3B7C.toInt(), 16)
            row.addView(MedalView(this, m.color, have), lp(dp(56), dp(56)))

            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.setPadding(dp(14), 0, dp(8), 0)
            val t = label(m.title, 17f, Color.WHITE)
            t.gravity = Gravity.START
            col.addView(t, lp(MATCH, WRAP))
            val d = label(m.desc, 13f, 0xBBFFFFFF.toInt())
            d.gravity = Gravity.START
            col.addView(d, lp(MATCH, WRAP))
            row.addView(col, lp(0, WRAP, 1f))

            val v = minOf(m.value(), m.target)
            row.addView(
                label(if (have) "\u2713" else "$v/${m.target}", 15f, if (have) 0xFF9BFFB0.toInt() else 0xFFBFD6FF.toInt()),
                lp(WRAP, WRAP)
            )
            val rl = lp(MATCH, WRAP)
            rl.bottomMargin = dp(10)
            list.addView(row, rl)
        }
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(list)
        root.addView(sv, lp(MATCH, 0, 1f))

        setScreen(root, ColorDrawable(0xFF3A4A8C.toInt()), 0xFF4F62D0.toInt(), Color.TRANSPARENT, false)
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
        setScreen(g, ColorDrawable(Skins.cur().bg), Color.TRANSPARENT, Color.TRANSPARENT, false)
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
        setScreen(g, ColorDrawable(Skins.cur().bg), Color.TRANSPARENT, Color.TRANSPARENT, false)
    }

    // when the skin is changed inside a game, the new colours show at once
    private fun applySkinNow() {
        if (screen == "classic" || screen == "adv") {
            curWrap?.background = ColorDrawable(Skins.cur().bg)
            game?.applySkin()
        }
    }

    // ---------- game over, revive, win, lose ----------

    override fun onGameOver(score: Int, canRevive: Boolean) {
        flushStats()
        if (canRevive && (screen == "adv" || screen == "classic")) {
            showRevive(score)
        } else {
            finishRun(score)
        }
    }

    // the run is really over: save the result and show the end screen (with an ad now and then)
    private fun finishRun(score: Int) {
        if (screen == "adv") {
            val num = Store.advLevel()
            Store.clearAdv()
            game = null
            Sfx.over()
            announceMedals()
            Ads.maybeInterstitial(this) { showLose(num, score, Levels.goal(num)) }
            return
        }
        Store.clearGame()
        game = null
        val old = Store.classicBest()
        val newBest = score > old
        if (newBest) Store.setClassicBest(score)
        if (newBest) Sfx.best() else Sfx.over()
        announceMedals()
        Ads.maybeInterstitial(this) { showGameOver(score, newBest, maxOf(score, old)) }
    }

    // clearing a level unlocks the next one
    override fun onLevelComplete(score: Int) {
        flushStats()
        val lvNum = Store.advLevel()
        Store.clearAdv()
        game = null
        Sfx.best()
        Store.recordWin()
        val newlyUnlocked = lvNum >= Store.advMax() && lvNum < Levels.COUNT
        if (newlyUnlocked) Store.setAdvMax(lvNum + 1)
        if (lvNum < Levels.COUNT) Store.setAdvLevel(lvNum + 1)
        announceMedals()
        Ads.maybeInterstitial(this) { showWin(lvNum, score, Levels.goal(lvNum), newlyUnlocked) }
    }

    override fun onSettings() {
        showSettings()
    }

    override fun onBack() {
        showMap()
    }

    private fun showRevive(score: Int) {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dlg.setCancelable(false)
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(dp(22), dp(22), dp(22), dp(18))
        panel.background = rounded(0xFF4F6FD6.toInt(), 26)

        panel.addView(label("No moves left!", 28f, Color.WHITE), lp(MATCH, WRAP))
        val sub = if (screen == "adv") {
            val num = Store.advLevel()
            "Level $num:  $score / ${Levels.goal(num)}"
        } else {
            "Score: $score"
        }
        val s = label(sub, 16f, 0xDDFFFFFF.toInt())
        s.setPadding(0, dp(8), 0, dp(6))
        panel.addView(s, lp(MATCH, WRAP))
        val info = label("Watch a short ad to clear some blocks and keep playing (one time per game)", 13f, 0xBBFFFFFF.toInt())
        info.setPadding(0, 0, 0, dp(16))
        panel.addView(info, lp(MATCH, WRAP))

        val green = 0xFF2DBE12.toInt()
        val greenEdge = 0xFF1A8A07.toInt()
        panel.addView(chunky("Revive", green, greenEdge, IconView(this, 2, green)) {
            Ads.showRewarded(
                this,
                {
                    dlg.dismiss()
                    game?.revive()
                },
                { toast("Revive nahi mila. Ad poora dekhna padta hai ya ad abhi ready nahi hai") }
            )
        }, lp(MATCH, dp(70)))
        panel.addView(link("No thanks") {
            dlg.dismiss()
            finishRun(score)
        }, lp(MATCH, WRAP))

        dlg.setContentView(panel)
        val w = dlg.window
        if (w != null) {
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setLayout((resources.displayMetrics.widthPixels * 0.86f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
        }
        dlg.show()
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
            u.setPadding(0, dp(12), 0, 0)
            content.addView(u, lp(MATCH, WRAP))
        }
        val dv = label("Daily Victories \u00D7${Store.currentStreak()}", 15f, 0xFFFFD86B.toInt())
        dv.setPadding(0, dp(6), 0, 0)
        content.addView(dv, lp(MATCH, WRAP))

        val next = chunky(
            if (allDone) "Play Again" else "Next Level", orange, orangeEdge,
            IconView(this, 2, orange)
        ) { showAdventure(false) }
        val nl = lp(MATCH, dp(80))
        nl.topMargin = dp(26)
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

    // sound / vibrate toggle tile
    private fun tile(kind: Int, text: String, on: Boolean, onToggle: (Boolean) -> Unit): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(dp(6), dp(14), dp(6), dp(14))
        val ic = ToggleIcon(this, kind)
        ic.off = !on
        box.addView(ic, lp(dp(48), dp(48)))
        val t = label(text, 14f, Color.WHITE)
        t.setPadding(0, dp(6), 0, 0)
        box.addView(t, lp(WRAP, WRAP))
        box.setOnClickListener {
            val nowOn = ic.off
            ic.off = !nowOn
            onToggle(nowOn)
        }
        return box
    }

    // tile that just does something when tapped (no on / off)
    private fun actionTile(kind: Int, text: String, onClick: () -> Unit): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(dp(6), dp(14), dp(6), dp(14))
        box.addView(ToggleIcon(this, kind), lp(dp(48), dp(48)))
        val t = label(text, 14f, Color.WHITE)
        t.setPadding(0, dp(6), 0, 0)
        box.addView(t, lp(WRAP, WRAP))
        box.setOnClickListener {
            Sfx.tap()
            onClick()
        }
        return box
    }

    private fun showSettings() {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(dp(22), dp(20), dp(22), dp(14))
        panel.background = rounded(0xFF4F6FD6.toInt(), 26)

        panel.addView(label("Settings", 26f, Color.WHITE), lp(MATCH, WRAP))

        val tiles = LinearLayout(this)
        tiles.orientation = LinearLayout.HORIZONTAL
        tiles.background = rounded(0xFF3A44A6.toInt(), 18)
        tiles.addView(tile(0, "Sound", Sfx.soundOn) { on ->
            Sfx.setSound(on)
            if (on) Sfx.tap()
        }, lp(0, WRAP, 1f))
        tiles.addView(tile(2, "Vibrate", Sfx.vibeOn) { on ->
            Sfx.setVibe(on)
            if (on) Sfx.vPlace()
        }, lp(0, WRAP, 1f))
        tiles.addView(actionTile(3, "Skin") {
            dlg.dismiss()
            showSkins()
        }, lp(0, WRAP, 1f))
        val tl = lp(MATCH, WRAP)
        tl.topMargin = dp(14)
        tl.bottomMargin = dp(16)
        panel.addView(tiles, tl)

        val green = 0xFF2DBE12.toInt()
        val greenEdge = 0xFF1A8A07.toInt()
        panel.addView(chunky("Continue", green, greenEdge, null) { dlg.dismiss() }, lp(MATCH, dp(66)))
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

        val more = label("More", 16f, 0xCCFFFFFF.toInt())
        more.setPadding(0, dp(12), 0, dp(6))
        more.setOnClickListener {
            Sfx.tap()
            dlg.dismiss()
            showMore()
        }
        panel.addView(more, lp(MATCH, WRAP))

        dlg.setContentView(panel)
        val w = dlg.window
        if (w != null) {
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setLayout((resources.displayMetrics.widthPixels * 0.86f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
        }
        dlg.show()
    }

    // ---------- skins ----------

    private fun showSkins() {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(dp(20), dp(20), dp(20), dp(12))
        panel.background = rounded(0xFF4F6FD6.toInt(), 26)
        panel.addView(label("Skin", 26f, Color.WHITE), lp(MATCH, WRAP))

        val cur = Store.skin()
        for (i in Skins.all.indices) {
            val s = Skins.all[i]
            val open = Skins.unlocked(s)
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(12), dp(10), dp(12), dp(10))
            val bg = rounded(0xFF3A44A6.toInt(), 16)
            if (i == cur) bg.setStroke(dp(3), Color.WHITE)
            row.background = bg
            row.addView(SwatchView(this, s.colors, s.bg), lp(dp(150), dp(36)))
            val medalName = Medals.all.firstOrNull { it.id == s.needMedal }?.title ?: ""
            val name = label(if (open) s.name else s.name + "  \uD83D\uDD12", 16f, Color.WHITE)
            name.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            name.setPadding(dp(14), 0, 0, 0)
            row.addView(name, lp(0, WRAP, 1f))
            row.setOnClickListener {
                if (open) {
                    Sfx.tap()
                    Store.setSkin(i)
                    applySkinNow()
                    dlg.dismiss()
                } else {
                    Sfx.back()
                    toast("Locked: '$medalName' medal jeetne par khulega")
                }
            }
            val rl = lp(MATCH, WRAP)
            rl.topMargin = dp(10)
            panel.addView(row, rl)
        }
        val close = label("Close", 18f, Color.WHITE)
        close.setPadding(0, dp(16), 0, dp(8))
        close.setOnClickListener {
            Sfx.tap()
            dlg.dismiss()
        }
        panel.addView(close, lp(MATCH, WRAP))

        dlg.setContentView(panel)
        val w = dlg.window
        if (w != null) {
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setLayout((resources.displayMetrics.widthPixels * 0.9f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
        }
        dlg.show()
    }

    // ---------- more (share, privacy policy, about) ----------

    private fun showMore() {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(dp(22), dp(20), dp(22), dp(14))
        panel.background = rounded(0xFF4F6FD6.toInt(), 26)
        panel.addView(label("More", 26f, Color.WHITE), lp(MATCH, WRAP))

        val green = 0xFF2DBE12.toInt()
        val greenEdge = 0xFF1A8A07.toInt()
        val share = chunky("Share with Friends", green, greenEdge, null) {
            try {
                val i = Intent(Intent.ACTION_SEND)
                i.type = "text/plain"
                i.putExtra(
                    Intent.EXTRA_TEXT,
                    "Try Block, a free puzzle game! https://play.google.com/store/apps/details?id=$packageName"
                )
                startActivity(Intent.createChooser(i, "Share"))
            } catch (e: Throwable) {
                toast("Share nahi ho paya")
            }
        } as LinearLayout
        (share.getChildAt(share.childCount - 1) as TextView).textSize = 20f
        val sl = lp(MATCH, dp(66))
        sl.topMargin = dp(14)
        panel.addView(share, sl)

        val priv = chunky("Privacy Policy", green, greenEdge, null) {
            if (PRIVACY_URL.isEmpty()) {
                toast("Privacy policy ka link Stage 10 mein judega")
            } else {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)))
                } catch (e: Throwable) {
                    toast("Link khul nahi paya")
                }
            }
        } as LinearLayout
        (priv.getChildAt(priv.childCount - 1) as TextView).textSize = 20f
        val pl = lp(MATCH, dp(66))
        pl.topMargin = dp(12)
        panel.addView(priv, pl)

        val about = chunky("About", green, greenEdge, null) {
            toast("Block v1.1  \u2022  Made by Akay")
        } as LinearLayout
        (about.getChildAt(about.childCount - 1) as TextView).textSize = 20f
        val al = lp(MATCH, dp(66))
        al.topMargin = dp(12)
        panel.addView(about, al)

        val close = label("Close", 18f, Color.WHITE)
        close.setPadding(0, dp(14), 0, dp(8))
        close.setOnClickListener {
            Sfx.tap()
            dlg.dismiss()
        }
        panel.addView(close, lp(MATCH, WRAP))

        dlg.setContentView(panel)
        val w = dlg.window
        if (w != null) {
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setLayout((resources.displayMetrics.widthPixels * 0.86f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
        }
        dlg.show()
    }
}
