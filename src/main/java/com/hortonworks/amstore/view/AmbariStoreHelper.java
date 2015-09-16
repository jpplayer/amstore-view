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
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.IOUtils;

import java.io.PrintWriter;

import com.hortonworks.amstore.view.proxy.Proxy;
import com.hortonworks.amstore.view.utils.ServiceFormattedException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AmbariStoreHelper {

	private final static Logger LOG = LoggerFactory
			.getLogger(AmbariStoreHelper.class);
	
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

		return proxy.request(ambari.getUrl() + url).setData(data).put().asJSON();

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

		return proxy.request(ambari.getUrl() + url).setData(data).put().asString();
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

		return proxy.request(ambari.getUrl() + url).setData(data).post().asJSON();
	}
	
	public static String doGet(URLStreamProvider urlStreamProvider,
			AmbariEndpoint ambari, String url)
			throws IOException {
		return readStringFromUrl( ambari.getUrl() + url, ambari.getUsername(), ambari.getPassword());
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

		return proxy.request(ambari.getUrl() + url).setData(data).post().asString();
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

		return proxy.request(ambari.getUrl() + url).delete().asString();
	}

	public static String jsonRequestToString(String json, String url) {
		JSONObject js = new JSONObject(json);
		return js.toString() + "\n" + url;
	}

	/** Untar an input file into an output file.

	 * The output file is created in the output folder, having the same name
	 * as the input file, minus the '.tar' extension. 
	 * 
	 * @param inputFile     the input .tar file
	 * @param outputDir     the output directory file. 
	 * @throws IOException 
	 * @throws FileNotFoundException
	 *  
	 * @return  The {@link List} of {@link File}s with the untared content.
	 * @throws ArchiveException 
	 */
	public static List<File> unTar(final File inputFile, final File outputDir) throws FileNotFoundException, IOException, ArchiveException {

	    LOG.info(String.format("Untaring %s to dir %s.", inputFile.getAbsolutePath(), outputDir.getAbsolutePath()));

	    final List<File> untaredFiles = new LinkedList<File>();
	    final InputStream is = new FileInputStream(inputFile); 
	    final TarArchiveInputStream debInputStream = (TarArchiveInputStream) new ArchiveStreamFactory().createArchiveInputStream("tar", is);
	    TarArchiveEntry entry = null; 
	    while ((entry = (TarArchiveEntry)debInputStream.getNextEntry()) != null) {
	        final File outputFile = new File(outputDir, entry.getName());
	        if (entry.isDirectory()) {
	            LOG.info(String.format("Attempting to write output directory %s.", outputFile.getAbsolutePath()));
	            if (!outputFile.exists()) {
	                LOG.info(String.format("Attempting to create output directory %s.", outputFile.getAbsolutePath()));
	                if (!outputFile.mkdirs()) {
	                    throw new IllegalStateException(String.format("Couldn't create directory %s.", outputFile.getAbsolutePath()));
	                }
	            }
	        } else {
	            LOG.info(String.format("Creating output file %s.", outputFile.getAbsolutePath()));
	            final OutputStream outputFileStream = new FileOutputStream(outputFile); 
	            IOUtils.copy(debInputStream, outputFileStream);
	            outputFileStream.close();
	        }
	        untaredFiles.add(outputFile);
	    }
	    debInputStream.close(); 

	    return untaredFiles;
	}

	/**
	 * Ungzip an input file into an output file.
	 * <p>
	 * The output file is created in the output folder, having the same name
	 * as the input file, minus the '.gz' extension. 
	 * 
	 * @param inputFile     the input .gz file
	 * @param outputDir     the output directory file. 
	 * @throws IOException 
	 * @throws FileNotFoundException
	 *  
	 * @return  The {@File} with the ungzipped content.
	 */
	public static File unGzip(final File inputFile, final File outputDir) throws FileNotFoundException, IOException {

	    LOG.info(String.format("Ungzipping %s to dir %s.", inputFile.getAbsolutePath(), outputDir.getAbsolutePath()));

	    final File outputFile = new File(outputDir, inputFile.getName().substring(0, inputFile.getName().length() - 3));

	    final GZIPInputStream in = new GZIPInputStream(new FileInputStream(inputFile));
	    final FileOutputStream out = new FileOutputStream(outputFile);

	    IOUtils.copy(in, out);

	    in.close();
	    out.close();

	    return outputFile;
	}
	
	/* 
	 * JSON Utility function. 
	 * TODO: Probably should be somewhere else.
	 */
	public static String _s(JSONObject o, String s) {
		try {
			return o.getString(s);
		} catch (Exception e) {
			return null;
		}
	}

	
	/* 
	 * JSON Utility function. 
	 * TODO: Probably should be somewhere else.
	 */
	public static boolean _b(JSONObject o, String s) {
		try {
			return o.getBoolean(s);
		} catch (Exception e) {
			return false;
		}
	}
	
	/* 
	 * JSON Utility function. 
	 * TODO: Probably should be somewhere else.
	 */
	public static JSONObject _o(JSONObject o, String s) {
		try {
			return (JSONObject) o.get(s);
		} catch (Exception e) {
			return null;
		}
	}

	/* 
	 * JSON Utility function. 
	 * TODO: Probably should be somewhere else.
	 */
	public static JSONArray _a(JSONObject o, String s) {
		try {
			return (JSONArray) o.getJSONArray(s);
		} catch (Exception e) {
			return new JSONArray("[]");
		}
	}
	
}