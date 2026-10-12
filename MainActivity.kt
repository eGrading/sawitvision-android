package id.sawitvision.app

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainActivity : ComponentActivity() {
    private val state = AppState()
    private var detector: Detector? = null
    private var pendingCameraUri: Uri? = null
    private var nextId = 1L

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            val uri = pendingCameraUri
            if (ok && uri != null) addPhotos(listOf(uri))
        }

    private val pickImages =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris.isNotEmpty()) addPhotos(uris)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state.history = Storage.load(this)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF15803D),
                    secondary = Color(0xFF166534),
                    primaryContainer = Color(0xFFDCFCE7),
                ),
            ) {
                AppRoot(
                    state = state,
                    onCamera = ::openCamera,
                    onGallery = { pickImages.launch("image/*") },
                    onRerunAll = ::rerunAll,
                    onRerunOne = { p -> runDetection(p) },
                    onSave = ::saveRecord,
                    onExport = ::exportCsv,
                    onDeleteRecord = { r ->
                        Storage.delete(this, r.timestamp)
                        state.history = Storage.load(this)
                    },
                    onClearHistory = {
                        Storage.clear(this)
                        state.history = emptyList()
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        detector?.close()
        detector = null
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun openCamera() {
        try {
            val dir = File(cacheDir, "photos").apply { mkdirs() }
            val f = File(dir, "foto_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            pendingCameraUri = uri
            takePicture.launch(uri)
        } catch (e: Exception) {
            toast("Kamera tidak bisa dibuka: ${e.message}")
        }
    }

    private fun addPhotos(uris: List<Uri>) {
        lifecycleScope.launch {
            for (uri in uris) {
                val bmp = withContext(Dispatchers.IO) { ImageUtil.load(this@MainActivity, uri) }
                if (bmp == null) {
                    toast("Foto tidak bisa dibuka")
                    continue
                }
                val p = PhotoState(nextId++, bmp)
                state.photos.add(p)
                runDetection(p)
            }
        }
    }

    private fun rerunAll() {
        state.photos.toList().forEach { runDetection(it) }
    }

    private fun runDetection(p: PhotoState) {
        lifecycleScope.launch {
            p.busy = true
            p.status = "Menjalankan AI..."
            try {
                val boxes = withContext(Dispatchers.Default) {
                    val d = detector ?: Detector(applicationContext).also { detector = it }
                    d.detect(p.bitmap, state.conf)
                }
                p.boxes = boxes
                p.selected = -1
                if (!p.roleManual) {
                    val j = boxes.count { it.cls == Cls.JANJANG }
                    val b = boxes.count { it.cls == Cls.BRONDOL }
                    p.role = if (b > j) Role.BRONDOL else Role.JANJANG
                }
                p.status = "Selesai: ${boxes.size} kotak (ambang ${(state.conf * 100).toInt()}%)"
            } catch (e: Throwable) {
                p.status = "Gagal: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                p.busy = false
            }
        }
    }

    private fun saveRecord() {
        val s = state
        if (s.photos.isEmpty()) {
            toast("Belum ada foto")
            return
        }
        if (s.aiBrdPiles > 0 && s.calibration == null) {
            toast("Isi kalibrasi berat per tumpukan brondol dulu (3 / 5 / 7 kg atau angka lain)")
            return
        }
        val selJ = s.laporJjg?.let { Analysis.selisih(s.aiJjg.toDouble(), it) }
        val selB = s.laporBrd?.let { r -> s.aiBrdKg?.let { Analysis.selisih(it, r) } }
        val photos = JSONArray()
        s.photos.forEach { p ->
            val arr = JSONArray()
            p.boxes.forEach { b ->
                arr.put(JSONObject().apply {
                    put("class", b.cls); put("x", b.x.toDouble()); put("y", b.y.toDouble())
                    put("width", b.w.toDouble()); put("height", b.h.toDouble()); put("confidence", b.conf)
                })
            }
            photos.put(JSONObject().apply { put("role", p.role.name.lowercase()); put("boxes", arr) })
        }
        val rec = Record(
            timestamp = Storage.now(),
            blok = s.blok.trim(), tph = s.tph.trim(), noBhp = s.noBhp.trim(),
            aiJjg = s.aiJjg, aiBrdPiles = s.aiBrdPiles,
            calibration = s.calibration, aiBrdKg = s.aiBrdKg,
            laporJjg = s.laporJjg, laporBrdKg = s.laporBrd,
            levelJjg = selJ?.level?.label ?: "-", levelBrd = selB?.level?.label ?: "-",
            photosJson = photos.toString(),
        )
        Storage.add(this, rec)
        s.history = Storage.load(this)
        s.resetCheck()
        toast("Tersimpan di Riwayat")
    }

    private fun exportCsv() {
        try {
            val f = Storage.exportCsv(this)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(send, "Bagikan riwayat (CSV)"))
        } catch (e: Exception) {
            toast("Gagal ekspor: ${e.message}")
        }
    }
}
