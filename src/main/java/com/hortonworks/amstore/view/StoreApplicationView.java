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
import java.util.Map;

import org.apache.ambari.view.ViewContext;
import org.apache.ambari.view.ViewDefinition;
import org.apache.ambari.view.ViewInstanceDefinition;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.NotImplementedException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hortonworks.amstore.view.StoreException.CODE;
import com.hortonworks.amstore.view.utils.ServiceFormattedException;

public class StoreApplicationView extends StoreApplication {
	
	private final static Logger LOG = LoggerFactory
			.getLogger(StoreApplicationView.class);

	protected String viewName;
	
	protected StoreApplicationView() {
		super();
		setType("VIEW");
	}

	
	public StoreApplicationView(ViewContext viewContext, String viewName,
			String version, String instanceName, String instanceDisplayName,
			String description){
		super(
			 version,  instanceName,  instanceDisplayName,
			 description);
		this.viewName = viewName;
	}
	
	@SuppressWarnings("static-access")
	public StoreApplicationView(JSONObject app) {
		super(app);
		AmbariStoreHelper h = new AmbariStoreHelper();
		viewName = h._s(app, "view_name");
	}
	
	public StoreApplicationView(ViewInstanceDefinition viewInstanceDefinition) {
		super();
		this.viewName = viewInstanceDefinition.getViewDefinition().getViewName();
		this.version = viewInstanceDefinition.getViewDefinition().getVersion();
		this.instanceName = viewInstanceDefinition.getInstanceName();
		this.instanceDisplayName = viewInstanceDefinition.getLabel();
		this.description = viewInstanceDefinition.getDescription();
	}

	@Override	
	public void deleteApplicationFiles(AmbariEndpoint localAmbari) {
		try {
			deletePackageFile();
			deleteWorkDirectory(localAmbari);
		} catch (IOException e) {
			LOG.warn("StoreApplication.deleteApplicationFiles: Not all files removed successfully");
		}
	}

	public String getPackageFilepath() {
		if (package_uri != null) {
			String targetPath = "/var/lib/ambari-server/resources/views";
			String filename = FilenameUtils.getName(package_uri);
			// Remove any leftover uri characters
			filename = filename.split("\\?")[0];
			return targetPath + "/" + filename;
		} else
			return null;
	}

	// TODO: This should be calling the ambari endpoint
	@Override
	public String getPackageWorkdir(AmbariEndpoint localAmbari) throws IOException {
		String workDirectory = "/var/lib/ambari-server/resources/views/work/"
				+ getViewName() + "{" + getVersion() + "}";
		return workDirectory;
	}

	public void deletePackageFile() throws IOException {
		String packagePath = getPackageFilepath();
		FileUtils.forceDelete(new File(packagePath));
	}
	
	protected void downloadPackageFile() {
		if (getPackage_uri() != null) {
			// Check whether the file is already present (do not re-download)
			String targetPath = getPackageFilepath();
			File file = new File(targetPath);

			if (!file.isFile()) {
				// How can we do this in a thread and provide download
				// update ? TODO
				AmbariStoreHelper.downloadFile(getPackage_uri(), targetPath);
			}
		}
	}
	
	@Override
	public void doInstallStage1(AmbariEndpoint localAmbari) throws IOException, StoreException {
		downloadPackageFile();
	}

	// TODO: WARNING: Desired properties should already be set.
	@Override
	public void doInstallStage2(AmbariEndpoint localAmbari, boolean reinstall)
	throws IOException, StoreException {
		/*
		 * Check whether we can install. If the package has not yet been
		 * expanded we must wait for Ambari restart.
		 */
		File workdir = new File(getPackageWorkdir(localAmbari));
		if (!workdir.isDirectory()) {
			LOG.debug("The application " + instanceDisplayName
					+ " has not yet been unpacked. Requires restart.");
			throw new StoreException("Application " + this.getInstanceDisplayName() + "not unpacked.\n", CODE.INFO);
		}
		if( reinstall )
			localAmbari.updateViewInstance(this);
		else
			localAmbari.createViewInstance(this);
	}
	
	@Override
	public void doUpdateStage1( StoreApplication newApplication ) throws IOException {
		// Delete old package but not work directory
		// This is important: the old instance must survive a restart
		this.deletePackageFile();

		// Download the new package
		((StoreApplicationView)newApplication).downloadPackageFile();
	}

	// TODO: WARNING. desiredMappedProperties must be set. Refactor this.
	@Override
	public void doUpdateStage2(AmbariEndpoint localAmbari, StoreApplication newApplication) throws IOException
	{
		
		
		// If its the same version, error and do nothing
		if (newApplication.getVersion().equals(
				this.getVersion()))
			throw new IllegalArgumentException(
					"Cannot update to the same version:"
							+ this.getInstanceDisplayName());

		/*
		 * Check whether we can install. If the package has not yet been
		 * expanded we must wait for Ambari restart.
		 */
		File workdir = new File(newApplication.getPackageWorkdir(localAmbari));
		if (!workdir.isDirectory()) {
			LOG.debug("The application has not yet been unpacked. Requires restart.");
			return;
		} 
		else {
			// instantiate the new view
			LOG.debug("Starting post update task for : "
					+ newApplication.getPackageWorkdir(localAmbari) + "\n");


			LOG.debug("creating view: " + newApplication.instanceName);
			localAmbari.createViewInstance( (StoreApplicationView)newApplication);

			// delete any remaining files
			this.deleteApplicationFiles(localAmbari);
			
			// delete any old instance
			// Exception: if we are updating the main store, could lead
			// to odd behavior
			localAmbari.deleteViewInstance( this );
		}
	}

	public String getViewName() {
		if (viewName == null)
			throw new RuntimeException("Call to viewname returning null");
		return viewName;
	}

	@Override
	public void doUninstallStage1(AmbariEndpoint localAmbari )
			throws IOException, StoreException {
		// delete view instance
		localAmbari.deleteViewInstance( this );
		// delete view files
		deleteApplicationFiles(localAmbari);
	}
	
	@Override
	public void doDeinstantiateStage1(AmbariEndpoint localAmbari)
			throws IOException, StoreException{
		// delete view instance
		localAmbari.deleteViewInstance( this );
	}
	
	@Override
	public String getCanonicalName(){
		return "view-" + getViewName(); // + "-" + getInstanceName();
	}
	
	@Override
	public String getName() {
		return getViewName();
	}
	
} // End class
