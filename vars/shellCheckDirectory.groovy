/**
 * shellcheck for jenkins-pipelines https://github.com/koalaman/shellcheck
 *
 */
package com.cloudogu.ces.dogubuildlib

/**
 * Run shellcheck on all files in directory
 * subdirectories are not checked
 *
 */
def call(directoryName) {
    docker.image('koalaman/shellcheck-alpine:stable').inside(){
        sh "/bin/shellcheck ./${directoryName}/*.sh"
    }
}