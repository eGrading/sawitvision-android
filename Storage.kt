package id.sawitvision.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Satu hasil pemeriksaan yang disimpan di riwayat lokal HP. */
data class Record(
    val timestamp: String,
    val blok: String,
    val tph: String,
    val noBhp: String,
    val aiJjg: Int,
    val aiBrdPiles: Int,
    val calibration: Double?,
    val aiBrdKg: Double?,
    val laporJjg: Double?,
    val laporBrdKg: Double?,
    val levelJjg: String,
    val levelBrd: String,
    val photosJson: String,
)

object Storage {
    private fun file(ctx: Context) = File(ctx.filesDir, "riwayat.json")

    fun now(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    fun load(ctx: Context): List<Record> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Record(
                    o.optString("timestamp"), o.optString("blok"), o.optString("tph"), o.optString("noBhp"),
                    o.optInt("aiJjg"), o.optInt("aiBrdPiles"),
                    if (o.isNull("calibration")) null else o.optDouble("calibration"),
                    if (o.isNull("aiBrdKg")) null else o.optDouble("aiBrdKg"),
                    if (o.isNull("laporJjg")) null else o.optDouble("laporJjg"),
                    if (o.isNull("laporBrdKg")) null else o.optDouble("laporBrdKg"),
                    o.optString("levelJjg"), o.optString("levelBrd"), o.optString("photos", "[]"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(ctx: Context, list: List<Record>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(JSONObject().apply {
                put("timestamp", r.timestamp); put("blok", r.blok); put("tph", r.tph); put("noBhp", r.noBhp)
                put("aiJjg", r.aiJjg); put("aiBrdPiles", r.aiBrdPiles)
                put("calibration", r.calibration ?: JSONObject.NULL)
                put("aiBrdKg", r.aiBrdKg ?: JSONObject.NULL)
                put("laporJjg", r.laporJjg ?: JSONObject.NULL)
                put("laporBrdKg", r.laporBrdKg ?: JSONObject.NULL)
                put("levelJjg", r.levelJjg); put("levelBrd", r.levelBrd); put("photos", r.photosJson)
            })
        }
        file(ctx).writeText(arr.toString())
    }

    fun add(ctx: Context, r: Record) = save(ctx, listOf(r) + load(ctx))

    fun delete(ctx: Context, timestamp: String) = save(ctx, load(ctx).filter { it.timestamp != timestamp })

    fun clear(ctx: Context) { file(ctx).delete() }

    private fun csv(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    /** Tulis riwayat ke CSV di cache dan kembalikan filenya (untuk dibagikan). */
    fun exportCsv(ctx: Context): File {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val f = File(dir, "sawitvision_riwayat.csv")
        val sb = StringBuilder()
        sb.append("Timestamp,Blok,TPH,No.e-BHP,Janjang AI,Brondol Tumpukan AI,Kalibrasi kg/tumpukan,Brondol KG AI,Lapor Janjang,Lapor Brondol KG,Selisih Janjang,Selisih Brondol,Data Foto (JSON)\n")
        load(ctx).forEach { r ->
            sb.append(
                listOf(
                    csv(r.timestamp), csv(r.blok), csv(r.tph), csv(r.noBhp),
                    r.aiJjg.toString(), r.aiBrdPiles.toString(),
                    r.calibration?.toString() ?: "", r.aiBrdKg?.toString() ?: "",
                    r.laporJjg?.toString() ?: "", r.laporBrdKg?.toString() ?: "",
                    csv(r.levelJjg), csv(r.levelBrd), csv(r.photosJson),
                ).joinToString(","),
            ).append('\n')
        }
        f.writeText(sb.toString())
        return f
    }
}
