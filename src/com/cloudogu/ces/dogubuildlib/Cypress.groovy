package com.cloudogu.ces.dogubuildlib

import groovy.json.JsonSlurper

class Cypress {

    def script
    public static def defaultIntegrationTestsConfig = [
            cypressImage         : "cypress/included:7.1.0",
            enableVideo          : true,
            enableScreenshots    : true,
            timeoutInMinutes     : 15,
            additionalDockerArgs : "",
            additionalCypressArgs: ""
    ]
    def config

    Cypress(script, config = [:]) {
        this.script = script
        // Merge default config with the one passed as parameter
        this.config = [:]
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
                        script.sh "cd integrationTests/ && yarn install && yarn cypress run ${cypressRunArgs}"
                    }
        }
    }

    /**
     * Achieves the artifacts from the integration test run. Includes the junit report, videos, and screenshots.
     */
    void archiveVideosAndScreenshots() {
        script.echo "archiving videos and screenshots from test execution..."
        script.junit allowEmptyResults: true, testResults: "integrationTests/cypress-reports/TEST-*.xml"
        if (this.config.enableVideo) {
            script.archiveArtifacts artifacts: "integrationTests/cypress/videos/**/*.mp4", allowEmptyArchive: true
        }
        if (this.config.enableScreenshots) {
            script.archiveArtifacts artifacts: "integrationTests/cypress/screenshots/**/*.png", allowEmptyArchive: true
        }
    }

    /**
     * This method updates the file integrationTests/cypress.json with the currently set admin group.
     *
     * @param vagrant The vagrant instance used to communicate with the EcoSystem. Is required to read the current
     * global admin group from the etcd.
     */
    void updateCypressConfiguration(Vagrant vagrant) {
        def hasCypressJsonFile = script.fileExists('integrationTests/cypress.json')
        def newAdminGroup = vagrant.sshOut "etcdctl get /config/_global/admin_group"
        if (hasCypressJsonFile) {
            def cypressConfig = script.readJSON(file: 'integrationTests/cypress.json')
            def adminGroup = cypressConfig.env.AdminGroup

            script.echo "Changing admin group name in integration test configuration (cypress.json)"
            def cypressConfigString = script.readFile(file: 'integrationTests/cypress.json')
            cypressConfigString = cypressConfigString.replaceAll(adminGroup, newAdminGroup)
            script.writeFile(file: 'integrationTests/cypress.json', text: cypressConfigString)
        } else {
            def adminGroupJson = "{\"AdminGroup\":  \"${newAdminGroup}\"}"
            script.writeFile(file: 'integrationTests/cypress.env.json', text: adminGroupJson)
        }
    }

    /**
     * Performs work before the actual integration tests are performed.
     * Currently removes all previous videos, screenshots, and reports.
     */
    void preTestWork() {
        script.echo "cleaning up previous test results..."
        script.sh "rm -rf integrationTests/cypress/videos"
        script.sh "rm -rf integrationTests/cypress/screenshots"
        script.sh "rm -rf integrationTests/cypress-reports"
    }

    String writePasswd() {
        def passwdPath = ".jenkins/etc/passwd"

        // e.g. "jenkins:x:1000:1000::/home/jenkins:/bin/sh"
        def etcPasswd = readJenkinsUserFromEtcPasswd()
        String passwd = readJenkinsUserFromEtcPasswdCutOffAfterGroupId(etcPasswd) + ":${script.pwd()}:/bin/sh"

        script.writeFile file: passwdPath, text: passwd
        return passwdPath
    }

    /**
     * Return from /etc/passwd (for user that executes build) only username, pw, UID and GID.
     * e.g. "jenkins:x:1000:1000:"
     */
    String readJenkinsUserFromEtcPasswdCutOffAfterGroupId(def etcPasswd) {
        def regexMatchesUntilFourthColon = "(.*?:){4}"

        // Storing matcher in a variable might lead to java.io.NotSerializableException: java.util.regex.Matcher
        if (!(etcPasswd =~ regexMatchesUntilFourthColon)) {
            script.error "/etc/passwd entry for current user does not match user:x:uid:gid:"
            return ""
        }
        return (etcPasswd =~ regexMatchesUntilFourthColon)[0][0]
    }

    String readJenkinsUserFromEtcPasswd() {
        // Query current jenkins user string, e.g. "jenkins:x:1000:1000:Jenkins,,,:/home/jenkins:/bin/bash"
        // An alternative (dirtier) approach: https://github.com/cloudogu/docker-golang/blob/master/Dockerfile
        def userName = script.sh(returnStdout: true, script: "whoami").trim()
        String jenkinsUserFromEtcPasswd = script.sh(returnStdout: true, script: "cat /etc/passwd | grep $userName").trim()

        if (jenkinsUserFromEtcPasswd.isEmpty()) {
            script.error "Unable to parse user jenkins from /etc/passwd."
        }
        return jenkinsUserFromEtcPasswd
    }
}
