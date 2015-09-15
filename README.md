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

The *Ambari Store View* provides access to a collection of Views and Services for Apache Ambari.

## Pre-built Installer

This installer also includes amstore-daemon to facilitate restarts:
```
curl http://amstore.cloudapp.net/amstore/install_store.sh | bash
```
## Manual installation
```
   git clone https://github.com/jpplayer/amstore-daemon.git
   cd amstore-daemon && sh build.sh && sh install.sh
   /usr/local/bin/amstore-daemon.sh start
   cd /var/lib/ambari-server/resources/views
   curl -O http://amstore.cloudapp.net/amstore/ambari-store-0.1.5.jar
   ambari-server restart
```
Login to Ambari then instanciate the Ambari Store view. The instance name must be "store".

## Screenshot

![Image](../master/package/storeview.jpg?raw=true)

## Documentation

Provides an interface to manage Ambari Views. Supported operations:
- Install
- Update
- Uninstall

The Store view works with an Ambari Views Server, but in that case only VIEWS are supported, not SERVICES.

Applications are automatically configured using the Store defaults. Supported endpoints:
- WebHDFS
- Hiveserver2
- WebHCAT
- Ambari

The Store supports a *Reconfigure* operation to refresh the above endpoints.

Installing views requires a restart of the Ambari server. This is facilitated by a separate 
REST service (amstore-daemon). The daemon will be removed once restarts are no longer needed.

Instructions to install a view:

1. Select the view
1. Click "Install Selected". This will download the package to the server. No feedback is currently provided, please be patient.
1. Click "Restart Ambari"
1. Refresh your browser when instructed, and re-login
1. Navigate to the *Ambari Store* view and click "Finish installations"

## Limitations
SERVICES can be downloaded, but unlike VIEWS, they must be manually installed using the "Add Service" wizard of Ambari.
Not all operations function well, in particular *updates* are not working. 

## License

*Ambari Store View* is released under [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

## Build

 sh ./build.sh

######Requirements
* JDK 1.7
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


## Install
  
 sh ./install.sh 

 Follow the on-screen instructions.
  


