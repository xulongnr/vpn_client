package com.sgcc.vpn_client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import android.content.res.AssetManager;
import android.util.Log;

public final class SystemCommands {

	private final static String TAG = "SystemCommand";
	private static String APP_PATH = "/data/data/com.sgcc.vpn_client";

	public static void setAppPath(final String path) {
		if (path != null && path != "") {
			APP_PATH = path;
		}
	}

	public static String getAppPath() {
		return APP_PATH;
	}

	protected static boolean copyFileAndChmod(AssetManager am, String src_path,
			String tgt_path, String mode) {
		if (null == mode) {
			mode = "755";
		}
		// Log.v(TAG, "src=" + src_path + ", target=" + tgt_path);
		try {
			InputStream is = am.open(src_path);
			copyFileFromStream(is, tgt_path);
			executeCommnad("chmod  " + mode + " " + tgt_path);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public static boolean copyFileFromStream(InputStream is, String target_path) {
		try {
			FileOutputStream fos = new FileOutputStream(target_path);
			int len = 0;
			byte[] b = new byte[is.available()];
			while ((len = is.read(b)) != -1) {
				fos.write(b, 0, len);
			}
			fos.flush();
			if (null != is) {
				is.close();
			}
			if (null != fos) {
				fos.close();
			}
			return true;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	public static boolean copyFilesFromAssets(AssetManager am) {
		String filenames[] = { "config.txt", "vpn-client" };
		String certs_dirname = "certs";

		for (String filename : filenames) {
			String toString = APP_PATH + File.separator + filename;

			// skip config file
			// if (filename == "config.txt" && new File(toString).exists()) {
			// continue;
			// }

			Log.v(TAG, "copying " + filename + " to " + toString);
			copyFileAndChmod(am, filename, toString, "755");
		}
		File dirCertsTarget = new File(APP_PATH + File.separator
				+ certs_dirname);
		if (!dirCertsTarget.exists()) {
			boolean ret = dirCertsTarget.mkdir();
			ret = dirCertsTarget.mkdirs();
			Log.d(TAG, "mkdirs(" + dirCertsTarget.getAbsolutePath()
					+ ") returns " + ret);
			executeCommnad("chmod  755 " + dirCertsTarget.getAbsolutePath());
		}

		ArrayList<String> certsNameArrayList = getAssetsFileList(am,
				certs_dirname);
		for (int i = 0; i < certsNameArrayList.size(); i++) {
			String certName = certsNameArrayList.get(i);
			String toString = APP_PATH + File.separator + certName;
			Log.v(TAG, "copying " + certName + " to " + toString);
			copyFileAndChmod(am, certName, toString, null);
		}

		return true;
	}

	public static String executeCommnad(String args) {
		String line, result = "";
		try {
			Process process;
			if (args != null) {
				process = Runtime.getRuntime().exec(args);
			} else {
				process = Runtime.getRuntime().exec("ls");
			}

			BufferedReader buffer_out = new BufferedReader(
					new InputStreamReader(process.getInputStream()));
			BufferedReader buffer_err = new BufferedReader(
					new InputStreamReader(process.getErrorStream()));

			while ((line = buffer_err.readLine()) != null) {
				result += line + "\n";
			}
			if (result != "") {
				return result;
			}
			while ((line = buffer_out.readLine()) != null) {
				result += line + "\n";
			}
			if (result != "") {
				return result;
			}
		} catch (Throwable t) {
			t.printStackTrace();
			return null;
		}
		return "";
	}

	protected static ArrayList<String> getAssetsFileList(AssetManager am,
			String path) {
		if (null == path) {
			path = "";
		}

		ArrayList<String> arrayList = new ArrayList<String>();
		try {
			String[] filenames = am.list(path);
			for (int i = 0; i < filenames.length; i++) {
				String filepath = path + File.separator + filenames[i];
				if (isValidCert(filepath)) {
					arrayList.add(filepath);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}

		return arrayList;
	}

	protected static boolean isValidCert(String filename) {
		filename = filename.toLowerCase();
		if (filename.endsWith(".pem") || filename.endsWith("key")) {
			return true;
		}

		return false;
	}
}
