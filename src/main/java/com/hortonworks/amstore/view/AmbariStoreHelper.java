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


//
import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.hortonworks.amstore.view.proxy.Proxy;
import com.hortonworks.amstore.view.utils.ServiceFormattedException;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

public class AmbariStoreHelper {

	public AmbariStoreHelper() {
	}

	// internal
	private static String readAll(Reader rd) throws IOException {
		StringBuilder sb = new StringBuilder();
		int cp;
		while ((cp = rd.read()) != -1) {
			sb.append((char) cp);
		}
		return sb.toString();
	}
	
	  private static String readStringFromUrl2( String url, String username,
				String password ) throws IOException {
		  URLConnection connection = new URL(url).openConnection();
		  connection.setConnectTimeout(5000);
		  connection.setDoOutput(true);
		  InputStream in = connection.getInputStream();
		  try {
			  return IOUtils.toString(in,"UTF-8");
		  } finally {
			  in.close();
		  }
	  }

	@SuppressWarnings("restriction")
	public static String readStringFromUrl(String url, String username,
			String password) throws IOException {

		// defaulting to max 2 seconds
		// int timeout = 2000;
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

		/* WAS:
		BufferedReader rd = new BufferedReader(new InputStreamReader(is,
				Charset.forName("UTF-8")));
		String response = readAll(rd);
		*/
		// NOW
		try {
			  return IOUtils.toString(in,"UTF-8");
		  } finally {
			  in.close();
		  }
		
	}

	// Reads a string into a JSON object
	public static JSONObject readJsonFromUrl(String url, String username,
			String password) throws IOException {

			String jsonText = readStringFromUrl( url, username, password );
		
			try {
				JSONObject json = new JSONObject(jsonText);
				return json;
			} catch (JSONException e) {
				// throw new
				// GenericException("Error converting output to json: "
				// + jsonText, e);
				return new JSONObject("{}");
			}

	}

	public static JSONObject readJsonFromUrl2(String url, String username,
			String password) throws IOException, JSONException {

		CloseableHttpClient httpclient;
		if (username != null) {
			CredentialsProvider credsProvider = new BasicCredentialsProvider();
			credsProvider.setCredentials(new AuthScope("localhost", 443),
					new UsernamePasswordCredentials("username", "password"));
			httpclient = HttpClients.custom()
					.setDefaultCredentialsProvider(credsProvider).build();
		} else {
			httpclient = HttpClients.custom().build();

		}

		try {
			HttpGet httpget = new HttpGet("http://localhost/");

			RequestConfig requestConfig = RequestConfig.custom()
					.setSocketTimeout(5000).setConnectTimeout(5000)
					.setConnectionRequestTimeout(5000).build();
			httpget.setConfig(requestConfig);

			// System.out.println("Executing request " +
			// httpget.getRequestLine());
			CloseableHttpResponse response = httpclient.execute(httpget);
			try {
				// System.out.println("----------------------------------------");
				// System.out.println(response.getStatusLine());
				HttpEntity entity = response.getEntity();
				return entity != null ? new JSONObject(
						EntityUtils.toString(entity)) : null;

			} finally {
				response.close();
			}
		} finally {
			httpclient.close();
		}
	}

	// Reads a string into a JSON object
	public static JSONObject readJsonFromUrl(String url) throws IOException,
			JSONException {
		return readJsonFromUrl(url, null, null);
	}

	// TODO: Eliminate the need for urlStreamProvider and associated Context
	// POST and interpret as JSON
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

		// DO THE REQUEST
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

		// DO THE REQUEST
		return proxy.request(ambari.url + url).setData(data).put().asString();

	}

	// TODO: rewrite this in a dynamic graphical manner
	public static void downloadFile(String url, String localFilePath) {

		try {
//			String filename = url.substring(url.lastIndexOf('/') + 1,
//					url.length());
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

		// DO THE REQUEST
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

		// DO THE REQUEST
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

		// DO THE REQUEST
		return proxy.request(ambari.url + url).delete().asString();
	}

	public static String jsonRequestToString(String json, String url) {
		JSONObject js = new JSONObject(json);
		return js.toString() + "\n" + url;
	}


	

	  
	
	  
	  /* HOW TO USE readFrom2
	  // Populate the weather property.
	  private Map<String, Object> getWeatherProperty(String city, String units) throws IOException {
	    URIBuilder uriBuilder = new URIBuilder();
	    uriBuilder.setScheme("http");
	    uriBuilder.setHost("api.openweathermap.org");
	    uriBuilder.setPath("/data/2.5/weather");
	    uriBuilder.setParameter("q", city);
	    uriBuilder.setParameter("units", units);

	    String url = uriBuilder.toString();

	    InputStream in = readFrom2(url);
	    try {
	      Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
	      Map<String, Object> results =  new Gson().fromJson(IOUtils.toString(in, "UTF-8"), mapType);

	      ArrayList list = (ArrayList) results.get("weather");
	      if (list != null) {
	        Map weather = (Map) list.get(0);
	        results.put("weather", weather);
	        results.put("icon_src", "http://openweathermap.org/img/w/" + weather.get("icon"));
	      }
	      return results;
	    } finally {
	      in.close();
	    }
	  }*/

}