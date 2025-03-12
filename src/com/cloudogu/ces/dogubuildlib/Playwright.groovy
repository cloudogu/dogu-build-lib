package com.cloudogu.ces.dogubuildlib

class Playwright {

    def script
    public static def defaultIntegrationTestsConfig = [
            playwrightImage      : "mcr.microsoft.com/playwright:v1.49.1-noble",
            enableVideo          : true,
            enableScreenshots    : true,
            timeoutInMinutes     : 15,
            additionalDockerArgs : "",
            additionalCypressArgs: ""
    ]
    def config

    Playwright(script, config = [:]) {
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
            String externalIP = ecoSystem.getExternalIP()

            // Create args for the docker run
            String dockerArgs = "--ipc=host"
            dockerArgs <<= " -e BASE_URL=https://${externalIP}"
            dockerArgs <<= " --entrypoint=''"
            dockerArgs <<= " " + this.config.additionalDockerArgs

            script.docker.image(this.config.playwrightImage)
                    .mountJenkinsUser()
                    .inside(dockerArgs) {
                        dir('playwright') {
                            sh 'npm ci'
                            sh 'npx bddgen'
                            sh "npx playwright test --reporter=junit"
                            junit allowEmptyResults: true, testResults: "test-results/results.xml"
                            archiveArtifacts artifacts: "../test-results/**/video.webm", allowEmptyArchive: true
                        }
                    }
        }
    }

}
