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
      
        sh("bash -x onap-azure-cicd/scripts/docker/create-registry.sh -d $CERTIFICATE_FOLDER -c $CERTIFICATE_FILENAME -k $KEY_FILENAME")
        def buildScript = load "onap-azure-cicd/pipeline/build/${params.GERRIT_PROJECT}/build-component.groovy"
        buildScript.buildComponent(params.GERRIT_PROJECT)
        sh("bash -x onap-azure-cicd/scripts/docker/tag-images.sh -p $ONAP_DOCKER_PREFIX -n $REGISTRY_DOCKER_PREFIX -r $REGISTRY_HOST -v $GERRIT_CHANGE_NUMBER-$GERRIT_PATCHSET_NUMBER")
    }
    stage('Deploy New Docker For Component') {
        echo 'Skipping Deploy for now'
        echo "Deploying Component ${params.GERRIT_PROJECT} on Azure OOM lab"
        //  build job: 'deploy-component', parameters: [string(name: 'OOM_FOLDER', value: 'oom'), string(name: 'COMPONENT_NAME', value: env.GERRIT_PROJECT), string(name: 'ONAP_DOCKER_PREFIX', value: 'onap'), string(name: 'REGISTRY_DOCKER_PREFIX', value: 'new-onap'), string(name: 'GERRIT_REVIEW', value: env.GERRIT_CHANGE_NUMBER), string(name: 'GERRIT_PATCHSET', value: env.GERRIT_PATCHSET_NUMBER), string(name: 'HELM_RELEASE_NAME', value: 'cc697w-tdpi'), string(name: 'REGISTRY_DOCKER', value: 'onapci.westus2.cloudapp.azure.com:443')]
    }
    stage('Run Tests For Component') {
        echo "Testing Component  ${params.GERRIT_PROJECT} with changes from Review ${params.GERRIT_CHANGE_URL}"
        //build job: 'test-component', parameters: [string(name: 'OOM_FOLDER', value: '/var/lib/jenkins/workspace/deploy-component/oom')]
        git(url: 'git@github.com:sebdet/onap-azure-cicd.git',credentialsId: 'github-key-cicd-project', branch: "master")
        TEST_STATUS = sh(returnStatus: true, script: "bash scripts/testing/$GERRIT_PROJECT/run-tests.sh -d /var/lib/jenkins/workspace/deploy-component/oom/kubernetes -o ./test-results/$GERRIT_CHANGE_NUMBER-$GERRIT_PATCHSET_NUMBER")
        if (TEST_STATUS != 0) {
            
            // For SSH private key authentication, try the sshagent step from the SSH Agent plugin.
            sshagent (credentials: ['github-key-cicd-project']) {
                sh("git add ./test-results/$GERRIT_PROJECT/$GERRIT_CHANGE_NUMBER-$GERRIT_PATCHSET_NUMBER/*")
                sh('git commit -m \"Result of project: $GERRIT_PROJECT, review: $GERRIT_CHANGE_URL\"')
                sh('git push origin master')
            }
            sshagent (credentials: ['lf-key-onap-bot']) {
                sh(script: "ssh -p $GERRIT_PORT OnapTesterBot@$GERRIT_HOST gerrit review --project $GERRIT_PROJECT --message \'\"Check result here: https://github.com/sebdet/onap-azure-cicd/tree/master/test-results/$GERRIT_CHANGE_NUMBER-$GERRIT_PATCHSET_NUMBER\"\' $GERRIT_PATCHSET_REVISION")
            }
            error("Build failed because of Tests failure")
        }

    }
    
}
