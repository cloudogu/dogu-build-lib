/**
 * shellcheck for jenkins-pipelines https://github.com/koalaman/shellcheck
 *
 */
package com.cloudogu.ces.dogubuildlib

/**
 * Run shellcheck on all files in directory.
 * Files are matched recursively.
 *
 */
def call(directoryName) {
    def fileList = sh (script: "find ${direcotyName} -type f -regex .*\\.sh -print", returnStdout: true)
    fileList='"'+fileList.trim().replaceAll('\n','" "')+'"'

    docker.image('koalaman/shellcheck-alpine:stable').inside(){
        sh "/bin/shellcheck ${fileList}"
    }
}
