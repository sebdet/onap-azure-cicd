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
   echo "Usage: $0 -d /onap/kubernetes/oom -r dev-so"

   echo -e "\t-d >> The OOM directory from where to upgrade the lab"
   echo -e "\t-r >> The component helm RELEASE name (dev-so, dev-clamp, ...)"
   exit 1 # Exit script after printing help
}

while getopts "d:r:" opt
do
   case "$opt" in
      d ) OOM_DIR="$OPTARG" ;;
      r ) HELM_RELEASE_NAME="$OPTARG" ;;
      ? ) helpFunction ;; # Print helpFunction in case parameter is non-existent
   esac
done

if [ -z "$OOM_DIR" ] || [ -z "$HELM_RELEASE_NAME" ]
then
   echo "Some or all of the parameters are empty";
   helpFunction
fi

cd $OOM_DIR
helm rollback $HELM_RELEASE_NAME  1
