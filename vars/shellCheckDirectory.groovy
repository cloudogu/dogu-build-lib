/**
 * shellcheck for jenkins-pipelines https://github.com/koalaman/shellcheck
 *
 */
package com.cloudogu.ces.dogubuildlib

/**
 * run shellcheck on all files in directory
 *
 */
def call(directoryName) {
    def allScriptsInDirectory = sh (script: "find ${directoryName} -type f -name \"*.sh\"", returnStdout: true)
    fileList='"'+allScriptsInDirectory.trim().replaceAll('\n','" "')+'"'
    shellcheckInsideDocker(fileList)
}

/*
* run the alpine based shellcheck image
* note: we encountered some problems while using the minified docker image
*/
private def shellcheckInsideDocker(fileList){
    docker.image('koalaman/shellcheck-alpine:stable').inside(){
        sh "/bin/shellcheck ${fileList}"
    }
}
