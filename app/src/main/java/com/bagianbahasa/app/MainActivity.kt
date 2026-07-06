package com.bagianbahasa.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    // Launcher untuk memilih file (fitur "Impor Excel")
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val results: Array<Uri>? = if (result.resultCode == Activity.RESULT_OK && data != null) {
            val clipData = data.clipData
            if (clipData != null) {
                Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
            } else {
                data.data?.let { arrayOf(it) }
            }
        } else null
        filePathCallback?.onReceiveValue(results)
        filePathCallback = null
    }

    // Launcher untuk minta izin storage (hanya dibutuhkan Android 9 ke bawah)
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Izin penyimpanan ditolak, ekspor mungkin gagal", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        setupWebView()

        webView.loadUrl("file:///android_asset/app_terbaru.html")
    }

    private fun setupWebView() {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // Jembatan JS <-> Android untuk menyimpan file hasil ekspor Excel
        webView.addJavascriptInterface(AndroidDownloader(), "AndroidDownloader")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Suntik shim SEBELUM script halaman berjalan, agar export XLSX (blob)
                // ditangkap dan disimpan lewat Android, bukan lewat mekanisme download browser.
                view?.evaluateJavascript(DOWNLOAD_SHIM_JS, null)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                // Tetap di dalam WebView untuk semua navigasi (aplikasi single-page)
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            // Menangani <input type="file"> pada tombol "Impor Excel"
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback
                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                }
                return try {
                    fileChooserLauncher.launch(intent)
                    true
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null
                    false
                }
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                // Izinkan permintaan media WebView (mis. kamera/mic) jika suatu saat dipakai
                request?.grant(request.resources)
            }
        }

        // Beberapa versi Android 9 ke bawah memerlukan izin eksplisit untuk menulis ke storage
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                storagePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    /**
     * Dipanggil dari JavaScript (lihat DOWNLOAD_SHIM_JS) saat file Excel hasil
     * ekspor (XLSX.writeFile) siap disimpan. Datanya dikirim sebagai base64.
     */
    inner class AndroidDownloader {
        @JavascriptInterface
        fun saveBase64File(base64Data: String, fileName: String, mimeType: String) {
            runOnUiThread {
                try {
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    val savedName = saveToDownloads(bytes, fileName, mimeType)
                    if (savedName != null) {
                        Toast.makeText(
                            this@MainActivity,
                            "File tersimpan di folder Downloads: $savedName",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Gagal menyimpan file", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Gagal menyimpan file: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveToDownloads(bytes: ByteArray, fileName: String, mimeType: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    val out: OutputStream? = resolver.openOutputStream(uri)
                    out?.use { it.write(bytes) }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    fileName
                } else null
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val file = java.io.File(downloadsDir, fileName)
                file.writeBytes(bytes)
                fileName
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        // Shim ini meng-override HTMLAnchorElement.click() dan window.open() agar
        // hasil "download" dari Blob (dipakai oleh xlsx.js saat ekspor Excel)
        // dibaca sebagai base64 lalu diteruskan ke Android, bukan didownload lewat browser.
        private const val DOWNLOAD_SHIM_JS = """
            (function() {
                if (window.__androidDownloadShimInstalled) return;
                window.__androidDownloadShimInstalled = true;

                function blobUrlToBase64(blobUrl, callback) {
                    fetch(blobUrl).then(function(res){ return res.blob(); }).then(function(blob){
                        var reader = new FileReader();
                        reader.onloadend = function() {
                            var base64 = reader.result.split(',')[1];
                            callback(base64, blob.type || 'application/octet-stream');
                        };
                        reader.readAsDataURL(blob);
                    }).catch(function(err){ console.error('shim fetch error', err); });
                }

                var originalClick = HTMLAnchorElement.prototype.click;
                HTMLAnchorElement.prototype.click = function() {
                    try {
                        if (this.href && this.href.indexOf('blob:') === 0 && this.download) {
                            var fileName = this.download || 'download';
                            blobUrlToBase64(this.href, function(base64, mime) {
                                if (window.AndroidDownloader) {
                                    window.AndroidDownloader.saveBase64File(base64, fileName, mime);
                                }
                            });
                            return;
                        }
                    } catch (e) { console.error('shim click error', e); }
                    return originalClick.apply(this, arguments);
                };
            })();
        """
    }
}
