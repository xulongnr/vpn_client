package org.tosmart.tosmartv.Activity_Web

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.{KeyEvent, View}
import android.webkit.{WebView, WebViewClient}
import org.tosmart.tosmartv.R

/**
  * Created by polly on 4/2/16.
  */
class WebviewActivity extends AppCompatActivity {
  private var webview: WebView = _
  private var toolbar: Toolbar = _

  override def onCreate(savedInstance: Bundle) {
    super.onCreate(savedInstance)
    this.setContentView(R.layout.activity_webview)

    val intent = this.getIntent()
    toolbar = findViewById(R.id.toolbar).asInstanceOf[Toolbar]
    toolbar.setTitle(intent.getStringExtra("title"))
    toolbar.setNavigationIcon(R.drawable.ic_close)
    toolbar.setNavigationOnClickListener((v: View) => {
      val intent = getParentActivityIntent
      if (intent == null) finish else navigateUpTo(intent)
    })

    //getWindow.requestFeature(Window.FEATURE_NO_TITLE)
    webview = findViewById(R.id.webview).asInstanceOf[WebView]
    webview.loadUrl(intent.getStringExtra("url"))
    webview.setWebViewClient(new WebViewClient() {
      override def shouldOverrideUrlLoading(view: WebView, url: String): Boolean = {
        view.loadUrl(url)
        true
      }
    })
  }

  override def onKeyDown(keyCode: Int, event: KeyEvent): Boolean = {
    if ((keyCode == KeyEvent.KEYCODE_BACK) && webview.canGoBack) {
      webview.goBack()
      return true
    }
    super.onKeyDown(keyCode, event)
  }
}
