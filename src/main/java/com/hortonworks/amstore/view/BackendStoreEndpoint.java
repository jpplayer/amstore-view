package com.hortonworks.amstore.view;

import java.io.IOException;

import java.util.Map;
import java.util.TreeMap;


import org.json.JSONArray;
import org.json.JSONObject;

public class BackendStoreEndpoint extends Endpoint {

	protected Map<String, StoreApplication> availableApplications = null;

	public BackendStoreEndpoint(	String url, String username, String password) {
		super(url, username, password);
	}

	//@Override
	public boolean isAvailable(){
		boolean result = false;
		if( url == null ) 
			return false;
//			throw new GenericException("Internal Error calling BackendStoreEndpoint. isAvailable: url was null");
			
		try {
			// Call the Ambari Store
			JSONObject json = AmbariStoreHelper
					.readJsonFromUrl( url + "/api/v1/status");
			// This is NOT and HTTP CODE
			String code = (String) json.get("code");
			if( code != null && code.equals("200") )
				result = true;
		} catch (IOException e) {
			// took out url
//			throw new GenericException("Error checking " + url + "/api/v1/status.\n", e );
//			throw new GenericException("IOException reading from" + "/api/v1/status" + ":" );
		}

		return result;
	}
	
	
	public StoreApplication netgetApplicationFromStoreByAppId( String appId, String version){
		
		// Lookup application by exact version. Returns null if not found.
		StoreApplication storeApplication = null;
		try {
			// Call the Ambari Store
			String application_uri = url + "/api/v1/applications/" + appId + "-" + version;
			JSONObject json = AmbariStoreHelper
					.readJsonFromUrl(application_uri);

			storeApplication = new StoreApplication(
					(JSONObject) json.get("application"));
			// TODO
		} catch (IOException e) {

/*TODO			throw new GenericException(
					"Error when communicating with backend: IOException: <br>"
							,e);
*/		} catch (Exception e) {
/*TODO			throw new GenericException("Error parsing applications: <br>"
					+ e );
*/		}
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
			// TODO
		} catch (IOException e) {

/*TODO			throw new GenericException(
					"Error when communicating with backend: IOException: <br>"
							,e);
*/		} catch (Exception e) {
/*TODO			throw new GenericException("Error parsing applications: <br>"
					+ e );
*/		}
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
			JSONObject applications = AmbariStoreHelper.readJsonFromUrl(url + "/api/v1/applications");

			// Really should convert all this into a resource / POJO
			JSONArray apps = applications.getJSONArray("applications");
			for (int i = 0; i < apps.length(); i++) {
				StoreApplication app = new StoreApplication(
						apps.getJSONObject(i));
				storeApplications.put(app.app_id, app);
			}
		} catch (IOException e) {
/*			 throw new GenericException(
			 "Error when communicating with backend: IOException: <br>"
			 ,e);*/
		} 
		return storeApplications;
	}

}
