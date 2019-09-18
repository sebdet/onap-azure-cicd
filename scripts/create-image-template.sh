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
   echo "Usage: $0 -d /onap/kubernetes/oom/so -c so -p onap/so -r registry.gitlab.com -n /onap-att-ci/so-build-docker -v 4.1.2 -o /tmp/myfile.yaml"

   echo -e "\t-d >> The directory where to search for all the images"
   echo -e "\t-c >> The component name (so, clamp, ...)"
   echo -e "\t-p >> The docker registry prefix that must be replaced"
   echo -e "\t-r >> The new docker registry url that must be inserted"
   echo -e "\t-n >> The new prefix that must be inserted"
   echo -e "\t-v >> The new version for each image found"
   echo -e "\t-u >> The registry user"
   echo -e "\t-s >> The registry password"
   echo -e "\t-o >> The output file path (optional), by default dump it to stdout"

   exit 1 # Exit script after printing help
}

while getopts "d:c:p:n:v:o:r:u:s:" opt
do
   case "$opt" in
      d ) WORKING_DIR="$OPTARG" ;;
      c ) COMPO_NAME="$OPTARG" ;;
      p ) OLD_URL_PREFIX="$OPTARG" ;;
      n ) NEW_URL_PREFIX="$OPTARG" ;;
      v ) NEW_VERSION="$OPTARG" ;;
      o ) OUTPUT_FILE="$OPTARG" ;;
      r ) NEW_REGISTRY="$OPTARG" ;;
      u ) REGISTRY_USER="$OPTARG" ;;
      s ) REGISTRY_PASSWORD="$OPTARG" ;;
      ? ) helpFunction ;; # Print helpFunction in case parameter is non-existent
   esac
done

# Print helpFunction in case parameters are empty
#echo "PARAMETERS: $COMPO_NAME, $OLD_URL_PREFIX, $NEW_URL_PREFIX, $NEW_VERSION, $WORKING_DIR", "$OUTPUT_FILE"
if [ -z "$COMPO_NAME" ] || [ -z "$OLD_URL_PREFIX" ] || [ -z "$NEW_URL_PREFIX" ] || [ -z "$NEW_VERSION" ] || [ -z "$WORKING_DIR" ] || [ -z "$NEW_REGISTRY" ] || [ -z "$REGISTRY_USER" ] || [ -z "$REGISTRY_PASSWORD" ]
then
   echo "Some or all of the parameters are empty";
   helpFunction
fi

CURRENT_DIR=`pwd`
cd $WORKING_DIR
grep -R " onap/" * | sed  -e 's/charts\///g' | sed -e 's/\/values.yaml//g' | sed -e 's/values.yaml/'$COMPO_NAME'/g' | sed -e 's/image:\(.*\)/\n   image:\1\n   repositoryOverride: '$NEW_REGISTRY'/g' > $CURRENT_DIR/image-template-tmp.yaml

#echo 'Found those images:' 
#cat $CURRENT_DIR/image-template-tmp.yaml

#echo 'New images /version template file:'
cat $CURRENT_DIR/image-template-tmp.yaml | sed -e 's#'$OLD_URL_PREFIX'#'$NEW_URL_PREFIX'#g' | sed -e 's/[0-9]\+\.[0-9]\+.*$/'$NEW_VERSION'/g' > $CURRENT_DIR/image-template-tmp2.yaml
#printf '\nglobal:\n   repository: '$NEW_REGISTRY'\n   repositoryCred:\n      user: '$REGISTRY_USER'\n      password: '$REGISTRY_PASSWORD'\n' >> $CURRENT_DIR/image-template-tmp2.yaml

cd $CURRENT_DIR
if [ -z "$OUTPUT_FILE" ]
then
  ## Stdout case
  cat $CURRENT_DIR/image-template-tmp2.yaml
else
  ## File case
  cp $CURRENT_DIR/image-template-tmp2.yaml $OUTPUT_FILE
fi

## Cleanup
rm $CURRENT_DIR/image-template-tmp.yaml $CURRENT_DIR/image-template-tmp2.yaml
