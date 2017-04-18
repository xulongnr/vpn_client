package org.tosmart.tosmartv.Activity_Web

import java.io.IOException
import java.math.BigDecimal
import java.net.URLEncoder
import java.security.spec.X509EncodedKeySpec
import java.security.{KeyFactory, PublicKey}
import java.text.{DecimalFormat, SimpleDateFormat}
import java.util
import java.util.{ArrayList, Date, HashMap, Locale, Random}

import android.app.{Activity, Service}
import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import android.net.Uri
import android.os.Build.VERSION_CODES
import android.os._
import android.support.design.widget.Snackbar
import android.util.Base64
import android.widget.{TableRow, TextView, Toast}
import com.alipay.sdk.app.PayTask
import com.android.volley.Request.Method
import com.android.volley.{ServerError, TimeoutError, _}
import com.android.volley.Response.{ErrorListener, Listener}
import com.android.volley.toolbox.StringRequest
import com.paypal.android.sdk.payments.{PayPalConfiguration, PayPalPayment, PayPalService, PaymentActivity}
import okhttp3.{MediaType, OkHttpClient, Request, RequestBody}
import org.json.{JSONArray, JSONException, JSONObject}
import org.tosmart.tosmartv.{BuildConfig, R, ShadowsocksApplication}
import org.tosmart.tosmartv.database.Payment
import org.tosmart.tosmartv.utils._

import scala.collection.mutable.ArrayBuffer

/**
  * Created by XuLong on 2016/6/30.
  */
object SyncService {
  lazy val TAG = "SyncService"
  lazy val MSG_SYNC_SIGNAL  = 10000
  lazy val MSG_SYNC_PAYMENT = 10001
  lazy val MSG_SYNC_PPP_EXP = 10002
  lazy val MSG_SYNC_BONUS   = 10003
  lazy val MSG_SYNC_NODES   = 10004
  lazy val MSG_SYNC_PLANS   = 10005
  lazy val MSG_SYNC_APPS    = 10006
  lazy val MSG_SYNC_DEVMAPS = 10007
  lazy val MSG_SYNC_DM_DEL  = 10008
  lazy val MSG_SYNC_SIGN    = 10009
  lazy val MSG_SYNC_ALIPAY  = 10010
  lazy val MSG_SYNC_PAYPAL  = 10011
  lazy val MSG_SYNC_REWARD  = 10012
  lazy val MSG_SYNC_UPDATE  = 10013
  lazy val MSG_SYNC_RECORDS = 10014

  lazy val REQUEST_CODE_PAYMENT         = 1
  lazy val REQUEST_CODE_FUTURE_PAYMENT  = 2
  lazy val REQUEST_CODE_PROFILE_SHARING = 3

  var activity: Activity = _
  var instance: SyncService = _
  def getInstance(): SyncService = synchronized {
    instance
  }
  val client: OkHttpClient = new OkHttpClient
  val URLEncoded: MediaType = MediaType.parse("application/x-www-form-urlencoded; charset=utf-8")

  val errListener = new Response.ErrorListener() {
    override def onErrorResponse(error: VolleyError) {
      var errMsg = ""
      error match {
        case err: AuthFailureError =>  errMsg = "AuthFailureError: " + err.getMessage
        case err: NetworkError =>      errMsg = "NetworkError: " + err.getMessage
      //case err: NoConnectionError => errMsg = "NoConnectionError: " + err.getMessage
        case err: ParseError =>        errMsg = "ParseError: " + err.getMessage
        case err: ServerError =>       errMsg = "ServerError: " + err.getMessage
        case err: TimeoutError =>      errMsg = "TimeoutError: " + err.getMessage
        case _ =>                      errMsg = "Error: " + error.getMessage
      }
      Log.e(TAG, errMsg)
      error.printStackTrace()
    }
  }

  def syncPayments(uid: String = "", token: String = "", replyTo: Messenger = null) {
    val payments = new ArrayBuffer[Payment]
    payments ++= ShadowsocksApplication.payManager.getAllNonSyncPayments.getOrElse(List.empty[Payment])
    val session = new sessionManager(SyncService.instance)
    val _u = if (uid.isEmpty) session.uid else uid
    val _t = if (token.isEmpty) session.token else token
    Log.v(TAG, "syncPayments(), uid="+_u+", isLogin="+session.isLogin+", nonSyncPay="+payments.size)
    if (!session.isLogin) {
      val all_payments = new ArrayBuffer[Payment]
      all_payments ++= ShadowsocksApplication.payManager.getAllPayments.getOrElse(List.empty[Payment])
      for (i <- all_payments.indices) {
        ShadowsocksApplication.payManager.delPayment(all_payments(i).id)
      }
      return
    }

    new Thread(() => {
      var updated: Boolean = false
      for (i <- payments.indices) {
        val payment = payments(i)
        val strReq = "uid="+_u+"&token="+_t+
                     "&pid="+payment.pid+
                     "&plan="+payment.plan+
                     "&time="+payment.time+
                     "&state="+payment.state+
                     "&pay_type="+payment.pay_via+
                     "&total_fee="+payment.amount.toString+
                     "&client_version="+ShadowsocksApplication.getVersionName
        val body = RequestBody.create(URLEncoded, strReq)
        val request = new okhttp3.Request.Builder()
                          .url(AppConfig.URL_PAY_SYNC)
                          .post(body)
                          .build()
        try {
          val response = client.newCall(request).execute()
          if (response.isSuccessful) {
            val szRsp = response.body().string()
            val jObj = new JSONObject(szRsp)
            val ok = if (jObj.has("ok")) jObj.getInt("ok") else 0
            val code = if (jObj.has("code")) jObj.getInt("code") else 0
            if (ok == 1 && code == 1) {
              //syncProfile(_u, _t)
              updated = true

              ShadowsocksApplication.payManager.getPayment(payment.pid).getOrElse(None) match {
                case pay: Payment =>
                  pay.sync = true
                  ShadowsocksApplication.payManager.updatePayment(pay)

                case None => Log.e(TAG, "No match payment(" + payment.pid + ") found!")
              }
            } else {
              Log.e(TAG, "payment sync error msg: " + jObj.getString("msg"))
            }
          } else throw new IOException("Unexpected code " + response)
        } catch {
          case e: Exception => e.printStackTrace()
        }
      }
      if (updated && replyTo != null) {
        replyTo.send(Message.obtain(null, MSG_SYNC_PAYMENT))
      }
    }).start()
  }

  def syncBonus(uid: String, token: String, replyTo: Messenger) {
    val strReq = "uid=" + uid + "&token=" + token
    val body = RequestBody.create(URLEncoded, strReq)
    val request = new okhttp3.Request.Builder()
                      .url(AppConfig.URL_GET_BNS)
                      .post(body)
                      .build()
    new Thread(() => {
      try {
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
          val szRsp = response.body().string()
          val jObj = new JSONObject(szRsp)
          val ok = if (jObj.has("ok")) jObj.getInt("ok") else 0
          if (ok == 1) {
            val bonus = jObj.getInt("bonus")
            if (replyTo != null)
              replyTo.send(Message.obtain(null, MSG_SYNC_BONUS, bonus, 0))
          } else
            Log.e(TAG, "update bonus error msg: " + jObj.getString("msg"))
        } else throw new IOException("Unexpected code " + response)
      } catch {
        case e: Exception => e.printStackTrace()
      }
    }).start()
  }

  def syncProfile(uid: String, token: String, replyTo: Messenger = null) {
    val strReq = "uid=" + uid + "&token=" + token
    val body = RequestBody.create(URLEncoded, strReq)
    val request = new okhttp3.Request.Builder()
                      .url(AppConfig.URL_GET_PFU)
                      .post(body)
                      .build()
    val handler = new Handler() {
      override def handleMessage(msg: Message) {
        super.handleMessage(msg)
        if (replyTo != null)
          replyTo.send(Message.obtain(null, MSG_SYNC_UPDATE))
      }
    }
    new Thread(() => {
      try {
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
          val szRsp = response.body().string()
          val jObj = new JSONObject(szRsp)
          if (jObj.has("ok") && jObj.getInt("ok") == 1) {
            val session = new sessionManager(SyncService.instance)
            session.plan = jObj.getString("plan")
            session.rewards = jObj.getInt("bonus")
            session.transfer = jObj.getLong("transfer")
            session.updatePassPort(jObj.getString("pass"), jObj.getString("port"))
            var expire = -1L
            val expireTem = jObj.getString("expire")
            if (!expireTem.isEmpty) {
              val sf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
              expire = sf.parse(expireTem).getTime()
            }
            session.timeExp = expire
            if (session.isVIP)
              session.acctype = 1
            else
              session.acctype = 2
            handler.sendEmptyMessage(0)
          }
        } else throw new IOException("Unexpected code " + response)
      } catch {
        case e: Exception => e.printStackTrace()
      }
    }).start()
  }

  def syncNodes(replyTo: Messenger = null) {
    val request = new okhttp3.Request.Builder()
                      .url(AppConfig.URL_NODES)
                      .build()
    val handler = new Handler() {
      override def handleMessage(msg: Message) {
        super.handleMessage(msg)
        if (replyTo != null)
          replyTo.send(Message.obtain(null, MSG_SYNC_NODES))
      }
    }
    new Thread(() => {
      try {
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
          val szRsp = response.body().string()
          val jObj = new JSONObject(szRsp)
          val profile = ShadowsocksApplication.profileManager.jsonToProfile(jObj)
          if (profile != null) handler.sendEmptyMessage(0)
        } else throw new IOException("Unexpected code " + response)
      } catch {
        case e: Exception => e.printStackTrace()
      }
    }).start()
  }

  def syncPlans(replyTo: Messenger = null) {
    val request = new okhttp3.Request.Builder()
      .url(AppConfig.URL_PLAN)
      .build()
    val handler = new Handler() {
      override def handleMessage(msg: Message) {
        super.handleMessage(msg)
        if (replyTo != null)
          replyTo.send(Message.obtain(null, MSG_SYNC_PLANS))
      }
    }
    new Thread(() => {
      try {
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
          val szRsp = response.body().string()
          val jObj = new JSONArray(szRsp)
          ShadowsocksApplication.planManager.jsonToPlans(jObj)
          handler.sendEmptyMessage(0)
        } else throw new IOException("Unexpected code " + response)
      } catch {
        case e: Exception => e.printStackTrace()
      }
    }).start()
  }

  def syncApps(replyTo: Messenger = null): Unit = {
    val request = new okhttp3.Request.Builder()
                      .url(AppConfig.URL_APPS)
                      .build()
    val handler = new Handler() {
      override def handleMessage(msg: Message) {
        super.handleMessage(msg)
        if (replyTo != null)
          replyTo.send(Message.obtain(null, MSG_SYNC_APPS))
      }
    }
    new Thread(() => {
      try {
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
          val szRsp = response.body().string()
          val jObj = new JSONArray(szRsp)
          ShadowsocksApplication.appManager.jsonToApps(jObj)
          handler.sendEmptyMessage(0)
        } else throw new IOException("Unexpected code " + response)
      } catch {
        case e: Exception => e.printStackTrace()
      }
    }).start()
  }

  def syncDevMaps(uid: String, token: String, replyTo: Messenger = null) {
    val strReq = "uid=" + uid + "&token=" + token
    val body = RequestBody.create(URLEncoded, strReq)
    val request = new okhttp3.Request.Builder()
                      .url(AppConfig.URL_DEVMAP)
                      .post(body)
                      .build()
    new Thread(() => {
      try {
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
          val szRsp = response.body().string()
          val jsonArray = new JSONArray(szRsp)
          val devices = new util.ArrayList[Device]
          for (i <- 0 until jsonArray.length()) {
            val jObj = jsonArray.get(i).asInstanceOf[JSONObject]
            val dev = new Device()
            dev.did = jObj.getString("id").toInt
            dev.devid = jObj.getString("devid")
            dev.model = jObj.getString("model")
            dev.manufacture = jObj.getString("manufacture")
            devices.add(dev)
          }
          if (replyTo != null) {
            val msg = Message.obtain(null, MSG_SYNC_DEVMAPS)
            val bundle = new Bundle()
            bundle.putParcelableArrayList(Device.KEY, devices.asInstanceOf[ArrayList[Parcelable]])
            msg.setData(bundle)
            replyTo.send(msg)
          }
        } else throw new IOException("Unexpected code " + response)
      } catch {
        case e: Exception => e.printStackTrace()
      }
    }).start()
  }

  def syncDmDel(uid: String, token: String, did: Int, replyTo: Messenger = null) {
    val strReq = "uid=" + uid + "&token=" + token + "&did=" + did.toString
    val body = RequestBody.create(URLEncoded, strReq)
    val request = new okhttp3.Request.Builder()
                      .url(AppConfig.URL_DEL_DM)
                      .post(body)
                      .build()
    new Thread(() => {
      try {
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
          val szRsp = response.body().string()
          val jObj = new JSONObject(szRsp)
          val ok = if (jObj.has("ok")) jObj.getInt("ok") else 0
          if (ok == 1) {
            if (replyTo != null)
              replyTo.send(Message.obtain(null, MSG_SYNC_DM_DEL))
          } else {
            Log.e(TAG, "failed to del devmap msg: "+jObj.getString("msg"))
          }
        } else throw new IOException("Unexpected code " + response)
      } catch {
        case e: Exception => e.printStackTrace()
      }
    }).start()
  }

  def syncPayRecords(uid: String, token: String, replyTo: Messenger = null) {
    val strReq = "uid=" + uid + "&token=" + token
    val body = RequestBody.create(URLEncoded, strReq)
    val request = new okhttp3.Request.Builder()
                      .url(AppConfig.URL_PAY_HIST)
                      .post(body)
                      .build()
    new Thread(() => {
      try {
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
          val szRsp = response.body().string()
          Log.v(TAG, "history response: " + szRsp)
          val jsonArray = new JSONArray(szRsp)
          val records = new util.ArrayList[PayRecord]
          for (i <- 0 until jsonArray.length()) {
            val jObj = jsonArray.getJSONObject(i)
            val record = new PayRecord()
            record.payId = jObj.getString("payid")
            record.payTime = jObj.getString("paydate")
            record.payType = jObj.getString("paytype")
            record.payPlan = jObj.getString("plan")
            record.payPromo = jObj.getString("promo")
            record.payAmount = jObj.getString("money_rmb")
            if (record.payAmount == "0.00")
              record.payAmount = "$" + jObj.getString("money_usd")
            records.add(record)
          }
          if (replyTo != null) {
            val msg = Message.obtain(null, MSG_SYNC_RECORDS)
            val bundle = new Bundle()
            bundle.putParcelableArrayList(PayRecord.KEY, records.asInstanceOf[ArrayList[Parcelable]])
            msg.setData(bundle)
            replyTo.send(msg)
          }
        } else throw new IOException("Unexpected code " + response)
      } catch {
        case e: Exception => e.printStackTrace()
      }
    }).start()
  }

  def syncReqSign(req: String, replyTo: Messenger = null) {
    val handler = new Handler() {
      override def handleMessage(msg: Message) {
        super.handleMessage(msg)
        if (replyTo != null) {
          val new_msg = Message.obtain(null, MSG_SYNC_SIGN)
//          val bundle = new Bundle()
//          bundle.putParcelable(RspString.KEY, new RspString(msg.obj.asInstanceOf[String]))
//          new_msg.setData(bundle)
          new_msg.obj = msg.obj
          replyTo.send(new_msg)
        }
      }
    }
    new Thread(() => {
      try {
        val strReq = "content=\"" + Base64.encodeToString(req.getBytes("UTF-8"), Base64.NO_WRAP | Base64.URL_SAFE)
        val body = RequestBody.create(URLEncoded, strReq)
        val request = new okhttp3.Request.Builder().url(AppConfig.URL_SIGN).post(body).build()
        val response = client.newCall(request).execute()
        if (response.isSuccessful) handler.sendMessage(Message.obtain(null, 0, response.body().string()))
      } catch {
        case e: Exception => e.printStackTrace(); Log.e(TAG, "Exception: " + e.getMessage)
      }
    }).start()
  }

  def syncAlipay(plan: String, orderInfo: String, replyTo: Messenger = null,
                 activity: Activity = null, prg_dlg: ProgressDlg = null) {
    if (null == activity) {
      Log.e(TAG, "activity=null, exit")
      return
    }
    this.activity = activity
    try {
      new AlipayAsyncTask().execute(plan, orderInfo, replyTo, prg_dlg)
    } catch {
      case e: Exception => e.printStackTrace(); Log.e(TAG, "Exception: " + e.getMessage)
    }
  }

  def syncPaypal(payTotal: String, payInfo: String, activity: Activity = null) {
    if (null == activity) {
      Log.e(TAG, "activity=null, exit")
      return
    }
    this.activity = activity

    /**
      * - Set to PayPalConfiguration.ENVIRONMENT_PRODUCTION to move real money.
      *
      * - Set to PayPalConfiguration.ENVIRONMENT_SANDBOX to use your test credentials
      * from https://developer.paypal.com
      *
      * - Set to PayPalConfiguration.ENVIRONMENT_NO_NETWORK to kick the tires
      * without communicating to PayPal's servers.
      */
    var CONFIG_CLIENT_ID: String = ""
    var CONFIG_ENVIRONMENT: String = ""
    if (BuildConfig.DEBUG) {
      CONFIG_CLIENT_ID = "AYfOcrAou1mLCTNbTNPIkDPAGfLT_V-jeC0S15FRVIXkHKWyRZxgPacmLXsyh6UEtsbEDCg91rfXJaDH"
      CONFIG_ENVIRONMENT = PayPalConfiguration.ENVIRONMENT_SANDBOX
    } else {
      CONFIG_CLIENT_ID = "AcQkROllNadA6lWz7UsZAiqEmwJKeHNJvqdGC5zXqSyLZxulT9xxonuuXHXoHhH_ns1YGuYVdkXfZnYX"
      CONFIG_ENVIRONMENT = PayPalConfiguration.ENVIRONMENT_PRODUCTION
    }

    val intent = new Intent(this.activity, classOf[PayPalService])
    val config = new PayPalConfiguration()
      .environment(CONFIG_ENVIRONMENT)
      .clientId(CONFIG_CLIENT_ID)
      .merchantName("To-Smart Corp")
      .merchantPrivacyPolicyUri(Uri.parse("https://www.tosmart.org/privacy"))
      .merchantUserAgreementUri(Uri.parse("https://www.tosmart.org/legal"))
    intent.putExtra(PayPalService.EXTRA_PAYPAL_CONFIGURATION, config)
    this.activity.startService(intent)

    //intent = new Intent(this.activity, classOf[PaymentActivity])
    //intent.putExtra(PayPalService.EXTRA_PAYPAL_CONFIGURATION, config)
    intent.setClass(this.activity, classOf[PaymentActivity])

    /**
     * PAYMENT_INTENT_SALE will cause the payment to complete immediately.
     * Change PAYMENT_INTENT_SALE to
     *   - PAYMENT_INTENT_AUTHORIZE to only authorize payment and capture funds later.
     *   - PAYMENT_INTENT_ORDER to create a payment for authorization and capture
     *     later via calls from your server.
     */
    val thingToBuy = new PayPalPayment(new BigDecimal(payTotal), "USD", payInfo,
      PayPalPayment.PAYMENT_INTENT_SALE)
    intent.putExtra(PaymentActivity.EXTRA_PAYMENT, thingToBuy)
    this.activity.startActivityForResult(intent, REQUEST_CODE_PAYMENT)
  }
}

class SyncService extends Service {
  lazy val TAG = "SyncService"

  val messenger = new Messenger(new Handler() {
    override def handleMessage(msg: Message) {
      Log.v(TAG, "msg.what="+msg.what+", replyTo="+msg.replyTo)
      val bundle = msg.getData
      bundle.setClassLoader(User.getClass.getClassLoader)
      val user = bundle.getParcelable[User](User.KEY)
      msg.what match {
        case SyncService.MSG_SYNC_PAYMENT =>
          SyncService.syncPayments(user.id, user.token, msg.replyTo)

        case SyncService.MSG_SYNC_BONUS =>
          SyncService.syncBonus(user.id, user.token, msg.replyTo)

        case SyncService.MSG_SYNC_UPDATE =>
          SyncService.syncProfile(user.id, user.token, msg.replyTo)

        case SyncService.MSG_SYNC_NODES =>
          SyncService.syncNodes(msg.replyTo)

        case SyncService.MSG_SYNC_PLANS =>
          SyncService.syncPlans(msg.replyTo)

        case SyncService.MSG_SYNC_APPS =>
          SyncService.syncApps(msg.replyTo)

        case SyncService.MSG_SYNC_DEVMAPS =>
          SyncService.syncDevMaps(user.id, user.token, msg.replyTo)

        case SyncService.MSG_SYNC_DM_DEL =>
          SyncService.syncDmDel(user.id, user.token, msg.arg1, msg.replyTo)

        case SyncService.MSG_SYNC_SIGN =>
          val rsp = msg.obj.asInstanceOf[String]
          Log.v(TAG, "string to sign: " + rsp)
          SyncService.syncReqSign(rsp, msg.replyTo)
//          val bundle = msg.getData
//          val rsp = bundle.getParcelable[RspString](RspString.KEY)
//          Log.v(TAG, "string to sign: " + rsp.response)
//          SyncService.syncReqSign(rsp.response, msg.replyTo)

        case SyncService.MSG_SYNC_RECORDS =>
          SyncService.syncPayRecords(user.id, user.token, msg.replyTo)

        case _ =>
          super.handleMessage(msg)
      }
    }
  })

  override def onBind(intent: Intent): IBinder = {
    messenger.getBinder
  }

  override def onCreate() {
    Log.v(TAG, "onCreate")
    SyncService.instance = this
    //SyncService.session = new sessionManager(this)
    super.onCreate()
    //Toast.makeText(this, "SyncService started.", Toast.LENGTH_SHORT).show

    if (!closeReceiverRegistered) {
      // register close receiver
      val filter = new IntentFilter()
      filter.addAction(Intent.ACTION_SHUTDOWN)
      filter.addAction(Action.CLOSE)
      registerReceiver(closeReceiver, filter)
      closeReceiverRegistered = true
    }

    new Thread(() => {
      SyncService.syncPayments()
      Thread.sleep(60*60*1000)
    }).start()
  }

  override def onDestroy() {
    Log.v(TAG, "onDestroy")
    super.onDestroy()
    //Toast.makeText(this, "SyncService stopped.", Toast.LENGTH_SHORT).show
    if (closeReceiverRegistered) {
      unregisterReceiver(closeReceiver)
      closeReceiverRegistered = false
    }
  }

  def StopSyncService() {
    Log.v(TAG, "StopSyncService")
    if (closeReceiverRegistered) {
      unregisterReceiver(closeReceiver)
      closeReceiverRegistered = false
    }
    stopSelf()
  }

  val closeReceiver: BroadcastReceiver =
    (context: Context, intent: Intent) => {
      SyncService.instance.StopSyncService()
    }

  var closeReceiverRegistered: Boolean = _
}

object Device {
  final val KEY = "device"
  final val CREATOR = new Parcelable.Creator[Device]() {
    override def newArray(size: Int): Array[Device] = {
      new Array[Device](size)
    }

    override def createFromParcel(source: Parcel): Device = {
      new Device(source)
    }
  }
}

class Device extends Parcelable {
  var did: Int = _
  var devid: String = _
  var model: String = _
  var manufacture: String = _
  def this(in: Parcel) {
    this()
    did = in.readInt()
    devid = in.readString()
    model = in.readString()
    manufacture = in.readString()
  }
  override def writeToParcel(out: Parcel, flags: Int)  {
    out.writeInt(did)
    out.writeString(devid)
    out.writeString(model)
    out.writeString(manufacture)
  }
  override def describeContents(): Int = { 0 }
}

object User {
  final val KEY = "user"
  final val CREATOR = new Parcelable.Creator[User]() {
    override def newArray(size: Int): Array[User] = {
      new Array[User](size)
    }
    override def createFromParcel(source: Parcel): User = {
      new User(source)
    }
  }
}
class User extends Parcelable {
  var id: String = _
  var token: String = _
  def this(id: String, token: String) {
    this()
    this.id = id
    this.token = token
  }
  def this(in: Parcel) {
    this()
    id = in.readString()
    token = in.readString()
  }
  override def writeToParcel(out: Parcel, flags: Int)  {
    out.writeString(id)
    out.writeString(token)
  }
  override def describeContents(): Int = { 0 }
}

object RspString {
  final val KEY = "response"
  final val CREATOR = new Parcelable.Creator[RspString]() {
    override def newArray(size: Int): Array[RspString] = {
      new Array[RspString](size)
    }
    override def createFromParcel(source: Parcel): RspString = {
      new RspString(source)
    }
  }
}
class RspString extends Parcelable {
  var response: String = _
  def this(response: String) {
    this()
    this.response = response
  }
  def this(in: Parcel) {
    this()
    response = in.readString()
  }

  override def writeToParcel(out: Parcel, flags: Int) = {
    out.writeString(response)
  }
  override def describeContents() = 0
}

object PayRecord {
  final val KEY = "pay_record"
  final val CREATOR = new Parcelable.Creator[PayRecord]() {
    override def newArray(size: Int): Array[PayRecord] = {
      new Array[PayRecord](size)
    }
    override def createFromParcel(source: Parcel): PayRecord = {
      new PayRecord(source)
    }
  }
}
class PayRecord extends Parcelable {
  var payId: String = _
  var payTime: String = _
  var payType: String = _
  var payPlan: String = _
  var payPromo: String = _
  var payAmount: String = _
  def this(in: Parcel) {
    this()
    payId = in.readString()
    payTime = in.readString()
    payType = in.readString()
    payPlan = in.readString()
    payPromo = in.readString()
    payAmount = in.readString()
  }
  override def writeToParcel(out: Parcel, flags: Int) = {
    out.writeString(payId)
    out.writeString(payTime)
    out.writeString(payType)
    out.writeString(payPlan)
    out.writeString(payPromo)
    out.writeString(payAmount)
  }
  override def describeContents() = 0
}

object AlipayInfo {
  final val KEY = "alipay"
  final val CREATOR = new Parcelable.Creator[AlipayInfo]() {
    override def newArray(size: Int): Array[AlipayInfo] = {
      new Array[AlipayInfo](size)
    }
    override def createFromParcel(source: Parcel): AlipayInfo = {
      new AlipayInfo(source)
    }
  }
}
class AlipayInfo extends Parcelable {
  var plan: String = _
  var orderInfo: String = _
  def this(plan: String, orderInfo: String) {
    this()
    this.plan = plan
    this.orderInfo = orderInfo
  }
  def this(in: Parcel) {
    this()
    plan = in.readString()
    orderInfo = in.readString()
  }
  override def writeToParcel(out: Parcel, flags: Int) = {
    out.writeString(plan)
    out.writeString(orderInfo)
  }
  override def describeContents() = 0
}

object AlipayAsyncTask {
  def getSignType: String = "sign_type=\"RSA\""

  def getOutTradeNo(uid: String): String = {
    val format: SimpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault)
    val date = new Date
    var key: String = format.format(date)
    val r: Random = new Random
    key = key + Math.abs(r.nextInt)
    key = key.substring(0, 18)
    key + "-" + uid
  }

  def getSDKVersion {
    val payTask: PayTask = new PayTask(SyncService.activity)
    val version: String = payTask.getVersion
    Toast.makeText(SyncService.activity, version, Toast.LENGTH_SHORT).show
  }

  def getOrderInfo(uid: String, subject: String, body: String, price: String): String = {
    // 签约合作者身份ID
    var orderInfo: String = "partner=" + "\"" + AliPay.PARTNER + "\""

    // 签约卖家支付宝账号
    orderInfo += "&seller_id=" + "\"" + AliPay.SELLER + "\""

    // 商户网站唯一订单号
    orderInfo += "&out_trade_no=" + "\"" + getOutTradeNo(uid) + "\""

    // 商品名称
    orderInfo += "&subject=" + "\"" + subject + "\""

    // 商品详情
    orderInfo += "&body=" + "\"" + body + "\""

    // 商品金额
    orderInfo += "&total_fee=" + "\"" + price + "\""

    // 服务器异步通知页面路径
    orderInfo += "&notify_url=" + "\"" + AppConfig.URL_NOTIFY + "\""

    // 服务接口名称， 固定值
    orderInfo += "&service=\"mobile.securitypay.pay\""

    // 支付类型， 固定值
    orderInfo += "&payment_type=\"1\""

    // 参数编码， 固定值
    orderInfo += "&_input_charset=\"utf-8\""

    // 设置未付款交易的超时时间
    // 默认30分钟，一旦超时，该笔交易就会自动被关闭。
    // 取值范围：1m～15d。
    // m-分钟，h-小时，d-天，1c-当天（无论交易何时创建，都在0点关闭）。
    // 该参数数值不接受小数点，如1.5h，可转换为90m。
    orderInfo += "&it_b_pay=\"30m\""

    // extern_token为经过快登授权获取到的alipay_open_id,带上此参数用户将使用授权的账户进行支付
    // orderInfo += "&extern_token=" + "\"" + extern_token + "\"";
    // 支付宝处理完请求后，当前页面跳转到商户指定页面的路径，可空
    orderInfo += "&return_url=\"m.alipay.com\""

    // 调用银行卡支付，需配置此参数，参与签名， 固定值 （需要签约《无线银行卡快捷支付》才能使用）
    // orderInfo += "&paymethod=\"expressGateway\""

    orderInfo
  }
}
class AlipayAsyncTask extends AsyncTask[AnyRef, AnyRef, AnyRef] {
  final val TAG = "AlipayAsyncTask"
  var strURLParamsOrig: String = ""
  var strURLParamsEnc: String = ""
  var payPlan: String = ""
  var replyTo: Messenger = _
  var prg_dlg: ProgressDlg = _
  def doInBackground(params: AnyRef*): AnyRef = {
    payPlan = params(0).asInstanceOf[String]
    replyTo = params(2).asInstanceOf[Messenger]
    prg_dlg = params(3).asInstanceOf[ProgressDlg]
    strURLParamsOrig = params(1).asInstanceOf[String]
    strURLParamsEnc  = URLEncoder.encode(strURLParamsOrig, "UTF-8")
    val URLEncoded: MediaType = MediaType.parse("application/x-www-form-urlencoded; charset=utf-8")
    try {
      val client: OkHttpClient = new OkHttpClient
      val strReq = "content=\"" + Base64.encodeToString(strURLParamsEnc.getBytes("UTF-8"), Base64.NO_WRAP|Base64.URL_SAFE)
      val body: RequestBody = RequestBody.create(URLEncoded, strReq)
      Log.v(TAG, "strReq="+strReq)

      val request: okhttp3.Request = new okhttp3.Request.Builder()
                                        .url(AppConfig.URL_SIGN)
                                        .post(body)
                                        .build
      val response = client.newCall(request).execute
      if (response.isSuccessful) return response.body.string
      else throw new IOException("Unexpected code " + response)
    }
    catch {
      case e: Exception => e.printStackTrace()
    }
    null
  }

  override def onPostExecute(s: AnyRef) {
    super.onPostExecute(s)
    try {
      val response: JSONObject = new JSONObject(s.asInstanceOf[String])
      val code: Int = response.getInt("ok")
      if (code == 1) {
        var signed: String = response.getString("sign")
        val keyFactory: KeyFactory = KeyFactory.getInstance("RSA")
        val encodedKey: Array[Byte] = Base64.decode(AliPay.RSA_PUBKEY_TOS, Base64.NO_WRAP)
        val signature = java.security.Signature.getInstance("SHA1WithRSA")
        val pubKey: PublicKey = keyFactory.generatePublic(new X509EncodedKeySpec(encodedKey))
        signature.initVerify(pubKey)
        signature.update(strURLParamsOrig.getBytes("UTF-8"))
        val bVerify = signature.verify(Base64.decode(signed, Base64.NO_WRAP))
        if (bVerify) {
          if (prg_dlg != null) prg_dlg.clearDialog()
          signed = URLEncoder.encode(signed, "UTF-8")
          val alipayInfo = strURLParamsOrig + "&sign=\"" + signed + "\"&" + AlipayAsyncTask.getSignType
          new Thread(() => {
            val aliPay: PayTask = new PayTask(SyncService.activity)
            Log.v(TAG, "payInfo: " + alipayInfo)
            val result = aliPay.pay(alipayInfo, true)
            Log.v(TAG, "result: " + result)
            val msg: Message = new Message
            msg.what = AliPay.SDK_PAY_FLAG
            msg.obj = result
            msg.replyTo = replyTo
            handler.sendMessage(msg)
          }).start()
        } else {
          Log.e(TAG, "failed to verify the signature!")
        }
      } else {
        Log.e(TAG, "failed to sign, errMsg: " + response.getString("msg"))
      }
      if (prg_dlg != null) prg_dlg.clearDialog()
    } catch {
      case e: Exception =>
        e.printStackTrace
        if (prg_dlg != null) prg_dlg.clearDialog()
    }
  }

  val handler: Handler = new Handler() {
    override def handleMessage(msg: Message) {
      super.handleMessage(msg)
      msg.what match {
        case AliPay.SDK_CHECK_FLAG =>

        case AliPay.SDK_PAY_FLAG =>
          val payResult = new PayResult(msg.obj.asInstanceOf[String])
          val resultForSign = payResult.getResultForSign

          // verify with AliPay's public key
          var bVerify: Boolean = false
          try {
            val keyFactory: KeyFactory = KeyFactory.getInstance("RSA")
            val encodedKey: Array[Byte] = Base64.decode(AliPay.RSA_PUBKEY_ALI, Base64.NO_WRAP)
            val signature = java.security.Signature.getInstance("SHA1WithRSA")
            val pubKey: PublicKey = keyFactory.generatePublic(new X509EncodedKeySpec(encodedKey))
            signature.initVerify(pubKey)
            signature.update(resultForSign.getBytes("UTF-8"))
            val sign = payResult.getSign
            bVerify = signature.verify(Base64.decode(sign, Base64.NO_WRAP))
          }
          catch {
            case e: Exception => e.printStackTrace
          }
          if (!bVerify) {
            Toast.makeText(SyncService.activity, R.string.pay_result_failed, Toast.LENGTH_SHORT).show
            return
          }

          payResult.getResultStatus match {
            case "9000" =>

              val payment = new Payment
              payment.pid = payResult.getParamValue(resultForSign, "out_trade_no")
              payment.amount = payResult.getParamValue(resultForSign, "total_fee").toDouble
              payment.state = payResult.getParamValue(resultForSign, "success")
              payment.pay_via = "Alipay"
              val now = java.util.Calendar.getInstance().getTime()
              val sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
              payment.time = sdf.format(now)
              payment.plan = payPlan
              ShadowsocksApplication.payManager.addPayment(payment)

              // To Sync payment
              val msg = Message.obtain(null, SyncService.MSG_SYNC_ALIPAY)
              msg.replyTo = replyTo
              replyTo.send(msg)

            case "8000" =>
              Snackbar.make(SyncService.activity.findViewById(android.R.id.content), R.string.pay_result_confirming, Snackbar.LENGTH_LONG).show

            case _ =>
              var msg = SyncService.activity.getString(R.string.pay_result_failed)
              val memo = payResult.getMemo
              if (!memo.isEmpty) msg += ": " + memo
              Snackbar.make(SyncService.activity.findViewById(android.R.id.content), msg, Snackbar.LENGTH_LONG).show
          }
      }
    }
  }
}