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
   echo "Usage: $0 -f /var/lib/myfile -k /testme"

   echo -e "\t-f >> The gerrit review message file"
   echo -e "\t-k >> The main keyword that triggers the pipeline"  

   exit 1 # Exit script after printing help
}

while getopts "f:k:" opt
do
   case "$opt" in
      f ) GERRIT_MESSAGE="$OPTARG" ;;
      k ) KEYWORD="$OPTARG" ;;
      ? ) helpFunction ;; # Print helpFunction in case parameter is non-existent
   esac
done

# Print helpFunction in case parameters are empty
if [ -z "$GERRIT_MESSAGE" ] || [ -z "$KEYWORD" ]
then
   echo "Some or all of the parameters are empty";
   helpFunction
fi

FILTERED_RESULT=$(cat $GERRIT_MESSAGE | sed -e "s/Patch Set [0-9]\+://g" | sed -e "s|$KEYWORD||g")
for config in $FILTERED_RESULT
do
	repo=$(echo $config | cut -d':' -f1)
	refspec=$(echo $config | cut -d':' -f2)
	if [ "$repo" == "oom" ] 
	then
	    echo $refspec
	    exit 0
	fi
	###echo $config | cut -d':' -f1
	###echo $config | cut -d':' -f2
done
exit 0
