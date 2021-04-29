package com.cloudogu.ces.dogubuildlib

class Cypress {

    def script
    EcoSystem ecoSystem
    String version
    boolean recordVideos
    boolean recordScreenshots

    Cypress(script, EcoSystem ecoSystem) {
        this.script = script
        this.version = "cypress/included:7.1.0"
        this.recordVideos = true
        this.recordScreenshots = true
        this.ecoSystem = ecoSystem
    }

    /**
     * Sets the version of the used cypress image as docker image format, e.g., default is `cypress/included:7.1.0`
     * @param version Version to use
     */
    void setCypressImage(String version) {
        this.version = version
    }

    /**
     * Determine whether cypress should record videos or not.
     * @param record true, when videos should be recorded.
     */
    void setRecordVideos(boolean record) {
        this.recordVideos = record
    }

    /**
     * Determine whether cypress should records screenshots when a test failed.
     * @param record true, when screenshots should be recorded.
     */
    void setRecordScreenshotsOnFailure(boolean record) {
        this.recordScreenshots = record
    }

    /**
     * Runs all the integration tests with cypress.
     */
    void runIntegrationTests(int timeoutInMinutes = 15, ArrayList<String> additionalDockerArgs = [], ArrayList<String> additionalCypressArgs = []) {

        script.timeout(time: timeoutInMinutes, unit: 'MINUTES') {
            // Yarn requires a user to download their dependencies and cacheable content.
            String passwdPath = writePasswd()
            String externalIP = this.ecoSystem.externalIP

            // Create args for the docker run
            String dockerArgs = "--ipc=host"
            dockerArgs <<= " -e CYPRESS_BASE_URL=https://${externalIP}"
            dockerArgs <<= " --entrypoint=''"
            dockerArgs <<= " -v ${script.pwd()}/${passwdPath}:/etc/passwd:ro"
            additionalDockerArgs.each { value ->
                dockerArgs <<= value
            }
            script.docker.image(this.version)
                    .inside(dockerArgs) {
                        // Create args for the cypress run
                        def runID = UUID.randomUUID().toString()
                        String cypressRunArgs = "-q"
                        cypressRunArgs <<= " --headless"
                        cypressRunArgs <<= " --config screenshotOnRunFailure=" + this.recordScreenshots
                        cypressRunArgs <<= " --config video=" + this.recordVideos
                        cypressRunArgs <<= " --reporter junit"
                        cypressRunArgs <<= " --reporter-options mochaFile=cypress-reports/TEST-${runID}-[hash].xml"
                        additionalCypressArgs.each { value ->
                            cypressRunArgs <<= value
                        }
                        script.sh "cd integrationTests/ && yarn install && yarn cypress run ${cypressRunArgs}"
                    }
        }
    }

    /**
     * Achieves the artifacts from the integration test run. Includes the junit report, videos, and screenshots.
     */
    void archiveVideosAndScreenshots() {
        script.echo "archiving videos and screenshots from test execution..."
        script.junit allowEmptyResults: true, testResults: 'integrationTests/cypress-reports/TEST-*.xml'
        if (this.recordVideos) {
            script.archiveArtifacts artifacts:"integrationTests/cypress/videos/**/*.mp4", allowEmptyArchive: true
        }
        if (this.recordScreenshots) {
            script.archiveArtifacts artifacts:"integrationTests/cypress/screenshots/**/*.png", allowEmptyArchive: true
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

    private String writePasswd() {
        def passwdPath = '.jenkins/etc/passwd'

        // e.g. "jenkins:x:1000:1000::/home/jenkins:/bin/sh"
        String passwd = readJenkinsUserFromEtcPasswdCutOffAfterGroupId() + ":${script.pwd()}:/bin/sh"

        script.writeFile file: passwdPath, text: passwd
        return passwdPath
    }

    /**
     * Return from /etc/passwd (for user that executes build) only username, pw, UID and GID.
     * e.g. "jenkins:x:1000:1000:"
     */
    private String readJenkinsUserFromEtcPasswdCutOffAfterGroupId() {
        def regexMatchesUntilFourthColon = '(.*?:){4}'

        def etcPasswd = readJenkinsUserFromEtcPasswd()

        // Storing matcher in a variable might lead to java.io.NotSerializableException: java.util.regex.Matcher
        if (!(etcPasswd =~ regexMatchesUntilFourthColon)) {
            script.error '/etc/passwd entry for current user does not match user:x:uid:gid:'
        }
        return (etcPasswd =~ regexMatchesUntilFourthColon)[0][0]
    }

    private String readJenkinsUserFromEtcPasswd() {
        // Query current jenkins user string, e.g. "jenkins:x:1000:1000:Jenkins,,,:/home/jenkins:/bin/bash"
        // An alternative (dirtier) approach: https://github.com/cloudogu/docker-golang/blob/master/Dockerfile
        def userName = script.sh(returnStdout: true, script: "whoami").trim()
        String jenkinsUserFromEtcPasswd = script.sh(returnStdout: true, script: "cat /etc/passwd | grep $userName").trim()

        if (jenkinsUserFromEtcPasswd.isEmpty()) {
            script.error 'Unable to parse user jenkins from /etc/passwd.'
        }
        return jenkinsUserFromEtcPasswd
    }
}
