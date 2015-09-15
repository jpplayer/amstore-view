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
import org.json.simple.JSONArray;
import org.json.simple.JSONValue;

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import java.io.IOException;
import java.net.URLDecoder;

public class TaskManagerService {

	/**
	 * The view context.
	 */
	@Inject
	ViewContext viewContext;

	public TaskManagerService() {

	}

	@GET
	@Path("/application")
	@Produces({ "text/html" })
	public Response getApplicationDetails(@Context HttpHeaders headers,
			@Context UriInfo ui) throws IOException {

		String entity = "ok";
		return Response.ok(entity).type("text/html").build();
	}

	/**
	 * Handles: POST /taskmanager/reconfigure Reconfigures the main Store view
	 * instance.
	 * 
	 * @param headers
	 *            http headers
	 * @param ui
	 *            uri info
	 *
	 * @return the response
	 */
	@POST
	@Path("/reconfigure")
	@Produces({ "text/json" })
	public Response reconfigure(String body, @Context HttpHeaders headers,
			@Context UriInfo ui) throws IOException {

		MainStoreApplication mainStoreApplication = new MainStoreApplication(
				viewContext);

		try {
			mainStoreApplication.reconfigure();

		} catch (Exception e) {
			// TODO Ignore everything, maybe log or flag something ?
		}
		String entity = "{ \"status\": \"ok\" }";
		return Response.ok(entity).type("text/json").build();
	}

	
	@GET
	@Path("/postinstalltasks")
	@Produces({ "text/plain" })
	public Response getInstallTasks(String body, @Context HttpHeaders headers,
			@Context UriInfo ui) throws IOException {
		
		String tasks = viewContext.getInstanceData("post-install-tasks");
		return Response.ok(tasks).type("text/plain").build();
	}
	
	/**
	 * Handles: POST /taskmanager/postinstalltasks Add a task to the list of
	 * tasks This is done because of BUG: a call to viewContext.putInstanceData
	 * inside the servlet returns ERROR 500 after a while.
	 * 
	 * @param headers
	 *            http headers
	 * @param ui
	 *            uri info
	 *
	 * @return the response
	 */
	@POST
	@Path("/postinstalltasks")
	@Produces({ "text/html" })
	public Response addPostInstallTasks(String body,
			@Context HttpHeaders headers, @Context UriInfo ui)
			throws IOException {

		String current = viewContext.getInstanceData("post-install-tasks");
		if (current == null)
			current = "[]";

		JSONArray array = (JSONArray) JSONValue.parse(current);
		array.add(body);

		viewContext.putInstanceData("post-install-tasks", array.toString());

		String output = "Added task:" + body;
		return Response.ok(output).type("text/html").build();
	}

	/**
	 * Handles: DELETE /taskmanager/postinstalltasks Removes a task from the
	 * list of tasks This is done because of BUG: a call to
	 * viewContext.putInstanceData inside the servlet returns ERROR 500 after a
	 * while.
	 * 
	 * @param headers
	 *            http headers
	 * @param ui
	 *            uri info
	 *
	 * @return the response
	 */
	@DELETE
	@Path("/postinstalltasks/{uri}")
	@Produces({ "text/html" })
	public Response delPostInstallTasks(@PathParam("uri") String uri,
			@Context HttpHeaders headers, @Context UriInfo ui)
			throws IOException {

		String response = "";
		try {

			String current = viewContext.getInstanceData("post-install-tasks");
			if (current == null)
				current = "[]";

			JSONArray array = (JSONArray) JSONValue.parse(current);
			array.remove(URLDecoder.decode(uri));

			viewContext.putInstanceData("post-install-tasks", array.toString());

			/*
			 * } catch( ParseException pe){ System.out.println("position: " +
			 * pe.getPosition()); System.out.println(pe); }
			 */
		} catch (Exception e) {
			response += "Failed to remove: current=" + uri + "<br>";
			response += "Error in service: " + e.toString();
			return Response.ok(response).type("text/html").build();
		}

		String entity = "{ \"status\": \"ok\" }";
		return Response.ok(entity).type("text/html").build();
	}

	/**
	 * Handles: POST /taskmanager/postinstalltasks/execute
	 * 
	 * Executes all the post installation tasks, after an Ambari Reboot
	 * 
	 * @param headers
	 *            http headers
	 * @param ui
	 *            uri info
	 *
	 * @return the response
	 */
	@POST
	@Path("/postinstalltasks/execute")
	@Produces({ "text/html" })
	public Response executePostInstallTasks(String body,
			@Context HttpHeaders headers, @Context UriInfo ui)
			throws IOException {

		String response = "{ \"status\": \"ok\" }";

		MainStoreApplication mainStoreApplication = new MainStoreApplication(
				viewContext);
		mainStoreApplication.doPostInstallTasks();

		return Response.ok(response).type("text/html").build();
	}

	// ============= UPDATES
	
	
	/**
	 * Handles: POST /taskmanager/postupdatetasks Add a task to the list of
	 * tasks This is done because of BUG: a call to viewContext.putInstanceData
	 * inside the servlet returns ERROR 500 after a while.
	 * 
	 * @param headers
	 *            http headers
	 * @param ui
	 *            uri info
	 *
	 * @return the response
	 */
	@POST
	@Path("/postupdatetasks")
	@Produces({ "text/html" })
	public Response addPostUpdateTasks(String body,
			@Context HttpHeaders headers, @Context UriInfo ui)
			throws IOException {

		String current = viewContext.getInstanceData("post-update-tasks");
		if (current == null)
			current = "[]";

		JSONArray array = (JSONArray) JSONValue.parse(current);
		array.add(body);

		viewContext.putInstanceData("post-update-tasks", array.toString());

		String output = "Added task:" + body;
		return Response.ok(output).type("text/html").build();
	}


	/**
	 * Handles: DELETE /taskmanager/postupdatetasks Removes a task from the
	 * list of tasks This is done because of BUG: a call to
	 * viewContext.putInstanceData inside the servlet returns ERROR 500 after a
	 * while.
	 * 
	 * @param headers
	 *            http headers
	 * @param ui
	 *            uri info
	 *
	 * @return the response
	 */
	@DELETE
	@Path("/postupdatetasks/{uri}")
	@Produces({ "text/html" })
	public Response delPostUpdateTasks(@PathParam("uri") String uri,
			@Context HttpHeaders headers, @Context UriInfo ui)
			throws IOException {

		String response = "";
		try {

			String current = viewContext.getInstanceData("post-update-tasks");
			if (current == null)
				current = "[]";

			JSONArray array = (JSONArray) JSONValue.parse(current);
			array.remove(URLDecoder.decode(uri));

			viewContext.putInstanceData("post-update-tasks", array.toString());

			/*
			 * } catch( ParseException pe){ System.out.println("position: " +
			 * pe.getPosition()); System.out.println(pe); }
			 */
		} catch (Exception e) {
			response += "Failed to remove: current=" + uri + "<br>";
			response += "Error in service: " + e.toString();
			return Response.ok(response).type("text/html").build();
		}

		String entity = "{ \"status\": \"ok\" }";
		return Response.ok(entity).type("text/html").build();
	}

	/**
	 * Handles: POST /taskmanager/postupdatetasks/execute
	 * 
	 * Executes all the post installation tasks, after an Ambari Reboot
	 * 
	 * @param headers
	 *            http headers
	 * @param ui
	 *            uri info
	 *
	 * @return the response
	 */
	@POST
	@Path("/postupdatetasks/execute")
	@Produces({ "text/html" })
	public Response executePostUpdateTasks(String body,
			@Context HttpHeaders headers, @Context UriInfo ui)
			throws IOException {

		String response = "{ \"status\": \"ok\" }";

		MainStoreApplication mainStoreApplication = new MainStoreApplication(
				viewContext);
		mainStoreApplication.doPostUpdateTasks();

		return Response.ok(response).type("text/html").build();
	}
	
}