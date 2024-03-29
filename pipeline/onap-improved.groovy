node {
    currentBuild.displayName = "${params.GERRIT_PROJECT} (${params.GERRIT_CHANGE_NUMBER}-${GERRIT_PATCHSET_NUMBER})"
    currentBuild.description = "Review URL: ${params.GERRIT_CHANGE_URL}"

    def mvnHome

    stage('Clear workspace') {
        deleteDir()
    }
    stage('Clone pipeline script') {
        checkout([$class: 'GitSCM',
            branches: [[name: 'master']],
            doGenerateSubmoduleConfigurations: false,
            extensions: [
                [$class: 'RelativeTargetDirectory',
                    relativeTargetDir: 'onap-azure-cicd']
            ],
            submoduleCfg: [],
            userRemoteConfigs: [
                [credentialsId: 'github-key-cicd-project',
                    url: 'git@github.com:sebdet/onap-azure-cicd.git',
                    name: 'pipeline_project']
            ]])
    }
    stage('Extract info from gerrit message') {
        sh('echo $GERRIT_EVENT_COMMENT_TEXT > $WORKSPACE/gerrit-message.log')
        env.OOM_REFSPEC = sh(returnStdout: true, script: "bash onap-azure-cicd/scripts/pipeline/extract-oom-gerrit-message.sh -f $WORKSPACE/gerrit-message.log -k /testme")
        echo "OOM Patch: ${env.OOM_REFSPEC}"
        if (env.OOM_REFSPEC) {
            sshagent (credentials: ['lf-key-onap-bot']) {
                sh(script: "ssh -p $GERRIT_PORT OnapTesterBot@$GERRIT_HOST gerrit review --project $GERRIT_PROJECT --message \'\"INFO: Using a specific review for OOM: $OOM_REFSPEC\"\' $GERRIT_PATCHSET_REVISION")
            }
        }
    }
    stage('Clone Component Code & OOM') {
        echo "Cloning everything for ${params.GERRIT_PROJECT}"
        parallel (
                "Cloning component code": {
                    checkout([$class: 'GitSCM',
                        branches: [[name: 'FETCH_HEAD']],
                        doGenerateSubmoduleConfigurations: false,
                        extensions: [
                            [$class: 'RelativeTargetDirectory',
                                relativeTargetDir: '${GERRIT_PROJECT}']
                        ],
                        submoduleCfg: [],
                        userRemoteConfigs: [
                            [credentialsId: 'lf-key-onap-bot',
                                refspec: '${GERRIT_REFSPEC}',
                                url: '${GERRIT_SCHEME}://OnapTesterBot@${GERRIT_HOST}:${GERRIT_PORT}/${GERRIT_PROJECT}',
                                name: 'onap_project']
                        ]])
                },
                "Cloning OOM": {
                    sshagent (credentials: ['lf-key-onap-bot']) {

                        sh('git clone --recursive \"$GERRIT_SCHEME://OnapTesterBot@$GERRIT_HOST:$GERRIT_PORT/oom\" $OOM_FOLDER')
                        sh('git --git-dir=${WORKSPACE}/$OOM_FOLDER/.git --work-tree=${WORKSPACE}/$OOM_FOLDER fetch \"$GERRIT_SCHEME://OnapTesterBot@$GERRIT_HOST:$GERRIT_PORT/oom\" $OOM_REFSPEC && git --git-dir=${WORKSPACE}/$OOM_FOLDER/.git --work-tree=${WORKSPACE}/$OOM_FOLDER checkout FETCH_HEAD')
                    }
                }
                )
    }
    stage ('Build Component Code & OOM') {
        parallel (
                "Purge and create docker registry": {
                    echo "Creating Docker registry on ${params.REGISTRY_HOST}, certif: ${params.CERTIFICATE_FOLDER}, key: ${params.KEY_FILENAME}"
                    sh("bash -x onap-azure-cicd/scripts/docker/create-registry.sh -d $CERTIFICATE_FOLDER -c $CERTIFICATE_FILENAME -k $KEY_FILENAME")
                },
                "Build docker images": {
                    echo "Building Component ${params.GERRIT_PROJECT}"
                    def buildScript = load "onap-azure-cicd/pipeline/build/${params.GERRIT_PROJECT}/build-component.groovy"

                    BUILD_STATUS = buildScript.buildComponent(params.GERRIT_PROJECT)
                    
                    if (BUILD_STATUS != 0) { 
                        sshagent (credentials: ['github-key-cicd-project']) {
                            sh("mkdir -p /${WORKSPACE}/onap-azure-cicd/job-results/${GERRIT_PROJECT}/${GERRIT_CHANGE_NUMBER}-${GERRIT_PATCHSET_NUMBER}/build/")
                            sh("mv ${WORKSPACE}/build.log ${WORKSPACE}/onap-azure-cicd/job-results/${GERRIT_PROJECT}/${GERRIT_CHANGE_NUMBER}-${GERRIT_PATCHSET_NUMBER}/build")
                            sh("git --git-dir=${WORKSPACE}/onap-azure-cicd/.git --work-tree=${WORKSPACE}/onap-azure-cicd add ${WORKSPACE}/onap-azure-cicd/job-results/${GERRIT_PROJECT}/${GERRIT_CHANGE_NUMBER}-${GERRIT_PATCHSET_NUMBER}/build/*")
                            sh('git --git-dir=${WORKSPACE}/onap-azure-cicd/.git --work-tree=${WORKSPACE}/onap-azure-cicd commit -m \"Build of project: $GERRIT_PROJECT, review: $GERRIT_CHANGE_URL\"')
                            sh('git --git-dir=${WORKSPACE}/onap-azure-cicd/.git --work-tree=${WORKSPACE}/onap-azure-cicd push pipeline_project HEAD:master')
                            }
                    
                        sshagent (credentials: ['lf-key-onap-bot']) {
                            sh(script: "ssh -p $GERRIT_PORT OnapTesterBot@$GERRIT_HOST gerrit review --project $GERRIT_PROJECT --message \'\"INFO: Build logs available, check the logs: https://github.com/sebdet/onap-azure-cicd/tree/master/job-results/$GERRIT_PROJECT/$GERRIT_CHANGE_NUMBER-$GERRIT_PATCHSET_NUMBER/build\"\' $GERRIT_PATCHSET_REVISION")
                            }
                    }  else {
                        sshagent (credentials: ['lf-key-onap-bot']) {
                            sh(script: "ssh -p $GERRIT_PORT OnapTesterBot@$GERRIT_HOST gerrit review --project $GERRIT_PROJECT --message \'\"$GERRIT_PROJECT Docker images SUCCESSFULLY built on AZURE\"\' $GERRIT_PATCHSET_REVISION")
                            }
                       }
                },
                "Build OOM": {
                    sh("make -C $OOM_FOLDER/kubernetes/ all")
                    sh("bash -x ${WORKSPACE}/onap-azure-cicd/scripts/oom/create-image-override.sh -d ${WORKSPACE}/${OOM_FOLDER}/kubernetes/${GERRIT_PROJECT} -c ${GERRIT_PROJECT} -p ${ONAP_DOCKER_PREFIX} -r ${REGISTRY_HOST} -n ${REGISTRY_DOCKER_PREFIX} -v ${GERRIT_CHANGE_NUMBER}-${GERRIT_PATCHSET_NUMBER} -o ${WORKSPACE}/${OOM_FOLDER}/override-onap.yaml")
                    echo("Override file content is:")
                    sh("cat ${WORKSPACE}/${OOM_FOLDER}/override-onap.yaml");
                }
                )
    }

    stage('Push docker images to registry') {
        echo "Retagging docker images with name prefix ${params.ONAP_DOCKER_PREFIX}/***:latest to name prefix ${params.REGISTRY_DOCKER_PREFIX}/***:${params.GERRIT_CHANGE_NUMBER}-${params.GERRIT_PATCHSET_NUMBER}"
        sh("bash -x onap-azure-cicd/scripts/docker/tag-images.sh -p $ONAP_DOCKER_PREFIX -n $REGISTRY_DOCKER_PREFIX -r $REGISTRY_HOST -v $GERRIT_CHANGE_NUMBER-$GERRIT_PATCHSET_NUMBER")
    }

    stage('Deploy docker on OOM') {
        echo "Deploying Component ${params.GERRIT_PROJECT} on Azure OOM lab"
        //  build job: 'deploy-component', parameters: [string(name: 'OOM_FOLDER', value: 'oom'), string(name: 'COMPONENT_NAME', value: env.GERRIT_PROJECT), string(name: 'ONAP_DOCKER_PREFIX', value: 'onap'), string(name: 'REGISTRY_DOCKER_PREFIX', value: 'new-onap'), string(name: 'GERRIT_REVIEW', value: env.GERRIT_CHANGE_NUMBER), string(name: 'GERRIT_PATCHSET', value: env.GERRIT_PATCHSET_NUMBER), string(name: 'HELM_RELEASE_NAME', value: 'cc697w-tdpi'), string(name: 'REGISTRY_DOCKER', value: 'onapci.westus2.cloudapp.azure.com:443')]
        NBR_POD_FAILING = sh(returnStatus: true, script: "bash -x ${WORKSPACE}/onap-azure-cicd/scripts/oom/upgrade-component.sh -d ${WORKSPACE}/${OOM_FOLDER}/kubernetes -r ${HELM_RELEASE_NAME}-${GERRIT_PROJECT} -c ${GERRIT_PROJECT} -f ${WORKSPACE}/${OOM_FOLDER}/override-onap.yaml -o ${WORKSPACE}/onap-azure-cicd/job-results/${GERRIT_PROJECT}/${GERRIT_CHANGE_NUMBER}-${GERRIT_PATCHSET_NUMBER}/deployment")
        if (NBR_POD_FAILING > 0) {
            // For SSH private key authentication, try the sshagent step from the SSH Agent plugin.
            sshagent (credentials: ['github-key-cicd-project']) {
                sh("git --git-dir=${WORKSPACE}/onap-azure-cicd/.git --work-tree=${WORKSPACE}/onap-azure-cicd add ${WORKSPACE}/onap-azure-cicd/job-results/${GERRIT_PROJECT}/${GERRIT_CHANGE_NUMBER}-${GERRIT_PATCHSET_NUMBER}/deployment/*")
                sh('git --git-dir=${WORKSPACE}/onap-azure-cicd/.git --work-tree=${WORKSPACE}/onap-azure-cicd commit -m \"Deployment of project: $GERRIT_PROJECT, review: $GERRIT_CHANGE_URL\"')
                sh('git --git-dir=${WORKSPACE}/onap-azure-cicd/.git --work-tree=${WORKSPACE}/onap-azure-cicd push pipeline_project HEAD:master')
            }
            sshagent (credentials: ['lf-key-onap-bot']) {
                sh(script: "ssh -p $GERRIT_PORT OnapTesterBot@$GERRIT_HOST gerrit review --project $GERRIT_PROJECT --message \'\"WARNING: OOM deployment issues($NBR_POD_FAILING Pods failing), check the logs: https://github.com/sebdet/onap-azure-cicd/tree/master/job-results/$GERRIT_PROJECT/$GERRIT_CHANGE_NUMBER-$GERRIT_PATCHSET_NUMBER/deployment\"\' $GERRIT_PATCHSET_REVISION")
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
        //git(url: 'git@github.com:sebdet/onap-azure-cicd.git',credentialsId: 'github-key-cicd-project', branch: "master")
        TEST_STATUS = sh(returnStatus: true, script: "bash -x onap-azure-cicd/scripts/testing/$GERRIT_PROJECT/run-tests.sh -d ${WORKSPACE}/${OOM_FOLDER}/kubernetes -o ${WORKSPACE}/onap-azure-cicd/job-results/${GERRIT_PROJECT}/${GERRIT_CHANGE_NUMBER}-${GERRIT_PATCHSET_NUMBER}/tests")
        if (TEST_STATUS != 0) {

            // For SSH private key authentication, try the sshagent step from the SSH Agent plugin.
            sshagent (credentials: ['github-key-cicd-project']) {
                sh("git --git-dir=${WORKSPACE}/onap-azure-cicd/.git --work-tree=${WORKSPACE}/onap-azure-cicd add ${WORKSPACE}/onap-azure-cicd/job-results/${GERRIT_PROJECT}/${GERRIT_CHANGE_NUMBER}-${GERRIT_PATCHSET_NUMBER}/tests/*")
                sh('git --git-dir=${WORKSPACE}/onap-azure-cicd/.git --work-tree=${WORKSPACE}/onap-azure-cicd commit -m \"Result of project: $GERRIT_PROJECT, review: $GERRIT_CHANGE_URL\"')
                sh('git --git-dir=${WORKSPACE}/onap-azure-cicd/.git --work-tree=${WORKSPACE}/onap-azure-cicd push pipeline_project HEAD:master')
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
    stage('Rollback the lab') {
           echo "Rollbacking the lab"
           sh(script: "bash -x ${WORKSPACE}/onap-azure-cicd/scripts/oom/rollback-component.sh -d ${WORKSPACE}/${OOM_FOLDER}/kubernetes -r ${HELM_RELEASE_NAME}-${GERRIT_PROJECT}")
    }
}
