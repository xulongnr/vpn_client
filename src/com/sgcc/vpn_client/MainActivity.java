package com.sgcc.vpn_client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.UnknownHostException;

import android.app.AlertDialog;
import android.app.TabActivity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnKeyListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;

import com.sgcc.vpn_client.util.Notifications;

@SuppressWarnings("deprecation")
public class MainActivity extends TabActivity {

	private final static String TAG = "MainActivity";
	private final int NOTIFICATION_VPN_CLIENT = 65000;

	private TabHost tabHost;
	private String szLastCmd;
	private int tabNameIDs[] = { R.string.tab_logs, R.string.tab_stat,
			R.string.tab_conf };
	private int tabContentIDs[] = { R.id.tab_logs, R.id.tab_stat, R.id.tab_conf };

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// copy shell binary, config and certs
		SystemCommands.copyFilesFromAssets(getAssets());

		// pop up login dialog
		loginDialog();

		// get configs from config.txt
		getConfig();

		tabHost = getTabHost();
		for (int i = 0; i < tabNameIDs.length; i++) {
			String tabName = getString(tabNameIDs[i]);
			TabSpec tabSpec = tabHost.newTabSpec(tabName).setIndicator(tabName)
					.setContent(tabContentIDs[i]);
			tabHost.addTab(tabSpec);
		}
		tabHost.setCurrentTab(0);

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

			// TODO Add tab change related code here
			private void showLayer(int idLayer) {
				LinearLayout layout;
				int layers[] = { R.id.tab_logs, R.id.tab_stat, R.id.tab_conf };
				for (int i = 0; i < layers.length; i++) {
					layout = (LinearLayout) findViewById(layers[i]);
					if (layers[i] == idLayer) {
						layout.setVisibility(View.VISIBLE);
					} else {
						layout.setVisibility(View.GONE);
					}
				}
			}
		});

		// TODO: Add code for buttons here
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
				startService();
			}
		});

		/*
		 * ********** STOP ***********
		 */
		btn = (Button) findViewById(R.id.btn_stop);
		btn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				stopService();
			}
		});

		/*
		 * ********** CONFIG SAVE ***********
		 */
		btn = (Button) findViewById(R.id.btn_save);
		btn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				boolean ret = saveConfig();
				if (ret) {
					new AlertDialog.Builder(MainActivity.this)
							.setMessage(getString(R.string.save_ok_msg))
							.setPositiveButton(getString(R.string.btn_ok), null)
							.create().show();
				}
				getConfig();
			}
		});

		/*
		 * ********** CONFIG REVERT ***********
		 */
		btn = (Button) findViewById(R.id.btn_cancel);
		btn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
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

	@Override
	public void onDestroy() {
		Log.d(TAG, "onDestroy");
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;

	}

	protected void exitWithAlertDialog() {
		new AlertDialog.Builder(this)
				.setMessage(getString(R.string.exit_msg))
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setPositiveButton(getString(R.string.btn_ok),
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								// TODO: entrance of exit
								stopService();
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
		new AlertDialog.Builder(this)
				.setMessage(getString(R.string.login_msg))
				.setView(input)
				.setPositiveButton(getString(R.string.btn_ok),
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								// TODO Auto-generated method stub
								dialog.dismiss();
							}
						})
				.setNegativeButton(getString(R.string.menu_exit),
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								// TODO Auto-generated method stub
								finish();
							}
						}).show();

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

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_about:
			new AlertDialog.Builder(this)
					.setMessage(getString(R.string.about_msg))
					.setIcon(R.drawable.ic_launcher)
					.setPositiveButton(getString(R.string.btn_ok),
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int which) {
									// TODO Auto-generated method stub
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

	private class terminal_state {
		byte[] login_time = new byte[20];
		byte[] logout_time = new byte[20];
		byte[] up_flow = new byte[4];
		byte[] down_flow = new byte[4];
	}

	private Handler statHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			// Deal with UI changes
			TextView tv = (TextView) findViewById(R.id.tv_stat);
			tv.setText(String.format(getString(R.string.str_stat_login_tm),
					"12:00:00"));
			tv.append(String.format(getString(R.string.str_stat_up_flow), 0));
			tv.append(String.format(getString(R.string.str_stat_dw_flow), 0));
		}
	};

	protected void refreshStat() {
		new Thread() {
			@Override
			public void run() {
				try {
					Socket socket = new Socket("127.0.0.1", 60702);
					OutputStream os = socket.getOutputStream();
					// BufferedWriter bw = new BufferedWriter(new
					// OutputStreamWriter(os));
					byte[] b = new byte[4];
					b[1] = 0x04;
					b[3] = 0x00;
					os.write(b);
					os.flush();
					InputStream is = socket.getInputStream();
					terminal_state ts = new terminal_state();
					int size = 48;
					b = new byte[size];
					is.read(b, 0, size);
					Log.d(TAG, "output" + ts.login_time + ", " + ts.up_flow
							+ ", " + ts.down_flow);

				} catch (UnknownHostException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}

				statHandler.sendEmptyMessage(0);
			}
		}.start();
	}

	protected String getConfigItem(String argumemt) {
		String pkgDirString = getCacheDir().getParent().toString(), line, arg, opt;
		String confFilePath = pkgDirString + File.separator + "config.txt";
		File cfgFile = new File(confFilePath);
		InputStream is;
		try {
			is = new FileInputStream(cfgFile);
			BufferedReader br = new BufferedReader(new InputStreamReader(is));
			while ((line = br.readLine()) != null) {
				int sep = line.indexOf('=');
				if (sep == -1) {
					continue;
				}
				arg = line.substring(0, sep - 1).trim();
				Log.v(TAG, "checking arg: " + arg);
				if (arg.equalsIgnoreCase(argumemt)) {
					opt = line.substring(line.indexOf('=') + 1).trim();
					Log.v(TAG, "matched arg, opt = " + opt);
					br.close();
					return opt;
				}
			}
			br.close();
		} catch (FileNotFoundException e) {
			Log.e(TAG, "File " + confFilePath + " not found error.");
			e.printStackTrace();
			new AlertDialog.Builder(this).setMessage(
					"Config file doesn't exist!").show();
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		return null;
	}

	protected void getConfig() {
		EditText etAddr = (EditText) findViewById(R.id.txt_addr);
		EditText etPort = (EditText) findViewById(R.id.txt_port);
		Spinner spAlgo = (Spinner) findViewById(R.id.spin_algo);

		String connect = getConfigItem("connect");
		if (connect != null) {
			Log.d(TAG, "connect = " + connect);
			int sep = connect.indexOf(':');
			etAddr.setText(connect.substring(0, sep).trim());
			etPort.setText(connect.substring(sep + 1).trim());
		}
		String cipher = getConfigItem("ciphers");
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
	}

	protected boolean saveConfig() {
		EditText etAddr = (EditText) findViewById(R.id.txt_addr);
		EditText etPort = (EditText) findViewById(R.id.txt_port);
		Spinner spAlgo = (Spinner) findViewById(R.id.spin_algo);

		String pkgDirString = getCacheDir().getParent().toString();
		String oldConfFilePath = pkgDirString + File.separator + "config.txt";
		String newConfFilePath = oldConfFilePath + "~";
		File oldConfFile = new File(oldConfFilePath);
		File newConfFile = new File(newConfFilePath);
		InputStream iStream;
		OutputStream oStream;
		String line, connect, ciphers;
		try {
			iStream = new FileInputStream(oldConfFile);
			oStream = new FileOutputStream(newConfFile);
			BufferedReader br = new BufferedReader(new InputStreamReader(
					iStream));
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
					oStream));
			while ((line = br.readLine()) != null) {
				if (line.contains("connect")) {
					String szAddr = etAddr.getText().toString();
					String szPort = etPort.getText().toString();
					connect = String.format("connect = %s:%s", szAddr, szPort);
					Log.d(TAG, connect);
					bw.write(connect + "\n");

				} else if (line.contains("ciphers")) {
					String[] selections = getResources().getStringArray(
							R.array.algorithms);
					int cipher_id = (int) spAlgo.getSelectedItemId();
					Log.d(TAG, "cipher ID = " + cipher_id);
					ciphers = String.format("ciphers = %s",
							selections[cipher_id]);
					Log.d(TAG, ciphers);
					bw.write(ciphers + "\n");

				} else {
					bw.write(line + "\n");
				}
			}
			bw.flush();
			oStream.close();
			iStream.close();
			boolean ret;
			ret = oldConfFile.delete();
			Log.d(TAG, "deleting " + oldConfFile.getAbsolutePath() + " : "
					+ ret);
			ret = newConfFile.renameTo(oldConfFile);
			Log.d(TAG, "moving " + newConfFile.getAbsolutePath() + " to "
					+ oldConfFile.getAbsolutePath() + " : " + ret);
			SystemCommands.executeCommnad("chmod 755 "
					+ oldConfFile.getAbsolutePath());
			return true;

		} catch (FileNotFoundException e) {
			Log.e(TAG, "File " + oldConfFilePath + " not found error.");
			e.printStackTrace();
			new AlertDialog.Builder(this).setMessage(
					"Config file doesn't exist!").show();
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	protected void startService() {
		String cmd = SystemCommands.APP_PATH + File.separator + "vpn-client"
				+ " " + SystemCommands.APP_PATH + File.separator + "config.txt";
		String ret = SystemCommands.executeCommnad(cmd);
		if (ret == "") {
			try {
				InputStream is = new FileInputStream(new File(
						getConfigItem("output")));
				BufferedReader bReader = new BufferedReader(
						new InputStreamReader(is));
				String line;
				try {
					while ((line = bReader.readLine()) != null) {
						ret += line + "\n";
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}

		}
		((TextView) findViewById(R.id.tv_logs)).setText(ret);
		Notifications.showNotification(this, getString(R.string.app_name),
				"VPN Client is running.", NOTIFICATION_VPN_CLIENT);
	}

	protected void stopService() {
		Notifications.clearNotification(this, NOTIFICATION_VPN_CLIENT);
	}

	protected void restartService() {
		stopService();
		startService();
	}
}