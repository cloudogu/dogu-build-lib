package com.cloudogu.ces.dogubuildlib

class Playwright extends TestFramework {

    public static def defaultIntegrationTestsConfig = [
            playwrightImage      : "mcr.microsoft.com/playwright:v1.49.1-noble",
            testDirectory        : "./integrationTests/playwright",
            enableVideo          : true,
            enableScreenshots    : true,
            timeoutInMinutes     : 15,
            additionalDockerArgs : "",
            additionalCypressArgs: ""
    ]

    Playwright(script, config = [:]) {
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
            String passwdPath = writePasswd()
            String externalIP = ecoSystem.getExternalIP()

            // Create args for the docker run
            String dockerArgs = "--ipc=host"
            dockerArgs <<= " -e BASE_URL=https://${externalIP}"
            dockerArgs <<= " --entrypoint=''"
            dockerArgs <<= " -v ${script.pwd()}/${passwdPath}:/etc/passwd:ro"
            dockerArgs <<= " " + this.config.additionalDockerArgs

            script.docker.image(this.config.playwrightImage)
                    .inside(dockerArgs) {
                        script.sh 'npm ci'
                        script.dir(this.config.testDirectory) {
                            script.sh 'npx bddgen'
                            script.sh "npx playwright test --reporter=junit"
                            script.junit allowEmptyResults: true, testResults: "./test-results/results.xml"
                        }
                    }
        }
    }

    /**
     * Achieves the artifacts from the integration test run. Includes the junit report, videos, and screenshots.
     */
    void archiveVideosAndScreenshots() {
        script.echo "archiving videos and screenshots from test execution..."
        script.dir("${this.config.testDirectory}/test-results") {
            script.sh '''
                    find . -type f -exec sh -c 'mv "$1" "$(dirname "$1")/$(basename "$(dirname "$1")")_$(basename "$1")"' _ {} \\;
                '''
            if (this.config.enableVideo) {
                script.archiveArtifacts artifacts: "**/*.webm", allowEmptyArchive: true
            }
            if (this.config.enableScreenshots) {
                script.archiveArtifacts artifacts: "**/*.png", allowEmptyArchive: true
            }
        }
    }

    /**
     * Performs work before the actual integration tests are performed.
     * Currently removes all previous videos, screenshots, and reports.
     */
    void preTestWork() {
        script.echo "cleaning up previous test results..."
        script.sh "rm -rf ${this.config.testResultsDirectory}"
    }

}
