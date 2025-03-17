package com.cloudogu.ces.dogubuildlib

class Cypress extends TestFramework{

    public static def defaultIntegrationTestsConfig = [
            cypressImage         : "cypress/included:7.1.0",
            testDirectory        : "./integrationTests/cypress",
            enableVideo          : true,
            enableScreenshots    : true,
            timeoutInMinutes     : 15,
            additionalDockerArgs : "",
            additionalCypressArgs: ""
    ]

    Cypress(script, config = [:]) {
        super(script)
        // Merge default config with the one passed as parameter
        this.config << defaultIntegrationTestsConfig
        this.config << config
    }

    /**
     * Runs all the integration tests with cypress.
     */
    void runIntegrationTests(EcoSystem ecoSystem) {
        script.timeout(time: this.config.timeoutInMinutes, unit: "MINUTES") {
            // Yarn requires a user to download their dependencies and cacheable content.
            String passwdPath = writePasswd()
            String externalIP = ecoSystem.getExternalIP()

            // Create args for the docker run
            String dockerArgs = "--ipc=host"
            dockerArgs <<= " -e CYPRESS_BASE_URL=https://${externalIP}"
            dockerArgs <<= " --entrypoint=''"
            dockerArgs <<= " -v ${script.pwd()}/${passwdPath}:/etc/passwd:ro"
            dockerArgs <<= " " + this.config.additionalDockerArgs
            script.docker.image(this.config.cypressImage)
                    .inside(dockerArgs) {
                        // Create args for the cypress run
                        def runID = UUID.randomUUID().toString()
                        String cypressRunArgs = "-q"
                        cypressRunArgs <<= " --headless"
                        cypressRunArgs <<= " --config screenshotOnRunFailure=" + this.config.enableScreenshots
                        cypressRunArgs <<= " --config video=" + this.config.enableVideo
                        cypressRunArgs <<= " --reporter junit"
                        cypressRunArgs <<= " --reporter-options mochaFile=cypress-reports/TEST-${runID}-[hash].xml"
                        cypressRunArgs <<= " " + this.config.additionalCypressArgs
                        script.sh "cd ${this.config.testDirectory}/ && yarn install && yarn cypress run ${cypressRunArgs}"
                    }
        }
    }

    /**
     * Achieves the artifacts from the integration test run. Includes the junit report, videos, and screenshots.
     */
    void archiveVideosAndScreenshots() {
        script.echo "archiving videos and screenshots from test execution..."
        script.junit allowEmptyResults: true, testResults: "${this.config.testDirectory}/cypress-reports/TEST-*.xml"
        if (this.config.enableVideo) {
            script.archiveArtifacts artifacts: "${this.config.testDirectory}/cypress/videos/**/*.mp4", allowEmptyArchive: true
        }
        if (this.config.enableScreenshots) {
            script.archiveArtifacts artifacts: "${this.config.testDirectory}/cypress/screenshots/**/*.png", allowEmptyArchive: true
        }
    }

    /**
     * This method updates the file integrationTests/cypress.json with the currently set admin group.
     *
     * @param vagrant The vagrant instance used to communicate with the EcoSystem. Is required to read the current
     * global admin group from the etcd.
     */
    void updateCypressConfiguration(Vagrant vagrant) {
        def hasCypressJsonFile = script.fileExists("${this.config.testDirectory}/cypress.json")
        def newAdminGroup = vagrant.sshOut "etcdctl get /config/_global/admin_group"
        if (hasCypressJsonFile) {
            def cypressConfig = script.readJSON(file: "${this.config.testDirectory}/cypress.json")
            def adminGroup = cypressConfig.env.AdminGroup

            script.echo "Changing admin group name in integration test configuration (cypress.json)"
            def cypressConfigString = script.readFile(file: "${this.config.testDirectory}/cypress.json")
            cypressConfigString = cypressConfigString.replaceAll(adminGroup, newAdminGroup)
            script.writeFile(file: "${this.config.testDirectory}/cypress.json", text: cypressConfigString)
        } else {
            def adminGroupJson = "{\"AdminGroup\":  \"${newAdminGroup}\"}"
            script.writeFile(file: "${this.config.testDirectory}/cypress.env.json", text: adminGroupJson)
        }
    }

    /**
     * Performs work before the actual integration tests are performed.
     * Currently removes all previous videos, screenshots, and reports.
     */
    void preTestWork() {
        script.echo "cleaning up previous test results..."
        script.sh "rm -rf ${this.config.testDirectory}/cypress/videos"
        script.sh "rm -rf ${this.config.testDirectory}/cypress/screenshots"
        script.sh "rm -rf ${this.config.testDirectory}/cypress-reports"
    }
}
