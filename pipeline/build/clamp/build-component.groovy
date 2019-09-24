def buildComponent() {
    withMaven(
        // Maven installation declared in the Jenkins "Global Tool Configuration"
        maven: 'maven-3.6.2',
        // Maven settings.xml file defined with the Jenkins Config File Provider Plugin
        // We recommend to define Maven settings.xml globally at the folder level using 
        // navigating to the folder configuration in the section "Pipeline Maven Configuration / Override global Maven configuration"
        // or globally to the entire master navigating to  "Manage Jenkins / Global Tools Configuration"
        mavenSettingsFilePath: '/var/lib/jenkins/maven-settings.xml'
    ) {
      // Run the maven build
      sh "mvn clean install -P docker -Dmaven.test.skip=true"
    }
}

return this
