package com.cloudogu.ces.dogubuildlib

import groovy.json.JsonSlurper
import org.junit.Test

class WebhookTest {

    private ScriptMock scriptMock(String remoteUrl, boolean hasDoguJson = true) {
        def script = new ScriptMock()
        script.env.BRANCH_NAME = "release/v2.5.0-2"
        script.expectedShRetValueForScript.put("git remote get-url origin", remoteUrl + "\n")
        if (hasDoguJson) {
            script.existingFiles.add("dogu.json")
            script.expectedShRetValueForScript.put("jq -r .Name dogu.json", "official/cockpit\n")
        }
        return script
    }

    private static String sentText(ScriptMock script) {
        new JsonSlurper().parseText(script.actualHttpRequestArgs[0].requestBody).text
    }

    @Test
    void dogu_readsNameFromDoguJsonAndVersionFromBranch() {
        def script = scriptMock("https://github.com/cloudogu/cockpit.git")

        new Webhook(script).notifyRelease()

        assert script.actualStringArgs.credentialsId == "sos-sw-release-webhook-url"
        String text = sentText(script)
        assert text.contains("*<https://github.com/cloudogu/cockpit|cockpit>*")
        assert text.contains("*v2.5.0-2*")
        assert text.contains("<https://github.com/cloudogu/cockpit/releases/tag/v2.5.0-2|View Changelog>")
        assert !script.unstable
    }

    @Test
    void baseImage_usesPassedNameAndVersion() {
        def script = scriptMock("https://github.com/cloudogu/base.git", false)

        new Webhook(script).notifyRelease(name: "base-alpine", version: "3.22.2-1")

        String text = sentText(script)
        assert text.contains("*<https://github.com/cloudogu/base|base-alpine>*")
        assert text.contains("<https://github.com/cloudogu/base/releases/tag/3.22.2-1|View Changelog>")
    }

    @Test
    void withoutDoguJson_fallsBackToRepositoryName() {
        def script = scriptMock("https://github.com/cloudogu/base.git", false)

        new Webhook(script).notifyRelease()

        assert sentText(script).contains("*<https://github.com/cloudogu/base|base>*")
    }

    @Test
    void convertsSshRemoteAndDropsCredentials() {
        def ssh = scriptMock("git@github.com:cloudogu/cockpit.git")
        new Webhook(ssh).notifyRelease()
        assert sentText(ssh).contains("https://github.com/cloudogu/cockpit|")

        def withToken = scriptMock("https://user:secret@github.com/cloudogu/cockpit.git")
        new Webhook(withToken).notifyRelease()
        assert sentText(withToken).contains("https://github.com/cloudogu/cockpit|")
        assert !sentText(withToken).contains("secret")
    }

    @Test
    void marksUnstableWhenPostFails() {
        def script = scriptMock("https://github.com/cloudogu/cockpit.git")
        script.httpRequestError = new RuntimeException("connection refused")

        new Webhook(script).notifyRelease(webhookCredentialsId: "other-team-webhook")

        assert script.actualStringArgs.credentialsId == "other-team-webhook"
        assert script.unstable
        assert script.unstableMsg.contains("connection refused")
    }
}
