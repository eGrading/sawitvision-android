@file:OptIn(ExperimentalMaterial3Api::class)

package id.sawitvision.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ColJanjang = Color(0xFFF59E0B)
private val ColBrondol = Color(0xFF16A34A)
private val ColKosong = Color(0xFF6B7280)

private fun clsColor(c: String) = when (c) {
    Cls.JANJANG -> ColJanjang
    Cls.BRONDOL -> ColBrondol
    else -> ColKosong
}

private fun levelColor(l: Level) = when (l) {
    Level.COCOK -> Color(0xFF16A34A)
    Level.KECIL -> Color(0xFFF97316)
    Level.SEDANG -> Color(0xFFDB2777)
    Level.BESAR -> Color(0xFFDC2626)
}

@Composable
fun AppRoot(
    state: AppState,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onRerunAll: () -> Unit,
    onRerunOne: (PhotoState) -> Unit,
    onSave: () -> Unit,
    onExport: () -> Unit,
    onDeleteRecord: (Record) -> Unit,
    onClearHistory: () -> Unit,
) {
    BackHandler(enabled = state.screen == Screen.HISTORY) { state.screen = Screen.CHECK }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.screen == Screen.CHECK) "🌴 SawitVision" else "Riwayat", fontWeight = FontWeight.Bold)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF14532D),
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
                actions = {
                    TextButton(onClick = {
                        state.screen = if (state.screen == Screen.CHECK) Screen.HISTORY else Screen.CHECK
                    }) {
                        Text(
                            if (state.screen == Screen.CHECK) "Riwayat (${state.history.size})" else "Periksa",
                            color = Color.White,
                        )
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (state.screen) {
                Screen.CHECK -> CheckScreen(state, onCamera, onGallery, onRerunAll, onRerunOne, onSave)
                Screen.HISTORY -> HistoryScreen(state, onExport, onDeleteRecord, onClearHistory)
            }
        }
    }
}

@Composable
private fun CheckScreen(
    s: AppState,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onRerunAll: () -> Unit,
    onRerunOne: (PhotoState) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- Info dokumen ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Info TPH (opsional)", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = s.blok, onValueChange = { s.blok = it }, label = { Text("Blok") },
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = s.tph, onValueChange = { s.tph = it }, label = { Text("TPH") },
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = s.noBhp, onValueChange = { s.noBhp = it }, label = { Text("No.e-BHP") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ---- Tambah foto ----
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onCamera, modifier = Modifier.weight(1f)) { Text("📷 Ambil Foto") }
            OutlinedButton(onClick = onGallery, modifier = Modifier.weight(1f)) { Text("🖼 Dari Galeri") }
        }

        // ---- Ambang keyakinan ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Ambang keyakinan minimum: ${(s.conf * 100).toInt()}%", fontWeight = FontWeight.Bold)
                Slider(
                    value = s.conf, onValueChange = { s.conf = it },
                    valueRange = 0.05f..0.90f,
                )
                Text(
                    "Berlaku untuk semua kelas. Turunkan jika ada objek terlewat, naikkan jika banyak kotak palsu.",
                    fontSize = 12.sp, color = Color.Gray,
                )
                if (s.photos.isNotEmpty()) {
                    TextButton(onClick = onRerunAll) { Text("Proses ulang semua foto") }
                }
            }
        }

        // ---- Foto ----
        s.photos.forEachIndexed { idx, p ->
            PhotoCard(idx, p, onRemove = { s.photos.remove(p) }, onRerun = { onRerunOne(p) })
        }
        if (s.photos.isEmpty()) {
            Text(
                "Ambil/pilih foto TPH (foto janjang dan/atau foto brondol). AI akan menghitung otomatis, " +
                    "lalu bandingkan dengan angka yang dilaporkan Kerani Panen.",
                color = Color.Gray,
            )
        }

        // ---- Ringkasan & perbandingan ----
        if (s.photos.isNotEmpty()) SummaryCard(s, onSave)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PhotoCard(idx: Int, p: PhotoState, onRemove: () -> Unit, onRerun: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Foto ${idx + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (p.busy) CircularProgressIndicator(modifier = Modifier.height(20.dp).aspectRatio(1f), strokeWidth = 2.dp)
                TextButton(onClick = onRerun, enabled = !p.busy) { Text("Ulang") }
                TextButton(onClick = onRemove) { Text("Hapus", color = Color(0xFFDC2626)) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Role.values().forEach { r ->
                    FilterChip(
                        selected = p.role == r,
                        onClick = { p.role = r; p.roleManual = true },
                        label = { Text(r.label) },
                    )
                }
            }
            AnnotatedImage(p)
            Text(p.status, fontSize = 12.sp, color = Color.Gray)
            val j = p.boxes.count { it.cls == Cls.JANJANG }
            val b = p.boxes.count { it.cls == Cls.BRONDOL }
            val k = p.boxes.count { it.cls == Cls.KOSONG }
            Text("Terdeteksi: Janjang $j · Brondol $b · Janjang kosong $k")

            val sel = p.boxes.getOrNull(p.selected)
            if (sel != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Kotak dipilih: ${Cls.label(sel.cls)} (${sel.conf}%)", fontWeight = FontWeight.Bold)
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Cls.NAMES.forEach { c ->
                                FilterChip(
                                    selected = sel.cls == c,
                                    onClick = {
                                        p.boxes = p.boxes.toMutableList().also { it[p.selected] = sel.copy(cls = c) }
                                    },
                                    label = { Text(Cls.short(c)) },
                                )
                            }
                            OutlinedButton(onClick = {
                                p.boxes = p.boxes.toMutableList().also { it.removeAt(p.selected) }
                                p.selected = -1
                            }) { Text("Hapus kotak", color = Color(0xFFDC2626)) }
                        }
                    }
                }
            } else if (p.boxes.isNotEmpty()) {
                Text("Ketuk sebuah kotak untuk mengubah kelas atau menghapusnya.", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun AnnotatedImage(p: PhotoState) {
    val img = remember(p.bitmap) { p.bitmap.asImageBitmap() }
    val paint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 34f
            isFakeBoldText = true
        }
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(p.bitmap.width.toFloat() / p.bitmap.height.toFloat())
            .pointerInput(p) {
                detectTapGestures { off ->
                    val nx = off.x / size.width
                    val ny = off.y / size.height
                    var best = -1
                    var bestArea = Float.MAX_VALUE
                    p.boxes.forEachIndexed { i, b ->
                        if (nx >= b.x && nx <= b.x + b.w && ny >= b.y && ny <= b.y + b.h) {
                            val area = b.w * b.h
                            if (area < bestArea) { bestArea = area; best = i }
                        }
                    }
                    p.selected = best
                }
            },
    ) {
        drawImage(img, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
        p.boxes.forEachIndexed { i, b ->
            val c = clsColor(b.cls)
            val tl = Offset(b.x * size.width, b.y * size.height)
            drawRect(
                color = c, topLeft = tl, size = Size(b.w * size.width, b.h * size.height),
                style = Stroke(width = if (i == p.selected) 8f else 3f),
            )
            paint.color = android.graphics.Color.argb(255, (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
            drawIntoCanvas { it.nativeCanvas.drawText("${Cls.short(b.cls)} ${b.conf}", tl.x + 4f, tl.y + 32f, paint) }
        }
    }
}

@Composable
private fun SummaryCard(s: AppState, onSave: () -> Unit) {
    val jjg = s.aiJjg
    val piles = s.aiBrdPiles
    val kg = s.aiBrdKg
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Hasil AI", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Janjang (AI): $jjg")
            Text("Brondol tumpukan (AI): $piles")

            Text("⚖️ Kalibrasi berat per tumpukan brondol (kg)", fontWeight = FontWeight.Bold)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Analysis.CALIBRATION_QUICK.forEach { w ->
                    FilterChip(
                        selected = s.calibration == w,
                        onClick = { s.calibText = Analysis.fmt(w) },
                        label = { Text("${Analysis.fmt(w)} kg") },
                    )
                }
                OutlinedTextField(
                    value = s.calibText, onValueChange = { s.calibText = it },
                    label = { Text("Custom") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.height(64.dp).aspectRatio(1.6f),
                )
            }
            if (piles > 0) {
                Text(
                    if (kg != null) "Taksasi brondol (AI): ${Analysis.fmt(kg)} kg"
                    else "Isi kalibrasi agar taksasi KG brondol bisa dihitung.",
                    color = if (kg != null) Color.Unspecified else Color(0xFFDC2626),
                )
            }

            Text("Angka yang dilaporkan Kerani Panen", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = s.laporJjgText, onValueChange = { s.laporJjgText = it },
                    label = { Text("JML JJG") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = s.laporBrdText, onValueChange = { s.laporBrdText = it },
                    label = { Text("JML BRD (kg)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }

            val lj = s.laporJjg
            if (lj != null) {
                CompareRow("Janjang", "AI $jjg", "Lapor ${Analysis.fmt(lj)}", Analysis.selisih(jjg.toDouble(), lj))
            }
            val lb = s.laporBrd
            if (lb != null && kg != null) {
                CompareRow("Brondol (kg)", "AI ${Analysis.fmt(kg)}", "Lapor ${Analysis.fmt(lb)}", Analysis.selisih(kg, lb))
            } else if (lb != null) {
                Text("Selisih brondol belum bisa dihitung (kalibrasi kosong).", color = Color.Gray, fontSize = 12.sp)
            }
            Text(
                "Hasil AI hanya membantu menentukan prioritas pengecekan ulang, bukan pengganti keputusan verifikasi.",
                fontSize = 11.sp, color = Color.Gray,
            )
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("💾 Simpan ke Riwayat") }
        }
    }
}

@Composable
private fun CompareRow(title: String, ai: String, lapor: String, sel: Selisih) {
    Row(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(8.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text("$ai · $lapor")
            Text(
                "Selisih ${if (sel.diff >= 0) "+" else ""}${Analysis.fmt(Analysis.round1(sel.diff))} " +
                    "(${(sel.pct * 100).toInt()}%)",
                fontSize = 12.sp, color = Color.Gray,
            )
        }
        Text(
            sel.level.label,
            color = Color.White, fontWeight = FontWeight.Bold,
            modifier = Modifier.background(levelColor(sel.level), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun HistoryScreen(
    s: AppState,
    onExport: () -> Unit,
    onDelete: (Record) -> Unit,
    onClear: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (s.history.isEmpty()) {
            Text("Belum ada riwayat pemeriksaan.", color = Color.Gray)
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onExport) { Text("Bagikan CSV") }
            OutlinedButton(onClick = onClear) { Text("Hapus semua", color = Color(0xFFDC2626)) }
        }
        s.history.forEach { r ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        listOf(r.blok, r.tph).filter { it.isNotBlank() }.joinToString(" / ").ifBlank { "Tanpa info TPH" },
                        fontWeight = FontWeight.Bold,
                    )
                    Text(r.timestamp + if (r.noBhp.isNotBlank()) " · ${r.noBhp}" else "", fontSize = 12.sp, color = Color.Gray)
                    Text("Janjang: AI ${r.aiJjg} / Lapor ${r.laporJjg?.let { Analysis.fmt(it) } ?: "-"} → ${r.levelJjg}")
                    Text(
                        "Brondol: ${r.aiBrdPiles} tumpukan" +
                            (r.aiBrdKg?.let { " ≈ ${Analysis.fmt(it)} kg" } ?: "") +
                            " / Lapor ${r.laporBrdKg?.let { Analysis.fmt(it) } ?: "-"} kg → ${r.levelBrd}",
                    )
                    TextButton(onClick = { onDelete(r) }) { Text("Hapus", color = Color(0xFFDC2626)) }
                }
            }
        }
    }
}
