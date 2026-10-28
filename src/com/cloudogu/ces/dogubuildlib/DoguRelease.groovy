package com.cloudogu.ces.dogubuildlib

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonOutput

/**
 * Announces a dogu release in a Google Chat space via an incoming webhook.
 */
class DoguRelease implements Serializable {
    private script

    DoguRelease(script) {
        this.script = script
    }

    /**
     * Posts "New Dogu Release" with the dogu name, version and a changelog link.
     * The name comes from dogu.json, the repository link from the git remote and the version
     * from the branch name (e.g. "release/v2.5.0-2" -> "v2.5.0-2").
     * A failed post marks the build unstable; the release itself is not affected.
     *
     * @param webhookCredentialsId Jenkins secret text credentials holding the webhook URL
     */
    void notifyWebhook(String webhookCredentialsId = "sos-sw-release-webhook-url") {
        try {
            String doguName = script.sh(script: "jq -r .Name dogu.json", returnStdout: true).trim().tokenize('/').last()
            String repoUrl = toBrowsableUrl(script.sh(script: "git remote get-url origin", returnStdout: true).trim())
            String branchName = script.env.BRANCH_NAME
            String version = branchName.substring(branchName.lastIndexOf('/') + 1)
            String text = "*New Dogu Release*\n" +
                    "• Project: *<${repoUrl}|${doguName}>*\n" +
                    "• Version: *${version}*\n" +
                    "• <${repoUrl}/releases/tag/${version}|View Changelog>\n"

            script.withCredentials([script.string(credentialsId: webhookCredentialsId, variable: 'webhookUrl')]) {
                def response = script.httpRequest(
                        httpMode: 'POST',
                        contentType: 'APPLICATION_JSON',
                        requestBody: JsonOutput.toJson([text: text]),
                        url: script.env.webhookUrl
                )
                script.echo "Notification sent to Google Chat: ${response.status} ${response.content}"
            }
        } catch (Exception e) {
            script.unstable("Failed to send release notification to Google Chat: ${e.getMessage()}")
        }
    }

    // git@github.com:cloudogu/cas.git and https://user:token@github.com/cloudogu/cas.git both
    // become https://github.com/cloudogu/cas - never post credentials from the remote URL.
    @NonCPS
    private static String toBrowsableUrl(String remoteUrl) {
        String url = remoteUrl.replaceFirst(/\.git$/, '')
        def ssh = url =~ /^git@([^:]+):(.+)$/
        if (ssh.matches()) {
            return "https://${ssh.group(1)}/${ssh.group(2)}"
        }
        return url.replaceFirst(/^(https?:\/\/)[^@\/]+@/, '$1')
    }
}
