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
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackendStoreEndpoint extends Endpoint {
	private final static Logger LOG = LoggerFactory
			.getLogger(BackendStoreEndpoint.class);
	protected Map<String, StoreApplication> availableApplications = null;

	public BackendStoreEndpoint(String url, String username, String password) {
		super(url, username, password);
	}

	// @Override
	public boolean isAvailable() {
		boolean result = false;
		if (url == null)
			return false;

		try {
			// Call the Ambari Store
			JSONObject json = AmbariStoreHelper.readJsonFromUrl(url
					+ "/api/v1/status");
			// This is NOT the HTTP CODE
			String code = (String) json.get("code");
			if (code != null && code.equals("200"))
				result = true;
		} catch (IOException e) {
			LOG.debug("IOException contacting " + url + "/api/v1/status");
		}
		return result;
	}

	public StoreApplication netgetApplicationFromStoreByAppId(String appId,
			String version) {

		// Lookup application by exact version. Returns null if not found.
		StoreApplication storeApplication = null;
		try {
			// Call the Ambari Store
			String application_uri = url + "/api/v1/applications/" + appId
					+ "-" + version;
			JSONObject json = AmbariStoreHelper
					.readJsonFromUrl(application_uri);

			storeApplication = new StoreApplication(
					(JSONObject) json.get("application"));
		} catch (IOException e) {
			LOG.debug("IOException contacting " + url + "/api/v1/applications/"
					+ appId + "-" + version);
		} catch (Exception e) {
			LOG.debug("Exception contacting " + url + "/api/v1/status");
		}
		return storeApplication;
	}

	public static StoreApplication netgetApplicationFromStoreByUri(
			String application_uri) {

		StoreApplication storeApplication = null;
		try {
			// Call the Ambari Store
			JSONObject json = AmbariStoreHelper
					.readJsonFromUrl(application_uri);
			storeApplication = new StoreApplication(
					(JSONObject) json.get("application"));
		} catch (IOException e) {
			LOG.debug("IOException getting application details for"
					+ application_uri);
		} catch (Exception e) {
			LOG.debug("Exception getting application details for"
					+ application_uri);
		}
		return storeApplication;
	}

	// Lazy initialization
	public Map<String, StoreApplication> getAllApplicationsFromStore() {
		if (availableApplications == null)
			availableApplications = netgetAllApplicationsFromStore();
		return availableApplications;
	}

	// Force a refresh
	public void refresh() {
		availableApplications = netgetAllApplicationsFromStore();
	}

	protected Map<String, StoreApplication> netgetAllApplicationsFromStore() {
		Map<String, StoreApplication> storeApplications = new TreeMap<String, StoreApplication>();
		try {
			// Call the Ambari Store
			JSONObject applications = AmbariStoreHelper.readJsonFromUrl(url
					+ "/api/v1/applications");

			// TODO: Really should convert all this into a resource / POJO
			JSONArray apps = applications.getJSONArray("applications");
			for (int i = 0; i < apps.length(); i++) {
				StoreApplication app = new StoreApplication(
						apps.getJSONObject(i));
				storeApplications.put(app.app_id, app);
			}
		} catch (IOException e) {
			LOG.debug("IOException getting applications from store.");
		}
		return storeApplications;
	}

}
