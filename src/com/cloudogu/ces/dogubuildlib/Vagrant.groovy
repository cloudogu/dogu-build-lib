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