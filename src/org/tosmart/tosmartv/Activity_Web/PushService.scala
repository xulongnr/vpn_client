package org.tosmart.tosmartv.Activity_Web

/**
  * Created by Xu Long on 7/18/16.
  */

import android.app
import android.os.{IBinder, StrictMode, Vibrator}
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.ContextCompat
import com.ibm.mqtt.IMqttClient
import com.ibm.mqtt.MqttClient
import com.ibm.mqtt.MqttException
import com.ibm.mqtt.MqttPersistence
import com.ibm.mqtt.MqttPersistenceException
import com.ibm.mqtt.MqttSimpleCallback
import android.app._
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.telephony.TelephonyManager
import com.anthonycr.grant.{PermissionsManager, PermissionsResultAction}
import org.tosmart.tosmartv.{Activity_Web, R, Shadowsocks, ShadowsocksApplication}
import org.tosmart.tosmartv.utils._

object PushService {
  val TAG: String = "PushService"
  private val MQTT_HOST: String =AppConfig.URL_SERVER
  private val MQTT_BROKER_PORT_NUM: Int = 1883
  private val MQTT_PERSISTENCE: MqttPersistence = null
  private val MQTT_CLEAN_START: Boolean = true
  private val MQTT_KEEP_ALIVE: Short = 900
  private val MQTT_QUALITIES_OF_SERVICE: Array[Int] = Array(0)
  private val MQTT_QUALITY_OF_SERVICE: Int = 0
  private val MQTT_RETAINED_PUBLISH: Boolean = false
  private val MQTT_CLIENT_ID   = "tosv"
  private val ACTION_START     = MQTT_CLIENT_ID + ".START"
  private val ACTION_STOP      = MQTT_CLIENT_ID + ".STOP"
  private val ACTION_KEEPALIVE = MQTT_CLIENT_ID + ".KEEP_ALIVE"
  private val ACTION_RECONNECT = MQTT_CLIENT_ID + ".RECONNECT"
  private val KEEP_ALIVE_INTERVAL: Long = 1000 * 60 * 28
  private val INITIAL_RETRY_INTERVAL: Long = 1000 * 10
  private val MAXIMUM_RETRY_INTERVAL: Long = 1000 * 60 * 30
  val PREF_STARTED: String = "isStarted"
  val PREF_DEVICE_ID: String = "deviceID"
  val PREF_RETRY: String = "retryInterval"
  var NOTIFY_TITLE: String = "ToSmartVPN"

  def actionStart(ctx: Context) {
    val i: Intent = new Intent(ctx, classOf[PushService])
    i.setAction(ACTION_START)
    ctx.startService(i)
  }

  def actionStop(ctx: Context) {
    val i: Intent = new Intent(ctx, classOf[PushService])
    i.setAction(ACTION_STOP)
    ctx.startService(i)
  }

  def actionPing(ctx: Context) {
    val i: Intent = new Intent(ctx, classOf[PushService])
    i.setAction(ACTION_KEEPALIVE)
    ctx.startService(i)
  }
}

class PushService extends Service {
  private var mConnMan: ConnectivityManager = null
  private var mNotifMan: NotificationManager = null
  private var mStarted: Boolean = false
  private var mPrefs: SharedPreferences = null
  private var mConnection: MQTTConnection = null
  private var mStartTime: Long = 0L
  private val PRIVATE_MODE = 0
  private val TAG = "PushService"

  override def onCreate() {
    super.onCreate()
    Log.v(TAG, "Creating service")
    mStartTime = System.currentTimeMillis
    mPrefs = getSharedPreferences(PushService.TAG, PRIVATE_MODE)
    mConnMan = getSystemService(Context.CONNECTIVITY_SERVICE).asInstanceOf[ConnectivityManager]
    mNotifMan = getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager]
    handleCrashedService
  }

  def handleCrashedService() {
    if (wasStarted) {
      Log.v(TAG, "Handling crashed service...")
      stopKeepAlives
      start
    }
  }

  override def onDestroy() {
    Log.v(TAG, "Service destroyed (started=" + mStarted + ")")
    if (mStarted) {
      stop
    }
  }

  override def onStart(intent: Intent, startId: Int) {
    super.onStart(intent, startId)
    Log.v(TAG, "Service started with intent=" + intent + ", startId=" + startId)
    if (null == intent) return
    intent.getAction match {
      case PushService.ACTION_STOP      => stop; stopSelf
      case PushService.ACTION_START     => start
      case PushService.ACTION_KEEPALIVE => keepAlive
      case PushService.ACTION_RECONNECT => if (isNetworkAvailable) reconnectIfNecessary
    }
  }

  def onBind(intent: Intent): IBinder = {
    null
  }

  def wasStarted(): Boolean = {
    mPrefs.getBoolean(PushService.PREF_STARTED, false)
  }

  def setStarted(started: Boolean) {
    mPrefs.edit.putBoolean(PushService.PREF_STARTED, started).commit
    mStarted = started
  }

  def start() {
    Log.v(TAG, "Starting service...")
    if (mStarted) {
      Log.w(PushService.TAG, "Attempt to start connection that is already active")
      return
    }
    connect
    registerReceiver(mConnectivityChanged, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
    registerReceiver(mLoginStateChanged, new IntentFilter(Action.LOGOUT))
    registerReceiver(mLoginStateChanged, new IntentFilter(Action.LOGIN))
  }

  def stop() {
    if (!mStarted) {
      Log.w(PushService.TAG, "Attempt to stop connection not active.")
      return
    }
    setStarted(false)
    unregisterReceiver(mConnectivityChanged)
    unregisterReceiver(mLoginStateChanged)
    cancelReconnect
    if (mConnection != null) {
      mConnection.disconnect
      mConnection = null
    }
  }

  def connect() {
    Log.v(TAG, "Connecting...")
    val deviceID: String = mPrefs.getString(PushService.PREF_DEVICE_ID, null)
    if (deviceID == null) {
      Log.v(TAG, "Device ID not found.")
    }
    else {
      if (android.os.Build.VERSION.SDK_INT > 9) {
        val policy = new StrictMode.ThreadPolicy.Builder().permitAll.build
        StrictMode.setThreadPolicy(policy)
      }
      try {
        mConnection = new MQTTConnection(PushService.MQTT_HOST, deviceID)
      }
      catch {
        case e: MqttException =>
          Log.v(TAG, "MqttException: " + (if (e.getMessage != null) e.getMessage else "NULL"))
          e.printStackTrace
          if (isNetworkAvailable) {
            scheduleReconnect(mStartTime)
          }
      }
      setStarted(true)
    }
  }

  def keepAlive() {
    try {
      if (mStarted && mConnection != null) {
        mConnection.sendKeepAlive
      }
    }
    catch {
      case e: MqttException =>
        Log.v(TAG, "MqttException: " + (if (e.getMessage != null) e.getMessage else "NULL"), e)
        mConnection.disconnect
        mConnection = null
        cancelReconnect
    }
  }

  def startKeepAlives() {
    val i: Intent = new Intent
    i.setClass(this, classOf[PushService])
    i.setAction(PushService.ACTION_KEEPALIVE)
    val pi: PendingIntent = PendingIntent.getService(this, 0, i, 0)
    val alarmMgr: AlarmManager = getSystemService(Context.ALARM_SERVICE).asInstanceOf[AlarmManager]
    alarmMgr.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis, PushService.KEEP_ALIVE_INTERVAL, pi)
  }

  def stopKeepAlives() {
    val i: Intent = new Intent
    i.setClass(this, classOf[PushService])
    i.setAction(PushService.ACTION_KEEPALIVE)
    val pi: PendingIntent = PendingIntent.getService(this, 0, i, 0)
    val alarmMgr: AlarmManager = getSystemService(Context.ALARM_SERVICE).asInstanceOf[AlarmManager]
    alarmMgr.cancel(pi)
  }

  def scheduleReconnect(startTime: Long) {
    var interval: Long = mPrefs.getLong(PushService.PREF_RETRY, PushService.INITIAL_RETRY_INTERVAL)
    val now: Long = System.currentTimeMillis
    val elapsed: Long = now - startTime
    if (elapsed < interval) {
      interval = Math.min(interval * 4, PushService.MAXIMUM_RETRY_INTERVAL)
    } else {
      interval = PushService.INITIAL_RETRY_INTERVAL
    }
    Log.v(TAG, "Rescheduling connection in " + interval + "ms.")
    mPrefs.edit.putLong(PushService.PREF_RETRY, interval).commit
    val i: Intent = new Intent
    i.setClass(this, classOf[PushService])
    i.setAction(PushService.ACTION_RECONNECT)
    val pi: PendingIntent = PendingIntent.getService(this, 0, i, 0)
    val alarmMgr: AlarmManager = getSystemService(Context.ALARM_SERVICE).asInstanceOf[AlarmManager]
    alarmMgr.set(AlarmManager.RTC_WAKEUP, now + interval, pi)
  }

  def cancelReconnect() {
    val i: Intent = new Intent
    i.setClass(this, classOf[PushService])
    i.setAction(PushService.ACTION_RECONNECT)
    val pi: PendingIntent = PendingIntent.getService(this, 0, i, 0)
    val alarmMgr: AlarmManager = getSystemService(Context.ALARM_SERVICE).asInstanceOf[AlarmManager]
    alarmMgr.cancel(pi)
  }

  def reconnectIfNecessary() {
    if (mStarted && mConnection == null) {
      Log.v(TAG, "Reconnecting...")
      connect()
    }
  }

  val mLoginStateChanged: BroadcastReceiver = new BroadcastReceiver {
    override def onReceive(context: Context, intent: Intent) {
      Log.v(TAG, "mLoginStateChanged:OnReceive() sending keepalive now.")
      keepAlive()
    }
  }

  val mConnectivityChanged: BroadcastReceiver = new BroadcastReceiver() {
    def onReceive(context: Context, intent: Intent) {
      val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE).asInstanceOf[ConnectivityManager]
      val n1 = cm.getActiveNetworkInfo()

//      val info = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO).asInstanceOf[NetworkInfo]
//      val hasConnectivity0 = if (info != null && info.isConnected) true else false
//      Log.v(TAG, "Connectivity("+info.getTypeName+") changed: connected=" + hasConnectivity0)

      val hasConnectivity = if (n1 != null && n1.isConnected) true else false
      val ConnectivityName = if (n1 != null) n1.getTypeName else "Unknown"
      Log.v(TAG, "Connectivity(" + ConnectivityName + ") changed: connected=" + hasConnectivity)

      if (hasConnectivity) {
        reconnectIfNecessary()
      }
      else if (mConnection != null) {
        mConnection.disconnect()
        cancelReconnect()
        mConnection = null
      }
    }
  }

  def showNotification(id: Int, text: String, intent: Intent, priority: Int=NotificationCompat.PRIORITY_DEFAULT) {
    val builder: NotificationCompat.Builder = new NotificationCompat.Builder(this)
    builder.setWhen(System.currentTimeMillis)
           .setColor(ContextCompat.getColor(this, R.color.material_accent_500))
         //.setContentTitle(PushService.NOTIFY_TITLE)
           .setContentTitle(getString(R.string.screen_name))
           .setContentText(text)
           .setAutoCancel(true)
           .setDefaults(Notification.DEFAULT_ALL)
           .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
           .setContentIntent(PendingIntent.getActivity(this, 0, intent, 0))
           .setSmallIcon(R.drawable.ic_stat_tosmartv)
           .setPriority(priority)
    NotificationManagerCompat.from(this).notify(id, builder.build)
  }

  def isNetworkAvailable: Boolean = {
    val info: NetworkInfo = mConnMan.getActiveNetworkInfo
    if (info == null) {
      return false
    }
    info.isConnected
  }

  // This inner class is a wrapper on top of MQTT client.
  class MQTTConnection(brokerHostName: String, var initTopic: String) extends MqttSimpleCallback {
    var mqttClient: IMqttClient = _

    val mqttConnSpec = "tcp://" + brokerHostName + ":" + PushService.MQTT_BROKER_PORT_NUM
    val clientID = PushService.MQTT_CLIENT_ID + "/" + mPrefs.getString(PushService.PREF_DEVICE_ID, "")
    mqttClient = MqttClient.createMqttClient(mqttConnSpec, PushService.MQTT_PERSISTENCE)
    mqttClient.connect(clientID, PushService.MQTT_CLEAN_START, PushService.MQTT_KEEP_ALIVE)
    mqttClient.registerSimpleHandler(this)
    initTopic = PushService.MQTT_CLIENT_ID + "/" + initTopic
    subscribeToTopic(initTopic)
    Log.v(TAG, "Connection established to " + brokerHostName + " on topic " + initTopic)
    mStartTime = System.currentTimeMillis
    startKeepAlives()

    def disconnect() {
      try {
        stopKeepAlives()
        mqttClient.disconnect()
      }
      catch {
        case e: MqttPersistenceException =>
          Log.v(TAG, "MqttException" + (if (e.getMessage != null) e.getMessage else " NULL"), e)
      }
    }

    @throws[MqttException]
    def subscribeToTopic(topicName: String) {
      if ((mqttClient == null) || (!mqttClient.isConnected)) {
        Log.v(TAG, "Connection error" + "No connection")
      }
      else {
        val topics: Array[String] = Array(topicName)
        mqttClient.subscribe(topics, PushService.MQTT_QUALITIES_OF_SERVICE)
      }
    }

    @throws[MqttException]
    def publishToTopic(topicName: String, message: String) {
      if ((mqttClient == null) || (!mqttClient.isConnected)) {
        Log.v(TAG, "No connection to public to")
      } else {
        mqttClient.publish(topicName, message.getBytes, PushService.MQTT_QUALITY_OF_SERVICE, PushService.MQTT_RETAINED_PUBLISH)
      }
    }

    @throws[Exception]
    def connectionLost() {
      Log.v(TAG, "Loss of connection, " + "connection downed")
      stopKeepAlives()
      mConnection = null
      if (isNetworkAvailable) {
        reconnectIfNecessary()
      }
    }

    def publishArrived(topicName: String, payload: Array[Byte], qos: Int, retained: Boolean) {
      val ctx: Context = getApplicationContext
      val s = new String(payload)
      Log.v(TAG, "Got message: " + s)
      s match {
        case "force_logout" =>
          showNotification(NotificationId.FORCE_LOGOUT, getString(R.string.force_logout),
            new Intent(ctx, classOf[LoginActivity]), NotificationCompat.PRIORITY_MAX)
          val session = new sessionManager(ctx)
          session.reset()
          ctx.sendBroadcast(new Intent(Action.CLOSE))
          ctx.sendBroadcast(new Intent(Action.LOGOUT))

        case "unbind_logout" =>
          showNotification(NotificationId.UNBIND_LOGOUT, getString(R.string.unbind_logout),
            new Intent(ctx, classOf[LoginActivity]), NotificationCompat.PRIORITY_MAX)
          val session = new sessionManager(ctx)
          session.reset()
          ctx.sendBroadcast(new Intent(Action.CLOSE))
          ctx.sendBroadcast(new Intent(Action.LOGOUT))

        case _ =>
          showNotification(NotificationId.MSG, s, new Intent(ctx, classOf[Shadowsocks]))
      }
    }

    @throws[MqttException]
    def sendKeepAlive() {
      Log.v(TAG, "Sending keep alive")
      var devid = mPrefs.getString(PushService.PREF_DEVICE_ID, "")
      val session = new sessionManager(getApplicationContext)
      if (session.isLogin) devid += "-" + session.uid
      publishToTopic(PushService.MQTT_CLIENT_ID + "/keepalive", devid)
    }
  }

}