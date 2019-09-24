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
   echo "Usage: $0 -d /onap/kubernetes/oom -r dev-so -c so -f /onap/override-onap.yaml"

   echo -e "\t-d >> The OOM directory from where to upgrade the lab"
   echo -e "\t-r >> The component helm RELEASE name (dev-so, dev-clamp, ...)"
   echo -e "\t-c >> The component folder name in OOM folder"
   echo -e "\t-f >> The override file"
   exit 1 # Exit script after printing help
}

while getopts "d:r:c:f:" opt
do
   case "$opt" in
      d ) OOM_DIR="$OPTARG" ;;
      r ) HELM_RELEASE_NAME="$OPTARG" ;;
      c ) COMPONENT_FOLDER="$OPTARG" ;;
      f ) OVERRIDE_FILE="$OPTARG" ;;
      ? ) helpFunction ;; # Print helpFunction in case parameter is non-existent
   esac
done

cd $OOM_DIR
helm upgrade $HELM_RELEASE_NAME $COMPONENT_FOLDER -f $OVERRIDE_FILE

i=0
NB_LINE=`kubectl get pods -n onap | grep $COMPONENT_FOLDER | wc -l`
SUCCESS=0
while [ $i -lt 10 ]
do
   RESULT=`kubectl get pods -n onap |grep ${$COMPONENT_FOLDER} | grep 'Running' | wc -l`
   echo 'Found $RESULT/$NB_LINE Pods in Running state'
   if [ $RESULT -eq $NB_LINE ] 
   then
      echo 'All ${$COMPONENT_FOLDER} Pods Running'
      $SUCCESS=1
      break
   fi
   ((i++))
   sleep 30
done

exit $SUCCESS
