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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.ambari.view.URLStreamProvider;
import org.apache.ambari.view.ViewContext;
import org.apache.ambari.view.ViewInstanceDefinition;
import org.apache.commons.lang.NotImplementedException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hortonworks.amstore.view.utils.ServiceFormattedException;

public class AmbariEndpoint extends Endpoint {

	private final static Logger LOG = LoggerFactory
			.getLogger(AmbariEndpoint.class);

	protected String clusterName;
	protected String clusterStackName;
	protected String clusterStackVersion;
	protected ViewContext viewContext;
	protected ServicesConfiguration servicesConfiguration;

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
			this.endpointUrl = "";
		// Remove any trailing slashes
		if (url.endsWith("/")) {
			this.endpointUrl = url.substring(0, url.length() - 1);
		}
	}

	public String getClusterApiEndpoint() {
		try {
			return getUrl() + "/api/v1/clusters/" + getClusterName();
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
		return this.getUrl().equals(other.getUrl())
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

	
	/* Calling /api/v1/clusters/<clustername> is an expensive operation (pulls all desired states),
	 * so we try to do it just once.
	 * Caching enabled.
	*/
	private JSONObject clusterdetails = null;
	// TODO	

	/* Calling /api/v1/clusters just once and filling out clusterName and clusterStackVersion
	 * 
	 */
	//TODO: replace all these global variables with a proper Cluster class.
	protected void netgetClusterInfo() throws IOException {
		JSONObject clusterdetails = AmbariStoreHelper.readJsonFromUrl(getUrl()
				+ "/api/v1/clusters", username, password);
		JSONArray clusters = clusterdetails.getJSONArray("items");
		if (clusters.length() == 0) {
			this.clusterName = "";
			this.clusterStackVersion = "";
		} else {
			JSONObject cluster = clusters.getJSONObject(0);
			this.clusterName = cluster.getJSONObject("Clusters").getString(
					"cluster_name");
			String[] clusterStackInfo = cluster.getJSONObject("Clusters").getString(
					"version").split("-");
			this.clusterStackName = clusterStackInfo[0];
			this.clusterStackVersion = clusterStackInfo[1];
		}
	}
	
	//TODO: replace all these global variables with a proper Cluster class.
	protected String netgetClusterName() throws IOException {
		if( clusterName == null)
			netgetClusterInfo();
		return clusterName;
	}

	//TODO: replace all these global variables with a proper Cluster class.
	protected String netgetClusterStackName() throws IOException {
		if( clusterStackName == null)
			netgetClusterInfo();
		return clusterStackName;
	}
	
	//TODO: replace all these global variables with a proper Cluster class.
	protected String netgetClusterStackVersion() throws IOException {
		if( clusterStackVersion == null)
			netgetClusterInfo();
		return clusterStackVersion;
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

	public void startService(StoreApplicationService applicationService ){
		String url = this.getClusterApiEndpoint() + "/services/" + applicationService.getServiceName();

		String data = "{\"RequestInfo\": {\"context\" :\"Stop " + 
				applicationService.getServiceName() + 
				" via REST\"}, \"Body\": {\"ServiceInfo\": {\"state\": \"STARTED\"}}}";
		
		LOG.debug("Attempting to start: '" + url);
		curlPut(url,data);
	}
	
	public String stopService(StoreApplicationService applicationService ){
		String url = this.getClusterApiEndpoint() + "/services/" + applicationService.getServiceName();

		String data = "{\"RequestInfo\": {\"context\" :\"Stop " + 
				applicationService.getServiceName() + 
				" via REST\"}, \"Body\": {\"ServiceInfo\": {\"state\": \"INSTALLED\"}}}";
		
		LOG.debug("Attempting to stop: '" + url);
		return curlPut(url,data);
	}
	
	private void waitForCompletion(String response, int timeout) {
		try {
			JSONObject json = new JSONObject( response );
			String url = json.getString("href") + "?fields=tasks/Tasks/*";
			
			boolean completed = false;
			while( timeout >= 0 && ! completed ){
				completed=true;
				String tasklistjson = this.curlGet(url);
				JSONArray tasks = new JSONObject(tasklistjson).getJSONArray("tasks");
				for (int i = 0; i < tasks .length(); i++) {
					String status = tasks.getJSONObject(i)
							.getJSONObject("Tasks")
							.getString("status");
					if(! status.equals("COMPLETED") )
						completed = false;
				}
				Thread.sleep(1000);  
				timeout --;
			}
		} catch( IOException e ){
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
		
	}

	public List<String> getServiceComponents(StoreApplicationService applicationService ) throws IOException {
		List<String> componentUris = new LinkedList<String>();
		String url = this.getClusterApiEndpoint() + 
				"/services/" + 
				applicationService.getServiceName() +
				"/components";
		JSONObject json = new JSONObject( curlGet(url) );
		JSONArray items = json.getJSONArray("items");
		for (int i = 0; i < items.length(); i++) {
			componentUris.add(items.getJSONObject(i).getString("href"));
		}
		return componentUris;
	}
	public void deleteServiceComponent( String componentUri ){
		curlDelete( componentUri );
	}
	
	public void deleteServiceInstance(StoreApplicationService applicationService) {
		String url = this.getClusterApiEndpoint() + 
				"/services/" + 
				applicationService.getServiceName();
		curlDelete (url);
	}

	
	public String getActiveStackName() throws IOException {
		return netgetClusterStackName();
	}
	
	public String getActiveStackVersion() throws IOException {
		return netgetClusterStackVersion();
	}


	protected String getServicesFolder() throws IOException {
		return "/var/lib/ambari-server/resources/stacks/"
				+ getActiveStackName() + "/"
				+ getActiveStackVersion() + "/services";
	}

	public void createService(StoreApplicationService applicationService) throws IOException {
		throw new NotImplementedException("Not implemented: createService");
		
		// Create service 
		// createServiceInstance(applicationService);
		//  $CURL -H "X-Requested-By: $agent" --silent -X POST -d '[ { "ServiceInfo" : { "service_name": "STOREAGENT"	  } } ] ' http://localhost:8080/api/v1/clusters/$CLUSTER_NAME/services 
		//curl -u admin:admin -X POST -H "X-Requested-By: ambari" http://localhost:8080/api/v1/clusters/$CLUSTER/services/SOLR

		
		// Create all service components
		// $CURL -H "X-Requested-By: $agent" --silent -X POST  http://localhost:8080/api/v1/clusters/$CLUSTER_NAME/services/STOREAGENT/components/STORE_CLIENT
		//curl -u admin:admin -X POST -H "X-Requested-By: ambari" http://localhost:8080/api/v1/clusters/$CLUSTER/services/SOLR/components/SOLR_MASTER

		
		// Create all Host components
		//  $CURL -H "X-Requested-By: $agent" --silent -X POST http://localhost:8080/api/v1/clusters/$CLUSTER_NAME/hosts/$FQDN/host_components/STORE_CLIENT
		
		// Install all host components
		// requestServiceInstall( applicationService )
		//$CURL -H "X-Requested-By: $agent" --silent -X PUT -d '[ { "RequestInfo": { "context": "Install Store Agent Client" }, "Body" : { "ServiceInfo": { "state": "INSTALLED" }	  } } ] ' http://localhost:8080/api/v1/clusters/$CLUSTER_NAME/services/STOREAGENT 
		
		// Start service
		//startService( applicationService );

	}

	
	public void deleteService(StoreApplicationService applicationService) throws IOException {

		String response = null;
		// Remove packages from disk on all nodes
		// TODO

		// Stop service
		response = stopService( applicationService );
		waitForCompletion( response, 30 );

		// Delete all service components
		List<String> components = getServiceComponents( applicationService );
		for( String component: components ){
			deleteServiceComponent( component );
		}

		// Delete service itself
		deleteServiceInstance(applicationService);
	}

	/*
	 * Makes a call to the local amstore-daemon. Will be obsolete once an ambari
	 * restart is no longer required.
	 */
	public String restartAmbari() throws IOException {
		String url = "http://localhost:5026/amstore/restart-ambari";
		return curlGet(url);
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

	public String curlDelete(String url) throws ServiceFormattedException {
		return AmbariStoreHelper.doDelete(this.viewContext.getURLStreamProvider(),
				this, url);
	}
	public String curlPost(String url, String data) throws ServiceFormattedException {
		return AmbariStoreHelper.doPost(this.viewContext.getURLStreamProvider(),
				this, url, data);
	}
	public String curlPut(String url, String data) throws ServiceFormattedException {
		return AmbariStoreHelper.doPut(this.viewContext.getURLStreamProvider(),
				this, url, data);
	}
	public String curlGet(String url) throws IOException {
		return AmbariStoreHelper.readStringFromUrl(url, this.getUsername(), this.getPassword());
	}

	// Only makes sense in the context of an non-Views Ambari
	// WARNING: NOT FUNCTIONAL, missing version.
	/*
	 * public Map<String, StoreApplicationService> getInstalledServices() throws
	 * IOException { Map<String, StoreApplicationService> installedApplications
	 * = new TreeMap<String, StoreApplicationService>();
	 * 
	 * String servicesUrl = getClusterApiEndpoint() + "/services";
	 * AmbariStoreHelper h = new AmbariStoreHelper();
	 * 
	 * JSONObject servicesJson = AmbariStoreHelper.readJsonFromUrl(servicesUrl,
	 * username, password); JSONArray items = h._a(servicesJson, "items");
	 * 
	 * for (int j = 0; j < items.length(); j++) { JSONObject serviceInfo =
	 * h._o(items.getJSONObject(j), "ServiceInfo"); String serviceName =
	 * h._s(serviceInfo, "service_name"); // TODO: get the version, then call
	 * Store to get corresponding application.
	 * //installedApplications.put(serviceName,
	 * newStoreApplicationService(serviceName)); }
	 * 
	 * return installedApplications; }
	 */

	// Indexed by serviceName
	public Set<String> getListInstalledServiceNames() throws IOException {
		Set<String> serviceNames = new HashSet<String>();

		String servicesUrl = getClusterApiEndpoint() + "/services";
		AmbariStoreHelper h = new AmbariStoreHelper();

		JSONObject servicesJson = AmbariStoreHelper.readJsonFromUrl(
				servicesUrl, username, password);
		JSONArray items = h._a(servicesJson, "items");

		for (int j = 0; j < items.length(); j++) {
			JSONObject serviceInfo = h
					._o(items.getJSONObject(j), "ServiceInfo");
			String serviceName = h._s(serviceInfo, "service_name");
			serviceNames.add(serviceName);
		}
		return serviceNames;
	}

	public class ServiceConfiguration {
		Map<String, String> configuration = new TreeMap<String, String>();

		public ServiceConfiguration() {
		}

		public String get(String key) {
			return configuration.get(key);
		}

		public void set(String key, String value) {
			if (key == null)
				throw new RuntimeException(
						"Call to ServiceConfiguration with null key");
			if (value == null)
				throw new RuntimeException(
						"Call to ServiceConfiguration with null value");
			configuration.put(key, value);
		}
		
		public Map<String, String> getMap(){
			return configuration;
		}

	}

	public class ServicesConfiguration {
		Map<String, ServiceConfiguration> clusterConfiguration = new TreeMap<String, ServiceConfiguration>();

		public ServicesConfiguration() {
		}

		public ServiceConfiguration get(String key) {
			return clusterConfiguration.get(key);
		}

		public void set(String key, ServiceConfiguration value) {
			clusterConfiguration.put(key, value);
		}
		
		public Map<String, ServiceConfiguration> getMap(){
			return clusterConfiguration;
		}

	}

	public ServicesConfiguration getServicesConfiguration()
			throws IOException {
		if ( servicesConfiguration == null ) {
			servicesConfiguration = netgetServicesConfiguration();
		}
		return servicesConfiguration;
			
	}
	
	protected ServicesConfiguration netgetServicesConfiguration()
			throws IOException {
		ServicesConfiguration servicesConfiguration = new ServicesConfiguration();

//		AmbariStoreHelper h = new AmbariStoreHelper();

		// Populate all settings
		String url = this.getClusterApiEndpoint()
				+ "?fields=Clusters/desired_configs";

		JSONObject configResponse = AmbariStoreHelper.readJsonFromUrl(url,
				username, password);
//		JSONObject configIndex = h._o(configResponse, "Clusters");
//		JSONObject configurations = h._o(configIndex, "desired_configs");
		
		JSONObject desiredConfigs = configResponse
				.getJSONObject("Clusters")
				.getJSONObject("desired_configs");
		Iterator<?> keys = desiredConfigs.keys();
		while (keys.hasNext()) {
			String key = (String) keys.next();

				if (desiredConfigs.get(key) instanceof JSONObject) {
					String configType = key;
					String tag = ((JSONObject) desiredConfigs.get(key)).getString("tag");
					ServiceConfiguration serviceConfiguration = netgetServiceConfiguration(
							configType, tag);
					servicesConfiguration.set(key, serviceConfiguration);
				}
		}
		return servicesConfiguration;
	}

	protected ServiceConfiguration netgetServiceConfiguration(
			String configType, String tag) throws IOException {

		ServiceConfiguration serviceConfiguration = new ServiceConfiguration();

		// http://lake01.cloud.hortonworks.com:8080/api/v1/clusters/LAKE/configurations?type=capacity-scheduler&tag=version1423613079534
		String url = this.getClusterApiEndpoint() + "/configurations?type="
				+ configType + "&tag=" + tag;

		JSONObject configResponse = AmbariStoreHelper.readJsonFromUrl(url,
				username, password);
		JSONObject properties = null;
		try { 
			properties = configResponse.getJSONArray("items")
				.getJSONObject(0).getJSONObject("properties");
		} catch(org.json.JSONException e){
			return serviceConfiguration;
		}

		Iterator<?> keys = properties.keys();

		while (keys.hasNext()) {
			String key = (String) keys.next();
			String value = properties.getString(key);
			serviceConfiguration.set(key, value);
		}
		return serviceConfiguration;
	}
} // end class