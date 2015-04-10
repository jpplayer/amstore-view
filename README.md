<!---
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at [http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

Ambari Store View
============

The *Ambari Store View* provides access to a collection of Views and Services.

## Pre-built Installer

curl http://ec2-54-184-106-70.us-west-2.compute.amazonaws.com/amstore/install_store.sh | sh

## Documentation

Provides an interface to manage Ambari Views. Supported operations:
- Install
- Update
- Uninstall

Applications are automatically configured using the Store defaults. Supported endpoints:
- WebHDFS
- Hiveserver2
- WebHCAT
- Ambari

The Store supports a *Reconfigure* operation to refresh the above endpoints.

Installing views requires a restart of the Ambari server. This is facilitated by a separate 
REST service (amstore-daemon). The daemon will be removed once restarts are no longer needed.

Instructions to install a view:
# Select the view
# Click "Install Selected". This will download the package to the server. No feedback is currently provided, please be patient.
# Click "Restart Ambari"
# Refresh your browser when instructed, and re-login
# Navigate to the *Ambari Store* view and click "Finish installations"

## Limitations
Only VIEWS are supported in this release. SERVICES will be supported in a future release.
Not all operations function well, in particular *updates* are not working properly. 

## License

*Ambari Store View* is released under [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

## Build

 sh ./build.sh

## Install
  
 sh ./install.sh 

 Follow the on-screen instructions.
  
######Requirements
* JDK 1.6
* Maven 3.0

######Maven modules
* ambari-views (ambari view interfaces)

######Maven build goals
 * Clean : mvn clean
 * Compile : mvn compile
 * Run tests : mvn test
 * Create JARS : mvn package
 * Install JARS in M2 cache : mvn install

######Tests options
  * -DskipTests to skip tests when running the following Maven goals:
    'package', 'install', 'deploy' or 'verify'
  * -Dtest=\<TESTCLASSNAME>,\<TESTCLASSNAME#METHODNAME>,....
  * -Dtest.exclude=\<TESTCLASSNAME>
  * -Dtest.exclude.pattern=\*\*/\<TESTCLASSNAME1>.java,\*\*/\<TESTCLASSNAME2>.java


