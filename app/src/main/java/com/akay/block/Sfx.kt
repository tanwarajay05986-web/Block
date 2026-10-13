package com.akay.block

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

// All sounds and the music are made here in code (no audio files, no copyright issues).
object Sfx {
    private const val RATE = 22050

    private var started = false
    private var pool: SoundPool? = null
    private val ids = ConcurrentHashMap<String, Int>()
    private val ready: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private var music: AudioTrack? = null

    @Volatile
    private var musicWanted = false

    var soundOn = true
        private set
    var musicOn = true
        private set

    fun init(ctx: Context) {
        if (started) return
        started = true
        soundOn = Store.soundOn()
        musicOn = Store.musicOn()
        val dir = ctx.cacheDir
        Thread {
            try {
                buildSounds(dir)
            } catch (e: Throwable) {
            }
            try {
                buildMusic()
            } catch (e: Throwable) {
            }
        }.start()
    }

    // ---------- playing ----------

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

    fun clear(combo: Int, lines: Int, perfect: Boolean) {
        val level = if (perfect) 5 else (combo + lines - 1).coerceIn(1, 5)
        play("clear$level", 1f)
    }

    fun setSound(on: Boolean) {
        soundOn = on
        Store.setSoundOn(on)
    }

    fun setMusic(on: Boolean) {
        musicOn = on
        Store.setMusicOn(on)
        syncMusic()
    }

    fun resumeMusic() {
        musicWanted = true
        syncMusic()
    }

    fun pauseMusic() {
        musicWanted = false
        syncMusic()
    }

    @Synchronized
    private fun syncMusic() {
        val t = music ?: return
        try {
            if (musicOn && musicWanted) {
                if (t.playState != AudioTrack.PLAYSTATE_PLAYING) t.play()
            } else if (t.playState == AudioTrack.PLAYSTATE_PLAYING) {
                t.pause()
            }
        } catch (e: Throwable) {
        }
    }

    // ---------- building ----------

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
        all["tap"] = toShorts(makeTap(), 0.9f, false)
        all["place"] = toShorts(makePlace(), 0.9f, false)
        all["back"] = toShorts(makeBack(), 0.9f, false)
        for (lv in 1..5) all["clear$lv"] = toShorts(makeClear(lv), 0.9f, false)
        all["over"] = toShorts(makeOver(), 0.9f, false)
        all["best"] = toShorts(makeBest(), 0.9f, false)
        for ((name, data) in all) {
            val f = File(dir, "blk_$name.wav")
            writeWav(f, data)
            ids[name] = sp.load(f.absolutePath, 1)
        }
        pool = sp
    }

    private fun buildMusic() {
        val data = makeMusic()
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val fmt = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val t = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(fmt)
            .setBufferSizeInBytes(data.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(data, 0, data.size)
        t.setLoopPoints(0, data.size, -1)
        t.setVolume(0.35f)
        synchronized(this) {
            music = t
        }
        syncMusic()
    }

    // ---------- synthesis helpers ----------

    // a soft bell / pluck note (kind 0) or a plain sine (kind 2)
    private fun note(
        b: FloatArray, start: Double, f: Double, dur: Double, vol: Double, kind: Int, wrap: Boolean
    ) {
        val s0 = (start * RATE).toInt()
        val n = (dur * RATE).toInt()
        val att = 0.004 * RATE
        val rel = 0.012 * RATE
        val tau = dur * 0.33
        for (i in 0 until n) {
            val idx = s0 + i
            val pos = if (wrap) idx % b.size else idx
            if (pos >= b.size) break
            val t = i / RATE.toDouble()
            var env = Math.exp(-t / tau)
            if (i < att) env *= i / att
            if (i > n - rel) env *= (n - i) / rel
            val ph = 2.0 * Math.PI * f * t
            val w = if (kind == 2) {
                Math.sin(ph)
            } else {
                Math.sin(ph) + 0.25 * Math.sin(2.0 * ph) + 0.08 * Math.sin(3.0 * ph)
            }
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

    private fun toShorts(b: FloatArray, target: Float, always: Boolean): ShortArray {
        var peak = 0f
        for (v in b) {
            val a = Math.abs(v)
            if (a > peak) peak = a
        }
        val k = if (peak <= 0f) 1f else if (always || peak > target) target / peak else 1f
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

    // ---------- the sounds ----------

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

    // level 1..5: more notes and a longer chime for bigger combos
    private fun makeClear(level: Int): FloatArray {
        val scale = doubleArrayOf(523.25, 587.33, 659.25, 783.99, 880.0, 1046.5, 1174.66, 1318.5)
        val count = level + 2
        val gap = 0.075 - level * 0.004
        val total = count * gap + 0.6
        val b = FloatArray((total * RATE).toInt())
        sweep(b, 0.0, 220.0, 90.0, 0.12, 0.5)
        for (i in 0 until count) note(b, i * gap, scale[i], 0.45, 0.45, 0, false)
        return b
    }

    private fun makeOver(): FloatArray {
        val b = FloatArray((1.5 * RATE).toInt())
        val notes = doubleArrayOf(392.0, 329.63, 261.63, 196.0)
        for (i in notes.indices) note(b, i * 0.2, notes[i], 0.55, 0.5, 0, false)
        return b
    }

    private fun makeBest(): FloatArray {
        val b = FloatArray((1.5 * RATE).toInt())
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.5)
        for (i in notes.indices) note(b, i * 0.13, notes[i], 0.6, 0.45, 0, false)
        note(b, 0.55, 659.25, 0.7, 0.3, 0, false)
        note(b, 0.55, 783.99, 0.7, 0.3, 0, false)
        return b
    }

    // 8 bars, C - Am - F - G twice. Notes wrap around the end so the loop has no click.
    private fun makeMusic(): ShortArray {
        val beat = 60.0 / 96.0
        val bar = beat * 4.0
        val n = (bar * 8.0 * RATE).toInt()
        val b = FloatArray(n)
        val bass = doubleArrayOf(130.81, 110.0, 87.31, 98.0)
        val chords = arrayOf(
            doubleArrayOf(261.63, 329.63, 392.0),
            doubleArrayOf(220.0, 261.63, 329.63),
            doubleArrayOf(174.61, 220.0, 261.63),
            doubleArrayOf(196.0, 246.94, 293.66)
        )
        val pat = intArrayOf(0, 1, 2, 1, 0, 1, 2, 1)
        val mel = arrayOf(
            doubleArrayOf(659.25, 783.99, 659.25, 587.33),
            doubleArrayOf(523.25, 659.25, 587.33, 523.25),
            doubleArrayOf(440.0, 523.25, 698.46, 523.25),
            doubleArrayOf(493.88, 587.33, 783.99, 587.33),
            doubleArrayOf(783.99, 659.25, 523.25, 659.25),
            doubleArrayOf(659.25, 523.25, 440.0, 523.25),
            doubleArrayOf(440.0, 523.25, 698.46, 659.25),
            doubleArrayOf(587.33, 493.88, 392.0, 493.88)
        )
        for (k in 0 until 8) {
            val t0 = k * bar
            val ci = k % 4
            note(b, t0, bass[ci], bar * 0.95, 0.30, 2, true)
            for (j in 0 until 8) {
                note(b, t0 + j * beat / 2.0, chords[ci][pat[j]], beat * 0.9, 0.11, 0, true)
            }
            for (j in 0 until 4) {
                note(b, t0 + j * beat, mel[k][j], beat * 1.5, 0.2, 0, true)
            }
        }
        return toShorts(b, 0.8f, true)
    }
}
