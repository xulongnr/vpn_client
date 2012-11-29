package com.sgcc.vpn_client.util;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.sgcc.vpn_client.MainActivity;
import com.sgcc.vpn_client.R;

public class Notifications {
	final static String TAG = "Notifications";

	@SuppressWarnings("deprecation")
	public static void showNotification(Context context, String title,
			String content, int id) {

		PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
				new Intent(context, MainActivity.class), 0);
		Notification msg = new Notification(R.drawable.vpn_client, null,
				System.currentTimeMillis());
		msg.setLatestEventInfo(context, title, content, contentIntent);

		NotificationManager mNM = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		mNM.notify(id, msg);
	}

	public static void clearNotification(Context context, int id) {
		NotificationManager mNM = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		mNM.cancel(id);
	}

	public static void clearAllNotifications(Context context) {
		NotificationManager mNM = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		mNM.cancelAll();
	}
}