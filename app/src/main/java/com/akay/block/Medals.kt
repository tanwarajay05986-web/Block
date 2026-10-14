package com.akay.block

class Medal(
    val id: String,
    val title: String,
    val desc: String,
    val color: Int,
    val target: Int,
    val value: () -> Int
)

object Medals {
    private fun cleared(): Int = (Store.advMax() - 1).coerceAtLeast(0)

    val all: List<Medal> = listOf(
        Medal("w1", "First Win", "Clear Level 1", 0xFF3EA6FF.toInt(), 1) { cleared() },
        Medal("l10", "Explorer", "Clear 10 levels", 0xFF3DDC84.toInt(), 10) { cleared() },
        Medal("l25", "Adventurer", "Clear 25 levels", 0xFFFFC21A.toInt(), 25) { cleared() },
        Medal("l50", "Hero", "Clear 50 levels", 0xFFFF9A3C.toInt(), 50) { cleared() },
        Medal("l100", "Legend", "Clear 100 levels", 0xFFB36BFF.toInt(), 100) { cleared() },
        Medal("c500", "Score 500", "Reach 500 in Classic", 0xFF3EA6FF.toInt(), 500) { Store.classicBest() },
        Medal("c1000", "Score 1000", "Reach 1000 in Classic", 0xFFFFC21A.toInt(), 1000) { Store.classicBest() },
        Medal("c2000", "Score 2000", "Reach 2000 in Classic", 0xFFFF5A5F.toInt(), 2000) { Store.classicBest() },
        Medal("cb3", "Combo x3", "Make a combo of 3", 0xFF3DDC84.toInt(), 3) { Store.maxCombo() },
        Medal("cb5", "Combo x5", "Make a combo of 5", 0xFFFF9A3C.toInt(), 5) { Store.maxCombo() },
        Medal("ln100", "Line Master", "Clear 100 lines", 0xFF3EA6FF.toInt(), 100) { Store.totalLines() },
        Medal("ln500", "Line King", "Clear 500 lines", 0xFFB36BFF.toInt(), 500) { Store.totalLines() },
        Medal("pf1", "Perfect!", "Clear the whole board", 0xFFFFC21A.toInt(), 1) { Store.perfects() },
        Medal("st3", "3 Day Streak", "Win 3 days in a row", 0xFFFF5A5F.toInt(), 3) { Store.bestStreak() },
        Medal("st7", "7 Day Streak", "Win 7 days in a row", 0xFFFF9A3C.toInt(), 7) { Store.bestStreak() }
    )

    // looks for medals that are now earned, saves them and returns the new ones
    fun checkNew(): List<Medal> {
        val have = Store.earnedMedals()
        val fresh = ArrayList<Medal>()
        for (m in all) {
            if (!have.contains(m.id) && m.value() >= m.target) fresh.add(m)
        }
        if (fresh.isNotEmpty()) {
            val n = HashSet<String>(have)
            for (m in fresh) n.add(m.id)
            Store.setEarnedMedals(n)
        }
        return fresh
    }
}
