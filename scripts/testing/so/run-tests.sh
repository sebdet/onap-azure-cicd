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
   echo "Usage: $0 -d /onap/oom/kubernetes -o /onap/test-result"

   echo -e "\t-d >> The OOM directory"
   echo -e "\t-o>> The folder where to store the test results"
   
   exit 1 # Exit script after printing help
}

while getopts "d:c:p:n:v:o:r:u:s:" opt
do<
   case "$opt" in
      d ) OOM_DIR="$OPTARG" ;;
      o ) RESULTS_DIR="$OPTARG" ;;
      ? ) helpFunction ;; # Print helpFunction in case parameter is non-existent
   esac
done

# Print helpFunction in case parameters are empty
if [ -z "$OOM_DIR" ] || [ -z "$RESULTS_DIR" ]
then
   echo "Some or all of the parameters are empty";
   helpFunction
fi
mkdir -p ${RESULTS_DIR}
$OOM_DIR/robot/ete-k8s.sh onap health > ${RESULTS_DIR}/ete-k8s-health.log
if (( $(grep -c "| FAIL |" ${RESULTS_DIR}/ete-k8s-health.log) > 0 ))
then  
  cat ${RESULTS_DIR}/ete-k8s-health.log
  exit 0
else
  echo "HEALTHCHECK SUCCESS"
  exit 0
fi

#./robot/ete-k8s.sh onap healthdist > ${RESULTS_DIR}/ete-k8s-healthdist.log
#if (( $(grep -c "| FAIL |" ${RESULTS_DIR}/ete-k8s-healthdist.log) > 0 ))
#  echo 'Found some errors in the Healhcheck'
#  exit 1
fi
$OOM_DIR/robot/demo-k8s.sh onap init > ${RESULTS_DIR}/demo-k8s-init.log
if (( $(grep -c "| FAIL |" ${RESULTS_DIR}/demo-k8s-init.log) > 0 ))
then
  cat ${RESULTS_DIR}/demo-k8s-init.log
  exit 1
else
  echo "INIT SUCCESS"
  exit 0
fi

#./robot/demo-k8s.sh onap init_robot > ${RESULTS_DIR}/demo-k8s-init_robot.log
#./robot/ete-k8s.sh onap instantiateVFWCL > ${RESULTS_DIR}/ete-k8s-instantiateVFWCL.log
