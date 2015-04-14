package com.hortonworks.amstore.view;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.apache.ambari.view.ViewContext;
import org.apache.ambari.view.ViewDefinition;
import org.apache.ambari.view.ViewInstanceDefinition;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.NotImplementedException;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hortonworks.amstore.view.AmbariEndpoint.ServicesConfiguration;
import com.hortonworks.amstore.view.StoreException.CODE;
import com.hortonworks.amstore.view.utils.ServiceFormattedException;

public class MainStoreApplication extends StoreApplicationView {
	private final static Logger LOG = LoggerFactory
			.getLogger(MainStoreApplication.class);

	protected AmbariEndpoint ambariViews = null;
	protected AmbariEndpoint ambariCluster = null;
	protected BackendStoreEndpoint storeEndpoint = null;

	ViewContext viewContext = null;

	public MainStoreApplication(ViewContext viewContext) {
		super();
		this.viewContext = viewContext;

		// TODO: Use Setters rather than direct variables.
		try {
			Properties properties = new Properties();
			InputStream stream = getClass().getClassLoader()
					.getResourceAsStream("store.properties");
			properties.load(stream);

			app_id = properties.getProperty("mainStoreAppId");
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
	// TODO: unsafe from a security standpoint. Too easy to get passwords.
	public Map<String, String> getMappedProperties(StoreApplication application)
			throws StoreException {
		Map<String, String> mapped = new TreeMap<String, String>();

		// Mappings are mostly the configured properties
		// But we always assign the "cluster" ones even if null.

		// We copy the properties from the mainStoreApplication as a baseline.
		// TODO: security - unsafe
		Map<String, String> amstoreMappings = getIntanceProperties();
		// Map<String, String> mappings = new TreeMap<String, String>();

		// Views Ambari
		amstoreMappings.put("amstore.ambari.views.url", ambariViews.getUrl());
		amstoreMappings.put("amstore.ambari.views.clusterurl",
				ambariViews.getClusterApiEndpoint());
		amstoreMappings.put("amstore.ambari.views.username",
				ambariViews.getUsername());
		amstoreMappings.put("amstore.ambari.views.password",
				ambariViews.getPassword());

		// Cluster Ambari
		amstoreMappings.put("amstore.ambari.cluster.url",
				ambariCluster.getUrl());
		amstoreMappings.put("amstore.ambari.cluster.clusterurl",
				ambariCluster.getClusterApiEndpoint());
		amstoreMappings.put("amstore.ambari.cluster.username",
				ambariCluster.getUsername());
		amstoreMappings.put("amstore.ambari.cluster.password",
				ambariCluster.getPassword());

		/*
		 * Old style: hardcode mappings in store. We no longer do this. for
		 * (Entry<String, String> e : application.getBackendProperties()
		 * .entrySet()) {
		 * 
		 * if (mappings.containsKey(e.getValue())) { mapped.put(e.getKey(),
		 * parseSpecials(mappings.get(e.getValue()))); } else {
		 * mapped.put(e.getKey(), parseSpecials(e.getValue())); } }
		 */

		/*
		 * We map properties based on configured services in Ambari Must use a
		 * ClusterAmbari.
		 */
		try {

			for (Entry<String, String> e : application.getBackendProperties()
					.entrySet()) {

				// if e.getValue() needs replacing
				String field = e.getValue();
				if (field == null) {
					// do nothing
				} else if (field.startsWith("ambari.")) {
					// Calls the store the first time. Keeping it here ensures
					// it only
					// gets called if we really have to. Not efficient.
					ServicesConfiguration servicesConfiguration = getAmbariCluster()
							.getServicesConfiguration();
					String config = field.split("\\.")[1];
					String property = field.split("\\.", 3)[2];
					String replacement = servicesConfiguration.get(config).get(
							property);
					mapped.put(e.getKey(), parseSpecials(replacement));
				} else if (field.startsWith("amstore.")) {
					mapped.put(e.getKey(),
							parseSpecials(amstoreMappings.get(e.getValue())));
				} else {
					mapped.put(e.getKey(), parseSpecials(e.getValue()));
				}

			}
		} catch (IOException e) {
			throw new StoreException(
					"Unable to obtain Ambari services properties.");
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
		ambariViews = AmbariEndpoint.getAmbariLocalEndpoint(viewContext);
	}

	protected void initClusterEndpoint() {
		ambariCluster = AmbariEndpoint.getAmbariClusterEndpoint(viewContext);
	}

	protected ViewContext getViewContext() {
		return viewContext;
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
				.getAmbariClusterEndpoint(viewContext);
		if (!ambariCluster.equals(ambariClusterconfig)) {
			// TODO: This is really sneaky.
			// We are actually resetting clusterName to null.
			ambariCluster = ambariClusterconfig;
		}
		return ambariCluster;
	}

	public AmbariEndpoint getAmbariViews() {
		AmbariEndpoint ambariViewsConfig = AmbariEndpoint
				.getAmbariLocalEndpoint(viewContext);
		if (!ambariViews.equals(ambariViewsConfig)) {
			// TODO: This is really sneaky. -- WARNING
			// We are actually resetting clusterName to null.
			ambariViews = ambariViewsConfig;
		}
		return ambariViews;
	}

	public AmbariEndpoint getAmbariLocal() {
		// For now, ambari views is always local, so return that.
		return getAmbariViews();
	}

	public AmbariEndpoint getAmbariRemote() {
		return AmbariEndpoint.getAmbariRemoteEndpoint(viewContext);
	}

	// Indexed by instanceName
	public Map<String, ViewInstanceDefinition> getInstancedViews() {
		Map<String, ViewInstanceDefinition> installedApplications = new TreeMap<String, ViewInstanceDefinition>();
		Collection<ViewInstanceDefinition> viewDefinitions = getViewContext()
				.getViewInstanceDefinitions();
		for (ViewInstanceDefinition instance : viewDefinitions) {
			installedApplications.put(instance.getInstanceName(), instance);
		}
		return installedApplications;
	}

	// Indexed by canonicalName
	// Note: The returned value is not fully instantiated
	public Map<String, StoreApplicationView> getInstalledViews() {
		Map<String, StoreApplicationView> installedApplications = new TreeMap<String, StoreApplicationView>();

		Collection<ViewInstanceDefinition> viewDefinitions = getViewContext()
				.getViewInstanceDefinitions();
		for (ViewInstanceDefinition instance : viewDefinitions) {
			StoreApplicationView installedBareView = new StoreApplicationFactory()
					.getBareboneStoreApplicationView(instance);
			installedApplications.put(installedBareView.getCanonicalName(),
					installedBareView);
		}
		return installedApplications;
	}

	// TODO WARNING: we are only the service_name NOT version
	// Indexed by canonicalName
	// Note: The returned value is not fully instantiated
	public Map<String, StoreApplicationService> getInstalledServices()
			throws IOException {
		Map<String, StoreApplicationService> installedServices = new TreeMap<String, StoreApplicationService>();

		// TODO: replace with something that also returns VERSION in the future
		Set<String> serviceNames = getAmbariCluster()
				.getListInstalledServiceNames();

		for (String serviceName : serviceNames) {
			StoreApplicationService serviceBare = new StoreApplicationFactory()
					.getBareboneStoreApplicationService(serviceName);

			installedServices.put(serviceBare.getCanonicalName(), serviceBare);
		}
		return installedServices;
	}

	// Indexed by canonicalName
	public Map<String, StoreApplication> getInstalledApplications()
			throws IOException {
		Map<String, StoreApplication> applicationStubs = new TreeMap<String, StoreApplication>();

		// Add Views
		for (Entry<String, StoreApplicationView> e : getInstalledViews()
				.entrySet()) {
			applicationStubs.put(e.getKey(), e.getValue());
		}
		// Add Services
		for (Entry<String, StoreApplicationService> e : getInstalledServices()
				.entrySet()) {
			applicationStubs.put(e.getKey(), e.getValue());
		}
		// Add assemblies
		// TODO

		return applicationStubs;
	}

	// Fully populated
	public StoreApplication getInstalledApplicationByAppId(String appId)
			throws StoreException {
		/*
		 * Get the latest version We can do this because there should always be
		 * at least one version available even if it's a later version than the
		 * one installed.
		 */
		// Efficient
		StoreApplication latestAppAvailable = getStoreEndpoint()
				.getAllApplicationsFromStore().get(appId);

		if (latestAppAvailable == null) {
			throw new StoreException(
					"Application "
							+ appId
							+ " was not found in the Store. If this app is not managed by the store it cannot be deleted via the store.");
		}

		if (latestAppAvailable.isView()) {
			ViewInstanceDefinition instance = getInstalledViewInstanceByCanonicalName(
					latestAppAvailable.getCanonicalName()
					);

			StoreApplicationView storeApplicationView = (StoreApplicationView)
					this
					.getStoreEndpoint()
					.netgetApplicationFromStoreByAppId(appId, instance.getViewDefinition().getVersion());
			
			// if we get here the application was not found.
			if( storeApplicationView == null )
			throw new StoreException(
					"Application "
							+ appId
							+ " with version "
							+ instance.getViewDefinition().getVersion()
							+ " was not found in the Store. Cannot delete information on disk.");
			return storeApplicationView;
		} else if (latestAppAvailable.isService()) {
			// We don't handle versions, so return the latest
			return latestAppAvailable;
		}
		throw new NotImplementedException(
				"Only Views and Services are supported.");
	}

	/*
	public StoreApplication getInstalledApplicationByAppId(String appId)
			throws IOException, StoreException {

		// Efficient
		StoreApplication latestAppAvailable = getStoreEndpoint()
				.getAllApplicationsFromStore().get(appId);

		if (latestAppAvailable == null) {
			throw new StoreException(
					"Application "
							+ appId
							+ " was not found in the Store. If this app is not managed by the store it cannot be deleted via the store.");
		}
		// Get the installed version
		StoreApplication installedApplicationStub = this
				.getInstalledApplicationByCanonicalName(latestAppAvailable
						.getCanonicalName());
		if (installedApplication == null) {

			if (latestAppAvailable.isView()) {

				ViewInstanceDefinition instance = this.getInstancedViews().get(
						latestAppAvailable.getInstanceName());
				throw new StoreException(
						"Application "
								+ appId
								+ " with version "
								+ instance.getViewDefinition().getVersion()
								+ " was not found in the Store. Cannot delete information on disk.");
			}
		}

		return installedApplication;

	}
*/
	public ViewInstanceDefinition getInstalledViewInstanceByCanonicalName(
			String canonicalName) {

		Collection<ViewInstanceDefinition> viewDefinitions = getViewContext()
				.getViewInstanceDefinitions();
		for (ViewInstanceDefinition instance : viewDefinitions) {
			String canonical = "view-" + 
					instance.getViewDefinition().getViewName() +
					"-" +
					instance.getInstanceName();
			if (canonical.equals(canonicalName)) {
				return instance;
			}
		}
		return null;
	}

	// TODO: Move into mainStoreApplication when feedback can be provided
	// dynamically
	public void installApplication(String appId) throws StoreException {
		LOG.debug("Starting downloads.");

		Map<String, StoreApplication> availableApplications = getStoreEndpoint()
				.getAllApplicationsFromStore();
		StoreApplication app = availableApplications.get(appId);
		try {
			app.doInstallStage1(getAmbariLocal());
		} catch (IOException e) {
			LOG.warn("IOException downloading application:" + appId);
		}
		addPostInstallTask(app.uri);
	}

	/*
	 * Removes the associated files of an instance. Requires a restart to
	 * complete. Assert: the view must be instantiated TODO: Refuses to
	 * uninstall the App Store itself. Need a better way.
	 */
	public void uninstallApplication(String appId) throws IOException,
			StoreException {
		StoreApplication installedApplication = this
				.getInstalledApplicationByAppId(appId);
		if (installedApplication == null)
			throw new StoreException("Application " + appId
					+ "must be installed first. Cannot uninstall.", CODE.INFO);

		// Do not delete Store
		if (installedApplication.isStore()) {
			throw new StoreException(
					"Attempted to uninstall store. Not supported.", CODE.INFO);
		}
		installedApplication.doUninstallStage1(this.getAmbariLocal());
	}

	public void updateApplication(String appId) throws IOException,
			StoreException {
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

		/*
		 * if( installedApplication.isView() ) ((StoreApplicationView)
		 * installedApplication).doUpdateStage1( newApplication );
		 * 
		 * if( installedApplication.isService() ) ((StoreApplicationService)
		 * installedApplication).doUpdateStage1( newApplication );
		 */

		installedApplication.doUpdateStage1(newApplication);

		// Line up post update tasks after restart
		addPostUpdateTask(newApplication.uri);
	}

	public void deleteApplication(String appId) throws IOException,
			StoreException {

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
		String baseurl = cluster.getUrl() + "/api/v1/clusters/"
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
		getAmbariViews().updateViewInstance(this);
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

	// StoreExceptions are propagated as the return string
	public List<StoreException> doPostInstallTasks() {
		LOG.debug("Starting postInstallTasks");
		List<StoreException> exceptions = new LinkedList<StoreException>();

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

			if (application.isView()) {

				try {
					// TODO: dangerous. Should be encapsulated.
					application
							.setDesiredInstanceProperties(getMappedProperties(application));

					Map<String, ViewInstanceDefinition> installedViews = getInstancedViews();
					boolean isreinstall;
					if (installedViews.containsKey(application.instanceName)) {
						isreinstall = true;
					} else {
						isreinstall = false;
					}
					application.doInstallStage2(getAmbariViews(), isreinstall);
					// If all goes well, remove task from list.
					LOG.debug("Removing tasks from list.");
					removePostInstallTask(uri);
				} catch (ServiceFormattedException e) {
					exceptions.add(new StoreException(
							"ServiceFormattedException:" + e.toString(),
							CODE.WARNING));
				} catch (IOException e) {
					exceptions.add(new StoreException("IOException:"
							+ e.toString(), CODE.WARNING));
				} catch (StoreException e) {
					LOG.warn("StoreException:" + e.toString());
					exceptions.add(e);
				}
			} else if (application.isService()) {

				try {
					// TODO: remove hardcoded "false"
					application.doInstallStage2(getAmbariCluster(), false);
					removePostInstallTask(uri);
				} catch (ServiceFormattedException e) {
					exceptions.add(new StoreException(
							"ServiceFormattedException:" + e.toString(),
							CODE.WARNING));
				} catch (StoreException e) {
					exceptions.add(e);
				} catch (IOException e) {
					exceptions.add(new StoreException("IOException:"
							+ e.toString(), CODE.WARNING));
				}

			} else {
				throw new NotImplementedException(
						"Installing unknown type not yet implemented.");
			}
		}
		return exceptions;
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
	public List<StoreException> doPostUpdateTasks() throws IOException {

		LOG.debug("Starting postUpdateTasks");
		List<StoreException> exceptions = new LinkedList<StoreException>();
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

				// Map to current properties
				// Exception: main store view would carry over old settings.
				if (newApplication.isStore()) {
					newApplication.setDesiredInstanceProperties(this
							.getProperties());
				} else {
					newApplication
							.setDesiredInstanceProperties(getMappedProperties(newApplication));
				}
				installedApplication.doUpdateStage2(getAmbariViews(),
						newApplication);

				// If all goes well, remove task from list.
				LOG.debug("Removing update task from list.");
				removePostUpdateTask(uri);
				LOG.debug("COMPLETED REMOVE POST INSTALL TASKS");

			} catch (ServiceFormattedException e) {
				LOG.warn("ServiceFormattedException:" + e.toString());
			} catch (StoreException e) {
				LOG.warn("StoreExceptionw:" + e.toString());
				exceptions.add(e);
			}
		}
		return exceptions;
	}
} // end class
