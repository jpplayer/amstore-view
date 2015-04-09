#!/bin/bash

VIEWS="/var/lib/ambari-server/resources/views"
STACK=""
FQDN=$(hostname -f)
CLUSTER_NAME=""
CURL='curl'
CREDENTIALS="admin:admin" 
agent="store-installer"
VERSION="0.1.1"
USE_DEMO_SERVER=no

read -d '' data << EOF
[{ 
"ViewInstanceInfo" : { "label" : "Ambari Store", "description" : "Ambari Application Store", 
"properties" : {"amstore.ambari.cluster.url":"http://lake01.cloud.hortonworks.com:8080","amstore.ambari.cluster.password":"hortonworks","amstore.ambari.local.password":"admin","amstore.ambari.cluster.username":"remoteambari","amstore.ambari.local.username":"admin","amstore.ambari.local.url":"http://localhost:8080" } } }]
EOF
function create_instance2() {
curl -u admin:admin -X POST -H "X-Requested-By: ambari" -d "${data}" http://localhost:8080/api/v1/views/AMBARI-STORE/versions/${VERSION}/instances/store     
}



function usage() {
	cat << EOF
Usage: install.sh <options>
    -u <username>:<password>   Specify username and password for Ambari. Defaults to admin:admin.

Example: ./install.sh -u operator:hadoop
EOF
}

while getopts ":u:d" opt; do
	case "$opt" in
	u)
		CREDENTIALS=${OPTARG}
	;;
	d)
		USE_DEMO_SERVER=yes
	;;
	\?)
		echo "Invalid option: -$OPTARG" >&2
		usage
		exit 1
	;;
	esac
done

CURL="$CURL -u $CREDENTIALS"

function check_ambari(){
	response=$( $CURL -i http://localhost:8080/api/v1/clusters 2>&1)
	if [[ $response != *"HTTP/1.1 200 OK"* ]]
	then
		echo "Could not connect to Ambari. Ensure that the server is started. Specify non-default username and password if necessary."
		usage
		exit 1
	fi
}

function get_cluster_name() {
	response=$( $CURL -i http://localhost:8080/api/v1/clusters 2>&1)

	if [[ $response == *"HTTP/1.1 200 OK"* ]]
       then
		# Run curl without headers (no -i)
		CLUSTER_NAME=$( $CURL --silent http://localhost:8080/api/v1/clusters | python -c 'import sys,json; print json.load( sys.stdin )["items"][0]["Clusters"]["cluster_name"]')
	elif [[ $response == *"HTTP/1.1 403 Bad credentials"* ]]
		then
			echo "Invalid credentials. Please specify using the -u switch"
			usage
			exit 1
	else
		echo "Error connecting to http://localhost:8080/api/v1/clusters $response"
			exit 1
    fi
  
	
}

function create_instance() {

$CURL -H "X-Requested-By: $agent" -X POST -d '[ { "ViewInstanceInfo" : { "label": "Ambari Store", "description": "Ambari Application Store"
  } } ] ' "http://localhost:8080/api/v1/views/AMBARI-STORE/versions/${VERSION}/instances/store"
}

function reconfigure_instance() {
$CURL -H "X-Requested-By: $agent" -X POST "http://localhost:8080/api/v1/views/AMBARI-STORE/versions/${VERSION}/instances/store/resources/taskmanager/reconfigure"
}

function install_agent() {
   rm -rf "/var/lib/ambari-server/resources/stacks/HDP/2.1/services/STOREAGENT"
   cp -rf "agent/STOREAGENT" "/var/lib/ambari-server/resources/stacks/HDP/2.1/services"
}

function create_agent() {

  # Create service
  $CURL -H "X-Requested-By: $agent" --silent -X POST -d '[ { "ServiceInfo" : { "service_name": "STOREAGENT"
  } } ] ' http://localhost:8080/api/v1/clusters/$CLUSTER_NAME/services 

  # Create service component 
  $CURL -H "X-Requested-By: $agent" --silent -X POST  http://localhost:8080/api/v1/clusters/$CLUSTER_NAME/services/STOREAGENT/components/STORE_CLIENT
  
  # Create host component
  $CURL -H "X-Requested-By: $agent" --silent -X POST http://localhost:8080/api/v1/clusters/$CLUSTER_NAME/hosts/$FQDN/host_components/STORE_CLIENT
  
  # Install agent on all hosts 
  $CURL -H "X-Requested-By: $agent" --silent -X PUT -d '[ { "RequestInfo": { "context": "Install Store Agent Client" }, "Body" : { "ServiceInfo": { "state": "INSTALLED" }
  } } ] ' http://localhost:8080/api/v1/clusters/$CLUSTER_NAME/services/STOREAGENT 
  
  # Configure client (not necessary yet)
  #$CURLH -X POST  -d 
  data='[
  {
  "RequestInfo" : {
    "command" : "CONFIGURE",
    "context" : "Config Test Srv Client"
  },
  "Requests/resource_filters": [{
    "service_name" : "TESTSRV",
    "component_name" : "TEST_CLIENT",
    "hosts" : "c6403.ambari.apache.org"
  }]
 }
}]'  # http://localhost:8080/api/v1/clusters/$CLUSTER_NAME/requests     

}

function delete_instance() {
$CURL -H "X-Requested-By: $agent" -X DELETE http://localhost:8080/api/v1/views/AMBARI-STORE/versions/$VERSION/instances/store
}

function wait_for_ambari() {
# poll for 60s or until we get a response
timeout=60
while [[ $timeout -ne 0 ]]; do

  status=$( $CURL --silent -i --connect-timeout 1 --max-time 1 http://localhost:8080/api/v1/clusters)
  if [[ $status == *"HTTP/1.1 200 OK"* ]]
       then
          break
  fi
  sleep 1
  timeout=$((timeout -1 ))
done
echo "Ambari ready."

} 

# Check that we have a local Ambari
check_ambari

# Get cluster name, serves as validation of settings also.
#get_cluster_name

rm -rf "${VIEWS}/work/AMBARI-STORE{$VERSION}"
rm -f "$VIEWS/ambari-store-$VERSION.jar"
cp -f target/ambari-store-$VERSION.jar "$VIEWS"
delete_instance
install_agent

ambari-server restart
echo "Waiting for server to accept requests"
wait_for_ambari

# Forget about agent, a Views server won't allow it.
#echo "Installing agent service"
#create_agent
echo "Creating view instance"
if [[ "${USE_DEMO_SERVER}" == "yes" ]]; then
	create_instance2
else
	create_instance
fi

#Couldn't do in deploy() so doing here.
reconfigure_instance
echo ""
echo "The store is installed at:"
echo "http://$FQDN:8080/#/main/views/AMBARI-STORE/$VERSION/store"
