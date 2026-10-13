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

    fun count(rows: List<String>): Int {
        var n = 0
        for (r in rows) for (ch in r) if (ch == 'X') n++
        return n
    }

    fun random(rnd: Random): Piece = Piece(all[rnd.nextInt(all.size)], 1 + rnd.nextInt(7))

    // a random piece with at most maxCells blocks (used for easier early levels)
    fun randomMax(rnd: Random, maxCells: Int): Piece {
        var shape = all[rnd.nextInt(all.size)]
        var tries = 0
        while (count(shape) > maxCells && tries < 30) {
            shape = all[rnd.nextInt(all.size)]
            tries++
        }
        return Piece(shape, 1 + rnd.nextInt(7))
    }
}
