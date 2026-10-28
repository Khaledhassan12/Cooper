package mod.hey.studios.activity.managers.assets

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import com.besome.sketch.lib.base.BaseAppCompatActivity
import pro.sketchware.utility.FileUtil

class HtmlPreviewActivity : BaseAppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdgeNoContrast()
        super.onCreate(savedInstanceState)

        val webView = WebView(this)
        setContentView(webView)

        val path = intent.getStringExtra("path")
        if (path != null) {
            val content = FileUtil.readFile(path)
            webView.settings.javaScriptEnabled = true
            webView.webViewClient = WebViewClient()
            webView.loadDataWithBaseURL("file://$path", content, "text/html", "UTF-8", null)
        }
    }
}
