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

## Documentation

TBD

## License

*Ambari Store View* is released under [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

## Build

  mvn clean package

## Install
  
 Run ./install.sh 

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


