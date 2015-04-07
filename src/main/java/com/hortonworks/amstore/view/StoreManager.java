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

import javax.inject.Inject;

import org.apache.ambari.view.View;
import org.apache.ambari.view.ViewDefinition;
import org.apache.ambari.view.ViewInstanceDefinition;

public class StoreManager implements View {

	/**
	 * The view context.
	 */
	@Inject
	ViewContext viewContext;

	public void onDeploy(ViewDefinition definition) {
	}

	public void onCreate(ViewInstanceDefinition definition) {

		// Do post-install-tasks
		
		
		/*
		 * IT DOES NOT APPEAR THAT THERE IS A VIEW CONTEXT ? Note: Only needed
		 * for the final put(), because we are using the URLStreamProvider.
		 * AmbariEndpoint ambari = AmbariStoreHelper.getAmbariLocalEndpoint(
		 * viewContext.getProperties() ); AmbariEndpoint cluster =
		 * AmbariStoreHelper.getAmbariClusterEndpoint(
		 * viewContext.getProperties() ); // TODO - This needs to be hardcoded
		 * in some static properties somewhere. Duplicated in Servlet.
		 * StoreApplication storeApplication = new StoreApplication(
		 * "AMBARI-STORE", "0.1.0", "store", "Store", "Ambari Application Store"
		 * );
		 * 
		 * try { Map<String,String> amstore =
		 * AmbariStoreHelper.getUpdatedStoreProperties( ambari, cluster );
		 * storeApplication.properties = amstore;
		 * 
		 * // POST IT ! AmbariStoreHelper.createOrUpdateViewInstance( ambari,
		 * viewContext, storeApplication ); } catch( Exception e) { // Ignore
		 * everything, maybe log or flag something ? }
		 */
	}

	public void onDestroy(ViewInstanceDefinition definition) {
	}
}