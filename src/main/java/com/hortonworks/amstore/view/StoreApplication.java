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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Properties;
import java.util.TreeMap;

import org.apache.ambari.view.ViewContext;
import org.apache.ambari.view.ViewInstanceDefinition;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class StoreApplication implements 
	Comparator<StoreApplication>,
	Comparable<StoreApplication>
{
	private final static Logger LOG = LoggerFactory
			.getLogger(StoreApplication.class);
	// Global
	protected static String mainStoreCanonicalName = null;

	// protected
	protected String id;
	protected String app_id;
	protected String category;

	protected String readiness;
	protected String homepage;
	protected String uri;

	// These come from the Backend Store.
	protected Map<String, String> properties = new TreeMap<String, String>();
	// Used for put()
	protected Map<String, String> desiredInstanceProperties = null;

	// Settings from view.xml
	protected String type;

	protected String version = "";
	protected String instanceName;
	protected String instanceDisplayName;
	protected String description;

	protected String contributor;

	protected String package_uri;
	protected List<String> tags = new ArrayList<String>();

	/*
	 * Should only ever be called by inheriting classes, which should set the
	 * main values (viewName, version, etc)
	 */
	protected StoreApplication() {
	}

	public StoreApplication(String version,
			String instanceName, String instanceDisplayName, String description) {
		this.version = version;
		this.instanceName = instanceName;
		this.instanceDisplayName = instanceDisplayName;
		this.description = description;
	}

	public StoreApplication(JSONObject app) {
		setVariablesFromJson(app);
	}

	@SuppressWarnings("static-access")
	protected void setVariablesFromJson(JSONObject app) {
		AmbariStoreHelper h = new AmbariStoreHelper();
		id = h._s(app, "id");
		uri = h._s(app, "uri");
		type = h._s(app, "type");
		app_id = h._s(app, "app_id");
		version = h._s(app, "version");
		category = h._s(app, "category");
		description = h._s(app, "description");
		readiness = h._s(app, "readiness");
		homepage = h._s(app, "homepage");
		instanceName = h._s(app, "instance_name");
		instanceDisplayName = h._s(app, "display_name");
		package_uri = h._s(app, "package_uri");

		contributor = h._s(app, "contributor");
		packager = h._s(app, "packager");

		JSONArray atags = h._a(app, "tags");
		for (int j = 0; j < atags.length(); j++) {
			tags.add(atags.getString(j));
		}

		JSONObject props = h._o(app, "properties");
		if (props != null) {
			Iterator<?> keys = props.keys();
			while (keys.hasNext()) {
				String key = (String) keys.next();
				properties.put(key, h._s(props, key));
			}
		}

	}

	/*
	 * Abstract methods
	 */
	public abstract void deleteApplicationFiles(AmbariEndpoint localAmbari) throws IOException;

	public abstract String getPackageWorkdir(AmbariEndpoint localAmbari) throws IOException;

	public abstract void doInstallStage1(AmbariEndpoint localAmbari)
			throws IOException, StoreException;

	/*
	 * doInstallStage2 is called right after doInstallStage1 It should throw a
	 * StoreException at INFO level if a package is missing due to Ambari not
	 * being restarted.
	 */
	public abstract void doInstallStage2(AmbariEndpoint localAmbari,
			boolean reinstall) throws IOException, StoreException;

	public abstract void doUpdateStage1(StoreApplication newApplication)
			throws IOException, StoreException;

	/*
	 * doUpdateStage2 is called right after doUpdateStage1 It should throw a
	 * StoreException at INFO level if a package is missing due to Ambari not
	 * being restarted.
	 */
	public abstract void doUpdateStage2(AmbariEndpoint localAmbari,
			StoreApplication newApplication) throws IOException, StoreException;

	public abstract void doUninstallStage1(AmbariEndpoint localAmbari)
			throws IOException, StoreException;

	public abstract void doDeinstantiateStage1(AmbariEndpoint localAmbari)
			throws IOException, StoreException;

	
	/*
	 * This method is used to quickly lookup an installed application (without
	 * the version) 
	 *   views: view-$viewName 
	 *   services: service-$serviceName 
	 *   assembly: assembly-$assemblyName
	 */
	public abstract String getCanonicalName();

	
	public abstract String getName();

	
	// Only used for isStore()
	private String getMainStoreCanonicalName() {
		if (mainStoreCanonicalName == null) {
			Properties properties = new Properties();
			InputStream stream = getClass().getClassLoader()
					.getResourceAsStream("store.properties");
			try {
				properties.load(stream);
				mainStoreCanonicalName = "view-" + properties.getProperty("viewName"); // + "-" + properties.getProperty("instanceName");
			} catch (IOException e) {
				throw new RuntimeException(
						"Error reading property file store.properties.");
			}
		}
		return mainStoreCanonicalName;

	}

	// Returns true if this application represents the Ambari Store itself
	// TODO: ensure the value is set correctly by the Factory, so we can simply
	// overload correctly
	public boolean isStore() {
		if (getCanonicalName() == null)
			throw new RuntimeException("Called isStore() with canonicalName null.");
		return getCanonicalName().equals(getMainStoreCanonicalName());
	}

	public boolean isView() {
		return this instanceof StoreApplicationView;
	}

	public boolean isService() {
		return this instanceof StoreApplicationService;
	}

	public String getId() {
		return id;
	}

	public String getContributor() {
		return contributor;
	}

	public void setContributor(String contributor) {
		this.contributor = contributor;
	}

	public String getPackager() {
		return packager;
	}

	public void setPackager(String packager) {
		this.packager = packager;
	}

	protected String packager;

	// Properties from backend Store
	public Map<String, String> getBackendProperties() {
		return properties;
	}

	public Map<String, String> getDesiredInstanceProperties() {
		return desiredInstanceProperties;
	}

	// Properties from managed Instance
	public void setDesiredInstanceProperties(Map<String, String> props) {
		desiredInstanceProperties = props;
	}

	class JSONOutput {
		private Object o;

		JSONOutput(Object o) {
			this.o = o;
		}

		public String getString() {
			return (String) o;
		}

		public JSONObject getJSONObject() {
			return (JSONObject) o;
		}
	}

	public String getVersion() {
		return version;
	}

	public String getApp_id() {
		return app_id;
	}

	public String getCategory() {
		return category;
	}

	public String getDescription() {
		return description;
	}

	public String getReadiness() {
		return readiness;
	}

	public String getHomepage() {
		return homepage;
	}

	public String getUri() {
		return uri;
	}

	public Map<String, String> getProperties() {
		return properties;
	}

	public String getInstanceName() {
		return instanceName;
	}

	public String getInstanceDisplayName() {
		return instanceDisplayName;
	}

	public String getPackage_uri() {
		return package_uri;
	}

	public List<String> getTags() {
		return tags;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public void deleteWorkDirectory(AmbariEndpoint localAmbari) throws IOException {
		String workDirectory = getPackageWorkdir(localAmbari);
		FileUtils.deleteDirectory(new File(workDirectory));
	}
	
	public int compareVersions(String a, String b){
		
		String[] adots = a.split("\\.",2);
		String[] bdots = b.split("\\.",2);
		if( adots[0].equals(bdots[0])) {
			if( adots.length == 1 && bdots.length == 1) return 0;
			if( adots.length == 1 ) return -1;
			if( bdots.length == 1 ) return 1;
			return compareVersions(adots[1], bdots[1]);
		}
		
		return new Integer(adots[0]) - new Integer(bdots[0]);
	}
	
	// Applications are compared on versions
	public int compare(StoreApplication a, StoreApplication b) {
		return compareVersions( a.getVersion(), b.getVersion());
	}		

	public int compareTo(StoreApplication o) {
		return compare(this,o);
	}


}