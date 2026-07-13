package com.cloudogu.ces.dogubuildlib

class Vagrant implements Serializable {

    def script
    String gcloudCredentials
    String sshCredentials

    Vagrant(script, gcloudCredentials, sshCredentials) {
        this.script = script
        this.gcloudCredentials = gcloudCredentials
        this.sshCredentials = sshCredentials
    }

    void installPlugin(String plugin) {
        script.sh("vagrant plugin install ${plugin}")
    }

    void scp(String source, String target) {
        withVagrantCredentials {
            script.sh "vagrant scp ${source} ${target}"
        }
    }

    void sync() {
        withVagrantCredentials {
            script.sh("vagrant rsync")
        }
    }

    void up() {
        withVagrantCredentials {
            script.sh "vagrant up"
        }
    }

    void ssh(String command) {
        withVagrantCredentials {
            script.sh "vagrant ssh -c \"${command}\""
        }
    }

    /**
     * Poll until the VM responds to SSH again, once per second.
     *
     * @param timeoutInSeconds Sec to wait before failing. Default: 60.
     */
    void waitUntilSSHReachable(int timeoutInSeconds = 60) {
        withVagrantCredentials {
            for (int i = 0; i < timeoutInSeconds; i++) {
                int exitCode = script.sh(script: "vagrant ssh -c \"true\"", returnStatus: true)
                if (exitCode == 0) {
                    return
                }
                script.sleep 1
            }
            script.error "VM did not become reachable via SSH within ${timeoutInSeconds} seconds."
        }
    }

    String getExternalIP() {
        return sshOut("curl http://metadata/computeMetadata/v1/instance/network-interfaces/0/access-configs/0/external-ip -H 'Metadata-Flavor: Google'")
    }

    String sshOut(String command) {
        withVagrantCredentials {
            return script.sh (
                    returnStdout: true,
                    script: "vagrant ssh -c \"${command}\""
            ).trim()
        }
    }

    void destroy() {
        withVagrantCredentials {
            script.sh "vagrant destroy -f"
        }
    }

    private void withVagrantCredentials(Closure body) {
        script.withCredentials([script.file(credentialsId: gcloudCredentials, variable: 'GCLOUD_SA_KEY'),
                                script.sshUserPrivateKey(credentialsId: sshCredentials, keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USERNAME')]) {
            body()
        }
    }

}
