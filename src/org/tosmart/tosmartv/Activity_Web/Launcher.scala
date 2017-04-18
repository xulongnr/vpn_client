package org.tosmart.tosmartv.Activity_Web

import android.app.Activity
import android.content._
import android.os._
import android.support.design.widget.Snackbar
import android.view.animation.{Animation, AnimationUtils}
import android.view.{View, Window, WindowManager}
import android.widget.{ImageView, TextView}
import org.tosmart.tosmartv
import org.tosmart.tosmartv.helper.NetworkCheck
import org.tosmart.tosmartv.{R, Shadowsocks, ShadowsocksApplication}

/**
  * Created by liu on 08/06/2016.
  */
class Launcher extends Activity {
  lazy val TAG = "Launcher"

  private val SPLASH_TIME_OUT = 2000
  private val SPLASH_TIME_INT = 1000
  private var count: Int = SPLASH_TIME_OUT / SPLASH_TIME_INT
  private var tvSec: TextView = _
  private var animation: Animation = _
  private val display_timer: Boolean = true

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    var uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                  View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                  View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                  View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | // hide nav bar
                  View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
    if (android.os.Build.VERSION.SDK_INT >= 19)
      uiFlags = uiFlags | 0x00001000
    else
      uiFlags = uiFlags | View.SYSTEM_UI_FLAG_LOW_PROFILE

    getWindow.getDecorView.setSystemUiVisibility(uiFlags)

    setContentView(R.layout.activity_launcher)
    if (ShadowsocksApplication.SKIP_WELCOME) {
      directToHome()
      return
    }
    ShadowsocksApplication.SKIP_WELCOME = true

    tvSec = findViewById(R.id.timer).asInstanceOf[TextView]
    tvSec.setText(count.toString)
    tvSec.setOnClickListener((v: View) => {
      splashHandler.removeMessages(0)
      directToHome()
    })
    if (!display_timer) tvSec.setVisibility(View.GONE)
    else tvSec.setVisibility(View.GONE)
    animation = AnimationUtils.loadAnimation(this, R.anim.animation_text)
    splashHandler.sendEmptyMessageDelayed(0, SPLASH_TIME_INT)
  }

  def getCount(): Int = {
    count = count - 1
    count match {
      case 0 => directToHome()
      case _ =>
    }
    count
  }

  def directToHome() {
    val intent = new Intent(Launcher.this, classOf[Shadowsocks])
    startActivity(intent)
    finish()
  }

  private val splashHandler: Handler = new Handler() {
    override def handleMessage(msg: Message) {
      super.handleMessage(msg)
      msg.what match {
        case 0 =>
          tvSec.setText(getCount().toString)
          splashHandler.sendEmptyMessageDelayed(0, SPLASH_TIME_INT)
          animation.reset()
          tvSec.startAnimation(animation)
      }
    }
  }
}
