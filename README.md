# Bagian Bahasa — Versi Android

Project ini adalah pembungkus (wrapper) Android dari aplikasi web "Laporan Bagian Bahasa" yang sudah kamu buat. Aplikasi web aslinya (HTML + Firebase + xlsx.js) dijalankan di dalam WebView native Android, sehingga semua fitur (login, lihat laporan, dashboard, database Excel-like, import/export Excel) tetap berfungsi sama persis — hanya sekarang berjalan sebagai aplikasi Android (APK) yang bisa dipasang di HP.

## Kenapa dibuat begini?

Aplikasi aslinya menggunakan Firebase (Auth + Realtime Database) dan library xlsx.js lewat CDN — ini murni JavaScript yang berjalan di browser/WebView, bukan native Android. Menulis ulang semuanya jadi Kotlin native akan memakan waktu sangat lama dan berisiko tinggi bug baru. Cara standar industri untuk kasus ini adalah membungkus web app dengan WebView (mirip cara kerja Cordova/Capacitor), sambil menambahkan jembatan native untuk hal-hal yang tidak bisa dilakukan browser biasa di HP, yaitu:

1. **Impor Excel** → memakai file picker asli Android.
2. **Ekspor Excel** → hasil `XLSX.writeFile()` (yang di browser normalnya "didownload") ditangkap dan disimpan otomatis ke folder **Downloads** HP.

## Cara dapat APK — TANPA install Android Studio (via GitHub Actions)

Project ini sudah dilengkapi `.github/workflows/build-apk.yml` yang otomatis meng-compile APK di server GitHub setiap kali kamu upload/push kode. Kamu cuma perlu akun GitHub gratis, tidak perlu install apa pun di komputer.

**Langkah-langkah:**

1. Buat akun di [github.com](https://github.com) kalau belum punya (gratis).
2. Klik **New repository** → beri nama bebas (misal `bagian-bahasa-app`) → **Create repository**. Boleh Public atau Private, sama saja.
3. Di halaman repo yang baru dibuat, klik **uploading an existing file** (atau menu **Add file → Upload files**).
4. **Ekstrak isi ZIP ini di komputer**, lalu drag-drop **seluruh isi folder `BagianBahasaApp`** (bukan folder itu sendiri, tapi isinya: `app`, `.github`, `build.gradle`, dll) ke halaman upload GitHub tadi.
5. Scroll ke bawah, klik **Commit changes**.
6. Buka tab **Actions** di repo tersebut. Akan ada proses "Build APK" yang otomatis berjalan (ikon kuning berputar) — tunggu sampai selesai (biasanya 3–6 menit, tandanya centang hijau ✅).
7. Klik proses yang sudah selesai tersebut, scroll ke bagian bawah halaman ke bagian **Artifacts**, lalu klik **BagianBahasa-APK** untuk mendownload (berbentuk file `.zip` berisi `app-debug.apk` di dalamnya).
8. Ekstrak zip tadi, kamu akan dapat file **`app-debug.apk`**.
9. Kirim file APK itu ke HP Android kamu (lewat WhatsApp ke diri sendiri, Google Drive, atau kabel data), lalu **sentuh sekali untuk install**. Kalau muncul peringatan "sumber tidak dikenal", aktifkan izin instalasi dari file manager/browser yang kamu pakai.

Selesai — tidak perlu Android Studio, tidak perlu command line di komputer.

Kalau nanti mau update aplikasinya, cukup timpa file `app/src/main/assets/app_terbaru.html` di repo GitHub tadi (edit langsung di GitHub atau upload ulang), otomatis APK baru akan ter-build lagi.

### Alternatif: build manual pakai Android Studio
1. Install [Android Studio](https://developer.android.com/studio) (gratis).
2. Buka Android Studio → **Open** → pilih folder project ini (`BagianBahasaApp`).
3. Tunggu Gradle sync selesai.
4. Klik menu **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

Untuk publish ke Play Store, gunakan **Build → Generate Signed Bundle / APK** dan buat keystore sendiri.

## Struktur penting

- `app/src/main/assets/app_terbaru.html` — file aplikasi web asli kamu, disalin apa adanya. Kalau kamu update aplikasi web-nya nanti, cukup **timpa file ini** dengan versi terbaru lalu build ulang.
- `app/src/main/java/com/bagianbahasa/app/MainActivity.kt` — kode Android yang memuat WebView dan menangani import/export Excel.
- `app/src/main/AndroidManifest.xml` — izin internet dan storage.

## Catatan

- Aplikasi butuh **koneksi internet** aktif di HP karena Firebase Auth/Database dan font/library (Google Fonts, xlsx.js) dimuat online, sama seperti versi web.
- Ikon aplikasi masih ikon sederhana bawaan (huruf oranye di atas hitam, mengikuti tema aplikasi). Kamu bisa ganti dengan logo sendiri lewat **File → New → Image Asset** di Android Studio.
- Minimum Android yang didukung: Android 8.0 (API 26) ke atas.
