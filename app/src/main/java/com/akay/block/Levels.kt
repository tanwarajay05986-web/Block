package com.akay.block

import java.util.Random

// one Adventure level: goal score, blocks already on the board, biggest piece that can appear
class Level(val number: Int, val goal: Int, val prefill: Array<IntArray>, val maxCells: Int)

object Levels {
    const val COUNT = 5555

    fun goal(n: Int): Int {
        val v = 100.0 + 9.0 * minOf(n, 60) + 0.6 * maxOf(0, n - 60)
        return (v / 5.0).toInt() * 5
    }

    fun maxCells(n: Int): Int = when {
        n < 8 -> 4
        n < 25 -> 6
        else -> 9
    }

    private fun hasFullLine(b: Array<IntArray>): Boolean {
        for (i in 0 until 8) {
            if ((0 until 8).all { b[i][it] != 0 }) return true
            if ((0 until 8).all { b[it][i] != 0 }) return true
        }
        return false
    }

    // the same level number always gives the same level
    fun make(n: Int): Level {
        val num = n.coerceIn(1, COUNT)
        val rnd = Random(num * 7919L + 13L)
        val b = Array(8) { IntArray(8) }
        val want = if (num == 1) 0 else minOf(2 + (num * 9) / 10, 36)
        var count = 0
        var tries = 0
        while (count < want && tries < 600) {
            tries++
            val pc = Piece(Shapes.all[rnd.nextInt(Shapes.all.size)], 1 + rnd.nextInt(7))
            if (pc.cells.size > 6) continue
            val r0 = rnd.nextInt(8 - pc.h + 1)
            val c0 = rnd.nextInt(8 - pc.w + 1)
            var ok = true
            for (cl in pc.cells) {
                if (b[r0 + cl[0]][c0 + cl[1]] != 0) ok = false
            }
            if (!ok) continue
            for (cl in pc.cells) b[r0 + cl[0]][c0 + cl[1]] = pc.color
            if (hasFullLine(b)) {
                for (cl in pc.cells) b[r0 + cl[0]][c0 + cl[1]] = 0
                continue
            }
            count += pc.cells.size
        }
        return Level(num, goal(num), b, maxCells(num))
    }
}
