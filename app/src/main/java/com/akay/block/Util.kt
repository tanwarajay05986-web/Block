package com.akay.block

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.widget.Toast

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.toast(m: String) {
    Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}

fun mixColor(a: Int, b: Int, t: Float): Int {
    val r = (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt()
    val g = (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt()
    val bl = (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
    return Color.rgb(r, g, bl)
}

fun withAlpha(c: Int, a: Int): Int = (a.coerceIn(0, 255) shl 24) or (c and 0x00FFFFFF)

object Store {
    private var sp: SharedPreferences? = null

    fun init(c: Context) {
        if (sp == null) {
            sp = c.applicationContext.getSharedPreferences("block", Context.MODE_PRIVATE)
            // older versions kept only the current level: that level counts as unlocked
            if (!sp!!.contains("adv_max")) {
                sp!!.edit().putInt("adv_max", sp!!.getInt("adv_level", 1)).apply()
            }
        }
    }

    fun classicBest(): Int = sp!!.getInt("classic_best", 0)

    fun setClassicBest(v: Int) {
        sp!!.edit().putInt("classic_best", v).apply()
    }

    // saved Classic game (so it can be continued later)
    fun saveGame(s: String) {
        sp!!.edit().putString("classic_save", s).apply()
    }

    fun loadGame(): String? = sp!!.getString("classic_save", null)

    fun clearGame() {
        sp!!.edit().remove("classic_save").apply()
    }

    // Adventure: highest unlocked level (a level unlocks only when the one before it is cleared)
    fun advMax(): Int = sp!!.getInt("adv_max", 1).coerceIn(1, Levels.COUNT)

    fun setAdvMax(v: Int) {
        sp!!.edit().putInt("adv_max", v.coerceIn(1, Levels.COUNT)).apply()
    }

    // Adventure: the level that is selected on the map (never above the unlocked level)
    fun advLevel(): Int = minOf(sp!!.getInt("adv_level", 1), advMax()).coerceAtLeast(1)

    fun setAdvLevel(v: Int) {
        sp!!.edit().putInt("adv_level", v).apply()
    }

    fun saveAdv(s: String) {
        sp!!.edit().putString("adv_save", s).apply()
    }

    fun loadAdv(): String? = sp!!.getString("adv_save", null)

    fun clearAdv() {
        sp!!.edit().remove("adv_save").apply()
    }

    // sound and vibration settings
    fun soundOn(): Boolean = sp!!.getBoolean("sound_on", true)

    fun setSoundOn(v: Boolean) {
        sp!!.edit().putBoolean("sound_on", v).apply()
    }

    fun vibeOn(): Boolean = sp!!.getBoolean("vibe_on", true)

    fun setVibeOn(v: Boolean) {
        sp!!.edit().putBoolean("vibe_on", v).apply()
    }
}

// simple crown drawn with the colour already set on the paint
fun drawCrown(c: Canvas, cx: Float, cy: Float, w: Float, p: Paint) {
    val h = w * 0.72f
    val l = cx - w / 2f
    val t = cy - h / 2f
    val base = p.color
    val path = Path()
    path.moveTo(l + w * 0.08f, t + h * 0.95f)
    path.lineTo(l + w * 0.02f, t + h * 0.25f)
    path.lineTo(l + w * 0.30f, t + h * 0.58f)
    path.lineTo(l + w * 0.50f, t + h * 0.12f)
    path.lineTo(l + w * 0.70f, t + h * 0.58f)
    path.lineTo(l + w * 0.98f, t + h * 0.25f)
    path.lineTo(l + w * 0.92f, t + h * 0.95f)
    path.close()
    c.drawPath(path, p)
    c.drawCircle(l + w * 0.02f, t + h * 0.22f, w * 0.07f, p)
    c.drawCircle(l + w * 0.50f, t + h * 0.08f, w * 0.07f, p)
    c.drawCircle(l + w * 0.98f, t + h * 0.22f, w * 0.07f, p)
    p.color = mixColor(base, Color.BLACK, 0.18f)
    c.drawRect(l + w * 0.09f, t + h * 0.78f, l + w * 0.91f, t + h * 0.95f, p)
    p.color = base
}
