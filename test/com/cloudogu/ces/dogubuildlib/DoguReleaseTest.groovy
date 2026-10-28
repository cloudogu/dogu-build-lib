package com.cloudogu.ces.dogubuildlib

import groovy.json.JsonSlurper
import org.junit.Test

class DoguReleaseTest {

    private ScriptMock scriptMock(String remoteUrl) {
        def script = new ScriptMock()
        script.env.BRANCH_NAME = "release/v2.5.0-2"
        script.expectedShRetValueForScript.put("jq -r .Name dogu.json", "official/cockpit\n")
        script.expectedShRetValueForScript.put("git remote get-url origin", remoteUrl + "\n")
        return script
    }

    @Test
    void postsNameVersionAndChangelogLink() {
        def script = scriptMock("https://github.com/cloudogu/cockpit.git")

        new DoguRelease(script).notifyWebhook()

        assert script.actualStringArgs.credentialsId == "sos-sw-release-webhook-url"
        String text = new JsonSlurper().parseText(script.actualHttpRequestArgs[0].requestBody).text
        assert text.contains("*<https://github.com/cloudogu/cockpit|cockpit>*")
        assert text.contains("*v2.5.0-2*")
        assert text.contains("<https://github.com/cloudogu/cockpit/releases/tag/v2.5.0-2|View Changelog>")
        assert !script.unstable
    }

    @Test
    void convertsSshRemoteAndDropsCredentials() {
        def ssh = scriptMock("git@github.com:cloudogu/cockpit.git")
        new DoguRelease(ssh).notifyWebhook()
        assert new JsonSlurper().parseText(ssh.actualHttpRequestArgs[0].requestBody).text.contains("https://github.com/cloudogu/cockpit|")

        def withToken = scriptMock("https://user:secret@github.com/cloudogu/cockpit.git")
        new DoguRelease(withToken).notifyWebhook()
        String text = new JsonSlurper().parseText(withToken.actualHttpRequestArgs[0].requestBody).text
        assert text.contains("https://github.com/cloudogu/cockpit|")
        assert !text.contains("secret")
    }

    @Test
    void marksUnstableWhenPostFails() {
        def script = scriptMock("https://github.com/cloudogu/cockpit.git")
        script.httpRequestError = new RuntimeException("connection refused")

        new DoguRelease(script).notifyWebhook("other-team-webhook")

        assert script.actualStringArgs.credentialsId == "other-team-webhook"
        assert script.unstable
        assert script.unstableMsg.contains("connection refused")
    }
}
