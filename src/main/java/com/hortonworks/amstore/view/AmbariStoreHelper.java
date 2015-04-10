/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hortonworks.amstore.view;

// Remove this if possible
import org.apache.ambari.view.URLStreamProvider;
import org.apache.commons.io.IOUtils;

import java.io.PrintWriter;


import com.hortonworks.amstore.view.proxy.Proxy;
import com.hortonworks.amstore.view.utils.ServiceFormattedException;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.LinkedHashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

public class AmbariStoreHelper {

	public AmbariStoreHelper() {
	}

	@SuppressWarnings("restriction")
	public static String readStringFromUrl(String url, String username,
			String password) throws IOException {

		URLConnection connection = new URL(url).openConnection();
		connection.setConnectTimeout(5000);
		connection.setReadTimeout(5000);
		connection.setDoOutput(true);

		if (username != null) {
			String userpass = username + ":" + password;
			// TODO: Use apache commons instead.
			String basicAuth = "Basic "
					+ javax.xml.bind.DatatypeConverter
							.printBase64Binary(userpass.getBytes());
			connection.setRequestProperty("Authorization", basicAuth);
		}
		InputStream in = connection.getInputStream();

		try {
			return IOUtils.toString(in, "UTF-8");
		} finally {
			in.close();
		}
	}

	// Reads a string into a JSON object
	public static JSONObject readJsonFromUrl(String url, String username,
			String password) throws IOException {

		String jsonText = readStringFromUrl(url, username, password);
		try {
			JSONObject json = new JSONObject(jsonText);
			return json;
		} catch (JSONException e) {
			return new JSONObject("{}");
		}

	}

	public static JSONObject readJsonFromUrl(String url) throws IOException,
			JSONException {
		return readJsonFromUrl(url, null, null);
	}

	public static String readFromUrlPut(URLStreamProvider urlStreamProvider,
			String url, String username, String password,
			Map<String, String> headers, String data, PrintWriter writer)
			throws IOException, JSONException {

		Proxy proxy = new Proxy(urlStreamProvider);
		proxy.setUseAuthorization(true);
		proxy.setUsername(username);
		proxy.setPassword(password);
		proxy.setCustomHeaders(headers);

		return proxy.request(url).put().asString();
	}

	public static org.json.simple.JSONObject doPutAsJson(
			URLStreamProvider urlStreamProvider, AmbariEndpoint ambari,
			String url, String data) {
		Proxy proxy = new Proxy(urlStreamProvider);
		proxy.setUseAuthorization(true);
		proxy.setUsername(ambari.username);
		proxy.setPassword(ambari.password);

		Map<String, String> headers = new LinkedHashMap<String, String>();
		headers.put("X-Requested-By", "view-ambari-store");
		proxy.setCustomHeaders(headers);

		return proxy.request(ambari.url + url).setData(data).put().asJSON();

	}

	public static String doPut(URLStreamProvider urlStreamProvider,
			AmbariEndpoint ambari, String url, String data) {
		Proxy proxy = new Proxy(urlStreamProvider);
		proxy.setUseAuthorization(true);
		proxy.setUsername(ambari.username);
		proxy.setPassword(ambari.password);

		Map<String, String> headers = new LinkedHashMap<String, String>();
		headers.put("X-Requested-By", "view-ambari-store");
		proxy.setCustomHeaders(headers);

		return proxy.request(ambari.url + url).setData(data).put().asString();
	}

	// TODO: rewrite this in a dynamic graphical manner
	public static void downloadFile(String url, String localFilePath) {

		try {
			URL website = new URL(url);
			ReadableByteChannel rbc = Channels.newChannel(website.openStream());
			// unix specific
			FileOutputStream fos = new FileOutputStream(localFilePath);
			fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
			fos.close();
		} catch (java.net.MalformedURLException e) {
		} catch (java.io.FileNotFoundException e) {
		} catch (java.io.IOException e) {
		}
	}

	public static org.json.simple.JSONObject doPostAsJson(
			URLStreamProvider urlStreamProvider, AmbariEndpoint ambari,
			String url, String data) {
		// copy paste
		Proxy proxy = new Proxy(urlStreamProvider);
		proxy.setUseAuthorization(true);
		proxy.setUsername(ambari.username);
		proxy.setPassword(ambari.password);

		Map<String, String> headers = new LinkedHashMap<String, String>();
		headers.put("X-Requested-By", "view-ambari-store");
		proxy.setCustomHeaders(headers);

		return proxy.request(ambari.url + url).setData(data).post().asJSON();
	}

	public static String doPost(URLStreamProvider urlStreamProvider,
			AmbariEndpoint ambari, String url, String data)
			throws ServiceFormattedException {
		Proxy proxy = new Proxy(urlStreamProvider);
		proxy.setUseAuthorization(true);
		proxy.setUsername(ambari.username);
		proxy.setPassword(ambari.password);

		Map<String, String> headers = new LinkedHashMap<String, String>();
		headers.put("X-Requested-By", "view-ambari-store");
		proxy.setCustomHeaders(headers);

		return proxy.request(ambari.url + url).setData(data).post().asString();
	}

	public static String doDelete(URLStreamProvider urlStreamProvider,
			AmbariEndpoint ambari, String url) throws ServiceFormattedException {
		Proxy proxy = new Proxy(urlStreamProvider);
		proxy.setUseAuthorization(true);
		proxy.setUsername(ambari.username);
		proxy.setPassword(ambari.password);

		Map<String, String> headers = new LinkedHashMap<String, String>();
		headers.put("X-Requested-By", "view-ambari-store");
		proxy.setCustomHeaders(headers);

		return proxy.request(ambari.url + url).delete().asString();
	}

	public static String jsonRequestToString(String json, String url) {
		JSONObject js = new JSONObject(json);
		return js.toString() + "\n" + url;
	}

}