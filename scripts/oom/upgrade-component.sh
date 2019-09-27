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
   echo "Usage: $0 -d /onap/kubernetes/oom -r dev-so -c so -f /onap/override-onap.yaml -o ./deployment_result"

   echo -e "\t-d >> The OOM directory from where to upgrade the lab"
   echo -e "\t-r >> The component helm RELEASE name (dev-so, dev-clamp, ...)"
   echo -e "\t-c >> The component folder name in OOM folder"
   echo -e "\t-f >> The override file"
   echo -e "\t-o >> The override file"
   exit 1 # Exit script after printing help
}

while getopts "d:r:c:f:o:" opt
do
   case "$opt" in
      d ) OOM_DIR="$OPTARG" ;;
      r ) HELM_RELEASE_NAME="$OPTARG" ;;
      c ) COMPONENT_FOLDER="$OPTARG" ;;
      f ) OVERRIDE_FILE="$OPTARG" ;;
      o ) OUTPUT_DIR="$OPTARG" ;;
      ? ) helpFunction ;; # Print helpFunction in case parameter is non-existent
   esac
done

if [ -z "$OOM_DIR" ] || [ -z "$HELM_RELEASE_NAME" ] || [ -z "$COMPONENT_FOLDER" ]  || [ -z "$OVERRIDE_FILE" ] || [ -z "$OUTPUT_DIR" ]
then
   echo "Some or all of the parameters are empty";
   helpFunction
fi

cd $OOM_DIR
helm upgrade $HELM_RELEASE_NAME $COMPONENT_FOLDER -f $OVERRIDE_FILE

i=0
TOTAL_LINES_FOR_COMPONENT=`kubectl get pods -n onap | grep "\-$COMPONENT_FOLDER\-" | wc -l`
FAILING_PODS=$TOTAL_LINES_FOR_COMPONENT
while [ $i -lt 15 ]
do
   NB_LINES_RUNNING=`kubectl get pods -n onap |grep "\-$COMPONENT_FOLDER\-" | grep -E '(Running|Completed)' | wc -l`
   echo "Found ${NB_LINES_RUNNING}/${TOTAL_LINES_FOR_COMPONENT} Pods in Running state"
   FAILING_PODS="$(($TOTAL_LINES_FOR_COMPONENT-$NB_LINES_RUNNING))"
   if [ $FAILING_PODS -eq 0 ] 
   then
      echo "All ${$COMPONENT_FOLDER} Pods Running"
      break
   fi
   ((i++))
   sleep 20
done

mkdir -p $OUTPUT_DIR

kubectl get pods -n onap | grep "\-$COMPONENT_FOLDER\-" > $OUTPUT_DIR/pod-states.log

PODS=$(kubectl get pods -n onap |grep "\-$COMPONENT_FOLDER\-" | cut -d' ' -f1)
for POD in ${PODS}
do
   kubectl describe pod $POD -n onap > $OUTPUT_DIR/$POD-describe.log
   kubectl logs $POD -n onap --all-containers > $OUTPUT_DIR/$POD-logs.log
done

exit $FAILING_PODS
