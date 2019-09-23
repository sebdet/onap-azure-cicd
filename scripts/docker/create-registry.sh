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
   echo "Usage: $0 -d /etc/mycertificates -c certificate.pem -k key.pem"

   echo -e "\t-d >> The directory where certificate and key files are located"
   echo -e "\t-c >> The certificate filename"
   echo -e "\t-k >> The key filename"

   exit 1 # Exit script after printing help
}

while getopts "d:c:k:" opt
do
   case "$opt" in
      d ) FILES_DIR="$OPTARG" ;;
      c ) CERTIFICATE_FILENAME="$OPTARG" ;;
      p ) KEY_FILENAME="$OPTARG" ;;
      ? ) helpFunction ;; # Print helpFunction in case parameter is non-existent
   esac
done

# Print helpFunction in case parameters are empty
if [ -z "$FILES_DIR" ] || [ -z "$CERTIFICATE_FILENAME" ] || [ -z "$KEY_FILENAME" ]
then
   echo "Some or all of the parameters are empty";
   helpFunction
fi

docker stop registry || true
docker rm registry || true
docker system prune -f
#docker run -d -p 5000:5000 --restart=always --name registry registry:2
docker run -d --restart=always --name registry -v $FILES_DIR:/certs -e REGISTRY_HTTP_ADDR=0.0.0.0:443 -e REGISTRY_HTTP_TLS_CERTIFICATE=/certs/$CERTIFICATE_FILENAME -e REGISTRY_HTTP_TLS_KEY=/certs/$KEY_FILENAME -p 443:443 registry:2
