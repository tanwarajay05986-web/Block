package com.akay.block

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

// "BLOCK" title for the home screen
class LogoView(ctx: Context) : View(ctx) {
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val word = "BLOCK"
    private val cols = intArrayOf(
        0xFFF5A31A.toInt(), 0xFF2EA8F5.toInt(), 0xFFE53935.toInt(),
        0xFFFFD21F.toInt(), 0xFF9B5DE5.toInt()
    )

    init {
        fillP.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        strokeP.typeface = fillP.typeface
        strokeP.style = Paint.Style.STROKE
        strokeP.strokeJoin = Paint.Join.ROUND
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        setMeasuredDimension(w, (w * 0.62f).toInt())
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        var size = w * 0.3f
        fillP.textSize = size
        var total = 0f
        for (ch in word) total += fillP.measureText(ch.toString())
        size *= (w * 0.84f) / total
        fillP.textSize = size
        strokeP.textSize = size
        strokeP.strokeWidth = size * 0.16f
        total = 0f
        for (ch in word) total += fillP.measureText(ch.toString())
        var x = (w - total) / 2f
        val y = height * 0.55f
        for (i in word.indices) {
            val s = word[i].toString()
            val wc = fillP.measureText(s)
            val dy = if (i % 2 == 0) 0f else -size * 0.05f
            strokeP.color = 0xFF0A1A55.toInt()
            c.drawText(s, x, y + dy + size * 0.07f, strokeP)
            strokeP.color = 0xFF16308F.toInt()
            c.drawText(s, x, y + dy, strokeP)
            fillP.color = cols[i]
            c.drawText(s, x, y + dy, fillP)
            x += wc
        }
        val sub = "PUZZLE"
        val ss = size * 0.42f
        fillP.textSize = ss
        strokeP.textSize = ss
        strokeP.strokeWidth = ss * 0.2f
        val sw = fillP.measureText(sub)
        val sx = (w - sw) / 2f
        val sy = y + size * 0.62f
        strokeP.color = 0xFF16308F.toInt()
        c.drawText(sub, sx, sy, strokeP)
        fillP.color = 0xFF19D3FF.toInt()
        c.drawText(sub, sx, sy, fillP)
    }
}

// small icons used on buttons: 0 = map pin, 1 = infinity, 2 = play
class IconView(ctx: Context, private val kind: Int, private val hole: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rf = RectF()
    private val path = Path()

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat()
        val ox = (width - s) / 2f
        val oy = (height - s) / 2f
        p.color = Color.WHITE
        if (kind == 0) {
            p.style = Paint.Style.FILL
            c.drawCircle(ox + 0.5f * s, oy + 0.38f * s, 0.26f * s, p)
            path.reset()
            path.moveTo(ox + 0.27f * s, oy + 0.5f * s)
            path.lineTo(ox + 0.73f * s, oy + 0.5f * s)
            path.lineTo(ox + 0.5f * s, oy + 0.92f * s)
            path.close()
            c.drawPath(path, p)
            p.color = hole
            c.drawCircle(ox + 0.5f * s, oy + 0.38f * s, 0.1f * s, p)
        } else if (kind == 1) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 0.11f * s
            rf.set(ox + 0.06f * s, oy + 0.3f * s, ox + 0.54f * s, oy + 0.7f * s)
            c.drawOval(rf, p)
            rf.set(ox + 0.46f * s, oy + 0.3f * s, ox + 0.94f * s, oy + 0.7f * s)
            c.drawOval(rf, p)
        } else {
            p.style = Paint.Style.FILL
            path.reset()
            path.moveTo(ox + 0.3f * s, oy + 0.2f * s)
            path.lineTo(ox + 0.82f * s, oy + 0.5f * s)
            path.lineTo(ox + 0.3f * s, oy + 0.8f * s)
            path.close()
            c.drawPath(path, p)
        }
    }
}

class CrownView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        p.style = Paint.Style.FILL
        p.color = 0xFFFFC21A.toInt()
        drawCrown(c, width / 2f, height / 2f, width.toFloat(), p)
    }
}

// settings icons: kind 0 = speaker (sound), kind 1 = music note. A red slash is drawn when off.
class ToggleIcon(ctx: Context, private val kind: Int) : View(ctx) {
    var off = false
        set(v) {
            field = v
            invalidate()
        }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rf = RectF()
    private val path = Path()

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat()
        val ox = (width - s) / 2f
        val oy = (height - s) / 2f
        p.color = Color.WHITE
        p.style = Paint.Style.FILL
        if (kind == 0) {
            rf.set(ox + 0.1f * s, oy + 0.38f * s, ox + 0.28f * s, oy + 0.62f * s)
            c.drawRoundRect(rf, 0.03f * s, 0.03f * s, p)
            path.reset()
            path.moveTo(ox + 0.28f * s, oy + 0.38f * s)
            path.lineTo(ox + 0.52f * s, oy + 0.18f * s)
            path.lineTo(ox + 0.52f * s, oy + 0.82f * s)
            path.lineTo(ox + 0.28f * s, oy + 0.62f * s)
            path.close()
            c.drawPath(path, p)
            if (!off) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 0.07f * s
                p.strokeCap = Paint.Cap.ROUND
                rf.set(ox + 0.33f * s, oy + 0.33f * s, ox + 0.67f * s, oy + 0.67f * s)
                c.drawArc(rf, -50f, 100f, false, p)
                rf.set(ox + 0.2f * s, oy + 0.2f * s, ox + 0.8f * s, oy + 0.8f * s)
                c.drawArc(rf, -50f, 100f, false, p)
            }
        } else {
            c.drawCircle(ox + 0.36f * s, oy + 0.74f * s, 0.14f * s, p)
            rf.set(ox + 0.44f * s, oy + 0.2f * s, ox + 0.52f * s, oy + 0.74f * s)
            c.drawRect(rf, p)
            path.reset()
            path.moveTo(ox + 0.52f * s, oy + 0.2f * s)
            path.lineTo(ox + 0.8f * s, oy + 0.3f * s)
            path.lineTo(ox + 0.8f * s, oy + 0.45f * s)
            path.lineTo(ox + 0.52f * s, oy + 0.35f * s)
            path.close()
            c.drawPath(path, p)
        }
        if (off) {
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = 0.09f * s
            p.color = 0xFFFF3B30.toInt()
            c.drawLine(ox + 0.15f * s, oy + 0.85f * s, ox + 0.85f * s, oy + 0.15f * s, p)
        }
    }
}
