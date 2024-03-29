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
   echo -e "\t-o >> The output file path (optional), by default dump it to stdout"

   exit 1 # Exit script after printing help
}

while getopts "d:c:p:r:n:v:o:" opt
do
   case "$opt" in
      d ) WORKING_DIR="$OPTARG" ;;
      c ) COMPO_NAME="$OPTARG" ;;
      p ) OLD_URL_PREFIX="$OPTARG" ;;
      r ) NEW_REGISTRY="$OPTARG" ;;
      n ) NEW_URL_PREFIX="$OPTARG" ;;
      v ) NEW_VERSION="$OPTARG" ;;
      o ) OUTPUT_FILE="$OPTARG" ;;
      ? ) helpFunction ;; # Print helpFunction in case parameter is non-existent
   esac
done

# Print helpFunction in case parameters are empty
#echo "PARAMETERS: $COMPO_NAME, $OLD_URL_PREFIX, $NEW_URL_PREFIX, $NEW_VERSION, $WORKING_DIR", "$OUTPUT_FILE"
if [ -z "$WORKING_DIR" ] || [ -z "$COMPO_NAME" ] || [ -z "$OLD_URL_PREFIX" ] || [ -z "$NEW_REGISTRY" ] || [ -z "$NEW_URL_PREFIX" ] || [ -z "$NEW_VERSION" ]
then
   echo "Some or all of the parameters are empty";
   helpFunction
fi

CURRENT_DIR=`pwd`
cd $WORKING_DIR
grep -R " onap/" * > $CURRENT_DIR/image-template-tmp.yaml
sed -i 's/charts\///g' $CURRENT_DIR/image-template-tmp.yaml
sed -i 's/^values.yaml://g' $CURRENT_DIR/image-template-tmp.yaml
sed -i 's/\/values.yaml//g' $CURRENT_DIR/image-template-tmp.yaml
sed -i 's/\(.\+\)image:\(.*\)/\1\n   image:\2\n   repositoryOverride: '$NEW_REGISTRY'/g' $CURRENT_DIR/image-template-tmp.yaml
sed -i 's/^image:\(.*\)/image:\1\nrepositoryOverride: '$NEW_REGISTRY'/g' $CURRENT_DIR/image-template-tmp.yaml

#echo 'Found those images:' 
#cat $CURRENT_DIR/image-template-tmp.yaml

#echo 'New images /version template file:'
sed -i 's#image: '$OLD_URL_PREFIX'/#image: '$NEW_URL_PREFIX'/#g' $CURRENT_DIR/image-template-tmp.yaml
sed -i 's/[0-9]\+\.[0-9]\+.*$/'$NEW_VERSION'/g' $CURRENT_DIR/image-template-tmp.yaml

cd $CURRENT_DIR
if [ -z "$OUTPUT_FILE" ]
then
  ## Stdout case
  cat $CURRENT_DIR/image-template-tmp.yaml
else
  ## File case
  cp $CURRENT_DIR/image-template-tmp.yaml $OUTPUT_FILE
fi

## Cleanup
rm $CURRENT_DIR/image-template-tmp.yaml
