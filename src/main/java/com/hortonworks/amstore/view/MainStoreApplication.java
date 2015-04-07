package com.hortonworks.amstore.view;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.apache.ambari.view.ViewContext;
import org.apache.ambari.view.ViewDefinition;
import org.apache.ambari.view.ViewInstanceDefinition;
import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.simple.parser.ParseException;

import com.hortonworks.amstore.view.utils.ServiceFormattedException;

public class MainStoreApplication extends StoreApplication {

	protected AmbariEndpoint ambariViews = null;
	protected AmbariEndpoint ambariCluster = null;
	protected BackendStoreEndpoint storeEndpoint = null;
	
	

	public MainStoreApplication(ViewContext viewContext) {
		// TODO: remove hard coded values and pick up from configuration !
		super(viewContext, "AMBARI-STORE", "0.1.0", "store", "Ambari Store",
				"Ambari Application Store");

		initAmbariEndpoints();
	}

	private String parseSpecials(String in) {
		if (in == null)
			return null;
		String replacement = in.replace("{{username}}", "${username}")
				.replace("{{viewName}}", "${viewName}")
				.replace("{{instanceName}}", "${instanceName}");
		// if (replacement == null ) {
		// throw new
		// GenericException("Error parsing specials. This is not supposed to happen");
		// }
		return replacement;
	}

	/*
	 * Configures an application using the configured mappings. We have three
	 * special values: {{username}} maps to ${username} {{}} maps to ${} {{}}
	 * maps o ${}
	 */
	public Map<String, String> getMappedProperties(StoreApplication application) {
		Map<String, String> mapped = new TreeMap<String, String>();

		// TODO: requires AmbariEndpoint ambari throw exception

		// Mappings are mostly the configured properties
		// But we always assign the "cluster" ones even if null.
		Map<String, String> mappings = getIntanceProperties();

		// Views Ambari
		mappings.put("amstore.ambari.views.url", ambariViews.getUrl());
		mappings.put("amstore.ambari.views.clusterurl",
				ambariViews.getClusterApiEndpoint());
		mappings.put("amstore.ambari.views.username", ambariViews.getUsername());
		mappings.put("amstore.ambari.views.password", ambariViews.getPassword());

		// Cluster Ambari
		mappings.put("amstore.ambari.cluster.url", ambariCluster.getUrl());
		mappings.put("amstore.ambari.cluster.clusterurl",
				ambariCluster.getClusterApiEndpoint());
		mappings.put("amstore.ambari.cluster.username",
				ambariCluster.getUsername());
		mappings.put("amstore.ambari.cluster.password",
				ambariCluster.getPassword());

		for (Entry<String, String> e : application.getBackendProperties()
				.entrySet()) {

			if (mappings.containsKey(e.getValue())) {
				mapped.put(e.getKey(),
						parseSpecials(mappings.get(e.getValue())));

			} else {
				mapped.put(e.getKey(), parseSpecials(e.getValue()));
			}
		}
		return mapped;
	}

	// This is public so we can re-init externally, on configuration change or
	// user request
	protected void initAmbariEndpoints() {
		initLocalEndpoint();
		initClusterEndpoint();
	}

	protected void initLocalEndpoint() {
		ambariViews = AmbariEndpoint.getAmbariLocalEndpoint(viewContext
				.getProperties());
	}

	protected void initClusterEndpoint() {
		ambariCluster = AmbariEndpoint.getAmbariClusterEndpoint(viewContext
				.getProperties());

	}

	public String getBackendStoreUrl() {
		return this.getIntanceProperties().get("amstore.url");
	}

	public BackendStoreEndpoint getStoreEndpoint() {
		if (storeEndpoint == null)
			storeEndpoint = new BackendStoreEndpoint(getBackendStoreUrl(),
					null, null);
		return storeEndpoint;
	}

	public Map<String, String> getIntanceProperties() {
		return new TreeMap<String, String>(viewContext.getProperties());
	}

	public AmbariEndpoint getAmbariCluster() {
		AmbariEndpoint ambariClusterconfig = AmbariEndpoint
				.getAmbariClusterEndpoint(viewContext.getProperties());
		if (!ambariCluster.equals(ambariClusterconfig)) {
			// TODO: This is really sneaky.
			// We are actually resetting clusterName to null.
			ambariCluster = ambariClusterconfig;
		}
		return ambariCluster;
	}

	// Always returns the version that is or will soon be installed
	// TODO: make this more efficient, we should not be re-indexing every time
	public String getInstalledVersion( StoreApplication app ){
		
		if (getInstalledApplications().containsKey( app.getInstanceName() )) {
			// Double check that the ViewName matches !
			String installedViewName = getInstalledApplications().get( app.getInstanceName() )
					.getViewName();

			
			// TODO: Might want to throw an Exception
			/*
			if( ! app.getViewName().equals(installedViewName)) {
				return null;
			}*/
						
			
			ViewDefinition viewDefinition = getInstalledApplications().get(
					app.getInstanceName()).getViewDefinition();
			return viewDefinition.getVersion();
		}
		else {
			return null;
		}
		
	}
	
	public AmbariEndpoint getAmbariViews() {
		AmbariEndpoint ambariViewsConfig = AmbariEndpoint
				.getAmbariLocalEndpoint(viewContext.getProperties());
		if (!ambariViews.equals(ambariViewsConfig)) {
			// TODO: This is really sneaky. -- WARNING
			// We are actually resetting clusterName to null.
			ambariViews = ambariViewsConfig;
		}
		return ambariViews;
	}

	public AmbariEndpoint getAmbariRemote() {
		return AmbariEndpoint.getAmbariRemoteEndpoint(viewContext
				.getProperties());
	}

	public Map<String, ViewInstanceDefinition> getInstalledApplications() {
		Map<String, ViewInstanceDefinition> installedApplications = new TreeMap<String, ViewInstanceDefinition>();

		Collection<ViewInstanceDefinition> viewDefinitions = getViewContext()
				.getViewInstanceDefinitions();
		for (ViewInstanceDefinition instance : viewDefinitions) {
			installedApplications.put(instance.getInstanceName(), instance);
		} 
		return installedApplications;

		
	}

	public String deleteApplication(String app_id) {
		// Indexed by instanceName not app_id
		// TODO: this is completely overkill ! Get directly details for that
		// version.
		StoreApplication app = getStoreEndpoint().getAllApplicationsFromStore()
				.get(app_id);
		ViewInstanceDefinition view = getInstalledApplications().get(
				app.instanceName);

		String url = "";
		String response = "";
		
		if ( view == null )
			return "Not found.";

		// Note: we must get the information about this exact version, not any
		// new version that might show up on the app store.
		url = "/api/v1/views/" + view.getViewDefinition().getViewName()
				+ "/versions/" + view.getViewDefinition().getVersion()
				+ "/instances/" + view.getInstanceName();
	

		response += "Attempting to delete: '" + url + "'.<br>";
		return response
				+ AmbariStoreHelper.doDelete(
						this.viewContext.getURLStreamProvider(),
						this.ambariViews, url);
	}

	// TODO: make this more efficient
	public String eraseApplication(String app_id) {
		String response = "";

		// We remove all related files
		// Indexed by instanceName not app_id. We must first determine the name
		// of the
		// managed instance
		StoreApplication newapp = getStoreEndpoint()
				.getAllApplicationsFromStore().get(app_id);

		// WARNING: make sure we use the ViewInstanceDefinition information !
		// This MUST be deon *before* deleting the viewInstance.
		// However, the package name can *only* come from the backend.
		ViewInstanceDefinition view = getInstalledApplications().get(
				newapp.instanceName);

		if ( view == null )
			return "Not found.";
		StoreApplication oldapp = getStoreEndpoint()
				.netgetApplicationFromStoreByAppId(app_id,
						view.getViewDefinition().getVersion());
		if (oldapp == null) {
			// The app was not found in the backend.
			response += "Application "
					+ app_id
					+ " with version "
					+ view.getViewDefinition().getVersion()
					+ " was not found in the Store. Cannot delete information on disk.";
			// throw new GenericException( "Application " + app_id +
			// " with version " + view.getViewDefinition().getVersion()
			// +
			// " was not found in the Store. Cannot delete information on disk."
			// );
		} else {

			String workDirectory = oldapp.getPackageWorkdir();
			String packagePath = oldapp.getPackageFilepath();
			try {
				response += "Deleting " + packagePath + " and " + workDirectory
						+ ".<br>";
				FileUtils.deleteDirectory(new File(workDirectory));
				FileUtils.forceDelete(new File(packagePath));

			} catch (IOException e) {
				response += "Something went wrong deleting " + workDirectory
						+ " or " + packagePath + "<br>";
			}
		}

		// We don't need the viewInstance anymore so we delete it now
		response += deleteApplication(app_id);
		/*
		 * TODO
		 * 
		 * WE NEED TO RESTART TWICE Once to clear any remnants of instance data
		 * The other to unpack the view.
		 */
		return response;
	}
	
	public String updateApplication(String app_id) {
		String response = "";

		// This deletes any *existing* viewInstance and removes files
		response += eraseApplication(app_id);
		
		// This installs the new view
		response += installApplication(app_id);
		
		return response;
	
	}	
	
	
	// TODO: Move into mainStoreApplication when feedback can be provided
	// dynamically
	public String installApplication(String app_id) {

		String response =  "Starting downloads.<br>";

		Map<String, StoreApplication> availableApplications = getStoreEndpoint().getAllApplicationsFromStore();

			StoreApplication app = availableApplications.get(app_id);
			// Verify that there is a package to download
			if (app.package_uri != null) {
				// Check whether the file is already present (do not
				// re-download)
				String targetPath = app.getPackageFilepath();
				File file = new File(targetPath);

				if (!file.isFile()) {
					response += "Downloading " + app.package_uri
							+ " to " + targetPath + "<br>";
					// How can we do this in a thread and provide download
					// update ? TODO
					AmbariStoreHelper.downloadFile(app.package_uri, targetPath);
				} else {
					response +=
							"File already available. Not downloading.<br>";
				}
				addPostInstallTask(app.uri);
			}
			return response;
		}


	/*
	 * Obtain all possible endpoints. This is currently dynamic !! (makes
	 * several REST calls every time) NO SUPPORT FOR KNOX CURRENTLY. "poc"
	 * coding - makes changes to the global variable 'amstore' (! WARNING)
	 * Future: get this in a single pass from blueprint ? Need to obtain:
	 * ambari: amstore.ambari.url, amstore.ambari.username,
	 * amstore.ambari.password yarn: amstore.yarn.ats.url, amstore.yarn.rm
	 * webhdfs: amstore.webhdfs.url, amstore.webhdfs.username,
	 * amstore.webhdfs.authentication webhcat: amstore.webhcat.url hiveserver2:
	 * amstore.hiveserver2.host, amstore.hiveserver2.port,
	 * amstore.hiveserver2.authentication TODO: make sure we pull all values
	 * from remote Ambari, eg not hardcode ports.
	 */
	public Map<String, String> netgetUpdatedStoreProperties()
			throws IOException, JSONException {

		Map<String, String> amstore;

		// Keep the backend as configured. WARNING HARDCODED !!
		// TODO "http://amstore.cloud.hortonworks.com:5025"
		amstore = new TreeMap<String, String>(getIntanceProperties());
		// TODO STACK WARNING NEED TO GET VERSION FROM INSTALLED AMBARI OR FROM
		// REMOTE CONFIGURATION
		// amstore.put("amstore.stack.version", "2.1");

		/*
		 * amstore.put("amstore.ambari.local.url", ambari.url);
		 * amstore.put("amstore.ambari.local.username", ambari.username);
		 * amstore.put("amstore.ambari.local.password", ambari.password);
		 * 
		 * AmbariEndpoint remote = ambari; if (!ambari.url.equals(cluster.url))
		 * { ambari = cluster; amstore.put("amstore.ambari.cluster.url",
		 * cluster.url); amstore.put("amstore.ambari.cluster.username",
		 * cluster.username); amstore.put("amstore.ambari.cluster.password",
		 * cluster.password); } else { // TODO There has to be a better way !
		 * amstore.put("amstore.ambari.cluster.url", "");
		 * amstore.put("amstore.ambari.cluster.username", "");
		 * amstore.put("amstore.ambari.cluster.password", ""); }
		 */

		AmbariEndpoint cluster = getAmbariCluster();
		// Validates correct access to the Hadoop cluster's Ambari
		String baseurl = cluster.url + "/api/v1/clusters/"
				+ cluster.getClusterName();

		// WEBHDFS
		try {
			JSONObject hdfs = AmbariStoreHelper.readJsonFromUrl(baseurl
					+ "/services/HDFS/components/NAMENODE", cluster.username,
					cluster.password);
			amstore.put("amstore.webhdfs.url", "webhdfs://"
					+ hdfs.getJSONArray("host_components").getJSONObject(0)
							.getJSONObject("HostRoles").getString("host_name")
					+ ":50070");
			// leave default of blank
			amstore.put("amstore.webhdfs.username", "");
			// leave default of blank
			amstore.put("amstore.webhdfs.auth", "");
		} catch (Exception e) {
		}

		// YARN
		try {
			JSONObject yarnrm = AmbariStoreHelper.readJsonFromUrl(baseurl
					+ "/services/YARN/components/RESOURCEMANAGER",
					cluster.username, cluster.password);
			amstore.put(
					"amstore.yarn.rm.url",
					"http://"
							+ yarnrm.getJSONArray("host_components")
									.getJSONObject(0)
									.getJSONObject("HostRoles")
									.getString("host_name") + ":8088");

			JSONObject yarnats = AmbariStoreHelper.readJsonFromUrl(baseurl
					+ "/services/YARN/components/APP_TIMELINE_SERVER",
					cluster.username, cluster.password);
			amstore.put("amstore.yarn.ats.url", "http://"
					+ yarnats.getJSONArray("host_components").getJSONObject(0)
							.getJSONObject("HostRoles").getString("host_name")
					+ ":8188");
		} catch (Exception e) {
		}

		// WEBHCAT
		try {
			JSONObject webhcat = AmbariStoreHelper.readJsonFromUrl(baseurl
					+ "/services/HIVE/components/WEBHCAT_SERVER",
					cluster.username, cluster.password);
			amstore.put("amstore.webhcat.url", "http://"
					+ webhcat.getJSONArray("host_components").getJSONObject(0)
							.getJSONObject("HostRoles").getString("host_name")
					+ ":50111/templeton");

		} catch (Exception e) {
			// COMPAT: Some older Ambaris had webhcat separate from Hive
			try {
				JSONObject webhcat = AmbariStoreHelper.readJsonFromUrl(baseurl
						+ "/services/WEBHCAT/components/WEBHCAT_SERVER",
						cluster.username, cluster.password);
				amstore.put("amstore.webhcat.url", "http://"
						+ webhcat.getJSONArray("host_components")
								.getJSONObject(0).getJSONObject("HostRoles")
								.getString("host_name") + ":50111/templeton");
			} catch (Exception e2) {

			}
		}

		// HIVESERVER2
		try {
			JSONObject hiveserver2 = AmbariStoreHelper.readJsonFromUrl(baseurl
					+ "/services/HIVE/components/HIVE_SERVER",
					cluster.username, cluster.password);
			amstore.put("amstore.hiveserver2.host",
					hiveserver2.getJSONArray("host_components")
							.getJSONObject(0).getJSONObject("HostRoles")
							.getString("host_name"));
			amstore.put("amstore.hiveserver2.port", "10000");
			// TODO: get proper port and AUTH
			amstore.put("amstore.hiveserver2.auth", "auth=NONE");
		} catch (Exception e) {
		}

		return amstore;

	}

	// Utility function. Makes REST Calls to ambariViews.
	public void reconfigure() throws JSONException, IOException {

		Map<String, String> amstore = this.netgetUpdatedStoreProperties();
		this.setDesiredInstanceProperties(amstore);
		getAmbariViews().updateViewInstance(viewContext, this);
	}

	/*
	 * Serialization is done using a JSON object. We could also use Apahce
	 * Commons: TODO: Alternative using apache commons To Serialize: byte[] data
	 * = SerializationUtils.serialize(yourObject); deserialize: YourObject
	 * yourObject = (YourObject) SerializationUtils.deserialize(byte[] data)
	 */

	public List<String> getPostInstallTasks() {
		String tasks = viewContext.getInstanceData("post-install-tasks");

		List<String> tasklist = new ArrayList<String>();
		if (tasks == null) {
			return tasklist;
		}

		try {
			org.json.simple.parser.JSONParser parser = new org.json.simple.parser.JSONParser();
			org.json.simple.JSONArray array = (org.json.simple.JSONArray) parser
					.parse(tasks);
			for (int i = 0; i < array.size(); i++) {
				String uri = (String) array.get(i);
				tasklist.add(uri);
			}
		} catch (ParseException pe) {
			// not supposed to happen !
			//throw new GenericException(
			//		"Error Parsing JSON in getPostInstallTasks()");
		}

		return tasklist;
	}

	public String doPostInstallTasks() {

		String response = " DO POST INSTALL TASKS <br>";

		List<String> tasks = getPostInstallTasks();
		for (String uri : tasks) {

			response += "got:" + uri;

			StoreApplication application = BackendStoreEndpoint
					.netgetApplicationFromStoreByUri(uri);

			if (application == null) {
				response += "ERROR: application is NULL !";
				continue;
			}

			response += "<br>Received from the backend:" + application.id
					+ "<br>";
			response += application.instanceDisplayName + "<br>";
			response += application.description + "<br>";
			response += application.properties.size() + "<br>";

			/*
			 * Check whether we can install. If the package has not yet been
			 * expanded we must wait for Ambari restart.
			 */
			File workdir = new File(application.getPackageWorkdir());
			if (!workdir.isDirectory()) {
				response += "The application has not yet been unpacked. Requires restart.<br>";
			} else {

				response += "STARTING POST INSTALL TASK FOR : "
						+ application.getPackageWorkdir() + "<br>";

				// Map to current properties
				// TODO extract out of StoreApplication
				application
						.setDesiredInstanceProperties(getMappedProperties(application));

				response += " DEBUG: the main store values are: "
						+ getIntanceProperties().get(
								"amstore.ambari.cluster.url") + ".<br>";

				try {

					Map<String, ViewInstanceDefinition> installedApplications = getInstalledApplications();

					if (installedApplications
							.containsKey(application.instanceName)) {

						ViewDefinition viewDefinition = installedApplications
								.get(application.instanceName)
								.getViewDefinition();
						response += "found potentially matching view: "
								+ viewDefinition.getVersion() + "<br>";

						// We use the instanceName to identify a view, but there is a possibility
						// of a conflict with a user-defined view. We at least check that the
						// viewName also matches.
						if (!viewDefinition.getViewName().equals(
								application.viewName)) {
							// Issue: we have a conflicting application.
							// Something
							// is wrong.
							response += "Red Herring. The InstanceName matches but not the ViewName.<br>";
							/*
							 * TODO throw new GenericException(
							 * "Warning: found an installed view whose instanceName conflicts with the Ambari Store.\n"
							 * + "installed view name = " +
							 * viewDefinition.getViewName() +
							 * "differs from store view name =" +
							 * application.viewName + "\n");
							 */
						}
						response += "updating view: "
								+ application.instanceName + "<br>";
						response += getAmbariViews().updateViewInstance(
								viewContext, application);

					} else {
						response += "creating view: "
								+ application.instanceName + "<br>";
						response += getAmbariViews().createViewInstance(
								viewContext, application);
					}

					// If all goes well, remove task from list.
					response += "Removing tasks from list.<br>";
					response += removePostInstallTask(uri);

					response += "COMPLETED REMOVE POST INSTALL TASKS<br>";
				} catch (ServiceFormattedException e) {
					response += "An EXCEPION OCCURED<br>";
					response += e.toString();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					response += "An EXCEPION OCCURED<br>";
					response += e.toString();
				}
			}
		}

		return response;
	}

	/*
	 * What Follows is to work around bug 500/ Happens in Ambari 1.7 when doing
	 * a putInstanceData() in a servlet. 500 status code received on GET method
	 * for API: /api/v1/clusters//requests?to=end&page_size=10&fields=Requests
	 * Error message: Work already begun on this thread. Looks like you have
	 * called UnitOfWork.begin() twice without a balancing call to end() in
	 * between.
	 */

	// This function is specifically to work around a BUG in
	// context.putInstanceData
	/*
	 * $CURL -H "X-Requested-By: $agent" -X POST
	 * 'http://localhost:8080/api/v1/views/AMBARI-STORE/versions/0.1.0/instances/store/resources/taskmanager/reconfigure'
	 */
	public String addPostInstallTask(String task) {

		/*
		 * TODO: ERROR 500. This completely breaks Ambari.
		 * viewContext.putInstanceData("post-install-tasks", app_id); INSTEAD:
		 */
		String response = "";
		String url = "/api/v1/views/" + this.getViewName() + "/versions/"
				+ this.version + "/instances/" + this.instanceName
				+ "/resources/taskmanager/postinstalltasks";
		
		if ( getPostInstallTasks().contains( task )) {
			return "Already added.";
		}
		
		response += AmbariStoreHelper.doPost(viewContext.getURLStreamProvider(),
				ambariViews, url, task);
		
		// Check that it was added correctly
		if ( ! getPostInstallTasks().contains( task )) {
			throw new RuntimeException("Failed to add post-install-task correctly");
		}
		
		return response;

	}

	public String removePostInstallTask(String uri) {
		// Loop through the list, adding elements that don't match

		// TODO: there must be a better way
		// CANNOT DO IT DIRECTLY BECAUSE THIS CAUSES error 500:

		// We call the TaskManagerService instead
		String url = "";
		String response = "";
		// try {
		url = "/api/v1/views/" + this.viewName + "/versions/" + this.version
				+ "/instances/" + this.instanceName
				+ "/resources/taskmanager/postinstalltasks/"
				+ URLEncoder.encode(uri);
		// } catch (UnsupportedEncodingException e) {
		// response += e.getMessage();
		// }

		response += "Attempting to delete: '" + url + "'.<br>";
		return response
				+ AmbariStoreHelper.doDelete(
						this.viewContext.getURLStreamProvider(),
						this.ambariViews, url);

	}

}
