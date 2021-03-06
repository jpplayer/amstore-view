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
import java.util.List;

import org.apache.ambari.view.ViewContext;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.NotImplementedException;
import org.json.JSONObject;
import org.rauschig.jarchivelib.Archiver;
import org.rauschig.jarchivelib.ArchiverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hortonworks.amstore.view.StoreException.CODE;

public class StoreApplicationService extends StoreApplication {
	private final static Logger LOG = LoggerFactory
			.getLogger(StoreApplicationService.class);

	//String stackVersion = null;
	String serviceName = null;

	String serviceFolderName = null;

	public StoreApplicationService(String serviceName, String serviceVersion) {
		super();
		this.serviceName = serviceName;
		this.version = serviceVersion;
		setType("SERVICE");
	}

	public StoreApplicationService(JSONObject app) {
		super(app);

		AmbariStoreHelper h = new AmbariStoreHelper();
		// Additional properties specific to SERVICE
		// These are currently mandatory, throw exception if they are null.
//		stackVersion = h._s(app, "stack_version");
		serviceName = h._s(app, "service_name");
		serviceFolderName = h._s(app, "service_folder_name");
	}

	
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.hortonworks.amstore.view.StoreApplication#deleteApplicationFiles()
	 */
	public void deleteApplicationFiles(AmbariEndpoint clusterAmbari) {
		try {
			deleteWorkDirectory(clusterAmbari);
		} catch (IOException e) {
			LOG.warn("StoreApplication.deleteApplicationFiles: Not all files removed successfully");
		}
	}

	public String getPackageFilepath() {
		if (package_uri != null) {
			String targetPath = "/tmp";
			String filename = FilenameUtils.getName(package_uri);
			// Remove any leftover uri characters
			filename = filename.split("\\?")[0];
			return targetPath + "/" + filename;
		} else
			return null;
	}

	public String getServiceFolderName() {
		return serviceFolderName;
	}

	public String getServiceName() {
		return serviceName;
	}

	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}

	public void downloadAndExtractPackage(AmbariEndpoint clusterAmbari) throws IOException {
		if (getPackage_uri() != null) {

			// Check whether the file is already present (do not re-download)
			// We pull the tarball into tmp
			String filePath = getPackageFilepath();
			File file = new File(filePath);
			File dir = new File(getPackageWorkdir(clusterAmbari));
			File svcs = new File(clusterAmbari.getServicesFolder());
			
			if (!file.isFile()) {
				// How can we do this in a thread and provide download
				// update ? TODO
				AmbariStoreHelper.downloadFile(getPackage_uri(), filePath);
			}
			// Remove old folder and unpack into services folder
			FileUtils.deleteDirectory(dir);
			LOG.info("Extracting service package '" + filePath + "' to '" + clusterAmbari.getServicesFolder() + "'.");
			Archiver archiver = ArchiverFactory.createArchiver("tar", "gz");
			archiver.extract(file, svcs);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.hortonworks.amstore.view.StoreApplication#getPackageWorkdir()
	 */
	@Override
	public String getPackageWorkdir(AmbariEndpoint localAmbari) throws IOException {
		String workDirectory = localAmbari.getServicesFolder() + "/"
				+ getServiceFolderName();
		return workDirectory;
	}
	
	@Override
	public void doInstallStage1(AmbariEndpoint localAmbari) throws IOException,
			StoreException {
		downloadAndExtractPackage(localAmbari);

		// check that local ambari is managing a cluster
		// If not, we cannot proceed to stage 2
		if (!localAmbari.isCluster())
			throw new StoreException(
					"Warning: cannot instanciate a service on an Ambari server that is not managing a cluster. The server at "
							+ localAmbari.getUrl()
							+ " is not managing a cluster.\n", CODE.WARNING);

	}

	@Override
	public void doInstallStage2(AmbariEndpoint localAmbari, boolean reinstall)
			throws IOException, StoreException {
		// Check that the service is installed
		if (!localAmbari. getAvailableServices().keySet().contains(
				getServiceName())) {
			throw new StoreException(
					"Info: Service not yet available. Cannot proceed.",
					CODE.INFO);
		}
		// Kick off automated installation if requested.
		// ambari.createServiceInstance( this );
	}

	@Override
	public void doUpdateStage1(StoreApplication newApplication)
			throws IOException {
		// TODO Auto-generated method stub
		throw new NotImplementedException("Updating a service not implemented");
	}

	@Override
	public void doUpdateStage2(AmbariEndpoint localAmbari,
			StoreApplication newApplication) throws IOException {
		// TODO Auto-generated method stub
		throw new NotImplementedException("Updating a service not implemented");
	}

	@Override
	public void doUninstallStage1(AmbariEndpoint localAmbari)
			throws IOException, StoreException {

		// delete service with all components
		localAmbari.deleteServiceInstance(this);

		// delete stack files
		deleteApplicationFiles(localAmbari);
	}
	
	@Override
	public void doDeinstantiateStage1(AmbariEndpoint localAmbari)
			throws IOException, StoreException{
		throw new NotImplementedException("DeInstantiating a service not implemented");
	}
	
	@Override
	public String getCanonicalName() {
		return "service-" + getServiceName();
	}

	@Override
	public String getName() {
		return getServiceName();
	}

	
}
