package com.cloudogu.ces.dogubuildlib

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonOutput

/**
 * Announces releases in a Google Chat space via an incoming webhook.
 */
class Webhook implements Serializable {
    private script

    Webhook(script) {
        this.script = script
    }

    /**
     * Posts "New Dogu Release" with the project name, version and a changelog link to the git repository.
     * A failed post marks the build unstable; the release itself is not affected.
     *
     * @param config
     *   name: project name shown in the message. Default: dogu.json Name without namespace,
     *         or the repository name if there is no dogu.json (e.g. base images).
     *   version: released version, also the tag of the changelog link. Default: last segment of the
     *            branch name, e.g. "release/v2.5.0-2" -> "v2.5.0-2".
     *   webhookCredentialsId: Jenkins secret text credentials holding the webhook URL.
     *                         Default: "sos-sw-release-webhook-url".
     */
    void notifyRelease(Map config = [:]) {
        try {
            String repoUrl = toBrowsableUrl(script.sh(script: "git remote get-url origin", returnStdout: true).trim())
            String name = config.name ?: defaultName(repoUrl)
            String version = config.version ?: defaultVersion()
            String webhookCredentialsId = config.webhookCredentialsId ?: "sos-sw-release-webhook-url"
            String text = "*New Dogu Release*\n" +
                    "• Project: *<${repoUrl}|${name}>*\n" +
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

    private String defaultName(String repoUrl) {
        if (script.fileExists("dogu.json")) {
            return script.sh(script: "jq -r .Name dogu.json", returnStdout: true).trim().tokenize('/').last()
        }
        return repoUrl.tokenize('/').last()
    }

    private String defaultVersion() {
        String branchName = script.env.BRANCH_NAME
        return branchName.substring(branchName.lastIndexOf('/') + 1)
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
