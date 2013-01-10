package com.sgcc.vpn_client;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.commons.io.FileUtils;

import android.app.AlertDialog;
import android.app.TabActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabSpec;
import android.widget.TabWidget;
import android.widget.TextView;
import android.widget.Toast;

import com.sgcc.vpn_client.utils.FileParser;
import com.sgcc.vpn_client.utils.Notifications;

@SuppressWarnings("deprecation")
public class MainActivity extends TabActivity {

	private final static String TAG = "MainActivity";
	private final static int IN_PWD_HASH = 1450575459;
	private final int NOTIFICATION_VPN_CLIENT = 65000;

	private String szLastCmd;
	private String szStatInfo;

	private int tabNameIDs[] = { R.string.tab_logs, R.string.tab_stat,
			R.string.tab_conf };
	private int tabContentIDs[] = { R.id.tab_logs, R.id.tab_stat, R.id.tab_conf };

	private final static String CONF_FILENAME = "config.txt";
	private final static String CONF_PIDFILE = "pid";
	private final static String CONF_OUTPUT = "output_ch";
	private final static String CONF_CONNECT = "connect";
	private final static String CONF_CIPHERS = "ciphers";
	private final static String CONF_COMPRESS = "compression";
	private final static String CONF_REC_TMS = "RECONNECTtimes";
	private final static String CONF_REC_INT = "RECONNECTtimeinterval";

	private final static String SOCKET_ACK_OK = "done\0";
	private final static String SOCKET_IP = "127.0.0.1";
	private final static int SOCKET_PORT = 60702;
	private final static String SOCKET_CMD_STAT = "cmd001";
	private final static String SOCKET_CMD_STOP = "cmd002";
	private final static String SOCKET_CMD_RECONF = "cmd003";
	private final static String SOCKET_CMD_RECONN = "cmd004";
	private final static String SOCKET_CMD_RENEGO = "cmd005";

	private final static int MSG_STAT = 65001;
	private final static int MSG_STOP = 65002;
	private final static int MSG_RECONF = 65003;
	private final static int MSG_RECONN = 65004;
	private final static int MSG_RENEGO = 65005;
	private final static int MSG_START = 65006;
	private final static int MSG_NOTIFY = 65007;

	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			switch (msg.what) {

			case MSG_STAT:
				if (szStatInfo == null || szStatInfo == "") {
					break;
				}
				String[] info = szStatInfo.split(";");
				if (info.length == 4) {
					TextView tv = (TextView) findViewById(R.id.tv_stat);
					tv.setText(String.format(
							getString(R.string.str_stat_login_tm), info[0]));
					tv.append(String.format(
							getString(R.string.str_stat_up_flow),
							Long.valueOf(info[1])));
					tv.append(String.format(
							getString(R.string.str_stat_dw_flow),
							Long.valueOf(info[2])));
					boolean online = false;
					if (info[3].equals("1\0")) {
						online = true;
					}

					if (getPID() != -1) {
						Notifications.updateNotification(MainActivity.this,
								getString(R.string.app_name),
								getString(R.string.start_msg), online,
								NOTIFICATION_VPN_CLIENT);
					}
				}
				break;

			case MSG_STOP:
				Toast.makeText(MainActivity.this, R.string.stop_msg,
						Toast.LENGTH_SHORT).show();
				Notifications.clearNotification(MainActivity.this,
						NOTIFICATION_VPN_CLIENT);
				break;

			case MSG_RECONF:
				Toast.makeText(MainActivity.this, R.string.reload_conf_ok_msg,
						Toast.LENGTH_SHORT).show();
				break;

			case MSG_RECONN:
				Toast.makeText(MainActivity.this, R.string.reconnect_ok_msg,
						Toast.LENGTH_SHORT).show();
				break;

			case MSG_RENEGO:
				Toast.makeText(MainActivity.this,
						R.string.renegotiation_ok_msg, Toast.LENGTH_SHORT);
				break;

			case MSG_START:
				Toast.makeText(MainActivity.this, R.string.start_msg,
						Toast.LENGTH_SHORT).show();
				Notifications.showNotification(MainActivity.this,
						getString(R.string.app_name),
						getString(R.string.start_msg), NOTIFICATION_VPN_CLIENT);
				break;

			case MSG_NOTIFY:
				if (getPID() != -1) {
					Notifications.showNotification(MainActivity.this,
							getString(R.string.app_name),
							getString(R.string.start_msg),
							NOTIFICATION_VPN_CLIENT);
					findViewById(R.id.btn_stop).setEnabled(true);
				} else {
					Notifications.clearNotification(MainActivity.this,
							NOTIFICATION_VPN_CLIENT);
					findViewById(R.id.btn_stop).setEnabled(false);
				}

				break;

			default:
			}
		}
	};

	Runnable mUpdateState = new Runnable() {
		@Override
		public void run() {
			if (getPID() != -1) {
				refreshStat();
				refreshLogs();
			} else {
				mHandler.sendEmptyMessage(MSG_NOTIFY);
			}
			mHandler.postDelayed(mUpdateState, 1000);
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.v(TAG, "onCreate().");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		setupViews();

		// get app running path
		SystemCommands.setAppPath(getCacheDir().getParent().toString());
		// copy shell binary, config and certs
		SystemCommands.copyFilesFromAssets(getAssets());

		// pop up login dialog
		if (-1 == getPID()) {
			loginDialog();
		}

		// get configs from config.txt
		getConfig();

		mHandler.post(mUpdateState);
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "onDestroy().");
		super.onDestroy();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		Log.v(TAG, "onConfigurationChanged().");
		super.onConfigurationChanged(newConfig);
		// TODO: add code for config changes here

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;

	}

	protected void exitWithAlertDialog() {
		new AlertDialog.Builder(this)
				.setTitle(R.string.corp_name)
				.setIcon(R.drawable.sgcc)
				.setMessage(getString(R.string.exit_msg))

				.setPositiveButton(getString(R.string.btn_ok),
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								if (getPID() != -1) {
									mHandler.post(stopService);
								}
								finish();
							}
						})
				.setNegativeButton(getString(R.string.btn_cancel),
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								dialog.dismiss();
							}
						}).create().show();
	}

	protected void loginDialog() {
		final EditText input = new EditText(this);
		input.setInputType(InputType.TYPE_CLASS_TEXT
				| InputType.TYPE_TEXT_VARIATION_PASSWORD);
		new AlertDialog.Builder(this)
				.setTitle(R.string.corp_name)
				.setIcon(R.drawable.sgcc)
				.setMessage(getString(R.string.login_msg))
				.setCancelable(false)
				.setView(input)
				.setPositiveButton(getString(R.string.btn_ok),
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								try {
									Field field = dialog.getClass()
											.getSuperclass()
											.getDeclaredField("mShowing");
									field.setAccessible(true);
									field.set(dialog, false);
								} catch (Exception e) {
									e.printStackTrace();
								}

								// TODO: Add authentication code here
								String szInput = input.getText().toString();
								if (IN_PWD_HASH == szInput.hashCode()) {
									try {
										Field field = dialog.getClass()
												.getSuperclass()
												.getDeclaredField("mShowing");
										field.setAccessible(true);
										field.set(dialog, true);
									} catch (Exception e) {
										e.printStackTrace();
									}
									dialog.dismiss();
									mHandler.post(startService);
								} else {
									Toast.makeText(MainActivity.this,
											R.string.invalid_password,
											Toast.LENGTH_SHORT).show();
									input.selectAll();
								}
							}
						})
				.setNegativeButton(getString(R.string.menu_exit),
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								finish();
							}
						}).show();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {

		case R.id.menu_renego:
			if (getPID() == -1) {
				Toast.makeText(MainActivity.this, R.string.not_yet_started_msg,
						Toast.LENGTH_SHORT).show();
				break;
			}

			new Thread() {
				@Override
				public void run() {
					String szRet = liteSocket(SOCKET_CMD_RENEGO);
					if (SOCKET_ACK_OK.equals(szRet)) {
						mHandler.sendEmptyMessage(MSG_RENEGO);
					}
				}
			}.start();
			break;

		case R.id.menu_reconn:
			if (getPID() == -1) {
				Toast.makeText(MainActivity.this, R.string.not_yet_started_msg,
						Toast.LENGTH_SHORT).show();
				break;
			}

			new Thread() {
				@Override
				public void run() {
					String szRet = liteSocket(SOCKET_CMD_RECONN);
					if (SOCKET_ACK_OK.equals(szRet)) {
						mHandler.sendEmptyMessage(MSG_RECONN);
					}
				}
			}.start();
			break;

		case R.id.menu_about:
			new AlertDialog.Builder(this)
					.setTitle(R.string.corp_name)
					.setIcon(R.drawable.sgcc)
					.setMessage(getString(R.string.about_msg))
					.setPositiveButton(getString(R.string.btn_ok),
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int which) {
									dialog.dismiss();
								}
							}).create().show();
			break;

		case R.id.menu_exit:
			exitWithAlertDialog();
			break;

		default:

		}
		return super.onOptionsItemSelected(item);
	}

	protected void refreshStat() {
		new Thread() {
			@Override
			public void run() {
				if (getPID() != -1) {
					szStatInfo = liteSocket(SOCKET_CMD_STAT);
					mHandler.sendEmptyMessage(MSG_STAT);
				}
			}
		}.start();
	}

	protected String liteSocket(final String out) {
		if (getPID() == -1) {
			Log.e(TAG, "socket server is not alive.");
			return null;
		}

		String in = "";
		try {
			Socket socket = new Socket(SOCKET_IP, SOCKET_PORT);
			OutputStream os = socket.getOutputStream();
			Log.v(TAG, "outSocket: " + out);
			os.write(out.getBytes());
			os.flush();
			BufferedReader br = new BufferedReader(new InputStreamReader(
					socket.getInputStream()));
			in = br.readLine();
			Log.v(TAG, "inSocket: " + in);
			os.close();
			socket.close();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return in;
	}

	protected String getConfigItem(String key) {
		assert key != null;
		String pkgDirString = getCacheDir().getParent().toString();
		String filePath = pkgDirString + File.separator + CONF_FILENAME;
		String value = null;
		try {
			value = FileParser.getProfileString(filePath, key);
		} catch (IOException e) {
			Toast.makeText(this, "Can't read from " + filePath,
					Toast.LENGTH_SHORT).show();
		}
		return value;
	}

	@Override
	public boolean onKeyUp(int keycode, KeyEvent event) {
		switch (keycode) {

		case KeyEvent.KEYCODE_BACK:

			/*
			 * if (true) { exitWithAlertDialog(); return false; }
			 */
		default:

		}
		return super.onKeyUp(keycode, event);
	}

	protected boolean setConfigItem(String key, String value) {
		assert key != null;
		assert value != null;
		String pkgDirString = getCacheDir().getParent().toString();
		String filePath = pkgDirString + File.separator + CONF_FILENAME;
		boolean ret = false;
		try {
			ret = FileParser.setProfileString(filePath, key, value);
		} catch (IOException e) {
			Toast.makeText(this, "Can't read/write " + filePath,
					Toast.LENGTH_SHORT).show();
		}
		return ret;
	}

	protected void getConfig() {
		EditText etAddr = (EditText) findViewById(R.id.txt_addr);
		EditText etPort = (EditText) findViewById(R.id.txt_port);
		Spinner spAlgo = (Spinner) findViewById(R.id.spin_algo);
		Spinner spComp = (Spinner) findViewById(R.id.spin_comp);
		EditText etTMS = (EditText) findViewById(R.id.txt_times);
		EditText etINT = (EditText) findViewById(R.id.txt_interval);

		String connect = getConfigItem(CONF_CONNECT);
		if (connect != null) {
			Log.d(TAG, "connect = " + connect);
			int sep = connect.indexOf(':');
			etAddr.setText(connect.substring(0, sep).trim());
			etPort.setText(connect.substring(sep + 1).trim());
		}

		String cipher = getConfigItem(CONF_CIPHERS);
		if (cipher != null) {
			Log.d(TAG, "ciphers = " + cipher);
			String[] selections = getResources().getStringArray(
					R.array.algorithms);
			int cipher_id = 0;
			for (int i = 0; i < selections.length; i++) {
				if (selections[i].equalsIgnoreCase(cipher)) {
					cipher_id = i;
					break;
				}
			}
			spAlgo.setSelection(cipher_id);
		}

		String compress = getConfigItem(CONF_COMPRESS);
		if (compress != null) {
			Log.d(TAG, "compress = " + compress);
			String[] selections = getResources().getStringArray(
					R.array.compression);
			int cmp_id = 0;
			for (int i = 0; i < selections.length; i++) {
				if (selections[i].equalsIgnoreCase(compress)) {
					cmp_id = i;
					break;
				}
			}
			spComp.setSelection(cmp_id);
		}

		String rec_times = getConfigItem(CONF_REC_TMS);
		if (rec_times != null) {
			etTMS.setText(rec_times);
		}
		String rec_interval = getConfigItem(CONF_REC_INT);
		if (rec_interval != null) {
			etINT.setText(rec_interval);
		}
	}

	protected boolean saveConfig() {
		EditText etAddr = (EditText) findViewById(R.id.txt_addr);
		EditText etPort = (EditText) findViewById(R.id.txt_port);
		Spinner spAlgo = (Spinner) findViewById(R.id.spin_algo);
		Spinner spComp = (Spinner) findViewById(R.id.spin_comp);
		EditText etTMS = (EditText) findViewById(R.id.txt_times);
		EditText etINT = (EditText) findViewById(R.id.txt_interval);
		boolean ret = setConfigItem(CONF_CONNECT, String.format("%s:%s", etAddr
				.getText().toString(), etPort.getText().toString()));

		String[] selections = getResources().getStringArray(R.array.algorithms);
		int cipher_id = (int) spAlgo.getSelectedItemId();
		ret = ret && setConfigItem(CONF_CIPHERS, selections[cipher_id]);
		selections = getResources().getStringArray(R.array.compression);
		int cmp_id = (int) spComp.getSelectedItemId();
		ret = ret && setConfigItem(CONF_COMPRESS, selections[cmp_id]);
		ret = ret && setConfigItem(CONF_REC_TMS, etTMS.getText().toString());
		ret = ret && setConfigItem(CONF_REC_INT, etINT.getText().toString());
		return ret;
	}

	private void refreshLogs() {
		try {
			String logfile = getConfigItem(CONF_OUTPUT);
			// Log.v(TAG, "log file is " + logfile);
			File file = new File(logfile);
			if (file.exists()) {
				String logs = FileUtils.readFileToString(file, "gb2312");

				TextView tvLogs = (TextView) findViewById(R.id.tv_logs);
				tvLogs.setText(logs);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private final Runnable startService = new Runnable() {
		@Override
		public void run() {
			String cmd = SystemCommands.getAppPath() + File.separator
					+ "vpn-client" + " " + SystemCommands.getAppPath()
					+ File.separator + CONF_FILENAME;
			String ret = SystemCommands.executeCommnad(cmd);
			mHandler.removeCallbacks(mUpdateState);
			if (ret != "") {
				TextView tvLogs = (TextView) findViewById(R.id.tv_logs);
				tvLogs.setText(ret);
			} else {
				mHandler.post(mUpdateState);
			}

			SystemClock.sleep(1500); // wait for daemon to catch up
			if (getPID() != -1) {
				mHandler.sendEmptyMessage(MSG_START);
				mHandler.sendEmptyMessage(MSG_NOTIFY);
			} else {
				Log.e(TAG, "quit startService while getpid=-1");
			}
		}
	};

	private final Runnable stopService = new Runnable() {
		@Override
		public void run() {
			long pid = getPID();
			if (pid == -1) {
				Toast.makeText(MainActivity.this, R.string.not_yet_started_msg,
						Toast.LENGTH_SHORT).show();
				return;
			}

			// use kill signal to stop service
			/*
			 * SystemCommands.executeCommnad("kill -9 " + String.valueOf(pid));
			 * try { FileUtils.forceDelete(new File(getPIDFile())); szStatInfo =
			 * ""; } catch (IOException e) { e.printStackTrace(); Log.e(TAG,
			 * " Error on deleting pidfile."); }
			 */

			new Thread() {
				@Override
				public void run() {
					mHandler.removeCallbacks(mUpdateState);
					String szRet = liteSocket(SOCKET_CMD_STOP);
					if (SOCKET_ACK_OK.equals(szRet)) {
						Log.v(TAG, "stop service successfully.");
						mHandler.sendEmptyMessage(MSG_STOP);
						mHandler.sendEmptyMessage(MSG_NOTIFY);
					}
				}
			}.start();
		}
	};

	private void restartService() {
		if (getPID() != -1) {
			mHandler.post(stopService);
			mHandler.postDelayed(startService, 2000);
		} else {
			mHandler.post(startService);
		}
	}

	protected String getPIDFile() {
		String pkgDirString = getCacheDir().getParent().toString();
		String filePath = pkgDirString + File.separator + CONF_FILENAME;
		String pidFile;
		try {
			pidFile = FileParser.getProfileString(filePath, CONF_PIDFILE);
			// Log.v(TAG, "pidfile = " + pidFile);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		return pidFile;
	}

	protected long getPID() {
		String pid, pidfile;
		pidfile = getPIDFile();
		if (pidfile == null) {
			return -1;
		}
		File file = new File(pidfile);
		if (!file.exists()) {
			return -1;
		}

		try {
			pid = FileUtils.readFileToString(file).trim();
			// Log.v(TAG, "pid = " + pid);
		} catch (IOException e) {
			e.printStackTrace();
			return -1;
		}
		return Long.valueOf(pid);
	}

	protected void setupViews() {
		TabHost tabHost = getTabHost();
		for (int i = 0; i < tabNameIDs.length; i++) {
			String tabName = getString(tabNameIDs[i]);
			TabSpec tabSpec = tabHost.newTabSpec(tabName).setIndicator(tabName)
					.setContent(tabContentIDs[i]);
			tabHost.addTab(tabSpec);
		}
		tabHost.setCurrentTab(0);

		TabWidget tabWidget = tabHost.getTabWidget();
		for (int i = 0; i < tabWidget.getChildCount(); i++) {
			DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
			int screen_width = displayMetrics.widthPixels;
			tabWidget.getChildAt(i).getLayoutParams().width = screen_width / 3;
			final TextView tv = (TextView) tabWidget.getChildAt(i)
					.findViewById(android.R.id.title);
			tv.setTextColor(Color.BLACK);
			tv.setTextSize(20); // set to 20sp, refer to 18sp for content
			tv.setGravity(Gravity.CENTER_HORIZONTAL);
		}

		tabHost.setOnTabChangedListener(new OnTabChangeListener() {
			@Override
			public void onTabChanged(String tabId) {
				for (int i = 0; i < tabNameIDs.length; i++) {
					if (tabId.equalsIgnoreCase(getString(tabNameIDs[i]))) {
						showLayer(tabContentIDs[i]);
						break;
					}
				}
			}

			private void showLayer(int idLayer) {
				LinearLayout layout;
				int layers[] = { R.id.tab_logs, R.id.tab_stat, R.id.tab_conf };
				for (int i = 0; i < layers.length; i++) {
					layout = (LinearLayout) findViewById(layers[i]);
					if (layers[i] == idLayer) {
						// if it is statistics tab, refresh data before show up
						if (idLayer == R.id.tab_stat) {
							refreshStat();
						}
						// hide soft keyboard if any
						InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
						imm.hideSoftInputFromWindow(MainActivity.this
								.getCurrentFocus().getWindowToken(), 0);

						layout.setVisibility(View.VISIBLE);
					} else {
						layout.setVisibility(View.GONE);
					}
				}
			}
		});

		/*
		 * ********** REFRESH ***********
		 */
		Button btn = (Button) findViewById(R.id.btn_refresh);
		btn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				refreshStat();
			}
		});
		// refreshStat();

		/*
		 * ********** RESTART ***********
		 */
		btn = (Button) findViewById(R.id.btn_restart);
		btn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				restartService();
			}
		});

		/*
		 * ********** STOP ***********
		 */
		btn = (Button) findViewById(R.id.btn_stop);
		btn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mHandler.post(stopService);
			}
		});

		/*
		 * ********** CONFIG SAVE ***********
		 */
		btn = (Button) findViewById(R.id.btn_save);
		btn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// hide soft keyboard if any
				InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(MainActivity.this.getCurrentFocus()
						.getWindowToken(), 0);

				boolean ret = saveConfig();
				if (ret && getPID() != -1) {
					Toast.makeText(MainActivity.this, R.string.save_ok_msg,
							Toast.LENGTH_SHORT).show();
					getConfig();

					new Thread() {
						@Override
						public void run() {
							String szRet = liteSocket(SOCKET_CMD_RECONF);
							if (SOCKET_ACK_OK.equals(szRet)) {
								Log.v(TAG, "Reload config successfully.");
								mHandler.sendEmptyMessage(MSG_RECONF);
							}
						}
					}.start();
				}
			}
		});

		/*
		 * ********** CONFIG REVERT ***********
		 */
		btn = (Button) findViewById(R.id.btn_cancel);
		btn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// hide soft keyboard if any
				InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(MainActivity.this.getCurrentFocus()
						.getWindowToken(), 0);

				Toast.makeText(MainActivity.this, R.string.revert_msg,
						Toast.LENGTH_SHORT).show();
				getConfig();
			}
		});

		// for test purpose
		final EditText in_cmd = (EditText) findViewById(R.id.in_cmd);
		in_cmd.setOnKeyListener(new OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				String cmdString = in_cmd.getText().toString();

				if (keyCode == KeyEvent.KEYCODE_VOLUME_UP
						&& event.getAction() == KeyEvent.ACTION_DOWN) {
					((EditText) findViewById(R.id.in_cmd)).setText(szLastCmd);
					return true;
				}

				if (keyCode == KeyEvent.KEYCODE_ENTER
						&& event.getAction() == KeyEvent.ACTION_DOWN) {
					String resultString = "#" + cmdString + "\n";
					szLastCmd = cmdString;
					resultString += SystemCommands.executeCommnad(cmdString);
					((EditText) findViewById(R.id.in_cmd)).setText("");
					((TextView) findViewById(R.id.tv_logs))
							.setText(resultString);
					return true;
				}

				/*
				 * if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() ==
				 * KeyEvent.ACTION_DOWN) { ((TextView)
				 * findViewById(R.id.in_cmd)).setText(""); return true; }
				 */
				return false;
			}
		});
	}
}
