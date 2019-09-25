node {
   def mvnHome
   stage('Build New Docker') {
        echo "Building Component ${params.GERRIT_PROJECT}"
        //  build job: 'build-component', parameters: [string(name: 'GERRIT_CHANGE_NUMBER', value: env.GERRIT_CHANGE_NUMBER), string(name: 'GERRIT_PATCHSET_NUMBER', value: env.GERRIT_PATCHSET_NUMBER), string(name: 'GERRIT_REFSPEC', value: env.GERRIT_REFSPEC), string(name: 'ONAP_DOCKER_PREFIX', value: 'onap'), string(name: 'REGISTRY_DOCKER_PREFIX', value: 'new-onap'), string(name: 'PROJECT_REGISTRY', value: 'localhost:443'), string(name: 'PROJECT', value: env.GERRIT_PROJECT)]
        checkout([$class: 'GitSCM', 
                  branches: [[name: 'master']], 
                  doGenerateSubmoduleConfigurations: false, 
                  extensions: [[$class: 'RelativeTargetDirectory', 
                                relativeTargetDir: 'onap-azure-cicd']], 
                  submoduleCfg: [], 
                  userRemoteConfigs: [[credentialsId: 'github-key-cicd-project', 
                                       url: 'git@github.com:sebdet/onap-azure-cicd.git',
                                       name: 'pipeline_project']]])  
        checkout([$class: 'GitSCM', 
                  branches: [[name: 'FETCH_HEAD']], 
                  doGenerateSubmoduleConfigurations: false, 
                  extensions: [[$class: 'RelativeTargetDirectory', 
                                relativeTargetDir: '${GERRIT_PROJECT}']], 
                  submoduleCfg: [], 
                  userRemoteConfigs: [[credentialsId: 'lf-key-onap-bot', 
                                       refspec: '${GERRIT_REFSPEC}', 
                                       url: '${GERRIT_SCHEME}://OnapTesterBot@${GERRIT_HOST}:${GERRIT_PORT}/${GERRIT_PROJECT}', 
                                       name: 'onap_project']]])
      
        //sh("bash -x onap-azure-cicd/scripts/docker/create-registry.sh -d $CERTIFICATE_FOLDER -c $CERTIFICATE_FILENAME -k $KEY_FILENAME")
        //def buildScript = load "onap-azure-cicd/pipeline/build/${params.GERRIT_PROJECT}/build-component.groovy"
        //buildScript.buildComponent(params.GERRIT_PROJECT)
        //sh("bash -x onap-azure-cicd/scripts/docker/tag-images.sh -p $ONAP_DOCKER_PREFIX -n $REGISTRY_DOCKER_PREFIX -r $REGISTRY_HOST -v $GERRIT_CHANGE_NUMBER-$GERRIT_PATCHSET_NUMBER")
    }
    stage('Deploy New Docker For Component') {
        echo "Deploying Component ${params.GERRIT_PROJECT} on Azure OOM lab"
        //  build job: 'deploy-component', parameters: [string(name: 'OOM_FOLDER', value: 'oom'), string(name: 'COMPONENT_NAME', value: env.GERRIT_PROJECT), string(name: 'ONAP_DOCKER_PREFIX', value: 'onap'), string(name: 'REGISTRY_DOCKER_PREFIX', value: 'new-onap'), string(name: 'GERRIT_REVIEW', value: env.GERRIT_CHANGE_NUMBER), string(name: 'GERRIT_PATCHSET', value: env.GERRIT_PATCHSET_NUMBER), string(name: 'HELM_RELEASE_NAME', value: 'cc697w-tdpi'), string(name: 'REGISTRY_DOCKER', value: 'onapci.westus2.cloudapp.azure.com:443')]
        checkout([$class: 'GitSCM', 
            branches: [[name: 'master']], 
            doGenerateSubmoduleConfigurations: false, 
            extensions: [[$class: 'RelativeTargetDirectory', 
                          relativeTargetDir: '${OOM_FOLDER}']], 
            submoduleCfg: [], 
            userRemoteConfigs: [[credentialsId: 'lf-key-onap-bot', 
                          url: '${GERRIT_SCHEME}://OnapTesterBot@${GERRIT_HOST}:${GERRIT_PORT}/oom', 
                          name: 'onap_oom_project']]])
        sh("make -C $OOM_FOLDER/kubernetes/ all")
        sh("bash -x ${WORKSPACE}/onap-azure-cicd/scripts/oom/create-image-override.sh -d ${WORKSPACE}/${OOM_FOLDER}/kubernetes/${GERRIT_PROJECT} -c ${GERRIT_PROJECT} -p ${ONAP_DOCKER_PREFIX} -r ${REGISTRY_HOST} -n ${REGISTRY_DOCKER_PREFIX} -v ${GERRIT_CHANGE_NUMBER}-${GERRIT_PATCHSET_NUMBER} -o ${WORKSPACE}/${OOM_FOLDER}/override-onap.yaml")
        UPGRADE_STATUS = sh(returnStatus: true, script: "bash -x ${WORKSPACE}/onap-azure-cicd/scripts/oom/upgrade-component.sh -d ${WORKSPACE}/${OOM_FOLDER}/kubernetes -r ${HELM_RELEASE_NAME}-${GERRIT_PROJECT} -c ${GERRIT_PROJECT} -f ${WORKSPACE}/${OOM_FOLDER}/override-onap.yaml -o ${WORKSPACE}/onap-azure-cicd/job-results/${GERRIT_PROJECT}/${GERRIT_CHANGE_NUMBER}-${GERRIT_PATCHSET_NUMBER}/deployment")
       if (UPGRADE_STATUS != 0) {
         // For SSH private key authentication, try the sshagent step from the SSH Agent plugin.
         sshagent (credentials: ['github-key-cicd-project']) {
               sh("git add ${WORKSPACE}/onap-azure-cicd/scripts/oom/upgrade-component.sh -d ${WORKSPACE}/${OOM_FOLDER}/kubernetes -r ${HELM_RELEASE_NAME}-${GERRIT_PROJECT} -c ${GERRIT_PROJECT} -f ${WORKSPACE}/${OOM_FOLDER}/override-onap.yaml -o ${WORKSPACE}/onap-azure-cicd/job-results/${GERRIT_PROJECT}/${GERRIT_CHANGE_NUMBER}-${GERRIT_PATCHSET_NUMBER}/deployment/*")
               sh('git commit -m \"Deployment of project: $GERRIT_PROJECT, review: $GERRIT_CHANGE_URL\"')
               sh('git push origin master')
         }
         sshagent (credentials: ['lf-key-onap-bot']) {
               sh(script: "ssh -p $GERRIT_PORT OnapTesterBot@$GERRIT_HOST gerrit review --project $GERRIT_PROJECT --message \'\"WARNING: OOM deployment had issues, check the logs: https://github.com/sebdet/onap-azure-cicd/tree/master/job-results/$GERRIT_PROJECT/$GERRIT_CHANGE_NUMBER-$GERRIT_PATCHSET_NUMBER/deployment\"\' $GERRIT_PATCHSET_REVISION")
         }
       } else {
           sshagent (credentials: ['lf-key-onap-bot']) {
               sh(script: "ssh -p $GERRIT_PORT OnapTesterBot@$GERRIT_HOST gerrit review --project $GERRIT_PROJECT --message \'\"OOM deployment SUCCESSFUL\"\' $GERRIT_PATCHSET_REVISION")
         }
       }
    }
    stage('Run Tests For Component') {
        echo "Testing Component  ${params.GERRIT_PROJECT} with changes from Review ${params.GERRIT_CHANGE_URL}"
        //build job: 'test-component', parameters: [string(name: 'OOM_FOLDER', value: '/var/lib/jenkins/workspace/deploy-component/oom')]
        git(url: 'git@github.com:sebdet/onap-azure-cicd.git',credentialsId: 'github-key-cicd-project', branch: "master")
        TEST_STATUS = sh(returnStatus: true, script: "bash -x onap-azure-cicd/scripts/testing/$GERRIT_PROJECT/run-tests.sh -d /var/lib/jenkins/workspace/deploy-component/oom/kubernetes -o ${WORKSPACE}/onap-azure-cicd/job-results/${GERRIT_PROJECT}/${GERRIT_CHANGE_NUMBER}-${GERRIT_PATCHSET_NUMBER}/tests")
        if (TEST_STATUS != 0) {
            
            // For SSH private key authentication, try the sshagent step from the SSH Agent plugin.
            sshagent (credentials: ['github-key-cicd-project']) {
               sh("git add ${WORKSPACE}/onap-azure-cicd/job-results/${GERRIT_PROJECT}/${GERRIT_CHANGE_NUMBER}-${GERRIT_PATCHSET_NUMBER}/tests/*")
               sh('git commit -m \"Result of project: $GERRIT_PROJECT, review: $GERRIT_CHANGE_URL\"')
                sh('git push origin master')
            }
            sshagent (credentials: ['lf-key-onap-bot']) {
                sh(script: "ssh -p $GERRIT_PORT OnapTesterBot@$GERRIT_HOST gerrit review --project $GERRIT_PROJECT --message \'\"ERROR: OOM tests have FAILED, check the results: https://github.com/sebdet/onap-azure-cicd/tree/master/job-results/$GERRIT_PROJECT/$GERRIT_CHANGE_NUMBER-$GERRIT_PATCHSET_NUMBER/tests\"\' $GERRIT_PATCHSET_REVISION")
            }
            error("Build failed because of Tests failure")
        } else {
            echo "Tests SUCCESSFUL"
            sshagent (credentials: ['lf-key-onap-bot']) {
                sh(script: "ssh -p $GERRIT_PORT OnapTesterBot@$GERRIT_HOST gerrit review --project $GERRIT_PROJECT --message \'\"OOM tests SUCCESSFUL\"\' $GERRIT_PATCHSET_REVISION")
            }
        }
    }
}
