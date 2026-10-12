# SawitVision Android

Versi Android (APK) dari ekstensi Chrome **SawitVision – Asisten Verifikasi Panen**.
Memakai model yang sama (`sawit_detector.tflite`, YOLO26n, kelas: janjang / brondol / janjang_kosong),
ambang keyakinan bawaan 10%, dan aturan selisih yang sama (≤10% kecil, ≤25% sedang, >25% besar).

## Alur pakai
1. Isi Blok / TPH / No.e-BHP (opsional).
2. **Ambil Foto** atau **Dari Galeri** (boleh banyak foto). AI langsung menghitung.
3. Atur peran tiap foto (Foto Janjang / Foto Brondol / Abaikan). Ketuk kotak untuk ubah kelas atau hapus.
4. Pilih kalibrasi berat per tumpukan brondol (3 / 5 / 7 kg atau angka sendiri).
5. Isi JML JJG dan JML BRD (kg) dari laporan Kerani Panen → muncul badge selisih.
6. **Simpan ke Riwayat**; riwayat bisa dibagikan sebagai CSV.

Semua data tersimpan lokal di HP; tidak ada yang dikirim ke internet.

## Membuat file APK
### Cara 1 – GitHub Actions (tanpa install apa pun)
1. Buat repo GitHub baru, upload seluruh isi folder ini (termasuk folder `.github`).
2. Buka tab **Actions** → workflow **Build APK** (jalan otomatis saat push; atau klik *Run workflow*).
3. Setelah hijau, buka run tersebut → bagian **Artifacts** → unduh `SawitVision-debug-apk` → ekstrak → `app-debug.apk`.
4. Kirim ke HP, izinkan "install dari sumber tidak dikenal", lalu install.

### Cara 2 – Android Studio
Buka folder ini → tunggu Gradle sync → **Build > Build APK(s)**. Hasil: `app/build/outputs/apk/debug/app-debug.apk`.

## Struktur kode
- `Detector.kt` – pra-proses letterbox 640×640, inferensi TFLite, decode 300×6, dedup IoU 0.45.
- `Analysis.kt` – hitung selisih & level.
- `Ui.kt` / `AppState.kt` / `MainActivity.kt` – layar, state, kamera/galeri, simpan.
- `Storage.kt` – riwayat lokal (JSON) + ekspor CSV.
