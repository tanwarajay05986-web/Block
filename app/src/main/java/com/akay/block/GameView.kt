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
import java.util.Random
import kotlin.math.hypot

class GameView(ctx: Context) : View(ctx) {

    interface Listener {
        fun onGameOver(score: Int)
        fun onSettings()
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
    private val tray = arrayOfNulls<Piece>(3)
    private val rnd = Random()
    private var score = 0
    private var shownScore = 0f
    private var combo = 0
    private var over = false

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
    private val prevRows = ArrayList<Int>()
    private val prevCols = ArrayList<Int>()

    // effects
    private class Fade(val r: Int, val c: Int, val color: Int)

    private val fades = ArrayList<Fade>()
    private var fadeT = 1f
    private var popT = 1f
    private var popText = ""
    private var popPhrase = ""
    private var fadeAnim: ValueAnimator? = null
    private var popAnim: ValueAnimator? = null
    private var scoreAnim: ValueAnimator? = null
    private var overRun: Runnable? = null

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rf = RectF()

    init {
        tp.typeface = Typeface.DEFAULT_BOLD
        tp.textAlign = Paint.Align.CENTER
    }

    // ---------- game logic ----------

    fun newGame() {
        fadeAnim?.cancel()
        popAnim?.cancel()
        scoreAnim?.cancel()
        removeCallbacks(overRun)
        for (r in 0 until N) java.util.Arrays.fill(board[r], 0)
        score = 0
        shownScore = 0f
        combo = 0
        over = false
        fades.clear()
        popT = 1f
        dragIdx = -1
        prevRows.clear()
        prevCols.clear()
        refill()
        invalidate()
    }

    private fun refill() {
        var tries = 0
        while (true) {
            for (i in 0..2) tray[i] = Shapes.random(rnd)
            tries++
            if (tries > 30 || tray.any { it != null && fitsAnywhere(it) }) break
        }
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
        for (cl in pc.cells) board[r0 + cl[0]][c0 + cl[1]] = pc.color
        tray[idx] = null
        val rows = fullRows(board)
        val cols = fullCols(board)
        val lines = rows.size + cols.size
        var gain = pc.cells.size
        if (lines > 0) {
            combo++
            fadeAnim?.cancel()
            val set = HashSet<Int>()
            for (r in rows) for (c in 0 until N) set.add(r * N + c)
            for (c in cols) for (r in 0 until N) set.add(r * N + c)
            fades.clear()
            for (k in set) fades.add(Fade(k / N, k % N, board[k / N][k % N]))
            for (k in set) board[k / N][k % N] = 0
            gain += lines * lines * 10 + (combo - 1) * 10 * lines
            startFade()
            showPop(gain, lines)
        } else {
            combo = 0
        }
        addScore(gain)
        if (tray.all { it == null }) refill()
        checkOver()
    }

    private fun checkOver() {
        val alive = tray.any { it != null && fitsAnywhere(it) }
        if (!alive) {
            over = true
            val run = Runnable { listener?.onGameOver(score) }
            overRun = run
            postDelayed(run, 700)
        }
    }

    private fun addScore(g: Int) {
        score += g
        if (score > best) best = score
        scoreAnim?.cancel()
        val a = ValueAnimator.ofFloat(shownScore, score.toFloat())
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
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 320
        a.addUpdateListener {
            fadeT = it.animatedValue as Float
            invalidate()
        }
        a.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                fades.clear()
                invalidate()
            }
        })
        a.start()
        fadeAnim = a
    }

    private fun showPop(gain: Int, lines: Int) {
        popText = "+$gain"
        popPhrase = when {
            lines >= 4 || combo >= 4 -> "Amazing!"
            lines == 3 || combo == 3 -> "Excellent!"
            lines == 2 || combo == 2 -> "Great!"
            else -> "Good!"
        }
        popAnim?.cancel()
        popT = 0f
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 950
        a.addUpdateListener {
            popT = it.animatedValue as Float
            invalidate()
        }
        a.start()
        popAnim = a
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

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gearDown = false
                if (gearHit(e.x, e.y)) {
                    gearDown = true
                    return true
                }
                if (over) return true
                val i = slotAt(e.x, e.y)
                if (i >= 0 && tray[i] != null) {
                    dragIdx = i
                    fx = e.x
                    fy = e.y
                    updateSnap()
                    invalidate()
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
                    if (gearHit(e.x, e.y)) listener?.onSettings()
                    return true
                }
                if (dragIdx >= 0) {
                    val idx = dragIdx
                    val pc = tray[idx]
                    dragIdx = -1
                    if (pc != null && snapOk) place(idx, pc, snapR, snapC)
                    prevRows.clear()
                    prevCols.clear()
                    invalidate()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                dragIdx = -1
                gearDown = false
                prevRows.clear()
                prevCols.clear()
                invalidate()
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        fadeAnim?.cancel()
        popAnim?.cancel()
        scoreAnim?.cancel()
        removeCallbacks(overRun)
        super.onDetachedFromWindow()
    }

    // ---------- drawing ----------

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        drawHud(c, w)
        drawBoard(c)
        drawFades(c)
        drawTray(c)
        drawDrag(c)
        drawPop(c, w)
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
                if (v != 0) drawBlock(c, bl + cc * cell, bt + r * cell, cell, v, 255)
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

    private fun drawTray(c: Canvas) {
        for (i in 0..2) {
            if (i == dragIdx) continue
            val pc = tray[i] ?: continue
            val left = slotX[i] - pc.w * trayCell / 2f
            val top = slotY - pc.h * trayCell / 2f
            for (cl in pc.cells) {
                drawBlock(c, left + cl[1] * trayCell, top + cl[0] * trayCell, trayCell, pc.color, 255)
            }
        }
    }

    private fun drawDrag(c: Canvas) {
        if (dragIdx < 0) return
        val pc = tray[dragIdx] ?: return
        val left = fx - pc.w * cell / 2f
        val top = fy - pc.h * cell / 2f - cell * 1.8f
        for (cl in pc.cells) {
            drawBlock(c, left + cl[1] * cell, top + cl[0] * cell, cell, pc.color, 255)
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
