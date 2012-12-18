package com.sgcc.vpn_client.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

import android.annotation.TargetApi;
import android.os.Build;

public class FileParser {

	public static String getProfileString(String file, String key)
			throws IOException {
		String strLine, value;
		BufferedReader br = new BufferedReader(new FileReader(file));
		try {
			while ((strLine = br.readLine()) != null) {
				strLine = strLine.trim();
				strLine = strLine.split("[;#]")[0];

				strLine = strLine.trim();
				String[] strArray = strLine.split("=");
				if (strArray.length == 1) {
					value = strArray[0].trim();
					if (value.equals(key)) {
						value = "";
						return value;
					}
				} else if (strArray.length == 2) {
					value = strArray[0].trim();
					if (value.equals(key)) {
						value = strArray[1].trim();
						return value;
					}
				} else if (strArray.length > 2) {
					value = strArray[0].trim();
					if (value.equals(key)) {
						value = strLine.substring(strLine.indexOf("=") + 1)
								.trim();
						return value;
					}
				}
			}
		} finally {
			br.close();
		}
		return null;
	}

	public static boolean setProfileString(String file, String key, String value)
			throws IOException {
		String fileContent, allLine, strLine, newLine, remarkStr;
		String getValue;
		BufferedReader br = new BufferedReader(new FileReader(file));
		fileContent = "";
		try {
			while ((allLine = br.readLine()) != null) {
				allLine = allLine.trim();
				if (allLine.split("[;]").length > 1) {
					remarkStr = ";" + allLine.split(";")[1];
				} else {
					remarkStr = "";
				}
				strLine = allLine.split("[;]")[0];
				strLine = strLine.trim();
				String[] strArray = strLine.split("=");
				getValue = strArray[0].trim();
				if (getValue.equals(key)) {
					newLine = getValue + "=" + value + " " + remarkStr;
					fileContent += newLine + "\r\n";
					while ((allLine = br.readLine()) != null) {
						fileContent += allLine + "\r\n";
					}
					br.close();
					BufferedWriter bw = new BufferedWriter(new FileWriter(file,
							false));
					bw.write(fileContent);
					bw.flush();
					bw.close();
					return true;
				}
				fileContent += allLine + "\r\n";
			}
		} catch (IOException e) {
			throw e;
		} finally {
			br.close();
		}
		return false;
	}

	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	public static String getPropertyString(String file, String key)
			throws IOException {
		Properties property = new Properties();
		property.load(new FileReader(file));
		return property.getProperty(key);
	}

	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	public static boolean setPropertyString(String file, String key,
			String value) throws IOException {
		Properties property = new Properties();
		property.load(new FileReader(file));
		return property.setProperty(key, value) != null;
	}
}
