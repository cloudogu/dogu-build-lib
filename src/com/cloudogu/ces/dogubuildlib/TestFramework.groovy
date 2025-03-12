package com.cloudogu.ces.dogubuildlib

class TestFramework {

    def script
    def config

    TestFramework(script) {
        this.script = script
        // Merge default config with the one passed as parameter
        this.config = [:]
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
