package com.akay.block

import java.util.Random

class Piece(val rows: List<String>, val color: Int) {
    val cells: List<IntArray>
    val w: Int
    val h: Int

    init {
        val l = ArrayList<IntArray>()
        for (r in rows.indices) {
            for (c in rows[r].indices) {
                if (rows[r][c] == 'X') l.add(intArrayOf(r, c))
            }
        }
        cells = l
        h = rows.size
        w = rows.maxOf { it.length }
    }
}

object Shapes {
    val all: List<List<String>> = listOf(
        listOf("X"),
        listOf("XX"), listOf("X", "X"),
        listOf("XXX"), listOf("X", "X", "X"),
        listOf("XXXX"), listOf("X", "X", "X", "X"),
        listOf("XXXXX"), listOf("X", "X", "X", "X", "X"),
        listOf("XX", "XX"),
        listOf("XXX", "XXX", "XXX"),
        listOf("XXX", "XXX"), listOf("XX", "XX", "XX"),
        listOf("X.", "XX"), listOf(".X", "XX"), listOf("XX", "X."), listOf("XX", ".X"),
        listOf("X..", "X..", "XXX"), listOf("..X", "..X", "XXX"),
        listOf("XXX", "X..", "X.."), listOf("XXX", "..X", "..X"),
        listOf("XXX", ".X."), listOf(".X.", "XXX"), listOf("X.", "XX", "X."), listOf(".X", "XX", ".X"),
        listOf(".XX", "XX."), listOf("XX.", ".XX"), listOf("X.", "XX", ".X"), listOf(".X", "XX", "X."),
        listOf("X.", "X.", "XX"), listOf(".X", ".X", "XX"), listOf("XX", "X.", "X."), listOf("XX", ".X", ".X"),
        listOf("X..", "XXX"), listOf("..X", "XXX"), listOf("XXX", "X.."), listOf("XXX", "..X")
    )

    // the awkward ones: 5-in-a-line, big L and S / Z shapes
    private val HARD = setOf(7, 8, 17, 18, 19, 20, 25, 26, 27, 28)

    fun count(rows: List<String>): Int {
        var n = 0
        for (r in rows) for (ch in r) if (ch == 'X') n++
        return n
    }

    fun random(rnd: Random): Piece = Piece(all[rnd.nextInt(all.size)], 1 + rnd.nextInt(7))

    // a random piece with at most maxCells blocks; when easy is true the awkward shapes show up less
    fun randomPick(rnd: Random, maxCells: Int, easy: Boolean): Piece {
        val w = IntArray(all.size)
        var total = 0
        for (i in all.indices) {
            if (count(all[i]) > maxCells) continue
            w[i] = if (i == 0) 2 else if (easy && HARD.contains(i)) 1 else 3
            total += w[i]
        }
        if (total == 0) return random(rnd)
        var r = rnd.nextInt(total)
        for (i in all.indices) {
            if (w[i] == 0) continue
            r -= w[i]
            if (r < 0) return Piece(all[i], 1 + rnd.nextInt(7))
        }
        return random(rnd)
    }
}
