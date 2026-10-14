package com.akay.block

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import java.util.Random
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

class GameView(ctx: Context) : View(ctx) {

    interface Listener {
        fun onGameOver(score: Int)
        fun onLevelComplete(score: Int)
        fun onSettings()
        fun onBack()
    }

    var listener: Listener? = null
    var best = 0

    private val N = 8
    private val den = ctx.resources.displayMetrics.density
    private val BG = 0xFF3B4F8F.toInt()
    private val COLORS = intArrayOf(
        0, 0xFF2FBF3C.toInt(), 0xFF3C5BE8.toInt(), 0xFFC93131.toInt(), 0xFFF0832B.toInt(),
        0xFFEDBA3A.toInt(), 0xFF8E4FD8.toInt(), 0xFF2BB4E5.toInt()
    )

    private val board = Array(N) { IntArray(N) }
    private val justPlaced = Array(N) { BooleanArray(N) }
    private val tray = arrayOfNulls<Piece>(3)
    private val rnd = Random()
    private var score = 0
    private var shownScore = 0f
    private var combo = 0
    private var streak = 0
    private var over = false

    // Adventure level (null in Classic mode)
    private var level: Level? = null

    // geometry
    private var cell = 0f
    private var bl = 0f
    private var bt = 0f
    private var trayCell = 0f
    private var slotY = 0f
    private val slotX = FloatArray(3)

    // drag
    private var dragIdx = -1
    private var fx = 0f
    private var fy = 0f
    private var snapR = 0
    private var snapC = 0
    private var snapOk = false
    private var gearDown = false
    private var backDown = false
    private val prevRows = ArrayList<Int>()
    private val prevCols = ArrayList<Int>()

    // piece flying back to the tray
    private var retIdx = -1
    private var retT = 1f
    private var retFromL = 0f
    private var retFromT = 0f
    private var retFromS = 0f

    // effects
    private class Fade(val r: Int, val c: Int, val color: Int)
    private class Spark(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, val color: Int, val size: Float
    )

    private val fades = ArrayList<Fade>()
    private val sparks = ArrayList<Spark>()
    private var fadeT = 1f
    private var popT = 1f
    private var placeT = 1f
    private var spawnT = 1f
    private var liftT = 1f
    private var shakeT = 1f
    private var popText = ""
    private var popPhrase = ""
    private var lastNano = 0L
    private val ovs = OvershootInterpolator(1.6f)

    private var fadeAnim: ValueAnimator? = null
    private var popAnim: ValueAnimator? = null
    private var scoreAnim: ValueAnimator? = null
    private var placeAnim: ValueAnimator? = null
    private var spawnAnim: ValueAnimator? = null
    private var liftAnim: ValueAnimator? = null
    private var retAnim: ValueAnimator? = null
    private var shakeAnim: ValueAnimator? = null
    private var overRun: Runnable? = null

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rf = RectF()

    init {
        tp.typeface = Typeface.DEFAULT_BOLD
        tp.textAlign = Paint.Align.CENTER
    }

    private fun anim(ms: Long, update: (Float) -> Unit, end: (() -> Unit)? = null): ValueAnimator {
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = ms
        a.addUpdateListener {
            update(it.animatedValue as Float)
            invalidate()
        }
        if (end != null) {
            a.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    end()
                }
            })
        }
        a.start()
        return a
    }

    // ---------- game state ----------

    private fun cancelAnims() {
        fadeAnim?.cancel()
        popAnim?.cancel()
        scoreAnim?.cancel()
        placeAnim?.cancel()
        spawnAnim?.cancel()
        liftAnim?.cancel()
        retAnim?.cancel()
        shakeAnim?.cancel()
    }

    private fun resetState() {
        cancelAnims()
        removeCallbacks(overRun)
        for (r in 0 until N) {
            java.util.Arrays.fill(board[r], 0)
            java.util.Arrays.fill(justPlaced[r], false)
        }
        score = 0
        shownScore = 0f
        combo = 0
        streak = 0
        over = false
        fades.clear()
        sparks.clear()
        popT = 1f
        placeT = 1f
        spawnT = 1f
        liftT = 1f
        shakeT = 1f
        retIdx = -1
        retT = 1f
        dragIdx = -1
        prevRows.clear()
        prevCols.clear()
        for (i in 0..2) tray[i] = null
    }

    // Classic mode
    fun newGame() {
        level = null
        resetState()
        refill()
        invalidate()
    }

    // Adventure mode: start a level with its blocks already on the board
    fun startLevel(lv: Level) {
        level = lv
        resetState()
        for (r in 0 until N) {
            for (c in 0 until N) board[r][c] = lv.prefill[r][c]
        }
        refill()
        invalidate()
    }

    // text form of the running game, so it can be saved. null when the game is over.
    fun snapshot(): String? {
        if (over) return null
        val sb = StringBuilder()
        sb.append("v1|").append(score).append('|').append(combo).append('|').append(streak).append('|')
        for (r in 0 until N) for (c in 0 until N) sb.append(board[r][c])
        for (i in 0..2) {
            sb.append('|')
            val pc = tray[i]
            if (pc == null) sb.append('-') else sb.append(Shapes.all.indexOf(pc.rows)).append(':').append(pc.color)
        }
        return sb.toString()
    }

    fun loadState(s: String): Boolean {
        return try {
            val parts = s.split("|")
            if (parts.size != 8 || parts[0] != "v1" || parts[4].length != N * N) return false
            val sc = parts[1].toInt()
            val cb = parts[2].toInt()
            val st = parts[3].toInt()
            val bd = parts[4]
            for (ch in bd) {
                if (ch < '0' || ch > '7') return false
            }
            val pcs = arrayOfNulls<Piece>(3)
            for (i in 0..2) {
                val t = parts[5 + i]
                if (t != "-") {
                    val kv = t.split(":")
                    val idx = kv[0].toInt()
                    val col = kv[1].toInt()
                    if (idx < 0 || idx >= Shapes.all.size || col < 1 || col > 7) return false
                    pcs[i] = Piece(Shapes.all[idx], col)
                }
            }
            resetState()
            score = sc
            shownScore = sc.toFloat()
            combo = cb
            streak = st
            for (r in 0 until N) for (c in 0 until N) board[r][c] = bd[r * N + c] - '0'
            for (i in 0..2) tray[i] = pcs[i]
            if (tray.all { it == null }) refill()
            if (score > best) best = score
            checkOver()
            invalidate()
            true
        } catch (e: Throwable) {
            false
        }
    }

    private fun refill() {
        val mx = level?.maxCells ?: 9
        var tries = 0
        while (true) {
            for (i in 0..2) tray[i] = Shapes.randomMax(rnd, mx)
            tries++
            if (tries > 30 || tray.any { it != null && fitsAnywhere(it) }) break
        }
        spawnAnim?.cancel()
        spawnT = 0f
        spawnAnim = anim(280, { spawnT = it })
    }

    private fun canPlace(pc: Piece, r0: Int, c0: Int): Boolean {
        for (cl in pc.cells) {
            val r = r0 + cl[0]
            val c = c0 + cl[1]
            if (r < 0 || r >= N || c < 0 || c >= N || board[r][c] != 0) return false
        }
        return true
    }

    private fun fitsAnywhere(pc: Piece): Boolean {
        for (r in 0..N - pc.h) {
            for (c in 0..N - pc.w) {
                if (canPlace(pc, r, c)) return true
            }
        }
        return false
    }

    private fun fullRows(b: Array<IntArray>): List<Int> =
        (0 until N).filter { r -> (0 until N).all { c -> b[r][c] != 0 } }

    private fun fullCols(b: Array<IntArray>): List<Int> =
        (0 until N).filter { c -> (0 until N).all { r -> b[r][c] != 0 } }

    private fun place(idx: Int, pc: Piece, r0: Int, c0: Int) {
        placeAnim?.cancel()
        fadeAnim?.cancel()
        for (cl in pc.cells) {
            board[r0 + cl[0]][c0 + cl[1]] = pc.color
            justPlaced[r0 + cl[0]][c0 + cl[1]] = true
        }
        tray[idx] = null
        val rows = fullRows(board)
        val cols = fullCols(board)
        val lines = rows.size + cols.size
        var gain = pc.cells.size
        if (lines > 0) {
            combo++
            streak = 0
            val set = HashSet<Int>()
            for (r in rows) for (c in 0 until N) set.add(r * N + c)
            for (c in cols) for (r in 0 until N) set.add(r * N + c)
            fades.clear()
            for (k in set) {
                val rr = k / N
                val cc = k % N
                fades.add(Fade(rr, cc, board[rr][cc]))
                spawnSparks(rr, cc, board[rr][cc])
            }
            for (k in set) {
                board[k / N][k % N] = 0
                justPlaced[k / N][k % N] = false
            }
            gain += lines * lines * 10 + (combo - 1) * 10 * lines
            val perfect = board.all { row -> row.all { it == 0 } }
            if (perfect) gain += 300
            startFade()
            showPop(gain, lines, perfect)
            if (lines >= 3 || combo >= 3 || perfect) shake()
            Sfx.vClear(combo, lines, perfect)
            Sfx.clear(combo, lines, perfect)
        } else {
            streak++
            if (streak >= 3) combo = 0
            Sfx.vPlace()
            Sfx.place()
        }
        placeT = 0f
        placeAnim = anim(220, { placeT = it }, { clearPlaced() })
        addScore(gain)

        // Adventure: goal reached -> level complete
        val lv = level
        if (lv != null && score >= lv.goal) {
            over = true
            Sfx.vWin()
            val run = Runnable { listener?.onLevelComplete(score) }
            overRun = run
            postDelayed(run, 900)
            return
        }

        if (tray.all { it == null }) refill()
        checkOver()
    }

    private fun clearPlaced() {
        for (r in 0 until N) java.util.Arrays.fill(justPlaced[r], false)
    }

    private fun spawnSparks(r: Int, cc: Int, color: Int) {
        val cx = bl + cc * cell + cell / 2f
        val cy = bt + r * cell + cell / 2f
        for (k in 0 until 3) {
            if (sparks.size > 260) return
            val ang = (rnd.nextFloat() * 6.2832f).toDouble()
            val sp = (80f + rnd.nextFloat() * 220f) * den
            sparks.add(
                Spark(
                    cx, cy,
                    Math.cos(ang).toFloat() * sp,
                    Math.sin(ang).toFloat() * sp - 120f * den,
                    1f, color, (3f + rnd.nextFloat() * 4f) * den
                )
            )
        }
    }

    private fun updateSparks(dt: Float) {
        var i = sparks.size - 1
        while (i >= 0) {
            val s = sparks[i]
            s.x += s.vx * dt
            s.y += s.vy * dt
            s.vy += 1500f * den * dt
            s.life -= dt / 0.75f
            if (s.life <= 0f) sparks.removeAt(i)
            i--
        }
    }

    private fun checkOver() {
        val alive = tray.any { it != null && fitsAnywhere(it) }
        if (!alive) {
            over = true
            Sfx.vLose()
            val run = Runnable { listener?.onGameOver(score) }
            overRun = run
            postDelayed(run, 700)
        }
    }

    private fun addScore(g: Int) {
        score += g
        if (score > best) best = score
        scoreAnim?.cancel()
        val from = shownScore
        val to = score.toFloat()
        val a = ValueAnimator.ofFloat(from, to)
        a.duration = 450
        a.addUpdateListener {
            shownScore = it.animatedValue as Float
            invalidate()
        }
        a.start()
        scoreAnim = a
    }

    private fun startFade() {
        fadeT = 0f
        fadeAnim = anim(320, { fadeT = it }, { fades.clear() })
    }

    private fun shake() {
        shakeAnim?.cancel()
        shakeT = 0f
        shakeAnim = anim(380, { shakeT = it })
    }

    private fun showPop(gain: Int, lines: Int, perfect: Boolean) {
        popText = "+$gain"
        popPhrase = when {
            perfect -> "Perfect!"
            lines >= 4 || combo >= 4 -> "Amazing!"
            lines == 3 || combo == 3 -> "Excellent!"
            lines == 2 || combo == 2 -> "Great!"
            else -> "Good!"
        }
        popAnim?.cancel()
        popT = 0f
        popAnim = anim(950, { popT = it })
    }

    // ---------- size and touch ----------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cell = Math.floor((w * 0.89f / N).toDouble()).toFloat()
        bl = (w - cell * N) / 2f
        bt = minOf(190f * den, h * 0.26f)
        trayCell = cell * 0.5f
        val boardBottom = bt + cell * N
        slotY = boardBottom + (h - boardBottom) * 0.42f
        slotX[0] = w * 0.2f
        slotX[1] = w * 0.5f
        slotX[2] = w * 0.8f
    }

    private fun slotAt(x: Float, y: Float): Int {
        val half = cell * 1.6f
        for (i in 0..2) {
            if (Math.abs(x - slotX[i]) <= half && Math.abs(y - slotY) <= half * 1.3f) return i
        }
        return -1
    }

    private fun updateSnap() {
        val pc = tray[dragIdx] ?: return
        val left = fx - pc.w * cell / 2f
        val top = fy - pc.h * cell / 2f - cell * 1.8f
        snapC = Math.round((left - bl) / cell)
        snapR = Math.round((top - bt) / cell)
        snapOk = canPlace(pc, snapR, snapC)
        prevRows.clear()
        prevCols.clear()
        if (snapOk) {
            val tmp = Array(N) { board[it].clone() }
            for (cl in pc.cells) tmp[snapR + cl[0]][snapC + cl[1]] = pc.color
            prevRows.addAll(fullRows(tmp))
            prevCols.addAll(fullCols(tmp))
        }
    }

    private fun gearHit(x: Float, y: Float): Boolean =
        hypot(x - (width - 38f * den), y - 40f * den) < 30f * den

    private fun backHit(x: Float, y: Float): Boolean =
        level != null && hypot(x - 40f * den, y - 40f * den) < 30f * den

    private fun dropBack(idx: Int, pc: Piece) {
        retAnim?.cancel()
        val ds = trayCell + (cell - trayCell) * liftT
        retFromL = fx - pc.w * ds / 2f
        retFromT = fy - pc.h * ds / 2f - cell * 1.8f * liftT
        retFromS = ds
        retIdx = idx
        retT = 0f
        retAnim = anim(200, { retT = it }, { retIdx = -1 })
        Sfx.back()
        Sfx.vBack()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gearDown = false
                backDown = false
                if (gearHit(e.x, e.y)) {
                    gearDown = true
                    return true
                }
                if (backHit(e.x, e.y)) {
                    backDown = true
                    return true
                }
                if (over) return true
                val i = slotAt(e.x, e.y)
                if (i >= 0 && tray[i] != null) {
                    retAnim?.cancel()
                    dragIdx = i
                    fx = e.x
                    fy = e.y
                    liftAnim?.cancel()
                    liftT = 0f
                    liftAnim = anim(130, { liftT = it })
                    updateSnap()
                    invalidate()
                    Sfx.tap()
                    Sfx.vPick()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragIdx >= 0) {
                    fx = e.x
                    fy = e.y
                    updateSnap()
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (gearDown) {
                    gearDown = false
                    if (gearHit(e.x, e.y)) {
                        Sfx.tap()
                        listener?.onSettings()
                    }
                    return true
                }
                if (backDown) {
                    backDown = false
                    if (backHit(e.x, e.y)) {
                        Sfx.tap()
                        listener?.onBack()
                    }
                    return true
                }
                if (dragIdx >= 0) {
                    val idx = dragIdx
                    val pc = tray[idx]
                    dragIdx = -1
                    if (pc != null) {
                        if (snapOk) place(idx, pc, snapR, snapC) else dropBack(idx, pc)
                    }
                    prevRows.clear()
                    prevCols.clear()
                    invalidate()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                dragIdx = -1
                gearDown = false
                backDown = false
                prevRows.clear()
                prevCols.clear()
                invalidate()
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        cancelAnims()
        removeCallbacks(overRun)
        super.onDetachedFromWindow()
    }

    // ---------- drawing ----------

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastNano == 0L) 0f else minOf(0.05f, (now - lastNano) / 1e9f)
        lastNano = now
        updateSparks(dt)

        val w = width.toFloat()
        drawHud(c, w)
        drawCombo(c, w)

        val sx = if (shakeT < 1f) sin(shakeT * PI.toFloat() * 7f) * (1f - shakeT) * 7f * den else 0f
        c.save()
        c.translate(sx, 0f)
        drawBoard(c)
        drawFades(c)
        drawSparks(c)
        c.restore()

        drawTray(c)
        drawRet(c)
        drawDrag(c)
        drawPop(c, w)

        if (sparks.isNotEmpty()) postInvalidateOnAnimation() else lastNano = 0L
    }

    private fun drawOutlined(
        c: Canvas, s: String, x: Float, y: Float, size: Float, fill: Int, stroke: Int, alpha: Int
    ) {
        tp.textSize = size
        tp.style = Paint.Style.STROKE
        tp.strokeWidth = size * 0.16f
        tp.strokeJoin = Paint.Join.ROUND
        tp.color = withAlpha(stroke, alpha)
        c.drawText(s, x, y, tp)
        tp.style = Paint.Style.FILL
        tp.color = withAlpha(fill, alpha)
        c.drawText(s, x, y, tp)
    }

    private fun drawGear(c: Canvas, cx: Float, cy: Float, r: Float) {
        p.style = Paint.Style.FILL
        p.color = 0xFFBFE0FF.toInt()
        c.save()
        c.translate(cx, cy)
        for (i in 0 until 8) {
            rf.set(-r * 0.2f, -r * 1.08f, r * 0.2f, -r * 0.7f)
            c.drawRoundRect(rf, r * 0.08f, r * 0.08f, p)
            c.rotate(45f)
        }
        c.restore()
        c.drawCircle(cx, cy, r * 0.82f, p)
        p.color = BG
        c.drawCircle(cx, cy, r * 0.36f, p)
    }

    private fun drawHud(c: Canvas, w: Float) {
        val lv = level
        if (lv != null) {
            drawAdvHud(c, w, lv)
            return
        }
        val hy = 40f * den
        p.style = Paint.Style.FILL
        p.color = 0xFFFFC21A.toInt()
        drawCrown(c, 40f * den, hy, 44f * den, p)
        tp.style = Paint.Style.FILL
        tp.textAlign = Paint.Align.LEFT
        tp.textSize = 26f * den
        tp.color = 0xFFF5A623.toInt()
        c.drawText(best.toString(), 70f * den, hy + 9f * den, tp)
        tp.textAlign = Paint.Align.CENTER
        drawGear(c, w - 38f * den, hy, 17f * den)

        val sy = 112f * den
        if (shownScore >= 1f) {
            c.save()
            c.translate(w / 2f, sy)
            c.rotate(45f)
            rf.set(-34f * den, -34f * den, 34f * den, 34f * den)
            p.color = withAlpha(0xFF3E9BFF.toInt(), 200)
            c.drawRoundRect(rf, 8f * den, 8f * den, p)
            c.restore()
        }
        tp.textSize = 58f * den
        tp.color = Color.WHITE
        c.drawText(shownScore.toInt().toString(), w / 2f, sy + 20f * den, tp)
    }

    // Adventure HUD: back arrow, gear, level badge, progress bar, goal
    private fun drawAdvHud(c: Canvas, w: Float, lv: Level) {
        val hy = 40f * den
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4.5f * den
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.color = 0xFFBFE0FF.toInt()
        path.reset()
        path.moveTo(46f * den, hy - 14f * den)
        path.lineTo(32f * den, hy)
        path.lineTo(46f * den, hy + 14f * den)
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
        drawGear(c, w - 38f * den, hy, 17f * den)

        val by = 108f * den
        val x0 = w * 0.25f
        val x1 = w * 0.75f
        val frac = (shownScore / lv.goal).coerceIn(0f, 1f)
        rf.set(x0, by - 9f * den, x1, by + 9f * den)
        p.color = 0xFF1D2240.toInt()
        c.drawRoundRect(rf, 9f * den, 9f * den, p)
        if (frac > 0.01f) {
            rf.set(x0, by - 9f * den, x0 + (x1 - x0) * frac, by + 9f * den)
            p.color = 0xFF3E9BFF.toInt()
            c.drawRoundRect(rf, 9f * den, 9f * den, p)
        }

        p.color = 0xFF2E8CF0.toInt()
        c.drawCircle(x0, by, 26f * den, p)
        tp.style = Paint.Style.FILL
        tp.textAlign = Paint.Align.CENTER
        tp.color = Color.WHITE
        tp.textSize = (if (lv.number >= 1000) 16f else 22f) * den
        c.drawText(lv.number.toString(), x0, by + 8f * den, tp)

        p.color = 0xFF14193A.toInt()
        c.drawCircle(x1, by, 28f * den, p)
        tp.color = 0xFFFFA600.toInt()
        tp.textSize = (if (lv.goal >= 1000) 15f else 19f) * den
        c.drawText(lv.goal.toString(), x1, by + 7f * den, tp)

        tp.color = 0xCCFFFFFF.toInt()
        tp.textSize = 13f * den
        c.drawText("${shownScore.toInt()} / ${lv.goal}", w / 2f, by + 44f * den, tp)
    }

    private fun drawCombo(c: Canvas, w: Float) {
        if (combo < 2) return
        val txt = "COMBO x$combo"
        tp.style = Paint.Style.FILL
        tp.textAlign = Paint.Align.CENTER
        tp.textSize = 14f * den
        val tw = tp.measureText(txt)
        val cy = bt - 22f * den
        rf.set(w / 2f - tw / 2f - 14f * den, cy - 13f * den, w / 2f + tw / 2f + 14f * den, cy + 13f * den)
        p.style = Paint.Style.FILL
        p.color = 0xFFFF7A18.toInt()
        c.drawRoundRect(rf, 13f * den, 13f * den, p)
        tp.color = Color.WHITE
        c.drawText(txt, w / 2f, cy + 5f * den, tp)
    }

    private fun drawBoard(c: Canvas) {
        p.style = Paint.Style.FILL
        rf.set(bl - 8f * den, bt - 8f * den, bl + cell * N + 8f * den, bt + cell * N + 8f * den)
        p.color = 0xFF2E3C75.toInt()
        c.drawRoundRect(rf, 14f * den, 14f * den, p)
        rf.set(bl - 2f * den, bt - 2f * den, bl + cell * N + 2f * den, bt + cell * N + 2f * den)
        p.color = 0xFF1D2240.toInt()
        c.drawRoundRect(rf, 8f * den, 8f * den, p)

        p.color = 0xFF262C54.toInt()
        for (i in 1 until N) {
            rf.set(bl + i * cell - den / 2f, bt, bl + i * cell + den / 2f, bt + N * cell)
            c.drawRect(rf, p)
            rf.set(bl, bt + i * cell - den / 2f, bl + N * cell, bt + i * cell + den / 2f)
            c.drawRect(rf, p)
        }

        for (r in 0 until N) {
            for (cc in 0 until N) {
                val v = board[r][cc]
                if (v == 0) continue
                if (placeT < 1f && justPlaced[r][cc]) {
                    val k = 1f + 0.16f * sin(placeT * PI.toFloat())
                    val s = cell * k
                    drawBlock(c, bl + cc * cell - (s - cell) / 2f, bt + r * cell - (s - cell) / 2f, s, v, 255)
                } else {
                    drawBlock(c, bl + cc * cell, bt + r * cell, cell, v, 255)
                }
            }
        }

        if (dragIdx >= 0 && snapOk) {
            p.style = Paint.Style.FILL
            p.color = 0x44BFE0FF
            for (r in prevRows) {
                rf.set(bl, bt + r * cell, bl + N * cell, bt + (r + 1) * cell)
                c.drawRect(rf, p)
            }
            for (cc in prevCols) {
                rf.set(bl + cc * cell, bt, bl + (cc + 1) * cell, bt + N * cell)
                c.drawRect(rf, p)
            }
            val pc = tray[dragIdx]
            if (pc != null) {
                for (cl in pc.cells) {
                    drawBlock(c, bl + (snapC + cl[1]) * cell, bt + (snapR + cl[0]) * cell, cell, pc.color, 110)
                }
            }
        }
    }

    private fun drawFades(c: Canvas) {
        if (fades.isEmpty()) return
        val t = fadeT
        val a = ((1f - t) * 255f).toInt()
        for (f in fades) {
            val s = cell * (1f + 0.25f * t)
            val x = bl + f.c * cell - (s - cell) / 2f
            val y = bt + f.r * cell - (s - cell) / 2f
            drawBlock(c, x, y, s, f.color, a)
            p.style = Paint.Style.FILL
            p.color = withAlpha(Color.WHITE, (150f * (1f - t)).toInt())
            rf.set(x, y, x + s, y + s)
            c.drawRect(rf, p)
        }
    }

    private fun drawSparks(c: Canvas) {
        if (sparks.isEmpty()) return
        p.style = Paint.Style.FILL
        for (s in sparks) {
            p.color = withAlpha(mixColor(COLORS[s.color], Color.WHITE, 0.3f), (s.life * 255f).toInt())
            rf.set(s.x - s.size / 2f, s.y - s.size / 2f, s.x + s.size / 2f, s.y + s.size / 2f)
            c.drawRect(rf, p)
        }
    }

    private fun drawTray(c: Canvas) {
        val k = if (spawnT < 1f) 0.45f + 0.55f * ovs.getInterpolation(spawnT) else 1f
        val ts = trayCell * k
        for (i in 0..2) {
            if (i == dragIdx || i == retIdx) continue
            val pc = tray[i] ?: continue
            val left = slotX[i] - pc.w * ts / 2f
            val top = slotY - pc.h * ts / 2f
            for (cl in pc.cells) {
                drawBlock(c, left + cl[1] * ts, top + cl[0] * ts, ts, pc.color, 255)
            }
        }
    }

    private fun drawRet(c: Canvas) {
        if (retIdx < 0) return
        val pc = tray[retIdx] ?: return
        val t = retT
        val e = 1f - (1f - t) * (1f - t)
        val s = retFromS + (trayCell - retFromS) * e
        val toL = slotX[retIdx] - pc.w * trayCell / 2f
        val toT = slotY - pc.h * trayCell / 2f
        val left = retFromL + (toL - retFromL) * e
        val top = retFromT + (toT - retFromT) * e
        for (cl in pc.cells) {
            drawBlock(c, left + cl[1] * s, top + cl[0] * s, s, pc.color, 255)
        }
    }

    private fun drawDrag(c: Canvas) {
        if (dragIdx < 0) return
        val pc = tray[dragIdx] ?: return
        val ds = trayCell + (cell - trayCell) * liftT
        val left = fx - pc.w * ds / 2f
        val top = fy - pc.h * ds / 2f - cell * 1.8f * liftT
        for (cl in pc.cells) {
            drawBlock(c, left + cl[1] * ds, top + cl[0] * ds, ds, pc.color, 255)
        }
    }

    private fun drawPop(c: Canvas, w: Float) {
        if (popT >= 1f || popText.isEmpty()) return
        val t = popT
        val a = when {
            t < 0.15f -> t / 0.15f
            t > 0.7f -> (1f - t) / 0.3f
            else -> 1f
        }
        val alpha = (a * 255f).toInt()
        val cy = bt + cell * 3.2f - t * 30f * den
        tp.textAlign = Paint.Align.CENTER
        drawOutlined(c, popText, w / 2f, cy, 46f * den, 0xFFFFE066.toInt(), 0xFFB23A00.toInt(), alpha)
        drawOutlined(c, popPhrase, w / 2f, cy + 70f * den, 40f * den, 0xFFFFC21A.toInt(), 0xFF9A3B00.toInt(), alpha)
    }

    // one bevelled block
    private fun drawBlock(c: Canvas, x: Float, y: Float, s: Float, color: Int, alpha: Int) {
        val base = COLORS[color]
        val gap = s * 0.02f
        val x0 = x + gap
        val y0 = y + gap
        val sz = s - gap * 2f
        val b = sz * 0.14f
        p.style = Paint.Style.FILL

        p.color = withAlpha(mixColor(base, Color.BLACK, 0.2f), alpha)
        c.drawRect(x0, y0, x0 + sz, y0 + sz, p)

        path.reset()
        path.moveTo(x0, y0)
        path.lineTo(x0 + sz, y0)
        path.lineTo(x0 + sz - b, y0 + b)
        path.lineTo(x0 + b, y0 + b)
        path.close()
        p.color = withAlpha(mixColor(base, Color.WHITE, 0.45f), alpha)
        c.drawPath(path, p)

        path.reset()
        path.moveTo(x0, y0)
        path.lineTo(x0 + b, y0 + b)
        path.lineTo(x0 + b, y0 + sz - b)
        path.lineTo(x0, y0 + sz)
        path.close()
        p.color = withAlpha(mixColor(base, Color.WHITE, 0.2f), alpha)
        c.drawPath(path, p)

        path.reset()
        path.moveTo(x0 + sz, y0)
        path.lineTo(x0 + sz, y0 + sz)
        path.lineTo(x0 + sz - b, y0 + sz - b)
        path.lineTo(x0 + sz - b, y0 + b)
        path.close()
        p.color = withAlpha(mixColor(base, Color.BLACK, 0.35f), alpha)
        c.drawPath(path, p)

        path.reset()
        path.moveTo(x0, y0 + sz)
        path.lineTo(x0 + sz, y0 + sz)
        path.lineTo(x0 + sz - b, y0 + sz - b)
        path.lineTo(x0 + b, y0 + sz - b)
        path.close()
        p.color = withAlpha(mixColor(base, Color.BLACK, 0.45f), alpha)
        c.drawPath(path, p)

        p.color = withAlpha(base, alpha)
        c.drawRect(x0 + b, y0 + b, x0 + sz - b, y0 + sz - b, p)
    }
}
