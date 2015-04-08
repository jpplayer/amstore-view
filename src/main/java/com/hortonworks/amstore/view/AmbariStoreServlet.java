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

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import static org.apache.commons.lang.StringEscapeUtils.escapeHtml;

/**
 * Servlet for Ambari Store view.
 */
public class AmbariStoreServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	/**
	 * The view context.
	 */
	private ViewContext viewContext;

	private PrintWriter debugWriter = null;

	private boolean endpointIssues = true; // we assume the worst

	// The StoreApplication representing the store instance (ie "this")
	private MainStoreApplication mainStoreApplication = null;

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

		bootstrapjs(response);

		// DELETE THIS
		debugWriter = response.getWriter();

		debugWriter.println("<h2>Ambari Store</h2>");

		try {

			// TODO: remove use of global variable endpointIssues
			if (endpointIssues) {
				displayChecks(request, response);
				// If we still have issues after the checks
				if (endpointIssues)
					return;
			}

			// debugWriter.println(mainStoreApplication.getIntanceProperties().toString());

			if (request.getParameter("app_id") != null) {
				displayApplicationDetails(request.getParameter("app_id"),
						response);
				return;
			}

			/*
			 * if (viewContext.getInstanceData("post-install-tasks") != null)
			 * debugWriter.println("Post Install tasks: " +
			 * viewContext.getInstanceData("post-install-tasks") + "<br>");
			 */
			displayAllApplications(request, response);

		} catch (NullPointerException e) {
			debugWriter.println("NullPointerException caught.<br>");
			debugWriter.println(GenericException.prettyTrace(e));

		} catch (Exception e) {
			debugWriter.println("Catch All Exception: " + e.toString());
		}

	}

	// POST - ROUTING
	@Override
	protected void doPost(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/html");
		response.setStatus(HttpServletResponse.SC_OK);

		bootstrapjs(response);

		PrintWriter writer = response.getWriter();

		try {

			if (endpointIssues) {
				displayChecks(request, response);
				// If we still have issues after the checks
				if (endpointIssues)
					return;
			}

			// TODO DELETE THIS
			debugWriter = response.getWriter();

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
			else if (request.getParameter("reconfigurepage") != null)
				action = "reconfigurepage";
			else if (request.getParameter("check_updates") != null)
				action = "check_updates";
			else if (request.getParameter("post_install") != null)
				action = "post_install";

			// Second form
			if (action.equals("reconfigurepage")) {
				displayPreferences(request, response);

			} else if (action.equals("reconfigure")) {
				reconfigure(request, response);
				displayPreferences(request, response);

			} else if (action.equals("restart_ambari")) {
				restartAmbari(response);
			} else if (action.equals("check_updates")) {
				// Reload applications from back and refresh to GET
				mainStoreApplication.getStoreEndpoint().refresh();
				displayAllApplications(request, response);

			} else if (action.equals("post_install")) {
				mainStoreApplication.doPostInstallTasks();
				writer.println("Complete. Please refresh your browser.");
				// redirectHome(response);
			} else { // First form
				String[] checked = null;
				if (request.getParameter("checked") != null) {

					checked = request.getParameterValues("checked");

					if (action.equals("install")) {
						doInstallation(checked);
						redirectHome(response);
					} else if (action.equals("update")) {
						doUpdate(checked);
						redirectHome(response);
					} else if (action.equals("delete")) {
						doDelete(checked);
						redirectHome(response);
					} else if (action.equals("uninstall")) {
						doUninstall(checked);
						redirectHome(response);
					}

				}

			}

		} catch (NullPointerException e) {
			debugWriter.println("NullPointerException caught.<br>");
			debugWriter.println(GenericException.prettyTrace(e));

		} catch (Exception e) {
			debugWriter.println("Catch All Exception: " + e.getMessage());
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
		writer.println("<tr><td>View Name</td><td>" + app.viewName
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
		List<String> tasks = mainStoreApplication.getPostInstallTasks();
		if (tasks.size() != 0) {
			writer.println("<h3>Installation steps remaining.</h3> After restarting Ambari, click \"Finish Installations\" to complete the installation. The following applications need a restart or finalize:");
			writer.println("<br><table class=table><tr>");
			// writer.println("<br><table BORDER=1 CELLPADDING=3 CELLSPACING=1 RULES=COLS FRAME=VSIDES><tr>");
			for (String uri : tasks) {
				StoreApplication application = BackendStoreEndpoint
						.netgetApplicationFromStoreByUri(uri);
				writer.println("<td>" + application.instanceDisplayName
						+ "</td>");
			}
			writer.println("</tr></table>");
		}

		// TODO: unsafe. Indexed by instanceName
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
		writer.println("<th>Readiness</th>");
		writer.println("<th>Installed</th>");
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
			// && !app.package_uri.equals("null")
			/*
			 * if (app.package_uri != null) writer.println("<a href='" +
			 * app.package_uri + "'>" + app.version + "</a>"); else
			 */
			writer.println(app.version);
			writer.println("</td>");
			writer.println("<td>" + app.description + "</td>");
			writer.println("<td>");

			/*
			 * Doing readiness instead for (String tag : app.tags) {
			 * writer.println(tag + " "); }
			 */

			writer.println(app.readiness);
			writer.println("</td>");

			writer.println("<td>");
			if (installedApplications.containsKey(app.getInstanceName())) {
				writer.println(installedApplications.get(app.getInstanceName()).getVersion());
			}

			// TODO: very inefficient TODO TODO
			// if (mainStoreApplication.getInstalledVersion(app) != null) {
			// writer.println(mainStoreApplication.getInstalledVersion(app));
			// }

			writer.println("</td>");

			writer.println("<td align='center'>");
			// TODO: bad logic. Should be encapsulated in the store type.
			if (!app.app_id.equals("ambari-store")) {
				writer.println("<input type='checkbox' name='checked' value='"
						+ app.app_id + "'>");
			}
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
	}

	// display preferences
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

		writer.println("</table>");

		writer.println("</form>");
	}

	// Reconfigures the Store. Should be called ondeploy() (or is it oncreate()
	// )?
	protected void reconfigure(HttpServletRequest request,
			HttpServletResponse response) throws IOException {
		PrintWriter writer = response.getWriter();

		try {
			mainStoreApplication.reconfigure();

			// DISPLAY IN STATUS BAR
			writer.println("<h2> Successful reconfigure </h2>");

		} catch (Exception e) {
			// DISPLAY IN STATUS BAR
			writer.println("<h2> Reconfiguration failure</h2>");
			writer.println("Exception reconfiguring:" + e.getMessage());
		}

	}

	protected void restartAmbari(HttpServletResponse response)
			throws IOException {
		PrintWriter writer = response.getWriter();

		String output = mainStoreApplication.getAmbariViews().restartAmbari();
		writer.println(output);
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

	// deletes instance
	private void doDelete(String[] app_ids) {
		if (app_ids == null || app_ids.length == 0)
			return;
		debugWriter.println("Starting deletes.<br>");
		String response = "";
		for (String app_id : app_ids) {

			response += mainStoreApplication.deleteApplication(app_id);
		}
		debugWriter.println("Respnse:" + response + "<br>");
	}

	// Like doDelete, deletes instance but also all associated files.
	private void doUninstall(String[] app_ids) {
		if (app_ids == null || app_ids.length == 0)
			return;
		debugWriter.println("Starting Uninstall.<br>");
		String response = "";

		// doDelete( app_ids);

		for (String app_id : app_ids) {
			response += mainStoreApplication.eraseApplication(app_id);
		}

		debugWriter.println("Respnse:" + response + "<br>");
	}

	private void doUpdate(String[] app_ids) {
		if (app_ids == null || app_ids.length == 0)
			return;
		debugWriter.println("Starting updates.<br>");
		String response = "";

		for (String app_id : app_ids) {

			response += mainStoreApplication.updateApplication(app_id);
		}
		debugWriter.println("Respnse:" + response + "<br>");
	}

	private void doInstallation(String[] app_ids) {
		if (app_ids == null || app_ids.length == 0)
			return;
		debugWriter.println("Starting installations.<br>");

		for (String app_id : app_ids) {

			debugWriter
					.println(mainStoreApplication.installApplication(app_id));
		}
		String output = mainStoreApplication.doPostInstallTasks();
		debugWriter.println(output);
	}

	protected void redirectHome(HttpServletResponse response)
			throws IOException {
		response.sendRedirect("");
		// BAD IDEA
		// PrintWriter writer = response.getWriter();
		// writer.println( "<script>location.reload(true)</script>");
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
	 * protected void bootstrap(HttpServletResponse response) throws IOException
	 * { PrintWriter writer = response.getWriter();
	 * writer.println(" <link href=\"css/bootstrap.min.css\" rel=\"stylesheet\">"
	 * );
	 * 
	 * writer.println(
	 * "<script src=\"https://ajax.googleapis.com/ajax/libs/jquery/1.11.2/jquery.min.js\"></script>"
	 * ); writer.println("<script src=\"js/bootstrap.min.js\"></script>"); }
	 */
}
