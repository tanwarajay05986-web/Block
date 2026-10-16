package com.akay.block

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.VibrationEffect
import android.os.Vibrator
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

// Sound effects are made here in code (no audio files). Vibration also lives here.
object Sfx {
    private const val RATE = 22050

    private var started = false
    private var pool: SoundPool? = null
    private val ids = ConcurrentHashMap<String, Int>()
    private val ready: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private var vib: Vibrator? = null

    var soundOn = true
        private set
    var vibeOn = true
        private set

    // vibration patterns: off, on, off, on ... (milliseconds)
    private val CLEAR_V = arrayOf(
        longArrayOf(0, 45),
        longArrayOf(0, 45, 50, 45),
        longArrayOf(0, 50, 50, 50, 50, 60),
        longArrayOf(0, 60, 50, 60, 50, 60, 50, 80),
        longArrayOf(0, 80, 60, 80, 60, 80, 60, 140)
    )

    fun init(ctx: Context) {
        if (started) return
        started = true
        soundOn = Store.soundOn()
        vibeOn = Store.vibeOn()
        val app = ctx.applicationContext
        try {
            vib = app.getSystemService(Vibrator::class.java)
        } catch (e: Throwable) {
        }
        val dir = app.cacheDir
        Thread {
            try {
                buildSounds(dir)
            } catch (e: Throwable) {
            }
        }.start()
    }

    // ---------- sounds ----------

    fun play(name: String, vol: Float = 1f) {
        if (!soundOn) return
        val sp = pool ?: return
        val id = ids[name] ?: return
        if (!ready.contains(id)) return
        sp.play(id, vol, vol, 1, 0, 1f)
    }

    fun tap() = play("tap", 0.8f)
    fun place() = play("place", 1f)
    fun back() = play("back", 0.7f)
    fun over() = play("over", 0.9f)
    fun best() = play("best", 0.9f)

    // 1 line = combo chime, 2 lines = great, 3 lines = excellent, 4+ lines or empty board = perfect
    fun clear(combo: Int, lines: Int, perfect: Boolean) {
        val name = when {
            perfect || lines >= 4 -> "perfect"
            lines == 3 -> "excellent"
            lines == 2 -> "great"
            else -> "clear" + combo.coerceIn(1, 3)
        }
        play(name, 1f)
    }

    fun setSound(on: Boolean) {
        soundOn = on
        Store.setSoundOn(on)
    }

    // ---------- vibration ----------

    fun setVibe(on: Boolean) {
        vibeOn = on
        Store.setVibeOn(on)
    }

    @Suppress("DEPRECATION")
    private fun buzz(timings: LongArray) {
        if (!vibeOn) return
        val v = vib ?: return
        try {
            if (!v.hasVibrator()) return
            val effect = VibrationEffect.createWaveform(timings, -1)
            try {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                v.vibrate(effect, attrs)
            } catch (e: Throwable) {
                v.vibrate(effect)
            }
        } catch (e: Throwable) {
        }
    }

    fun vPick() = buzz(longArrayOf(0, 15))
    fun vPlace() = buzz(longArrayOf(0, 30))
    fun vBack() = buzz(longArrayOf(0, 18))
    fun vLose() = buzz(longArrayOf(0, 200, 100, 350))
    fun vWin() = buzz(longArrayOf(0, 60, 60, 60, 60, 60, 60, 220))

    fun vClear(combo: Int, lines: Int, perfect: Boolean) {
        val level = when {
            perfect || lines >= 4 -> 5
            lines == 3 -> 4
            lines == 2 -> 3
            else -> combo.coerceIn(1, 2)
        }
        buzz(CLEAR_V[level - 1])
    }

    // ---------- building the sounds ----------

    private fun buildSounds(dir: File) {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val sp = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attrs).build()
        sp.setOnLoadCompleteListener { _, id, status ->
            if (status == 0) ready.add(id)
        }
        val all = LinkedHashMap<String, ShortArray>()
        all["tap"] = toShorts(makeTap(), 0.9f)
        all["place"] = toShorts(makePlace(), 0.9f)
        all["back"] = toShorts(makeBack(), 0.9f)
        for (lv in 1..3) all["clear$lv"] = toShorts(makeClear(lv), 0.9f)
        all["great"] = toShorts(makeGreat(), 0.9f)
        all["excellent"] = toShorts(makeExcellent(), 0.9f)
        all["perfect"] = toShorts(makePerfect(), 0.9f)
        all["over"] = toShorts(makeOver(), 0.9f)
        all["best"] = toShorts(makeBest(), 0.9f)
        for ((name, data) in all) {
            val f = File(dir, "blk_$name.wav")
            writeWav(f, data)
            ids[name] = sp.load(f.absolutePath, 1)
        }
        pool = sp
    }

    // a soft bell note
    private fun note(b: FloatArray, start: Double, f: Double, dur: Double, vol: Double) {
        val s0 = (start * RATE).toInt()
        val n = (dur * RATE).toInt()
        val att = 0.004 * RATE
        val rel = 0.012 * RATE
        val tau = dur * 0.33
        for (i in 0 until n) {
            val pos = s0 + i
            if (pos >= b.size) break
            val t = i / RATE.toDouble()
            var env = Math.exp(-t / tau)
            if (i < att) env *= i / att
            if (i > n - rel) env *= (n - i) / rel
            val ph = 2.0 * Math.PI * f * t
            val w = Math.sin(ph) + 0.25 * Math.sin(2.0 * ph) + 0.08 * Math.sin(3.0 * ph)
            b[pos] += (w * env * vol).toFloat()
        }
    }

    // a quick falling or rising "pop"
    private fun sweep(b: FloatArray, start: Double, f0: Double, f1: Double, dur: Double, vol: Double) {
        val s0 = (start * RATE).toInt()
        val n = (dur * RATE).toInt()
        var ph = 0.0
        for (i in 0 until n) {
            val pos = s0 + i
            if (pos >= b.size) break
            val t = i / RATE.toDouble()
            val f = f0 + (f1 - f0) * (t / dur)
            ph += 2.0 * Math.PI * f / RATE
            var env = Math.exp(-t / (dur * 0.3))
            if (i < 40) env *= i / 40.0
            if (i > n - 100) env *= (n - i) / 100.0
            b[pos] += (Math.sin(ph) * env * vol).toFloat()
        }
    }

    private fun toShorts(b: FloatArray, target: Float): ShortArray {
        var peak = 0f
        for (v in b) {
            val a = Math.abs(v)
            if (a > peak) peak = a
        }
        val k = if (peak <= target) 1f else target / peak
        return ShortArray(b.size) {
            (b[it] * k * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private fun writeWav(f: File, d: ShortArray) {
        val bytes = d.size * 2
        val bb = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray())
        bb.putInt(36 + bytes)
        bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray())
        bb.putInt(16)
        bb.putShort(1)
        bb.putShort(1)
        bb.putInt(RATE)
        bb.putInt(RATE * 2)
        bb.putShort(2)
        bb.putShort(16)
        bb.put("data".toByteArray())
        bb.putInt(bytes)
        for (s in d) bb.putShort(s)
        f.writeBytes(bb.array())
    }

    private fun makeTap(): FloatArray {
        val b = FloatArray((0.1 * RATE).toInt())
        sweep(b, 0.0, 900.0, 600.0, 0.07, 0.6)
        return b
    }

    private fun makePlace(): FloatArray {
        val b = FloatArray((0.2 * RATE).toInt())
        sweep(b, 0.0, 380.0, 190.0, 0.12, 0.9)
        sweep(b, 0.0, 1400.0, 900.0, 0.025, 0.25)
        return b
    }

    private fun makeBack(): FloatArray {
        val b = FloatArray((0.12 * RATE).toInt())
        sweep(b, 0.0, 520.0, 300.0, 0.08, 0.5)
        return b
    }

    // 1 line (combo): a short rising chime, level 1..3 for longer combo chains
    private fun makeClear(level: Int): FloatArray {
        val scale = doubleArrayOf(523.25, 587.33, 659.25, 783.99, 880.0, 1046.5, 1174.66, 1318.5)
        val count = level + 2
        val gap = 0.075 - level * 0.004
        val total = count * gap + 0.6
        val b = FloatArray((total * RATE).toInt())
        sweep(b, 0.0, 220.0, 90.0, 0.12, 0.5)
        for (i in 0 until count) note(b, i * gap, scale[i], 0.45, 0.45)
        return b
    }

    // 2 lines: bright 4-note run with a sparkle on top
    private fun makeGreat(): FloatArray {
        val b = FloatArray((1.2 * RATE).toInt())
        sweep(b, 0.0, 260.0, 110.0, 0.12, 0.55)
        val f = doubleArrayOf(523.25, 659.25, 783.99, 1046.5)
        for (i in f.indices) note(b, i * 0.07, f[i], 0.55, 0.45)
        note(b, 0.30, 1318.5, 0.5, 0.25)
        note(b, 0.36, 1568.0, 0.5, 0.2)
        return b
    }

    // 3 lines: 5-note run that ends on a full chord
    private fun makeExcellent(): FloatArray {
        val b = FloatArray((1.6 * RATE).toInt())
        sweep(b, 0.0, 240.0, 90.0, 0.14, 0.6)
        val f = doubleArrayOf(523.25, 659.25, 783.99, 1046.5, 1318.5)
        for (i in f.indices) note(b, i * 0.065, f[i], 0.6, 0.42)
        note(b, 0.36, 1046.5, 0.8, 0.3)
        note(b, 0.36, 1318.5, 0.8, 0.28)
        note(b, 0.36, 1568.0, 0.8, 0.25)
        return b
    }

    // 4+ lines or empty board: fanfare with a long shining chord
    private fun makePerfect(): FloatArray {
        val b = FloatArray((2.0 * RATE).toInt())
        sweep(b, 0.0, 220.0, 80.0, 0.16, 0.65)
        val f = doubleArrayOf(783.99, 1046.5, 1318.5, 1568.0, 2093.0)
        for (i in f.indices) note(b, i * 0.09, f[i], 0.7, 0.4)
        note(b, 0.5, 1046.5, 1.1, 0.28)
        note(b, 0.5, 1318.5, 1.1, 0.28)
        note(b, 0.5, 1568.0, 1.1, 0.26)
        note(b, 0.5, 2093.0, 1.1, 0.22)
        for (k in 0 until 6) note(b, 0.55 + k * 0.08, 2093.0 + k * 220.0, 0.25, 0.12)
        return b
    }

    private fun makeOver(): FloatArray {
        val b = FloatArray((1.5 * RATE).toInt())
        val notes = doubleArrayOf(392.0, 329.63, 261.63, 196.0)
        for (i in notes.indices) note(b, i * 0.2, notes[i], 0.55, 0.5)
        return b
    }

    private fun makeBest(): FloatArray {
        val b = FloatArray((1.5 * RATE).toInt())
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.5)
        for (i in notes.indices) note(b, i * 0.13, notes[i], 0.6, 0.45)
        note(b, 0.55, 659.25, 0.7, 0.3)
        note(b, 0.55, 783.99, 0.7, 0.3)
        return b
    }
}
