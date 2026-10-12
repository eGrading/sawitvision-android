package id.sawitvision.app

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Screen { CHECK, HISTORY }

enum class Role(val label: String) {
    JANJANG("Foto Janjang"),
    BRONDOL("Foto Brondol"),
    ABAIKAN("Abaikan"),
}

/** State satu foto: bitmap + kotak deteksi yang bisa diedit user. */
class PhotoState(val id: Long, val bitmap: Bitmap) {
    var boxes by mutableStateOf<List<Detection>>(emptyList())
    var role by mutableStateOf(Role.JANJANG)
    var roleManual = false
    var selected by mutableStateOf(-1)
    var busy by mutableStateOf(false)
    var status by mutableStateOf("Menunggu...")
}

class AppState {
    var screen by mutableStateOf(Screen.CHECK)
    val photos = mutableStateListOf<PhotoState>()
    var history by mutableStateOf<List<Record>>(emptyList())

    var blok by mutableStateOf("")
    var tph by mutableStateOf("")
    var noBhp by mutableStateOf("")

    /** Ambang keyakinan minimum (0..1), berlaku untuk semua kelas. */
    var conf by mutableStateOf(Detector.DEFAULT_CONF)

    /** Kalibrasi berat per tumpukan brondol (kg) -- teks supaya bisa diketik bebas. */
    var calibText by mutableStateOf("")
    var laporJjgText by mutableStateOf("")
    var laporBrdText by mutableStateOf("")

    val calibration: Double? get() = calibText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    val laporJjg: Double? get() = laporJjgText.replace(',', '.').toDoubleOrNull()
    val laporBrd: Double? get() = laporBrdText.replace(',', '.').toDoubleOrNull()

    val aiJjg: Int
        get() = photos.fold(0) { acc, p ->
            if (p.role == Role.JANJANG) acc + p.boxes.count { it.cls == Cls.JANJANG } else acc
        }
    val aiBrdPiles: Int
        get() = photos.fold(0) { acc, p ->
            if (p.role == Role.BRONDOL) acc + p.boxes.count { it.cls == Cls.BRONDOL } else acc
        }
    val aiBrdKg: Double? get() = calibration?.let { Analysis.round1(aiBrdPiles * it) }

    fun resetCheck() {
        photos.clear()
        tph = ""
        noBhp = ""
        laporJjgText = ""
        laporBrdText = ""
    }
}
