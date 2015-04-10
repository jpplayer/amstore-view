package com.hortonworks.amstore.view;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.apache.ambari.view.ViewContext;
import org.apache.ambari.view.ViewDefinition;
import org.apache.ambari.view.ViewInstanceDefinition;
import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hortonworks.amstore.view.utils.ServiceFormattedException;

public class MainStoreApplication extends StoreApplication {
	private final static Logger LOG = LoggerFactory
			.getLogger(MainStoreApplication.class);

	protected AmbariEndpoint ambariViews = null;
	protected AmbariEndpoint ambariCluster = null;
	protected BackendStoreEndpoint storeEndpoint = null;

	public MainStoreApplication(ViewContext viewContext) {
		super(viewContext);

		// TODO: Use Setters rather than direct variables.
		try {
			Properties properties = new Properties();
			InputStream stream = getClass().getClassLoader()
					.getResourceAsStream("store.properties");
			properties.load(stream);

			viewName = properties.getProperty("viewName");
			version = properties.getProperty("version");
			instanceName = properties.getProperty("instanceName");
			instanceDisplayName = properties.getProperty("instanceDisplayName");
			description = properties.getProperty("description");
		} catch (java.io.IOException e) {
			LOG.debug("IOException in mainStoreApplication constructor.");
		}
		initAmbariEndpoints();
	}

	private String parseSpecials(String in) {
		if (in == null)
			return null;
		String replacement = in.replace("{{username}}", "${username}")
				.replace("{{viewName}}", "${viewName}")
				.replace("{{instanceName}}", "${instanceName}");
		return replacement;
	}

	/*
	 * Configures an application using the configured mappings. We have three
	 * special values: {{username}} maps to ${username} {{viewName}} maps to
	 * ${viewName} {{instanceName}} maps to ${instanceName}
	 */
	public Map<String, String> getMappedProperties(StoreApplication application) {
		Map<String, String> mapped = new TreeMap<String, String>();

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

	/*
	 * This is public so we can re-init externally, on configuration change or
	 * user request
	 */
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

	// Indexed by instanceName
	public Map<String, ViewInstanceDefinition> getInstalledViews() {
		Map<String, ViewInstanceDefinition> installedApplications = new TreeMap<String, ViewInstanceDefinition>();
		Collection<ViewInstanceDefinition> viewDefinitions = getViewContext()
				.getViewInstanceDefinitions();
		for (ViewInstanceDefinition instance : viewDefinitions) {
			installedApplications.put(instance.getInstanceName(), instance);
		}
		return installedApplications;
	}

	public Map<String, StoreApplication> getInstalledApplications() {
		Map<String, StoreApplication> installedApplications = new TreeMap<String, StoreApplication>();

		Collection<ViewInstanceDefinition> viewDefinitions = getViewContext()
				.getViewInstanceDefinitions();
		for (ViewInstanceDefinition instance : viewDefinitions) {
			installedApplications.put(instance.getInstanceName(),
					new StoreApplication(viewContext, instance));
		}
		return installedApplications;
	}

	public StoreApplication getInstalledApplicationByAppId(String appId) {
		// Efficient
		StoreApplication app = getStoreEndpoint().getAllApplicationsFromStore()
				.get(appId);

		Collection<ViewInstanceDefinition> viewDefinitions = getViewContext()
				.getViewInstanceDefinitions();
		for (ViewInstanceDefinition instance : viewDefinitions) {
			if (instance.getInstanceName().equals(app.getInstanceName())) {
				return storeEndpoint.netgetApplicationFromStoreByAppId(appId,
						instance.getViewDefinition().getVersion());
			}
		}
		return null;
	}

	public void deleteApplication(String appId) {

		// Get the installed version first
		StoreApplication installedApplication = this
				.getInstalledApplicationByAppId(appId);

		String url = "";

		if (installedApplication == null) {
			LOG.warn("Delete request for " + appId
					+ " failed: Application not found.");
			return;
		}

		// Note: we must get the information about this exact version, not any
		// new version that might show up on the app store.
		url = "/api/v1/views/" + installedApplication.getViewName()
				+ "/versions/" + installedApplication.getVersion()
				+ "/instances/" + installedApplication.getInstanceName();

		LOG.debug("Attempting to delete: '" + url);
		AmbariStoreHelper.doDelete(this.viewContext.getURLStreamProvider(),
				this.ambariViews, url);
	}

	// TODO: make this more efficient
	/*
	 * Removes the associated files of an instance. Requires a restart to
	 * complete. Assert: the view must be instantiated TODO: Refuses to
	 * uninstall the App Store itself. Need a better way.
	 */
	public void uninstallApplication(String appId) throws IOException {

		// Get the latest version
		StoreApplication latestAppAvailable = getStoreEndpoint()
				.getAllApplicationsFromStore().get(appId);

		if (latestAppAvailable == null) {
			LOG.warn("Application "
					+ appId
					+ " was not found in the Store. If this app is not managed by the store it cannot be deleted via the store.");
			return;
		}
		// Get the installed version
		StoreApplication installedApplication = this
				.getInstalledApplicationByAppId(appId);
		if (installedApplication == null) {
			try {
				ViewInstanceDefinition instance = this.getInstalledViews().get(
						latestAppAvailable.getInstanceName());
				LOG.warn("Application "
						+ appId
						+ " with version "
						+ instance.getViewDefinition().getVersion()
						+ " was not found in the Store. Cannot delete information on disk.");
			} catch (Exception e) {
				LOG.warn("Erroro trying to uninstall application " + appId );
			}
			return;
		}

		// Do not delete Store
		if (installedApplication.isStore()) {
			LOG.warn("Attempted to uninstall store. Not supported.");
			return;
		}
		installedApplication.deleteApplicationFiles();

		// We don't need the viewInstance anymore so we delete it now
		deleteApplication(appId);
		/*
		 * TODO
		 * 
		 * WE NEED TO RESTART TWICE Once to clear any remnants of instance data
		 * The other to unpack the view.
		 */
	}

	public void updateApplication(String appId) throws IOException {
		LOG.debug("Starting downloads for updates.");

		Map<String, StoreApplication> availableApplications = getStoreEndpoint()
				.getAllApplicationsFromStore();
		StoreApplication newApplication = availableApplications.get(appId);

		// Get the installed version
		StoreApplication installedApplication = this
				.getInstalledApplicationByAppId(appId);

		if (installedApplication == null) {
			LOG.warn("updateApplication with AppId " + appId
					+ ". No installed application found.");
			return;
		}

		if (newApplication.getVersion().equals(
				installedApplication.getVersion())) {
			// TODO: turn this into an exception and display at top.
			LOG.warn("Can't update to same version. Use force installation instead.");
			return;
		}

		// Delete old package but not work directory
		// This is important: the old instance must survive a restart
		installedApplication.deletePackageFile();

		// Download the new package
		newApplication.downloadPackageFile();

		// Line up post update tasks after restart
		addPostUpdateTask(newApplication.uri);
	}

	// TODO: Move into mainStoreApplication when feedback can be provided
	// dynamically
	public void installApplication(String appId) {
		LOG.debug("Starting downloads.");

		Map<String, StoreApplication> availableApplications = getStoreEndpoint()
				.getAllApplicationsFromStore();
		StoreApplication app = availableApplications.get(appId);
		app.downloadPackageFile();
		addPostInstallTask(app.uri);
	}

	/*
	 * TODO Obtain all possible endpoints. This is currently dynamic !! (makes
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

		amstore = new TreeMap<String, String>(getIntanceProperties());

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
	 * TODO: leverage a View Resource instead Serialization is done using a JSON
	 * object. We could also use Apache Commons: TODO: Alternative using apache
	 * commons To Serialize: byte[] data =
	 * SerializationUtils.serialize(yourObject); deserialize: YourObject
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
			LOG.warn("ParseException during postInstallTask");
		}
		return tasklist;
	}

	public void doPostInstallTasks() {
		LOG.debug("Starting postInstallTasks");

		List<String> tasks = getPostInstallTasks();
		for (String uri : tasks) {
			LOG.debug("Processing uri: " + uri);

			StoreApplication application = BackendStoreEndpoint
					.netgetApplicationFromStoreByUri(uri);
			if (application == null) {
				LOG.warn("ERROR: application is NULL !");
				continue;
			}

			String response = "Received from the backend:" + application.id
					+ "\n";
			response += application.instanceDisplayName + "\n";
			response += application.description + "\n";
			response += application.properties.size() + "\n";
			LOG.debug(response);

			/*
			 * Check whether we can install. If the package has not yet been
			 * expanded we must wait for Ambari restart.
			 */
			File workdir = new File(application.getPackageWorkdir());
			if (!workdir.isDirectory()) {
				LOG.debug("The application " + application.instanceDisplayName
						+ " has not yet been unpacked. Requires restart.");
			} else {
				LOG.debug("Starting post install task for "
						+ application.getPackageWorkdir() + "\n");
				application
						.setDesiredInstanceProperties(getMappedProperties(application));
				try {
					Map<String, ViewInstanceDefinition> installedViews = getInstalledViews();
					if (installedViews.containsKey(application.instanceName)) {
						ViewDefinition viewDefinition = installedViews.get(
								application.instanceName).getViewDefinition();
						LOG.debug("found potentially matching view: "
								+ viewDefinition.getVersion());

						// We use the instanceName to identify a view, but there
						// is a possibility
						// of a conflict with a user-defined view. We at least
						// check that the
						// viewName also matches.
						if (!viewDefinition.getViewName().equals(
								application.viewName)) {
							// Issue: we have a conflicting application.
							// Something is wrong.
							LOG.warn("Red Herring. The InstanceName matches but not the ViewName.");
							throw new GenericException(
									"Warning: found an installed view whose instanceName conflicts with the Ambari Store.\n"
											+ "installed view name = "
											+ viewDefinition.getViewName()
											+ "differs from store view name ="
											+ application.viewName + "\n");
						}
						LOG.debug("updating view: " + application.instanceName
								+ "\n");
						getAmbariViews().updateViewInstance(viewContext,
								application);
					} else {
						LOG.debug("creating view: " + application.instanceName
								+ "\n");
						getAmbariViews().createViewInstance(viewContext,
								application);
					}

					// If all goes well, remove task from list.
					LOG.debug("Removing tasks from list.");
					removePostInstallTask(uri);
				} catch (ServiceFormattedException e) {
					LOG.warn("ServiceFormattedException:" + e.toString());
				} catch (IOException e) {
					LOG.warn("IOException:" + e.toString());
				}
			}
		}
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
	public void addPostInstallTask(String task) {

		/*
		 * TODO: ERROR 500. This completely breaks Ambari.
		 * viewContext.putInstanceData("post-install-tasks", app_id); INSTEAD:
		 */
		String url = "/api/v1/views/" + this.getViewName() + "/versions/"
				+ this.version + "/instances/" + this.instanceName
				+ "/resources/taskmanager/postinstalltasks";
		if (getPostInstallTasks().contains(task)) {
			LOG.debug("Already added:" + task);
			return;
		}
		AmbariStoreHelper.doPost(viewContext.getURLStreamProvider(),
				ambariViews, url, task);
		// Check that it was added correctly
		if (!getPostInstallTasks().contains(task)) {
			throw new RuntimeException(
					"Failed to add post-install-task correctly");
		}
	}

	// For debugging
	public void cleartasks() {
		List<String> updates = getPostUpdateTasks();
		for (String task : updates) {
			removePostUpdateTask(task);
		}
		List<String> installs = getPostInstallTasks();
		for (String task : installs) {
			removePostInstallTask(task);
		}

	}

	public void removePostInstallTask(String uri) {
		// Loop through the list, adding elements that don't match

		// TODO: there must be a better way
		// CANNOT DO IT DIRECTLY BECAUSE THIS CAUSES error 500:

		// We call the TaskManagerService instead
		String url = "";
		url = "/api/v1/views/" + this.viewName + "/versions/" + this.version
				+ "/instances/" + this.instanceName
				+ "/resources/taskmanager/postinstalltasks/"
				+ URLEncoder.encode(uri);

		LOG.debug("Attempting to delete: '" + url);
		AmbariStoreHelper.doDelete(this.viewContext.getURLStreamProvider(),
				this.ambariViews, url);
	}

	// This function is specifically to work around a BUG in
	// context.putInstanceData
	/*
	 * $CURL -H "X-Requested-By: $agent" -X POST
	 * 'http://localhost:8080/api/v1/views/AMBARI-STORE/versions/0.1.0/instances/store/resources/taskmanager/reconfigure'
	 */
	public void addPostUpdateTask(String task) {
		/*
		 * TODO: ERROR 500. This completely breaks Ambari.
		 * viewContext.putInstanceData("post-install-tasks", app_id); INSTEAD:
		 */
		String url = "/api/v1/views/" + this.getViewName() + "/versions/"
				+ this.version + "/instances/" + this.instanceName
				+ "/resources/taskmanager/postupdatetasks";
		if (getPostUpdateTasks().contains(task)) {
			LOG.debug("Already added:" + task);
			return;
		}
		AmbariStoreHelper.doPost(viewContext.getURLStreamProvider(),
				ambariViews, url, task);
		// Check that it was added correctly
		if (!getPostUpdateTasks().contains(task)) {
			throw new RuntimeException(
					"Failed to add post-install-task correctly");
		}
	}

	public void removePostUpdateTask(String uri) {
		// Loop through the list, adding elements that don't match

		// TODO: there must be a better way
		// CANNOT DO IT DIRECTLY BECAUSE THIS CAUSES error 500:

		// We call the TaskManagerService instead
		String url = "";
		url = "/api/v1/views/" + this.viewName + "/versions/" + this.version
				+ "/instances/" + this.instanceName
				+ "/resources/taskmanager/postupdatetasks/"
				+ URLEncoder.encode(uri);

		LOG.debug("Attempting to delete: '" + url);
		AmbariStoreHelper.doDelete(this.viewContext.getURLStreamProvider(),
				this.ambariViews, url);
	}

	/*
	 * Serialization is done using a JSON object. We could also use Apahce
	 * Commons: TODO: Alternative using apache commons To Serialize: byte[] data
	 * = SerializationUtils.serialize(yourObject); deserialize: YourObject
	 * yourObject = (YourObject) SerializationUtils.deserialize(byte[] data)
	 */

	public List<String> getPostUpdateTasks() {
		String tasks = viewContext.getInstanceData("post-update-tasks");

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
			LOG.warn("ParseException during postUpdateTask");
		}
		return tasklist;
	}

	// Mostly a copy-paste from doPostInstallTasks.
	public void doPostUpdateTasks() throws IOException {

		LOG.debug("Starting postUpdateTasks");
		List<String> tasks = getPostUpdateTasks();
		for (String uri : tasks) {
			LOG.debug("Processing uri: " + uri);
			try {

				StoreApplication newApplication = BackendStoreEndpoint
						.netgetApplicationFromStoreByUri(uri);
				if (newApplication == null) {
					LOG.warn("ERROR: Application " + uri + "Not found.");
					removePostUpdateTask(uri);
					continue;
				}

				// Get the installed version
				StoreApplication installedApplication = this
						.getInstalledApplicationByAppId(newApplication
								.getApp_id());

				String response = "Received from the backend:"
						+ installedApplication.id + "\n";
				response += installedApplication.instanceDisplayName + "\n";
				response += installedApplication.description + "\n";
				response += installedApplication.properties.size() + "\n";
				LOG.debug(response);

				// If its the same version, error and do nothing
				if (newApplication.getVersion().equals(
						installedApplication.getVersion()))
					throw new IllegalArgumentException(
							"Cannot update to the same version:"
									+ installedApplication
											.getInstanceDisplayName());

				/*
				 * Check whether we can install. If the package has not yet been
				 * expanded we must wait for Ambari restart.
				 */
				File workdir = new File(newApplication.getPackageWorkdir());
				if (!workdir.isDirectory()) {
					LOG.debug("The application has not yet been unpacked. Requires restart.");
				} else {
					// instantiate the new view
					LOG.debug("Starting post update task for : "
							+ newApplication.getPackageWorkdir() + "\n");

					// Map to current properties
					// Exception: main store view would carry over old settings.
					if (newApplication.isStore()) {
						newApplication
								.setDesiredInstanceProperties(installedApplication
										.getProperties());
					} else {
						newApplication
								.setDesiredInstanceProperties(getMappedProperties(newApplication));
					}

					LOG.debug("creating view: " + newApplication.instanceName);
					getAmbariViews().createViewInstance(viewContext,
							newApplication);

					// delete any remaining files
					installedApplication.deleteApplicationFiles();

					// delete any old instance
					// Exception: if we are updating the main store, could lead
					// to odd behavior
					deleteApplication(installedApplication.getApp_id());

					// If all goes well, remove task from list.
					LOG.debug("Removing update task from list.");
					removePostUpdateTask(uri);
					LOG.debug("COMPLETED REMOVE POST INSTALL TASKS");
				}
			} catch (ServiceFormattedException e) {
				LOG.warn("ServiceFormattedException:" + e.toString());
			}
		}
	}
}
