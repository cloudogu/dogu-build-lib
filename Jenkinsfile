#!groovy

// Keep the version in sync with the one used in pom.xml in order to get correct syntax completion.
@Library('github.com/cloudogu/ces-build-lib@63442716')
import com.cloudogu.ces.cesbuildlib.*

node('docker') {

    properties([
            // Keep only the last 10 build to preserve space
            buildDiscarder(logRotator(numToKeepStr: '10')),
            // Don't run concurrent builds for a branch, because they use the same workspace directory
            disableConcurrentBuilds()
    ])

    Git git = new Git(this)

    catchError {

        stage('Checkout') {
            checkout scm
            /* Don't remove folders starting in "." like
             * .m2 (maven)
             * .npm
             * .cache, .local (bower)
             */
            git.clean('".*/"')
        }

        Maven mvn = setupMavenBuild()

        stage('Build') {
            // Run the maven build
            mvn 'clean install -DskipTests'
            archive 'target/*.jar'
        }

        stage('Unit Test') {
            mvn 'test'
        }

        stage('SonarQube') {

            def sonarQube = new SonarQube(this, 'ces-sonar')
            sonarQube.updateAnalysisResultOfPullRequestsToGitHub('sonarqube-gh-token')

            sonarQube.analyzeWith(mvn)

            if (!sonarQube.waitForQualityGateWebhookToBeCalled()) {
                currentBuild.result = 'UNSTABLE'
            }
        }
    }

    // Archive Unit and integration test results, if any
    junit allowEmptyResults: true, testResults: '**/target/failsafe-reports/TEST-*.xml,**/target/surefire-reports/TEST-*.xml'

    mailIfStatusChanged(git.commitAuthorEmail)
}

Maven setupMavenBuild() {
    Maven mvn = new MavenInDocker(this, "3.6.0-jdk-8")


    // run SQ analysis in specific project for each branch
    mvn.additionalArgs = "-Dsonar.branch=${env.BRANCH_NAME}" +
                         // Workaround SUREFIRE-1588 on Debian/Ubuntu. Should be fixed in Surefire 3.0.0
                         ' -DargLine="-Djdk.net.URLClassPath.disableClassPathURLCheck=true"'

    return mvn
}
