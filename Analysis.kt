package id.sawitvision.app

import kotlin.math.abs
import kotlin.math.roundToInt

/** Level selisih AI vs Lapor -- ambang sama dengan ekstensi: <=10% kecil, <=25% sedang, selebihnya besar. */
enum class Level(val label: String) { COCOK("Cocok"), KECIL("Kecil"), SEDANG("Sedang"), BESAR("Besar") }

data class Selisih(val diff: Double, val pct: Double, val level: Level)

object Analysis {
    const val PCT_SEDANG = 0.10
    const val PCT_BESAR = 0.25
    val CALIBRATION_QUICK = listOf(3.0, 5.0, 7.0)

    fun pctDiff(diff: Double, reported: Double): Double =
        if (reported > 0) abs(diff) / reported else if (abs(diff) > 0.0001) 1.0 else 0.0

    fun levelFromPct(pct: Double): Level = when {
        pct <= 0.0001 -> Level.COCOK
        pct <= PCT_SEDANG -> Level.KECIL
        pct <= PCT_BESAR -> Level.SEDANG
        else -> Level.BESAR
    }

    /** diff = AI - Lapor. */
    fun selisih(ai: Double, reported: Double): Selisih {
        val diff = ai - reported
        val pct = pctDiff(diff, reported)
        return Selisih(diff, pct, levelFromPct(pct))
    }

    fun round1(v: Double) = (v * 10).roundToInt() / 10.0

    fun fmt(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)
}
