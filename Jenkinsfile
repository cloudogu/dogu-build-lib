#!groovy

// Keep the version in sync with the one used in pom.xml in order to get correct syntax completion.
@Library('github.com/cloudogu/ces-build-lib@63442716')
import com.cloudogu.ces.cesbuildlib.*

String productionReleaseBranch = "master"
String branch = "${env.BRANCH_NAME}"

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
            def scannerHome = tool name: 'sonar-scanner', type: 'hudson.plugins.sonar.SonarRunnerInstallation'
            withSonarQubeEnv {
                sh "git config 'remote.origin.fetch' '+refs/heads/*:refs/remotes/origin/*'"
                gitWithCredentials("fetch --all")
                String parameters = ' -Dsonar.projectKey=dogu-build-lib'
                if (branch == productionReleaseBranch) {
                    echo "This branch has been detected as the " + productionReleaseBranch + " branch."
                    parameters += " -Dsonar.branch.name=${env.BRANCH_NAME}"
                } else if (branch == "develop") {
                    echo "This branch has been detected as the develop branch."
                    parameters += " -Dsonar.branch.name=${env.BRANCH_NAME} -Dsonar.branch.target=" + productionReleaseBranch
                } else if (env.CHANGE_TARGET) {
                    echo "This branch has been detected as a pull request."
                    parameters += " -Dsonar.branch.name=${env.CHANGE_BRANCH}-PR${env.CHANGE_ID} -Dsonar.branch.target=${env.CHANGE_TARGET}"
                } else if (branch.startsWith("feature/")) {
                    echo "This branch has been detected as a feature branch."
                    parameters += " -Dsonar.branch.name=${env.BRANCH_NAME} -Dsonar.branch.target=develop"
                } else {
                    echo "This branch has been detected as a miscellaneous branch."
                    parameters += " -Dsonar.branch.name=${env.BRANCH_NAME} -Dsonar.branch.target=develop"
                }
                sh "${scannerHome}/bin/sonar-scanner ${parameters}"
            }
            timeout(time: 2, unit: 'MINUTES') { // Needed when there is no webhook for example
                def qGate = waitForQualityGate()
                if (qGate.status != 'OK') {
                    unstable("Pipeline unstable due to SonarQube quality gate failure")
                }
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

void gitWithCredentials(String command) {
    withCredentials([usernamePassword(credentialsId: 'cesmarvin', usernameVariable: 'GIT_AUTH_USR', passwordVariable: 'GIT_AUTH_PSW')]) {
        sh(
                script: "git -c credential.helper=\"!f() { echo username='\$GIT_AUTH_USR'; echo password='\$GIT_AUTH_PSW'; }; f\" " + command,
                returnStdout: true
        )
    }
}