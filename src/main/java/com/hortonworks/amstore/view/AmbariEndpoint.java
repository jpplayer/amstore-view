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

import java.io.IOException;
import java.util.Map;

import org.apache.ambari.view.ViewContext;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AmbariEndpoint extends Endpoint {

	private final static Logger LOG = LoggerFactory
			.getLogger(AmbariEndpoint.class);

	protected String clusterName;

	public AmbariEndpoint(String url, String username, String password) {
		super(url, username, password);
		setUrl(url);
		// Note: we do not initialize the clusterName yet.
	}

	@Override
	public void setUrl(String url) {
		if (url == null)
			this.url = "";
		// Remove any trailing slashes
		if (url.endsWith("/")) {
			this.url = url.substring(0, url.length() - 1);
		}
	}

	public String getClusterApiEndpoint() {
		try {
			return url + "/api/v1/clusters/" + getClusterName();
		} catch (IOException e) {
			return null;
		}
	}

	public String getClusterName() throws IOException {
		// Lazy init
		if (clusterName == null) {
			clusterName = netgetClusterName();
		}
		return clusterName;
	}

	// This is only true because we ignore the clusterName
	public boolean equals(AmbariEndpoint other) {
		return this.url.equals(other.url)
				&& this.username.equals(other.username)
				&& this.password.equals(other.password);
	}

	public void reload() throws IOException {
		clusterName = netgetClusterName();
	}

	public static AmbariEndpoint getAmbariLocalEndpoint(
			Map<String, String> propertyMap) {
		return new AmbariEndpoint(propertyMap.get("amstore.ambari.local.url"),
				propertyMap.get("amstore.ambari.local.username"),
				propertyMap.get("amstore.ambari.local.password"));
	}

	// Returns the endpoint if one is configured. Can be null.
	public static AmbariEndpoint getAmbariRemoteEndpoint(
			Map<String, String> propertyMap) {
		String local = propertyMap.get("amstore.ambari.cluster.url");

		if (local != null && !local.isEmpty()) {
			return new AmbariEndpoint(
					propertyMap.get("amstore.ambari.cluster.url"),
					propertyMap.get("amstore.ambari.cluster.username"),
					propertyMap.get("amstore.ambari.cluster.password"));
		} else {
			return null;
		}
	}

	// Select appropriate Ambari server (local or remote if Views Server)
	public static AmbariEndpoint getAmbariClusterEndpoint(
			Map<String, String> propertyMap) {
		String local = propertyMap.get("amstore.ambari.cluster.url");

		if (local != null && !local.isEmpty()) {
			return new AmbariEndpoint(
					propertyMap.get("amstore.ambari.cluster.url"),
					propertyMap.get("amstore.ambari.cluster.username"),
					propertyMap.get("amstore.ambari.cluster.password"));
		} else {
			// Duplicate local cluster information
			return getAmbariLocalEndpoint(propertyMap);
		}
	}

	protected String netgetClusterName() throws IOException {
		JSONObject clusterpage = AmbariStoreHelper.readJsonFromUrl(url
				+ "/api/v1/clusters", username, password);
		JSONArray clusters = clusterpage.getJSONArray("items");
		if (clusters.length() == 0) {
			clusterName = "";
		} else {
			JSONObject cluster = clusters.getJSONObject(0);
			clusterName = cluster.getJSONObject("Clusters").getString(
					"cluster_name");
		}
		return clusterName;
	}

	public void createViewInstance(ViewContext viewContext,
			StoreApplication application) throws IOException {
		createOrUpdateViewInstance(viewContext, application, "create");
	}

	public void updateViewInstance(ViewContext viewContext,
			StoreApplication application) throws IOException {
		createOrUpdateViewInstance(viewContext, application, "update");
	}

	public void createOrUpdateViewInstance(ViewContext viewContext,
			StoreApplication application, String operation) throws IOException {

		String data = "[{\n" + "  \"ViewInstanceInfo\" : {\n"
				+ "    \"label\" : \"LABEL\",\n"
				+ "    \"description\" : \"DESCRIPTION\",\n"
				+ "    \"properties\" : PROPERTIES \n" + "  }\n" + "}]";
		data = data.replaceFirst("LABEL", application.instanceDisplayName);
		data = data.replaceFirst("DESCRIPTION", application.description);

		JSONObject properties = new JSONObject(
				application.getDesiredInstanceProperties());
		data = data.replace("PROPERTIES", properties.toString());

		String url = "/api/v1/views/" + application.viewName + "/versions/"
				+ application.version + "/instances/"
				+ application.instanceName;

		LOG.debug("createOrUpdateViewInstance:" + data + "\n" + url);
		if (operation.equals("create")) {
			org.json.simple.JSONObject output = AmbariStoreHelper.doPostAsJson(
					viewContext.getURLStreamProvider(), this, url, data);
		} else {
			org.json.simple.JSONObject output = AmbariStoreHelper.doPutAsJson(
					viewContext.getURLStreamProvider(), this, url, data);
		}
		// TODO: check the output
	}

	/*
	 * Makes a call to the local amstore-daemon. Will be obsolete once an ambari
	 * restart is no longer required.
	 */
	public String restartAmbari() throws IOException {
		String url = "http://localhost:5026/amstore/restart-ambari";
		return AmbariStoreHelper.readStringFromUrl(url, null, null);
	}

	// This function calls the Service for postInstallTasks
	/*
	 * $CURL -H "X-Requested-By: $agent" -X POST
	 * 'http://localhost:8080/api/v1/views/AMBARI-STORE/versions/0.1.0/instances/store/resources/taskmanager/postinstalltasks/execute'
	 */
	public String callExecutePostInstallTasks(ViewContext viewContext,
			MainStoreApplication application) {

		String url = "/api/v1/views/" + application.viewName + "/versions/"
				+ application.version + "/instances/"
				+ application.instanceName
				+ "/resources/taskmanager/postinstalltasks/execute";

		return AmbariStoreHelper.doPost(viewContext.getURLStreamProvider(),
				this, url, "");
	}

	@Override
	public boolean isAvailable() {
		// Implemented via a call to getClusterName
		try {
			isCluster();
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	// Returns true if this Ambari Endpoint manages a cluster
	public boolean isCluster() throws IOException {
		String name = getClusterName();
		if (name.equals(""))
			return false;
		else
			return true;
	}

}