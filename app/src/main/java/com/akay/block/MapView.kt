package com.akay.block

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.hypot

// Adventure level map: one chapter (100 levels) at a time, level 1 at the bottom
class MapView(ctx: Context) : View(ctx) {

    interface Listener {
        fun onPick(level: Int)
        fun onLocked(level: Int)
        fun onChapter()
    }

    var listener: Listener? = null
    var maxLevel = 1
    var selected = 1

    private val PER = 100
    private val COLS = 8
    private val chapters = (Levels.COUNT + PER - 1) / PER
    private val den = ctx.resources.displayMetrics.density
    private val pad = 14f * den
    private val headH = 235f * den
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var chapter = 0
    private var cell = 0f
    private var downX = 0f
    private var downY = 0f

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rf = RectF()
    private val path = Path()

    init {
        tp.typeface = Typeface.DEFAULT_BOLD
        tp.textAlign = Paint.Align.CENTER
    }

    private fun firstLevel(): Int = chapter * PER + 1

    private fun countIn(): Int = minOf(PER, Levels.COUNT - chapter * PER)

    private fun rows(): Int = (countIn() + COLS - 1) / COLS

    fun showChapterOf(level: Int) {
        chapter = ((level - 1) / PER).coerceIn(0, chapters - 1)
        requestLayout()
        invalidate()
    }

    // y position (inside this view) of the middle of a level cell, or -1 if it is in another chapter
    fun yOf(level: Int): Int {
        val i = level - firstLevel()
        if (i < 0 || i >= countIn()) return -1
        val row = i / COLS
        return (headH + (rows() - 1 - row) * cell + cell / 2f).toInt()
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        cell = (w - 2f * pad) / COLS
        val h = (headH + rows() * cell + 24f * den).toInt()
        setMeasuredDimension(w, h)
    }

    // ---------- touch ----------

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (abs(e.x - downX) < slop && abs(e.y - downY) < slop) tap(e.x, e.y)
                return true
            }
        }
        return true
    }

    private fun changed() {
        requestLayout()
        invalidate()
        listener?.onChapter()
    }

    private fun tap(x: Float, y: Float) {
        val w = width.toFloat()
        if (y < headH) {
            val ty = 70f * den
            if (hypot(x - w * 0.18f, y - ty) < 38f * den && chapter > 0) {
                chapter--
                changed()
            } else if (hypot(x - w * 0.82f, y - ty) < 38f * den && chapter < chapters - 1) {
                chapter++
                changed()
            }
            return
        }
        val col = Math.floor(((x - pad) / cell).toDouble()).toInt()
        val rowTop = Math.floor(((y - headH) / cell).toDouble()).toInt()
        val row = rows() - 1 - rowTop
        if (col < 0 || col >= COLS || row < 0 || row >= rows()) return
        val i = row * COLS + col
        if (i >= countIn()) return
        val lv = firstLevel() + i
        if (lv > maxLevel) listener?.onLocked(lv) else listener?.onPick(lv)
    }

    // ---------- drawing ----------

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val first = firstLevel()
        val count = countIn()
        val last = first + count - 1
        val gold = maxLevel > last

        drawTrophy(c, w / 2f, 70f * den, 90f * den, gold)
        chevron(c, w * 0.18f, 70f * den, -1, chapter > 0)
        chevron(c, w * 0.82f, 70f * den, 1, chapter < chapters - 1)

        tp.style = Paint.Style.FILL
        tp.textAlign = Paint.Align.CENTER
        tp.color = Color.WHITE
        tp.textSize = 20f * den
        c.drawText("Chapter ${chapter + 1}", w / 2f, 150f * den, tp)
        tp.color = 0xFFBFD6FF.toInt()
        tp.textSize = 14f * den
        c.drawText("Levels $first - $last", w / 2f, 176f * den, tp)
        val done = (maxLevel - first).coerceIn(0, count)
        tp.textSize = 13f * den
        tp.color = 0xAAFFFFFF.toInt()
        c.drawText("$done / $count cleared", w / 2f, 200f * den, tp)

        val rowsN = rows()
        val inset = 2f * den
        for (i in 0 until count) {
            val lv = first + i
            val row = i / COLS
            val col = i % COLS
            val x = pad + col * cell
            val y = headH + (rowsN - 1 - row) * cell
            val locked = lv > maxLevel
            val cur = lv == maxLevel
            val face: Int
            val edge: Int
            val txt: Int
            when {
                locked -> {
                    face = 0xFF2B3768.toInt(); edge = 0xFF222C58.toInt(); txt = 0xFF6F7DB5.toInt()
                }
                cur -> {
                    face = 0xFFFFC21A.toInt(); edge = 0xFFD08A00.toInt(); txt = 0xFF7A4A00.toInt()
                }
                else -> {
                    face = 0xFF3E7BFA.toInt(); edge = 0xFF2A57C4.toInt(); txt = Color.WHITE
                }
            }
            p.style = Paint.Style.FILL
            p.color = edge
            rf.set(x + inset, y + inset + 2f * den, x + cell - inset, y + cell - inset + 2f * den)
            c.drawRoundRect(rf, 7f * den, 7f * den, p)
            p.color = face
            rf.set(x + inset, y + inset, x + cell - inset, y + cell - inset - 1f * den)
            c.drawRoundRect(rf, 7f * den, 7f * den, p)

            if (lv == selected) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 2.5f * den
                p.color = Color.WHITE
                rf.set(x + inset, y + inset, x + cell - inset, y + cell - inset - 1f * den)
                c.drawRoundRect(rf, 7f * den, 7f * den, p)
            }

            val digits = lv.toString().length
            tp.style = Paint.Style.FILL
            tp.textAlign = Paint.Align.CENTER
            tp.color = txt
            tp.textSize = cell * (if (digits >= 4) 0.27f else if (digits == 3) 0.31f else 0.38f)
            c.drawText(lv.toString(), x + cell / 2f, y + cell / 2f + tp.textSize * 0.33f, tp)
        }
    }

    private fun chevron(c: Canvas, cx: Float, cy: Float, dir: Int, on: Boolean) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4.5f * den
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.color = if (on) 0xFFBFD6FF.toInt() else 0x44BFD6FF
        val a = 11f * den
        path.reset()
        path.moveTo(cx - dir * a / 2f, cy - a)
        path.lineTo(cx + dir * a / 2f, cy)
        path.lineTo(cx - dir * a / 2f, cy + a)
        c.drawPath(path, p)
    }

    private fun drawTrophy(c: Canvas, cx: Float, cy: Float, s: Float, gold: Boolean) {
        val body = if (gold) 0xFFFFC21A.toInt() else 0xFF1B2650.toInt()
        p.style = Paint.Style.FILL
        p.color = body
        path.reset()
        path.moveTo(cx - 0.3f * s, cy - 0.45f * s)
        path.lineTo(cx + 0.3f * s, cy - 0.45f * s)
        path.lineTo(cx + 0.3f * s, cy - 0.1f * s)
        path.quadTo(cx + 0.3f * s, cy + 0.22f * s, cx, cy + 0.22f * s)
        path.quadTo(cx - 0.3f * s, cy + 0.22f * s, cx - 0.3f * s, cy - 0.1f * s)
        path.close()
        c.drawPath(path, p)
        rf.set(cx - 0.06f * s, cy + 0.2f * s, cx + 0.06f * s, cy + 0.38f * s)
        c.drawRect(rf, p)
        rf.set(cx - 0.22f * s, cy + 0.38f * s, cx + 0.22f * s, cy + 0.48f * s)
        c.drawRoundRect(rf, 0.03f * s, 0.03f * s, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.07f * s
        p.strokeCap = Paint.Cap.ROUND
        rf.set(cx - 0.52f * s, cy - 0.4f * s, cx - 0.2f * s, cy - 0.02f * s)
        c.drawArc(rf, 90f, 180f, false, p)
        rf.set(cx + 0.2f * s, cy - 0.4f * s, cx + 0.52f * s, cy - 0.02f * s)
        c.drawArc(rf, 270f, 180f, false, p)
        tp.style = Paint.Style.FILL
        tp.textAlign = Paint.Align.CENTER
        tp.textSize = 0.34f * s
        tp.color = if (gold) 0xFFB36B00.toInt() else 0xFF3A4A8C.toInt()
        c.drawText("\u2605", cx, cy - 0.04f * s, tp)
    }
}

// back arrow for the top bar of the map screen
class BackView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat()
        val ox = (width - s) / 2f
        val oy = (height - s) / 2f
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.09f * s
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.color = 0xFFE6EEFF.toInt()
        path.reset()
        path.moveTo(ox + 0.6f * s, oy + 0.25f * s)
        path.lineTo(ox + 0.35f * s, oy + 0.5f * s)
        path.lineTo(ox + 0.6f * s, oy + 0.75f * s)
        c.drawPath(path, p)
    }
}
