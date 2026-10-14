package com.akay.block

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.view.animation.OvershootInterpolator
import java.util.Random

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

// settings icons: kind 0 = speaker (sound), kind 2 = phone (vibrate). A red slash is drawn when off.
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
            p.style = Paint.Style.STROKE
            p.strokeWidth = 0.07f * s
            p.strokeCap = Paint.Cap.ROUND
            rf.set(ox + 0.36f * s, oy + 0.2f * s, ox + 0.64f * s, oy + 0.8f * s)
            c.drawRoundRect(rf, 0.06f * s, 0.06f * s, p)
            if (!off) {
                c.drawLine(ox + 0.24f * s, oy + 0.36f * s, ox + 0.24f * s, oy + 0.64f * s, p)
                c.drawLine(ox + 0.76f * s, oy + 0.36f * s, ox + 0.76f * s, oy + 0.64f * s, p)
                c.drawLine(ox + 0.13f * s, oy + 0.42f * s, ox + 0.13f * s, oy + 0.58f * s, p)
                c.drawLine(ox + 0.87f * s, oy + 0.42f * s, ox + 0.87f * s, oy + 0.58f * s, p)
            }
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

// three stars that pop in one after the other (only the earned ones light up)
class StarsView(ctx: Context, private val count: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val ovs = OvershootInterpolator(2.2f)
    private var t = 0f
    private var anim: ValueAnimator? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim?.cancel()
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 1400
        a.startDelay = 250
        a.addUpdateListener {
            t = it.animatedValue as Float
            invalidate()
        }
        a.start()
        anim = a
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    private fun star(c: Canvas, cx: Float, cy: Float, r: Float, fill: Int, edge: Int) {
        path.reset()
        for (i in 0 until 10) {
            val rad = if (i % 2 == 0) r else r * 0.46f
            val a = Math.toRadians(-90.0 + i * 36.0)
            val x = cx + (rad * Math.cos(a)).toFloat()
            val y = cy + (rad * Math.sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        p.style = Paint.Style.FILL
        p.color = fill
        c.drawPath(path, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.12f
        p.strokeJoin = Paint.Join.ROUND
        p.color = edge
        c.drawPath(path, p)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val base = minOf(w * 0.17f, h * 0.38f)
        val xs = floatArrayOf(w * 0.2f, w * 0.5f, w * 0.8f)
        val ys = floatArrayOf(h * 0.62f, h * 0.46f, h * 0.62f)
        val sz = floatArrayOf(base * 0.9f, base * 1.2f, base * 0.9f)
        for (i in 0..2) {
            star(c, xs[i], ys[i], sz[i], 0xFF4A3F8F.toInt(), 0xFF3A3178.toInt())
            if (i < count) {
                val pr = (t * 3.2f - i).coerceIn(0f, 1f)
                if (pr > 0f) {
                    val sc = ovs.getInterpolation(pr)
                    star(c, xs[i], ys[i], sz[i] * sc, 0xFFFFC21A.toInt(), 0xFFE39B00.toInt())
                }
            }
        }
    }
}

// colourful pieces falling on the win screen
class ConfettiView(ctx: Context) : View(ctx) {
    private class Bit(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var rot: Float, var vr: Float, val color: Int, val sz: Float
    )

    private val bits = ArrayList<Bit>()
    private val rnd = Random()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rf = RectF()
    private val den = ctx.resources.displayMetrics.density
    private val cols = intArrayOf(
        0xFFFFC21A.toInt(), 0xFFFF5A5F.toInt(), 0xFF3EA6FF.toInt(),
        0xFF3DDC84.toInt(), 0xFFB36BFF.toInt(), 0xFFFF9A3C.toInt()
    )
    private var last = 0L
    private var elapsed = 0f

    private fun fresh(w: Float, spread: Float): Bit = Bit(
        rnd.nextFloat() * w, -rnd.nextFloat() * spread - 10f * den,
        (rnd.nextFloat() - 0.5f) * 60f * den, (160f + rnd.nextFloat() * 220f) * den,
        rnd.nextFloat() * 360f, (rnd.nextFloat() - 0.5f) * 500f,
        cols[rnd.nextInt(cols.size)], (5f + rnd.nextFloat() * 6f) * den
    )

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        bits.clear()
        for (i in 0 until 70) bits.add(fresh(w.toFloat(), h * 0.6f))
        last = 0L
        elapsed = 0f
    }

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime()
        val dt = if (last == 0L) 0f else minOf(0.05f, (now - last) / 1e9f)
        last = now
        elapsed += dt
        var alive = false
        p.style = Paint.Style.FILL
        for (i in bits.indices) {
            var b = bits[i]
            b.x += b.vx * dt
            b.y += b.vy * dt
            b.rot += b.vr * dt
            if (b.y > height + 20f * den) {
                if (elapsed < 3.2f) {
                    b = fresh(width.toFloat(), 200f * den)
                    bits[i] = b
                } else {
                    continue
                }
            }
            alive = true
            if (b.y < -10f * den) continue
            c.save()
            c.translate(b.x, b.y)
            c.rotate(b.rot)
            p.color = b.color
            rf.set(-b.sz / 2f, -b.sz * 0.3f, b.sz / 2f, b.sz * 0.3f)
            c.drawRect(rf, p)
            c.restore()
        }
        if (alive) postInvalidateOnAnimation()
    }
}

// score / goal bar shown on the lose screen
class GoalBarView(ctx: Context, private val score: Int, private val goal: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rf = RectF()
    private val den = ctx.resources.displayMetrics.density

    init {
        tp.typeface = Typeface.DEFAULT_BOLD
        tp.textAlign = Paint.Align.CENTER
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        tp.color = Color.WHITE
        tp.textSize = 24f * den
        c.drawText("$score / $goal", w / 2f, h * 0.38f, tp)
        val left = 10f * den
        val right = w - 10f * den
        val top = h * 0.55f
        val bar = 18f * den
        p.style = Paint.Style.FILL
        p.color = 0xFF1D2240.toInt()
        rf.set(left, top, right, top + bar)
        c.drawRoundRect(rf, bar / 2f, bar / 2f, p)
        val frac = if (goal <= 0) 0f else (score.toFloat() / goal).coerceIn(0f, 1f)
        if (frac > 0.01f) {
            p.color = 0xFF3E9BFF.toInt()
            rf.set(left, top, left + (right - left) * frac, top + bar)
            c.drawRoundRect(rf, bar / 2f, bar / 2f, p)
        }
    }
}
