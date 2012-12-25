package com.sgcc.vpn_client.utils;

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
		msg.flags = Notification.FLAG_ONGOING_EVENT;
		mNM.notify(id, msg);
	}

	@SuppressWarnings("deprecation")
	public static void updateNotification(Context context, String title,
			String content, boolean online, int id) {
		int icon;
		String szStatus;
		if (online) {
			icon = R.drawable.vpn_client_on;
			szStatus = context.getString(R.string.online);
		} else {
			icon = R.drawable.vpn_client;
			szStatus = context.getString(R.string.offline);
		}
		PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
				new Intent(context, MainActivity.class), 0);
		Notification msg = new Notification(icon, null,
				System.currentTimeMillis());
		msg.setLatestEventInfo(context, title, content + szStatus,
				contentIntent);
		NotificationManager mNM = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		msg.flags = Notification.FLAG_ONGOING_EVENT;
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