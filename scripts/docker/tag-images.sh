#!/bin/bash

###
# ============LICENSE_START=======================================================
# ONAP CI/CD
# ================================================================================
# Copyright (C) 2019 AT&T Intellectual Property. All rights
#                             reserved.
# ================================================================================
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ============LICENSE_END============================================
# ===================================================================
# 
###

helpFunction()
{
   echo ""
   echo "Usage: $0 -p onap -n new-onap -v 64656-2 -r registry.gitlab.com"

   echo -e "\t-p >> The docker registry prefix that must be replaced"
   echo -e "\t-r >> The new docker registry url that must be inserted (https)"
   echo -e "\t-n >> The new prefix that must be inserted"
   echo -e "\t-v >> The new version for each image found"
   
   exit 1 # Exit script after printing help
}

while getopts "p:r:n:v:" opt
do
   case "$opt" in
      p ) ONAP_DOCKER_PREFIX="$OPTARG" ;;
      n ) REGISTRY_DOCKER_PREFIX="$OPTARG" ;;
      v ) NEW_VERSION="$OPTARG" ;;
      r ) NEW_REGISTRY="$OPTARG" ;;
      ? ) helpFunction ;; # Print helpFunction in case parameter is non-existent
   esac
done

# Print helpFunction in case parameters are empty
if [ -z "$ONAP_DOCKER_PREFIX" ] || [ -z "$REGISTRY_DOCKER_PREFIX" ] || [ -z "$NEW_VERSION" ]  || [ -z "$NEW_REGISTRY" ] 
then
   echo "Some or all of the parameters are empty";
   helpFunction
fi

DOCKERS=$(docker images | grep -v SNAPSHOT | grep ${ONAP_DOCKER_PREFIX} | grep latest | cut -d' '  -f1)
for docker_image in ${DOCKERS}
do 
    echo "Working on Image: ${docker_image}"
    #component=$(echo ${docker_image} | rev | cut -d/ -f1 | rev)
    component=$(echo ${docker_image}|sed -e "s/${ONAP_DOCKER_PREFIX}\///g")
    echo "Component found: ${component}"
    echo "RETAGGING ${docker_image}:latest as ${REGISTRY_DOCKER_PREFIX}/${component}:${NEW_VERSION}"
    docker tag ${docker_image}:latest ${NEW_REGISTRY}/${REGISTRY_DOCKER_PREFIX}/${component}:${NEW_VERSION}
    docker push ${NEW_REGISTRY}/${REGISTRY_DOCKER_PREFIX}/${component}:${NEW_VERSION}
done

docker rmi -f $(docker images |grep ${ONAP_DOCKER_PREFIX}) || true
