package org.tosmart.tosmartv.Activity_Web

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.View.OnClickListener
import android.view.{Gravity, View}
import android.webkit.{WebView, WebViewClient}
import android.widget.{Button, TextView}
import org.tosmart.tosmartv.utils.{AppConfig, sessionManager}
import org.tosmart.tosmartv.{BuildConfig, R, ShadowsocksApplication, UpdateData_Noti}

/**
  * Created by tonyBt on 2016/11/12.
  */
class Activity_AboutPage extends AppCompatActivity {

  override def onCreate(savedInstanceState:Bundle){
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_about)

    val version_txt=findViewById(R.id.Version_ID).asInstanceOf[TextView]
    val PP=findViewById(R.id.PP).asInstanceOf[Button]
    val TOS=findViewById(R.id.TOS).asInstanceOf[Button]
    val CFU=findViewById(R.id.CFU).asInstanceOf[Button]

    val toolbar = findViewById(R.id.toolbar).asInstanceOf[Toolbar]
    toolbar.setTitle(R.string.about)
    toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_material)
    setSupportActionBar(toolbar)
    toolbar.setNavigationOnClickListener((v: View) => {
      val intent = getParentActivityIntent
      if (intent == null) finish() else navigateUpTo(intent)
    })

    version_txt.setText("Version "+ShadowsocksApplication.getVersionName)
    if (BuildConfig.DEBUG) {
      version_txt.setText(version_txt.getText + "(beta)")
    }

    PP.setOnClickListener(new OnClickListener {
      override def onClick(view: View){
        val intent = new Intent(Activity_AboutPage.this, classOf[WebviewActivity])
        intent.putExtra("title", getString(R.string.about))
        intent.putExtra("url", "file:///android_asset/pages/Privacy.html")
        startActivity(intent)
      }
    }  )

    TOS.setOnClickListener(new OnClickListener {
      override def onClick(view: View){
        val intent = new Intent(Activity_AboutPage.this, classOf[WebviewActivity])
        intent.putExtra("title", getString(R.string.about))
        intent.putExtra("url", "file:///android_asset/pages/Tos.html")
        startActivity(intent)
      }
    })

    val newVersionCode = new sessionManager(this).new_verion_code
    if (newVersionCode != 0 && newVersionCode > ShadowsocksApplication.getVersionCode) {
      CFU.setTextColor(R.color.red)
    }
    CFU.setOnClickListener((_: View) => {
      UpdateData_Noti.checkForDialog(Activity_AboutPage.this, AppConfig.URL_Check_Update, true)
    })

//    val aboutPage = new AboutPage(this)
//      .isRTL(false)
//      .setImage(R.drawable.ic_launcher)
//      .addItem(new Element().setTitle("Version "+ShadowsocksApplication.getVersionName)
//                            .setGravity(Gravity.CENTER))
//      .addItem(new Element().setTitle("Privacy Policy"))
//      .addItem(new Element().setTitle("Terms of Service"))
//
//      .addGroup("Connect with us")
//      .addEmail("elmehdi.sakout@gmail.com")
//      .addWebsite("http://medyo.github.io/")
//      .addFacebook("the.medy")
//      .addTwitter("medyo80")
//      .addYoutube("UCdPQtdWIsg7_pi4mrRu46vA")
//      .addPlayStore("com.ideashower.readitlater.pro")
//      .addInstagram("medyo80")
//      .addGitHub("medyo")
//      .addItem(getPrivacyElement())
//      .addItem(getTermsElement())
//      .create()

//    setContentView(aboutPage)
  }

//  def getPrivacyElement():Element= {
//    val copyRightsElement = new Element()
//        copyRightsElement.setTitle("Privacy Policy")
//    copyRightsElement.setGravity(Gravity.CENTER)
//    copyRightsElement.setOnClickListener(new OnClickListener {
//      override def onClick(view: View){
//        val web = new WebView(Activity_AboutPage.this)
//        web.loadUrl("file:///android_asset/pages/Privacy.html")
//      }
//    })
//     copyRightsElement
//  }

//  def getTermsElement():Element= {
//    val copyRightsElement = new Element()
//    copyRightsElement.setTitle("Terms of Service")
//    copyRightsElement.setGravity(Gravity.CENTER)
//    copyRightsElement.setOnClickListener(new OnClickListener {
//      override def onClick(view: View){
//        val web = new WebView(Activity_AboutPage.this)
//        web.loadUrl("file:///android_asset/pages/Tos.html")
//      }
//    })
//     copyRightsElement
//  }

}
