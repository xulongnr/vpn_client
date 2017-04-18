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
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.view.{KeyEvent, View}
import android.view.View.{OnClickListener, OnFocusChangeListener}
import android.webkit.{WebView, WebViewClient}
import android.widget.{Button, EditText, ImageButton, Toast}
import com.android.volley.Request.Method
import com.android.volley.Response.{ErrorListener, Listener}
import com.android.volley.{AuthFailureError, NetworkError, NoConnectionError, ParseError, ServerError, TimeoutError}
import com.android.volley.toolbox.{JsonObjectRequest, HttpHeaderParser, StringRequest}
import com.android.volley.{DefaultRetryPolicy, NetworkResponse, Response, VolleyError}
import com.anthonycr.grant.{PermissionsResultAction, PermissionsManager}
import org.tosmart.tosmartv.{BuildConfig, R, ShadowsocksApplication}
import org.json.{JSONException, JSONObject}
import java.util.{Locale, HashMap}

import org.tosmart.tosmartv.utils.{AppConfig, Log, ProgressDlg, sessionManager}

/**
  * Created by liu on 27/01/2016.
  */
class ResetPass extends Activity {

  val TAG = "ResetPass"
  var btnResetPwd:Button=_
  var inputEmail:EditText=_
  private var toolbar: Toolbar = _


  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate( savedInstanceState )
    setContentView( R.layout.activity_reset )

    toolbar = findViewById( R.id.toolbar ).asInstanceOf[Toolbar]
    toolbar.setTitle( R.string.title_reset_pwd )
    toolbar.setNavigationIcon( R.drawable.abc_ic_ab_back_material )
    toolbar.setNavigationOnClickListener( (v: View) => {
      val intent = getParentActivityIntent
      if (intent == null) finish() else navigateUpTo(intent)
    } )

    inputEmail = findViewById( R.id.InputEmail ).asInstanceOf[EditText]
    btnResetPwd = findViewById( R.id.btnResetPWD ).asInstanceOf[Button]

    // Login button Click Event
    btnResetPwd.setOnClickListener( (v: View) => {

      val email = inputEmail.getText.toString.trim( )
      // Check for empty data in the form
      if (!email.isEmpty) {
        // login user
        checkEmail(email)
      }
    } )
  }

  def checkEmail(email: String) {
    // Tag used to cancel the request
    val tag_string_req = "req_resetpwd"
    val progress = new ProgressDlg(this)
    progress.showProgress(R.string.hint_Sending)

    Log.v(TAG, AppConfig.URL_RESETPWD)
    val strReq = new StringRequest(Method.POST, AppConfig.URL_RESETPWD,
      new Listener[String]() {
        override def onResponse(response: String) {
          Log.d(TAG, "ResetPWD Response: " + response)
          progress.clearDialog()
          try {
            val jObj = new JSONObject(response)
            Log.v(TAG, "Login Response: " + response)
            //code 0 for error ,code 1 for success
            val ok   = if (jObj.has("ok"))   jObj.getInt("ok") else 0
            val code = if (jObj.has("code")) jObj.getInt("code") else 0
            val msg  = if (jObj.has("msg"))  jObj.getString("msg") else "Something Bad"
            // Check for error node in json
            if (ok == 1 && code == 1) {
              // user successfully Send email
              Toast.makeText(ResetPass.this,msg,Toast.LENGTH_LONG).show()
              startActivity(new Intent(getApplicationContext, classOf[MainActivity]))
              finish()
            } else {
              Toast.makeText(ResetPass.this,msg,Toast.LENGTH_LONG).show()
            }
          } catch {
            case e: NullPointerException =>
              val msg = String.format(getString(R.string.connection_test_error), "no response")
              Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_LONG).show

            case e: JSONException =>
              e.printStackTrace()
              val msg = getString(R.string.connection_test_error, "Json error: " + e.getMessage)
              Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_LONG).show
          }
        }
      },

      new Response.ErrorListener() {

        override def onErrorResponse(error:VolleyError) {
          progress.clearDialog()
          var errMsg = ""
          error match {
            case err: AuthFailureError =>  errMsg = "AuthFailureError: " + error.getMessage
            case err: NetworkError =>      errMsg = "NetworkError: " + error.getMessage
            //case err: NoConnectionError => errMsg = "NoConnectionError: " + error.getMessage
            case err: ParseError =>        errMsg = "ParseError: " + error.getMessage
            case err: ServerError =>       errMsg = "ServerError: " + error.getMessage
            case err: TimeoutError =>      errMsg = "TimeoutError: " + error.getMessage
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
        // Posting parameters to resetpwd url
        val params = new HashMap[String, String]()
        params.put("email",  email)
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
}
