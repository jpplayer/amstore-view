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
import java.util.Iterator;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.TreeMap;

import org.apache.ambari.view.ViewContext;
import org.apache.ambari.view.ViewInstanceDefinition;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.json.JSONArray;
import org.json.JSONObject;

// TODO: getters and setters
public class StoreApplication {

	// protected
	protected String id;
	protected String app_id;
	protected String category;

	protected String label; // obsolete. label is View Name
	protected String description;
	protected String readiness;
	protected String homepage;
	protected String uri;
	// These come from the Backend Store.
	protected Map<String, String> properties = new TreeMap<String, String>();
	// Used for put()
	protected Map<String, String> desiredInstanceProperties = null;

	// Settings from view.xml
	protected String viewName;
	protected String version;

	protected String contributor;

	// name of the instance managed by the Store
	// public String viewVersion;
	protected String instanceName;
	protected String instanceDisplayName;

	protected String package_uri;
	protected List<String> tags = new ArrayList<String>();

	/*
	public StoreApplication(ViewContext viewContext) {
		this.viewContext = viewContext;
	}*/
	public StoreApplication(ViewContext viewContext, String appId) {		
		this.viewContext = viewContext;
		
	}
	
	
	public StoreApplication(ViewContext viewContext, String viewName,
			String version, String instanceName, String instanceDisplayName,
			String description) {
		this.viewContext = viewContext;
		this.viewName = viewName;
		this.version = version;
		this.instanceName = instanceName;
		this.instanceDisplayName = instanceDisplayName;
		this.description = description;
	}
	
	public StoreApplication(ViewContext viewContext, ViewInstanceDefinition viewInstanceDefinition) {
		this.viewContext = viewContext;
		this.viewName = viewInstanceDefinition.getViewName();
		this.version = viewInstanceDefinition.getViewDefinition().getVersion();
		this.instanceName = viewInstanceDefinition.getInstanceName();
		this.instanceDisplayName = viewInstanceDefinition.getLabel();
		this.description = viewInstanceDefinition.getDescription();
	}

	public StoreApplication(JSONObject app) {
		id = _s(app, "id");
		uri = _s(app, "uri");
		app_id = _s(app, "app_id");
		version = _s(app, "version");
		category = _s(app, "category");
		description = _s(app, "description");
		readiness = _s(app, "readiness");
		homepage = _s(app, "homepage");
		viewName = _s(app, "view_name");
		// viewVersion = _s(app, "view_version");
		instanceName = _s(app, "instance_name");
		instanceDisplayName = _s(app, "display_name");
		package_uri = _s(app, "package_uri");

		contributor = _s(app, "contributor");
		packager = _s(app, "packager");
		
		JSONArray atags = _a(app, "tags");
		for (int j = 0; j < atags.length(); j++) {
			tags.add(atags.getString(j));
		}

		JSONObject props = _o(app, "properties");
		if (props != null) {
			Iterator<?> keys = props.keys();
			while (keys.hasNext()) {
				String key = (String) keys.next();
				properties.put(key, _s(props, key));
			}
		}

	}

	// Returns true if this application represents the Ambari Store itself
	public boolean isStore(){
		return app_id.equals("ambari-store");
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
	
	
	// The viewContext
	protected ViewContext viewContext;


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

	private String _s(JSONObject o, String s) {
		try {
			return o.getString(s);
		} catch (Exception e) {
			return null;
		}
	}

	private JSONObject _o(JSONObject o, String s) {
		try {
			return (JSONObject) o.get(s);
		} catch (Exception e) {
			return null;
		}
	}

	private JSONArray _a(JSONObject o, String s) {
		try {
			return (JSONArray) o.getJSONArray(s);
		} catch (Exception e) {
			return new JSONArray("[]");
		}
	}

	public String getPackageFilepath() {
		if (package_uri != null) {
			String targetPath = "/var/lib/ambari-server/resources/views";
			String filename = FilenameUtils.getName(package_uri);
			// Remove any leftover uri characters
			filename = filename.split("\\?")[0];
//					substring(0, filename.indexOf('?'));
			
			return targetPath + "/" + filename;
		} else
			return null;
	}

	public String getViewName() {
		if ( viewName == null)
			throw new RuntimeException("Call to viewname returning null");
		return viewName;
	}

	public String getVersion() {
		return version;
	}

	public String getPackageWorkdir() {

		String workDirectory = "/var/lib/ambari-server/resources/views/work/"
				+ getViewName() + "{" + getVersion() + "}";
		return workDirectory;
	}

	public String getApp_id() {
		return app_id;
	}

	public String getCategory() {
		return category;
	}

	public String getLabel() {
		return label;
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

	public ViewContext getViewContext() {
		return viewContext;
	}

	
	public String deleteApplicationFiles(){
		String response = "";
		String workDirectory = getPackageWorkdir();
		String packagePath = getPackageFilepath();
		try {
			response += "Deleting " + packagePath + " and " + workDirectory
					+ ".<br>";
			FileUtils.deleteDirectory(new File(workDirectory));
			FileUtils.forceDelete(new File(packagePath));

		} catch (IOException e) {
			response += "Something went wrong deleting " + workDirectory
					+ " or " + packagePath + "<br>";
		}
		return response;
	}
}