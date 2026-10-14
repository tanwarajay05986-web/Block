package com.akay.block

// a look for the blocks and the board. colors[1..7] are the block colours (index 0 is not used)
class Skin(
    val name: String,
    val colors: IntArray,
    val bg: Int,
    val outer: Int,
    val inner: Int,
    val grid: Int,
    val needMedal: String?
)

object Skins {
    val all: List<Skin> = listOf(
        Skin(
            "Classic",
            intArrayOf(
                0, 0xFF2FBF3C.toInt(), 0xFF3C5BE8.toInt(), 0xFFC93131.toInt(), 0xFFF0832B.toInt(),
                0xFFEDBA3A.toInt(), 0xFF8E4FD8.toInt(), 0xFF2BB4E5.toInt()
            ),
            0xFF3B4F8F.toInt(), 0xFF2E3C75.toInt(), 0xFF1D2240.toInt(), 0xFF262C54.toInt(), null
        ),
        Skin(
            "Candy",
            intArrayOf(
                0, 0xFF6FD66F.toInt(), 0xFF6F9BFF.toInt(), 0xFFFF6F8A.toInt(), 0xFFFFA45C.toInt(),
                0xFFFFD95C.toInt(), 0xFFC08CFF.toInt(), 0xFF6FD6E8.toInt()
            ),
            0xFF8A4F9E.toInt(), 0xFF6B3A82.toInt(), 0xFF4A2660.toInt(), 0xFF5A3372.toInt(), null
        ),
        Skin(
            "Neon",
            intArrayOf(
                0, 0xFF00FF6A.toInt(), 0xFF3D6BFF.toInt(), 0xFFFF2D55.toInt(), 0xFFFF8A00.toInt(),
                0xFFFFE600.toInt(), 0xFFB400FF.toInt(), 0xFF00E5FF.toInt()
            ),
            0xFF0B0B1F.toInt(), 0xFF1A1A3A.toInt(), 0xFF0D0D24.toInt(), 0xFF1C1C40.toInt(), "l10"
        ),
        Skin(
            "Forest",
            intArrayOf(
                0, 0xFF3FA34D.toInt(), 0xFF3F7FBF.toInt(), 0xFFB5483A.toInt(), 0xFFD98A3D.toInt(),
                0xFFD9B84A.toInt(), 0xFF8A5FA8.toInt(), 0xFF3FA3A3.toInt()
            ),
            0xFF2F5D45.toInt(), 0xFF244A37.toInt(), 0xFF173325.toInt(), 0xFF1E3F2E.toInt(), "c500"
        )
    )

    fun cur(): Skin = all[Store.skin().coerceIn(0, all.size - 1)]

    fun unlocked(s: Skin): Boolean = s.needMedal == null || Store.earnedMedals().contains(s.needMedal)
}
