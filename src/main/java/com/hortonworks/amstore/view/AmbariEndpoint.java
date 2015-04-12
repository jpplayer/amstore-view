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
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.ambari.view.URLStreamProvider;
import org.apache.ambari.view.ViewContext;
import org.apache.ambari.view.ViewInstanceDefinition;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hortonworks.amstore.view.utils.ServiceFormattedException;

public class AmbariEndpoint extends Endpoint {

	private final static Logger LOG = LoggerFactory
			.getLogger(AmbariEndpoint.class);

	protected String clusterName;
	protected ViewContext viewContext;

	public AmbariEndpoint(ViewContext viewContext, String url, String username,
			String password) {
		super(url, username, password);
		setUrl(url);
		// Note: we do not initialize the clusterName yet.
		this.viewContext = viewContext;
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

	public static AmbariEndpoint getAmbariLocalEndpoint(ViewContext viewContext) {
		return new AmbariEndpoint(viewContext, viewContext.getProperties().get(
				"amstore.ambari.local.url"), viewContext.getProperties().get(
				"amstore.ambari.local.username"), viewContext.getProperties()
				.get("amstore.ambari.local.password"));
	}

	// Returns the endpoint if one is configured. Can be null.
	public static AmbariEndpoint getAmbariRemoteEndpoint(ViewContext viewContext) {
		String local = viewContext.getProperties().get(
				"amstore.ambari.cluster.url");

		if (local != null && !local.isEmpty()) {
			return new AmbariEndpoint(viewContext, viewContext.getProperties()
					.get("amstore.ambari.cluster.url"), viewContext
					.getProperties().get("amstore.ambari.cluster.username"),
					viewContext.getProperties().get(
							"amstore.ambari.cluster.password"));
		} else {
			return null;
		}
	}

	// Select appropriate Ambari server (local or remote if Views Server)
	public static AmbariEndpoint getAmbariClusterEndpoint(
			ViewContext viewContext) {
		String local = viewContext.getProperties().get(
				"amstore.ambari.cluster.url");

		if (local != null && !local.isEmpty()) {
			return new AmbariEndpoint(viewContext, viewContext.getProperties()
					.get("amstore.ambari.cluster.url"), viewContext
					.getProperties().get("amstore.ambari.cluster.username"),
					viewContext.getProperties().get(
							"amstore.ambari.cluster.password"));
		} else {
			// Duplicate local cluster information
			return getAmbariLocalEndpoint(viewContext);
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

	public void createViewInstance(StoreApplicationView application)
			throws IOException {
		createOrUpdateViewInstance(application, "create");
	}

	public void updateViewInstance(StoreApplicationView application)
			throws IOException {
		createOrUpdateViewInstance(application, "update");
	}

	public void createOrUpdateViewInstance(StoreApplicationView application,
			String operation) throws IOException {

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

	public void deleteViewInstance(StoreApplicationView application) {

		String url = "/api/v1/views/" + application.getViewName()
				+ "/versions/" + application.getVersion() + "/instances/"
				+ application.getInstanceName();

		LOG.debug("Attempting to delete: '" + url);
		curlDelete(url);
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
	public String callExecutePostInstallTasks(MainStoreApplication application) {

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

	public void curlDelete(String url) throws ServiceFormattedException {
		AmbariStoreHelper.doDelete(this.viewContext.getURLStreamProvider(),
				this, url);
	}
	
	// Only makes sense in the context of an non-Views Ambari
	// WARNING: NOT FUNCTIONAL, missing version.
	public Map<String, StoreApplicationService> getInstalledServices() throws IOException {
		Map<String, StoreApplicationService> installedApplications = new TreeMap<String, StoreApplicationService>();

		String servicesUrl = getClusterApiEndpoint() + "/services";
		AmbariStoreHelper h = new AmbariStoreHelper();
		
		JSONObject servicesJson = AmbariStoreHelper.readJsonFromUrl(servicesUrl, username, password);
		JSONArray items = h._a(servicesJson, "items");
		
		for (int j = 0; j < items.length(); j++) {
			JSONObject serviceInfo = h._o(items.getJSONObject(j), "ServiceInfo");
			String serviceName = h._s(serviceInfo,  "service_name");
			// TODO: get the version, then call Store to get corresponding application.
			//installedApplications.put(serviceName, newStoreApplicationService(serviceName));
		}

		return installedApplications;
	}
	
	public Set<String> getListInstalledServiceNames() throws IOException {
		Set<String> serviceNames = new HashSet<String>();

		String servicesUrl = getClusterApiEndpoint() + "/services";
		AmbariStoreHelper h = new AmbariStoreHelper();
		
		JSONObject servicesJson = AmbariStoreHelper.readJsonFromUrl(servicesUrl, username, password);
		JSONArray items = h._a(servicesJson, "items");
		
		for (int j = 0; j < items.length(); j++) {
			JSONObject serviceInfo = h._o(items.getJSONObject(j), "ServiceInfo");
			String serviceName = h._s(serviceInfo,  "service_name");
			serviceNames.add(serviceName);
		}
		return serviceNames;
	}


}