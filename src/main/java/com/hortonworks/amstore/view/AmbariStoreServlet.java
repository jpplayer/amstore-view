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

import org.apache.ambari.view.ViewContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hortonworks.amstore.view.StoreException.CODE;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import static org.apache.commons.lang.StringEscapeUtils.escapeHtml;

/**
 * Servlet for Ambari Store view.
 */
public class AmbariStoreServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private final static Logger LOG = LoggerFactory
			.getLogger(AmbariStoreServlet.class);

	private ViewContext viewContext;
	private boolean endpointIssues = true; // we assume the worst

	// The StoreApplication representing the store instance (ie "this")
	private MainStoreApplication mainStoreApplication = null;

	private PrintWriter writer = null;
	private List<StoreException> latestExceptions = new LinkedList<StoreException>();

	/*
	 * List of all available Store Applications. Refreshed on load and when user
	 * hits 'Check for Updates' Indexed on app_id
	 */

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);

		ServletContext context = config.getServletContext();
		viewContext = (ViewContext) context
				.getAttribute(ViewContext.CONTEXT_ATTRIBUTE);

		// main Store Instance (this)
		mainStoreApplication = new MainStoreApplication(viewContext);
	}

	// GET
	@Override
	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws IOException {
		response.setContentType("text/html");
		response.setStatus(HttpServletResponse.SC_OK);
		// very important, used throughout
		writer = response.getWriter();

		bootstrapjs(response);
		writer.println("<h2>Ambari Store</h2>");
		displayExceptions(latestExceptions);
		latestExceptions = new LinkedList<StoreException>();
		try {

			// TODO: remove use of global variable endpointIssues
			if (endpointIssues) {
				displayChecks(request, response);
				// If we still have issues after the checks
				if (endpointIssues)
					return;
			}

			if (request.getParameter("app_id") != null) {
				displayApplicationDetails(request.getParameter("app_id"),
						response);
				return;
			}

			displayAllApplications(request, response);

		} catch (NullPointerException e) {
			writer.println("NullPointerException caught.<br>");
			writer.println(GenericException.prettyTrace(e));

		} catch (Exception e) {
			writer.println("Catch All Exception: " + e.toString());
		}

	}

	// POST - ROUTING
	@Override
	protected void doPost(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/html");
		response.setStatus(HttpServletResponse.SC_OK);
		// very important, used throughout
		writer = response.getWriter();

		// Duplicates Get()
		bootstrapjs(response);
		writer.println("<h2>Ambari Store</h2>");
		displayExceptions(latestExceptions);
		latestExceptions = new LinkedList<StoreException>();
		try {

			if (endpointIssues) {
				displayChecks(request, response);
				// If we still have issues after the checks
				if (endpointIssues)
					return;
			}

			String action = null;

			if (request.getParameter("install") != null)
				action = "install";
			else if (request.getParameter("update") != null)
				action = "update";
			else if (request.getParameter("delete") != null)
				action = "delete";
			else if (request.getParameter("uninstall") != null)
				action = "uninstall";
			else if (request.getParameter("restart_ambari") != null)
				action = "restart_ambari";
			else if (request.getParameter("reconfigure") != null)
				action = "reconfigure";
			else if (request.getParameter("cleartasks") != null)
				action = "cleartasks";
			else if (request.getParameter("reconfigurepage") != null)
				action = "reconfigurepage";
			else if (request.getParameter("check_updates") != null)
				action = "check_updates";
			else if (request.getParameter("post_install") != null)
				action = "post_install";
			else if (request.getParameter("home") != null)
				action = "home";

			if (action.equals("reconfigurepage")) {
				displayPreferences(request, response);
			} else if (action.equals("reconfigure")) {
				reconfigure(request, response);
				displayPreferences(request, response);
			} else if (action.equals("restart_ambari")) {
				restartAmbari(response);
			} else if (action.equals("home")) {
				redirectHome(response);
			} else if (action.equals("cleartasks")) {
				mainStoreApplication.cleartasks();
				redirectHome(response);
			} else if (action.equals("check_updates")) {
				mainStoreApplication.getStoreEndpoint().refresh();
				displayAllApplications(request, response);
			} else if (action.equals("post_install")) {
				if (mainStoreApplication.getPostUpdateTasks().size()
						+ mainStoreApplication.getPostInstallTasks().size() == 0)
					redirectHome(response);
				List<StoreException> updateExceptions = new LinkedList<StoreException>();
				List<StoreException> installExceptions = new LinkedList<StoreException>();
				;
				try {
					updateExceptions = mainStoreApplication.doPostUpdateTasks();
					installExceptions = mainStoreApplication
							.doPostInstallTasks();
					writer.println("Please refresh your browser.");
				} catch (GenericException e) {
					writer.println("Could not proceed. Make sure you have restarted Ambari");
				}
				if (mainStoreApplication.getPostUpdateTasks().size()
						+ mainStoreApplication.getPostInstallTasks().size() != 0)
					writer.println("Not all tasks completed. Make sure you have restarted Ambari");
				displayExceptions(updateExceptions);
				displayExceptions(installExceptions);
			} else { // Process checked apps
				String[] checked = null;
				if (request.getParameter("checked") != null) {

					checked = request.getParameterValues("checked");

					if (action.equals("install")) {
						List<StoreException> exceptions = doInstallation(checked);
						latestExceptions.addAll(exceptions);
					} else if (action.equals("update")) {
						List<StoreException> exceptions = doUpdate(checked);
						latestExceptions.addAll(exceptions);
					} else if (action.equals("delete")) {
						List<StoreException> exceptions = doDelete(checked);
						latestExceptions.addAll(exceptions);
					} else if (action.equals("uninstall")) {
						List<StoreException> exceptions = doUninstall(checked);
						latestExceptions.addAll(exceptions);
					}
				}
				redirectHome(response);
			}

		} catch (GenericException e) {
			writer.println("Warning: " + e.getMessage() + "<br>");
			displayAllApplications(request, response);
		} catch (NullPointerException e) {
			writer.println("NullPointerException caught.<br>");
			writer.println(GenericException.prettyTrace(e));
		} catch (Exception e) {
			writer.println("Catch All Exception: " + e.getMessage());
		}
	}

	protected void displayApplicationDetails(String app_id,
			HttpServletResponse response) throws IOException {

		PrintWriter writer = response.getWriter();

		// TODO: simplify the next 2 statements
		Map<String, StoreApplication> availableApplications = mainStoreApplication
				.getStoreEndpoint().getAllApplicationsFromStore();
		StoreApplication app = BackendStoreEndpoint
				.netgetApplicationFromStoreByUri(availableApplications
						.get(app_id).uri);

		writer.println("<table class=table>");
		writer.println("<tr><td>Display Name</td><td><b>"
				+ app.instanceDisplayName + "</b></td></tr>");
		writer.println("<tr></tr>");
		writer.println("<tr><td>app_id</td><td>" + app.app_id + "</td></tr>");
		writer.println("<tr><td>Name</td><td>" + app.getCanonicalName()
				+ "</td></tr>");
		writer.println("<tr><td>Version</td><td>" + app.version + "</td></tr>");
		writer.println("<tr><td>Instance Name</td><td>" + app.instanceName
				+ "</td></tr>");
		writer.println("<tr><td>Download link</td><td>" + app.getPackage_uri()
				+ "</td></tr>");
		writer.println("<tr><td>Readiness</td><td>" + app.readiness
				+ "</td></tr>");
		writer.println("<tr><td>Contributed by</td><td>"
				+ escapeHtml(app.getContributor()) + "</td></tr>");
		writer.println("<tr><td>Packaged by</td><td>"
				+ escapeHtml(app.getPackager()) + "</td></tr>");
		writer.println("<tr><td>Description</td><td>" + app.description
				+ "</td></tr>");
		writer.println("<tr><td>Homepage</td><td><a target='_blank' href='"
				+ app.homepage + "'>" + app.homepage + "</a></td></tr>");
		writer.println("<tr><td>Properties</td></tr>");
		writer.println("<tr></tr>");

		Map<String, String> mapped = mainStoreApplication
				.getMappedProperties(app);
		for (Entry<String, String> e : app.getBackendProperties().entrySet()) {
			writer.println("<tr><td>" + e.getKey() + "</td><td>" + e.getValue()
					+ "</td><td>" + mapped.get(e.getKey()) + "</td></tr>");
		}

		writer.println("</table>");

	}

	// list all amstore applications
	private void displayAllApplications(HttpServletRequest request,
			HttpServletResponse response) throws IOException {

		PrintWriter writer = response.getWriter();
		// Post Install Tasks
		List<String> updateTasks = mainStoreApplication.getPostUpdateTasks();
		List<String> installTasks = mainStoreApplication.getPostInstallTasks();

		Set<String> allTasks = new HashSet<String>(updateTasks);
		allTasks.addAll(installTasks);

		if (allTasks.size() != 0) {
			writer.println("<h3>Update steps remaining.</h3> After restarting Ambari, click \"Finish Installations\" to complete the installation. The following applications need a restart or finalize:");
			writer.println("<br><table class=table><tr>");
			for (String uri : allTasks) {
				StoreApplication application = BackendStoreEndpoint
						.netgetApplicationFromStoreByUri(uri);
				writer.println("<td>" + application.instanceDisplayName
						+ "</td>");
			}
			writer.println("</tr></table>");
		}

		// TODO: unsafe. Indexed by canonicalName
		Map<String, StoreApplication> installedApplications = mainStoreApplication
				.getInstalledApplications();

		writer.println("<form name=\"input\" method=\"POST\">");

		writer.println("<input type=\"submit\" value=\"Check for updates\" name=\"check_updates\"/>");
		writer.println("<input type=\"submit\" value=\"Restart Ambari\" name=\"restart_ambari\"/>");
		writer.println("<input type=\"submit\" value=\"Finish installations\" name=\"post_install\"/>");
		writer.println("<p></p>");

		writer.println("<div class=\"panel panel-success\">\n"
				+ "  <!-- Default panel contents -->\n"
				+ "  <div class=\"panel-heading\">All Applications</div>\n"
				+ "  <div class=\"panel-body\">\n"
				+ "    <p> Please select the applications you want to install. </p>\n"
				+ "  </div>");

		writer.println("<table class=table>");
		writer.println("<tr>");
		writer.println("<th>Category</th>");
		writer.println("<th>Name</th>");
		writer.println("<th>Version</th>");
		writer.println("<th>Description</th>");
		writer.println("<th>Type</th>");
		writer.println("<th>Readiness</th>");
		writer.println("<th>Installed</th>");
		writer.println("<th></th>");
		writer.println("<th>Select</th>");
		writer.println("</tr>");

		Map<String, StoreApplication> availableApplications = mainStoreApplication
				.getStoreEndpoint().getAllApplicationsFromStore();
		for (Map.Entry<String, StoreApplication> e : availableApplications
				.entrySet()) {
			StoreApplication app = e.getValue();
			writer.println("<tr>");
			writer.println("<td>" + app.category + "</td>");

			writer.println("<td><a href='?app_id=" + app.app_id + "'>"
					+ app.instanceDisplayName + "</a></td>");

			writer.println("<td>");
			writer.println(app.version);
			writer.println("</td>");
			writer.println("<td>" + app.description + "</td>");
			writer.println("<td>" + app.getType() + "</td>");
			writer.println("<td>");
			writer.println(app.readiness);
			writer.println("</td>");

			writer.println("<td>");
			if (installedApplications.containsKey(app.getCanonicalName())) {
				writer.println(installedApplications
						.get(app.getCanonicalName()).getVersion());
			}
			writer.println("</td>");

			writer.println("<td>");
			if (installedApplications.containsKey(app.getCanonicalName())
					&& !installedApplications.get(app.getCanonicalName())
							.getVersion().equals(app.getVersion())) {
				writer.println("update");
			}
			writer.println("</td>");

			writer.println("<td align='center'>");
			writer.println("<input type='checkbox' name='checked' value='"
					+ app.app_id + "'>");
			writer.println("</td>");
			writer.println("</tr>");
		}

		writer.println("</table>");
		writer.println("</div>");
		writer.println("<br/>");
		writer.println("<input type=\"submit\" value=\"Install Selected\" name=\"install\"/>");
		writer.println("<input type=\"submit\" value=\"Update Selected\" name=\"update\"/>");
		writer.println("<input type=\"submit\" value=\"Delete Selected\" name=\"delete\"/>");
		writer.println("<input type=\"submit\" value=\"Uninstall Selected\" name=\"uninstall\"/>");
		writer.println("<input type=\"submit\" value=\"Reconfigure Store\" name=\"reconfigurepage\">");
		writer.println("</form>");

		// writer.println("<br>");
		// displayInstalledApplicationInformation(response);
	}

	private void displayPreferences(HttpServletRequest request,
			HttpServletResponse response) throws IOException {

		Map<String, String> data = new TreeMap<String, String>(
				viewContext.getProperties());

		PrintWriter writer = response.getWriter();

		writer.println("<form name=\"preferences\" method=\"POST\">");
		writer.println("<table><tr><td>");
		writer.println("<h1>Current Preferences</h1>");
		writer.println("<table border=\"1\" style=\"width:300px\">");
		writer.println("<tr>");
		writer.println("<td>Property</td>");
		writer.println("<td>Value</td>");
		writer.println("</tr>");
		for (Map.Entry<String, String> entry : data.entrySet()) {
			writer.println("<tr>");
			writer.println("<td>" + entry.getKey() + "</td>");
			writer.println("<td>" + entry.getValue() + "</td>");
			writer.println("</tr>");
		}
		writer.println("</table>");
		writer.println("</td><td valign='top'>");

		Map<String, String> amstore = new LinkedHashMap<String, String>();
		try {
			amstore = mainStoreApplication.netgetUpdatedStoreProperties();
		} catch (Exception e) {
			writer.println("Exception getting settings:" + e.getMessage());
		}

		writer.println("<h1>Proposed Configuration</h1>");

		writer.println("<table border=\"1\" style=\"width:300px\">");
		writer.println("<tr>");
		writer.println("<td>Property</td>");
		writer.println("<td>Value</td>");
		writer.println("</tr>");
		// Get Inferred values
		for (Map.Entry<String, String> entry : amstore.entrySet()) {
			writer.println("<tr>");
			writer.println("<td>" + entry.getKey() + "</td>");
			writer.println("<td>" + entry.getValue() + "</td>");
			writer.println("</tr>");
		}
		writer.println("</table>");

		writer.println("<tr><td colspan=2 align=center>");
		writer.println("<input type=\"submit\" value=\"Reconfigure\" name=\"reconfigure\">");
		writer.println("</td></tr>");

		writer.println("<tr><td colspan=2 align=center>");
		writer.println("<input disabled type=\"submit\" value=\"Apply to all Installed Applications\" name=\"reconfigureapps\">");
		writer.println("</td></tr>");

		writer.println("<tr><td colspan=2 align=center>");
		writer.println("<input type=\"submit\" value=\"Clear tasks\" name=\"cleartasks\">");
		writer.println("</td></tr>");

		writer.println("<tr><td colspan=2 align=center>");
		writer.println("<input type=\"submit\" value=\"Return home\" name=\"home\">");
		writer.println("</td></tr>");

		writer.println("</table>");

		writer.println("</form>");
	}

	/*
	 * Reconfigure the Ambari Store Properties, based on configured Ambari
	 * Server Endpoints
	 */
	protected void reconfigure(HttpServletRequest request,
			HttpServletResponse response) throws IOException {
		PrintWriter writer = response.getWriter();

		try {
			mainStoreApplication.reconfigure();
			writer.println("<h2> Successful reconfigure </h2>");
		} catch (Exception e) {
			writer.println("<h2> Reconfiguration failure</h2>");
			writer.println("Exception reconfiguring:" + e.getMessage());
		}
	}

	protected void restartAmbari(HttpServletResponse response)
			throws IOException {
		PrintWriter writer = response.getWriter();

		String output = mainStoreApplication.getAmbariViews().restartAmbari();
		writer.println(output);
		writer.println(waitForAmbariHtml());
	}

	// We get called if endpointChecks == false
	private void displayChecks(HttpServletRequest request,
			HttpServletResponse response) throws IOException {

		PrintWriter writer = response.getWriter();
		boolean issue = false;

		if (!mainStoreApplication.getStoreEndpoint().isAvailable()) {
			displayStoreEndpointHelp(request, response);
			issue = true;
		}

		if (!mainStoreApplication.getAmbariViews().isAvailable()) {
			writer.println("<h1>Error connecting to local Ambari</h1>");
			writer.println("Issue connecting to Ambari Views Server. Please verify connectivity to "
					+ mainStoreApplication.getAmbariViews().getUrl() + "<br>");
			issue = true;
		}

		if (mainStoreApplication.getAmbariRemote() != null
				&& !mainStoreApplication.getAmbariRemote().isAvailable()) {
			writer.println("<h1>Error connecting to remote Ambari</h1>");
			writer.println("Issue connecting to Ambari Remote Server. Please verify connectivity to "
					+ mainStoreApplication.getAmbariRemote().getUrl() + "<br>");
			issue = true;
		}

		if (mainStoreApplication.getAmbariCluster().isAvailable()
				&& !mainStoreApplication.getAmbariCluster().isCluster()) {
			writer.println("<h1>No Hadoop Cluster found.</h1>");
			writer.println("You do not have any configured Ambari Cluster Servers. If your local Ambari is a Views only server, please specify an additional Ambari in the store configuration. <br>");
			writer.println(mainStoreApplication.getAmbariCluster().getUrl()
					+ " is not managing a cluster.");
			issue = true;
		}

		if (!issue)
			endpointIssues = false;
	}

	// What to do if endpoint not available.
	private void displayStoreEndpointHelp(HttpServletRequest request,
			HttpServletResponse response) throws IOException {

		PrintWriter writer = response.getWriter();
		writer.println("<h1>Backend Store is not available</h1>");
		writer.println("Issue connecting to backend store. Please verify connectivity to "
				+ mainStoreApplication.getStoreEndpoint().getUrl() + "<br>");
		writer.println("Hints:<br><ul>");
		writer.println("<li>You must be on VPN to access the Store.</li>");
		writer.println("<li>A sandbox VM running on a laptop will need a \"Host-Only adapter\" in addition to the NAT network, .</li>");
		writer.println("</ul><br>");
		writer.println("For more information see <a href='http://wiki.hortonworks.com'>http://wiki.hortonworks.com</a>");
	}

	// For debug
	private void displayInstalledApplicationInformation(
			HttpServletResponse response) throws IOException {
		PrintWriter writer = response.getWriter();
		Map<String, StoreApplication> installedApplications = mainStoreApplication
				.getInstalledApplications();
		writer.println("<table>");
		for (Entry<String, StoreApplication> app : installedApplications
				.entrySet()) {
			writer.println("<tr>");
			writer.println("<td>" + app.getKey() + "</td>");
			if (app.getValue().isView())
				writer.println("<td>" + app.getValue().getViewName() + "</td>");
			else
				writer.println("<td>"
						+ ((StoreApplicationService) app.getValue())
								.getServiceName() + "</td>");

			writer.println("<td>" + app.getValue().getInstanceName() + "</td>");
			writer.println("</tr>");
		}
		writer.println("</table>");
	}

	private void displayExceptions(List<StoreException> exceptions) {
			for (StoreException e : exceptions) {
				// Only display errors or warnings
				if (e.getCode() == CODE.ERROR || e.getCode() == CODE.WARNING ) {
					writer.println("<br><pre>" + "Exception:\n" + e.getMessage() + "</pre>");
				}
			}
	}

	private List<StoreException> doInstallation(String[] app_ids) {
		List<StoreException> exceptions = new LinkedList<StoreException>();
		if (app_ids == null || app_ids.length == 0)
			return exceptions;
		LOG.debug("Starting Installations.\n");

		for (String app_id : app_ids) {
			try {
				mainStoreApplication.installApplication(app_id);
			} catch (StoreException e) {
				exceptions.add(e);
			}
		}
		List<StoreException> postInstallExceptions = mainStoreApplication
				.doPostInstallTasks();
		exceptions.addAll(postInstallExceptions);
		return exceptions;
	}

	private List<StoreException> doUpdate(String[] app_ids) {
		List<StoreException> exceptions = new LinkedList<StoreException>();
		if (app_ids == null || app_ids.length == 0)
			return exceptions;
		LOG.debug("Starting Updates.\n");

		for (String app_id : app_ids) {
			try {
				mainStoreApplication.updateApplication(app_id);
			} catch (IOException e) {
				// TODO: we ignore any issues
			} catch (StoreException e) {
				exceptions.add(e);
			}
		}
		return exceptions;
	}

	// deletes instance
	// TODO: we ignore all exceptions
	private List<StoreException> doDelete(String[] app_ids) {
		List<StoreException> exceptions = new LinkedList<StoreException>();
		if (app_ids == null || app_ids.length == 0)
			return exceptions;
		LOG.debug("Starting deletions.\n");
		for (String app_id : app_ids) {
			// try {
			mainStoreApplication.deleteApplication(app_id);
			// } catch(StoreException e){
			// exceptions.add(e);
			// }
		}
		return exceptions;
	}

	// Like doDelete, deletes instance but also all associated files.
	// TODO: add exceptions
	private List<StoreException> doUninstall(String[] app_ids) {
		List<StoreException> exceptions = new LinkedList<StoreException>();
		if (app_ids == null || app_ids.length == 0)
			return exceptions;
		LOG.debug("Starting Uninstall.\n");
		for (String app_id : app_ids) {
			try {
				mainStoreApplication.uninstallApplication(app_id);
			} catch (IOException e) {
				// TODO: we ignore any issues
				// } catch (StoreException e){

			}
		}
		return exceptions;
	}

	protected void redirectHome(HttpServletResponse response)
			throws IOException {
		response.sendRedirect("");
	}

	protected void bootstrapjs(HttpServletResponse response) throws IOException {
		PrintWriter writer = response.getWriter();
		writer.println("		<!-- Latest compiled and minified CSS -->\n"
				+ "	<head>	<link rel=\"stylesheet\" href=\"https://maxcdn.bootstrapcdn.com/bootstrap/3.3.4/css/bootstrap.min.css\">\n"
				+ "\n"
				+ "		<!-- Optional theme -->\n"
				+ "		<link rel=\"stylesheet\" href=\"https://maxcdn.bootstrapcdn.com/bootstrap/3.3.4/css/bootstrap-theme.min.css\">\n"
				+ "\n"
				+ "		<!-- Latest compiled and minified JavaScript -->\n"
				+ "		<script src=\"https://maxcdn.bootstrapcdn.com/bootstrap/3.3.4/js/bootstrap.min.js\"></script>\n"
				+ "</head>");
	}

	/*
	 * Outputs Javascript that waits for Ambari to restart. Will be part of
	 * proper template once we move to Ember.
	 */
	protected String waitForAmbariHtml() {
		String html = "<div id='display'></div>"
				+ "<script src=\"http://code.jquery.com/jquery-1.11.1.min.js\"></script>"
				+ "<script>\n"
				+ "    function doPoll(){\n"
				+ "       $.ajax({\n"
				+ "          url: '/api/v1/',\n"
				+ "success: function(result){\n"
				+ " $('#display').html('Ambari ready. Please refresh your browser');"
				+ "         alert('Ambari is ready. Please refresh your browser')\n"
				+ "          },     \n"
				+ "          error: function(result){\n"
				+ "          if( result.status == 403 ) { $('#display').html('<font color=red>Ambari ready. Please refresh your browser</font>'); }    "
				+ "              //alert('timeout/error');\n"
				+ "          else {$('#display').html('Ambari is restarting. Please wait ...');"
				+ "			  setTimeout(doPoll,1000);}\n" + "          }\n"
				+ "       });\n" + "    }\n" + "   setTimeout(doPoll,5000);"
				+ "</script>\n" + "";
		return html;
	}

}
