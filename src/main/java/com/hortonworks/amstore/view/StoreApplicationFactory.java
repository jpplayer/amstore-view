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

import org.apache.ambari.view.ViewInstanceDefinition;
import org.json.JSONException;
import org.json.JSONObject;

import com.hortonworks.amstore.view.utils.ServiceFormattedException;

public class StoreApplicationFactory {

	public StoreApplicationFactory(){
	}
	
	/*
	 * The JSONObject should be clean, and directly point to the content, no wrappers.
	 */
	public StoreApplication getStoreApplication(JSONObject app) {
//		JSONObject app; 
		StoreApplication storeApplication = null;
		String type;
	//		app = (JSONObject) json.get("application");

		try {
			type = app.getString("type");
		} catch (JSONException e){
			// TODO: bad coding. The method that calls this function will handle null applications but should be handled
			// as an exception. This exception is really a warning (the application submitted to the store is missing a TYPE.
			// Backend amstore, please keep your data clean !
			return null;	
		}
			
		if( type.equals("VIEW")) {
			storeApplication = new StoreApplicationView(app );
		} else if ( type.equals("SERVICE") ){
			storeApplication = new StoreApplicationService( app );
		} else {
			throw new ServiceFormattedException("Invalid json received");
		}
		return storeApplication;
	}
	
	public  StoreApplicationView getBareboneStoreApplicationView (ViewInstanceDefinition viewInstanceDefinition) {
		return new StoreApplicationView(viewInstanceDefinition);
	}


	// TODO: really bare bones until we can access the installed version
	public  StoreApplicationService getBareboneStoreApplicationService ( String serviceName ) {
		return new StoreApplicationService( serviceName );
	}
}
