package org.tosmart.tosmartv.Activity_Web

import java.math.BigInteger
import java.security.MessageDigest

import android.graphics.Color
import android.net.Uri
import android.support.annotation.NonNull
import android.telephony.TelephonyManager
import android.support.design.widget.Snackbar
import android.support.v7.widget.Toolbar
import android.app.{Activity, ProgressDialog}
import android.content.{Context, Intent}
import android.os.{Build, Bundle}
import android.view.inputmethod.InputMethodManager
import android.view.{KeyEvent, View}
import android.view.View.{OnClickListener, OnFocusChangeListener}
import android.webkit.{WebView, WebViewClient}
import android.widget.{Button, EditText, ImageButton, Toast}
import com.android.volley.Request.Method
import com.android.volley.Response.{ErrorListener, Listener}
import com.android.volley.{AuthFailureError, NetworkError, NoConnectionError, ParseError, ServerError, TimeoutError}
import com.android.volley.toolbox.{HttpHeaderParser, JsonObjectRequest, StringRequest}
import com.android.volley.{DefaultRetryPolicy, NetworkResponse, Response, VolleyError}
import com.anthonycr.grant.{PermissionsManager, PermissionsResultAction}
import org.tosmart.tosmartv.{BuildConfig, R, Shadowsocks, ShadowsocksApplication}
import org.json.{JSONException, JSONObject}
import java.util.{HashMap, Locale}

import android.support.v4.app.{NotificationCompat, NotificationManagerCompat}
import org.tosmart.tosmartv.utils._

/**
  * Created by liu on 27/01/2016.
  */
class LoginActivity extends Activity {

  val TAG = "LoginActivity"
  var btnLogin:Button=_
  var btnLinkToRegister:Button=_
  var inputEmail:EditText=_
  var inputPassword:EditText=_
  private var session:sessionManager=_
  private var toolbar: Toolbar = _

  private var and_id=""
  private var tm:TelephonyManager=_
  private var ssn=""
  private var dev=""

  override def onCreate(savedInstanceState: Bundle){
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_log)

    toolbar = findViewById(R.id.toolbar).asInstanceOf[Toolbar]
    toolbar.setTitle(R.string.title_login)
    toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_material)
    toolbar.setNavigationOnClickListener((v: View) => {
      val intent = getParentActivityIntent
      if (intent == null) finish() else navigateUpTo(intent)
    })

    inputEmail = findViewById(R.id.email).asInstanceOf[EditText]
    inputPassword=findViewById(R.id.password).asInstanceOf[EditText]
    btnLogin=findViewById(R.id.btnLogin).asInstanceOf[Button]
    btnLinkToRegister=findViewById(R.id.btnLinkToRegisterScreen).asInstanceOf[Button]
    val btnLinkToResetPwd = findViewById(R.id.btnLinkToResetPwd).asInstanceOf[Button]

    session=new sessionManager(getApplicationContext)
    inputEmail.setText(session.lastlog)

    // User is already logged in. Take him to main activity
    if (session.isLogin) {
       val intent=new Intent(LoginActivity.this, classOf[Shadowsocks])
       startActivity(intent)
       finish()
    }

    // Login button Click Event
    btnLogin.setOnClickListener((v: View) => {
      val imm = getSystemService(Context.INPUT_METHOD_SERVICE).asInstanceOf[InputMethodManager]
      imm.hideSoftInputFromWindow(v.getWindowToken, 0)

      val email = inputEmail.getText.toString.trim()
      val password = inputPassword.getText.toString.trim()

      // remember last logging email for convenience
      session.lastlog = email

      // Check for empty data in the form
      if (!email.isEmpty && !password.isEmpty) {
        // login user
        checkLogin(email, password)

      } else {
        // Prompt user to enter credentials
        Snackbar.make(findViewById(android.R.id.content), "Please enter the credentials!", Snackbar.LENGTH_LONG)
          .setActionTextColor(Color.parseColor("#ff0000"))
          .show
      }
    })

    // Link to Register Screen
    btnLinkToRegister.setOnClickListener((v: View) => {
      startActivity(new Intent(getApplicationContext(), classOf[RegisterActivity]))
      finish
    })

//    // Link to Reset Password WebView
//    btnLinkToResetPwd.setOnClickListener((v: View) => {
//      val data=new Intent(Intent.ACTION_SENDTO)
//      data.setType("message/rfc822")
//
//      data.setData(Uri.parse("mailto:wl881208@gmail.com,xulongnr@gmail.com"))
//      data.putExtra(Intent.EXTRA_SUBJECT, "ReSet Your PassWord");
//      data.putExtra(Intent.EXTRA_TEXT, "Please Leave Your Email Below:")
//      startActivity(data)
//    })

    btnLinkToResetPwd.setOnClickListener((_: View) => {
       val intent=new Intent(getApplicationContext(),classOf[ResetPass])
       startActivity(intent)
    })

//    val btn_clear_email = findViewById(R.id.clear_email).asInstanceOf[ImageButton]
//    btn_clear_email.setOnClickListener((v: View) => {
//      inputEmail.setText("")
//    })
//
//    val btn_clear_password = findViewById(R.id.clear_password).asInstanceOf[ImageButton]
//    btn_clear_password.setOnClickListener((v: View) => {
//      inputPassword.setText("")
//    })
//
//    inputEmail.setOnFocusChangeListener((v: View, focus: Boolean) => {
//      btn_clear_email.setVisibility(if (inputEmail.getText.length != 0) View.VISIBLE else View.GONE)
//    })
//    inputPassword.setOnFocusChangeListener((v: View, focus: Boolean) => {
//      btn_clear_password.setVisibility(if (inputPassword.getText.length != 0) View.VISIBLE else View.GONE)
//    })
  }
//permission reslt callback
  override def onRequestPermissionsResult(requestCode:Int, @NonNull permissions:Array[String],
                                          @NonNull grantResults:Array[Int]) {
    Log.i(TAG, "Activity-onRequestPermissionsResult() PermissionsManager.notifyPermissionsChange()")
    PermissionsManager.getInstance().notifyPermissionsChange(permissions, grantResults)
  }

  /**
    * function to verify login details in mysql db
    * */
  def checkLogin(email:String, password:String) {
    // Tag used to cancel the request
    val tag_string_req = "req_login"
    val progress = new ProgressDlg(this)
    progress.showProgress(R.string.hint_logging)

    val strReq = new StringRequest(Method.POST, AppConfig.URL_LOGIN,
      new Listener[String]() {
        override def onResponse(response: String) {
          Log.d(TAG, "Login Response: " + response)
          progress.clearDialog()
          try {
            val jObj = new JSONObject(response)
            //code 0 for error ,code 1 for success

            val ok    = if (jObj.has("ok"))    jObj.getInt("ok") else 0
            val code  = if (jObj.has("code"))  jObj.getInt("code") else 0
            val error = if (jObj.has("error")) jObj.getInt("error") else 0
            val real_email = if (jObj.has("email")) jObj.getString("email")
                             else email

            // Check for error node in json
            if (ok == 1 && code == 1) {
              // user successfully logged in
              // generate token and save it to pref
              val pwd = password + "ss-panel"
              val md1 = MessageDigest.getInstance("SHA-256")
              md1.update(pwd.getBytes)
              val passwd = String.format("%064x", new BigInteger(1, md1.digest))
              val md2 = MessageDigest.getInstance("MD5")
              md2.update(real_email.getBytes)
              val em_md5 = String.format("%032x", new BigInteger(1, md2.digest))
              val token = em_md5 + passwd.substring(32)

              session.setUserInfo(jObj.getString("uid"), jObj.getString("name"), jObj.getString("email"),
                  jObj.getString("reg_at"), token, -1L, jObj.getString("invite"))
              session.pay_cnt = if (jObj.has("pay_rec")) jObj.getInt("pay_rec") else 0

              val context = getApplicationContext
              NotificationManagerCompat.from(context).cancel(NotificationId.FORCE_LOGOUT)

              sendBroadcast(new Intent(Action.LOGIN))

              startActivity(new Intent(context, classOf[MainActivity]))
              finish()

            } else {
              // Error in login. Get the error message
              Log.e(TAG, "Login error: " + jObj.getString("msg"))
              val err_tip = error match {
                  case 2 => R.string.login_error2
                  case _ => R.string.login_error
              }
              Snackbar.make(findViewById(android.R.id.content), err_tip, Snackbar.LENGTH_LONG).show()
            }
          } catch {
            case e: NullPointerException =>
              e.printStackTrace()
              val msg = String.format(getString(R.string.connection_test_error), "no response")
              Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_LONG).show()

            case e: JSONException =>
              e.printStackTrace()
              val msg = getString(R.string.connection_test_error, "Json error: " + e.getMessage)
              Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_LONG).show()
          }
        }
      },

      new Response.ErrorListener() {

        override def onErrorResponse(error:VolleyError) {
          progress.clearDialog()
          var errMsg = ""
          error match {
            case err: AuthFailureError =>  errMsg = "AuthFailureError: " + err.getMessage
            case err: NetworkError =>      errMsg = "NetworkError: " + err.getMessage
          //case err: NoConnectionError => errMsg = "NoConnectionError: " + err.getMessage
            case err: ParseError =>        errMsg = "ParseError: " + err.getMessage
            case err: ServerError =>       errMsg = "ServerError: " + err.getMessage
            case err: TimeoutError =>      errMsg = "TimeoutError: " + err.getMessage
          }
          Log.e(TAG, errMsg)

          Snackbar.make(findViewById(android.R.id.content), "网络异常,请重试", Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.retry, new OnClickListener {
              override def onClick(v: View) {
                  
              }
            })
        }

      }) {
        override def getParams(): HashMap[String,String] = {
          // Posting parameters to login url
          val params = new HashMap[String, String]()

          //send sha256 passcode as password
          //val pwd = password + "ss-panel"
          //val md = MessageDigest.getInstance("SHA-256")
          //md.update(pwd.getBytes)
          //val passwd = String.format("%064x", new BigInteger(1, md.digest))
          //params.put("passwd", passwd)
          params.put("dev_id", session.devid)
          params.put("sim_sn", session.simsn)
          params.put("and_id", session.android_id)
          params.put("email",  email)
          params.put("passwd", password)
          params.put("client_version", ShadowsocksApplication.getVersionName)
          params.put("sdk_version", Build.VERSION.RELEASE)
          params.put("manufacture", Build.MANUFACTURER)
          params.put("model",       Build.MODEL)
          Log.v(TAG, params.toString)
          params
        }
    }
    //
    strReq.setRetryPolicy(new DefaultRetryPolicy(
      10*1000,
      2,
      1))
    // Adding request to request queue
    ShadowsocksApplication.getInstance().addToRequestQueue(strReq, tag_string_req)
  }

  override def onStop() {
    Log.v(TAG, "onStop()")
    super.onStop()
  }
}