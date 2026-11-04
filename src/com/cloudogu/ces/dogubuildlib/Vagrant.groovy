package com.cloudogu.ces.dogubuildlib

class Vagrant implements Serializable {

    // Built from https://github.com/cloudogu/vagrant-docker, HashiCorp publishes no vagrant image
    static final String IMAGE = 'europe-docker.pkg.dev/cloudogu-backend/team-sos/vagrant:2.4.1-1'

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
            withVagrantContainer {
                script.sh "vagrant scp ${source} ${target}"
            }
        }
    }

    void sync() {
        withVagrantCredentials {
            withVagrantContainer {
                script.sh("vagrant rsync")
            }
        }
    }

    void up() {
        withVagrantCredentials {
            withVagrantContainer {
                script.sh "vagrant up"
            }
        }
    }

    void ssh(String command) {
        withVagrantCredentials {
            withVagrantContainer {
                script.sh "vagrant ssh -c \"${command}\""
            }
        }
    }

    String getExternalIP() {
        return sshOut("curl http://metadata/computeMetadata/v1/instance/network-interfaces/0/access-configs/0/external-ip -H 'Metadata-Flavor: Google'")
    }

    String sshOut(String command) {
        withVagrantCredentials {
            withVagrantContainer {
                return script.sh (
                        returnStdout: true,
                        script: "vagrant ssh -c \"${command}\""
                ).trim()
            }
        }
    }

    void destroy() {
        withVagrantCredentials {
            withVagrantContainer {
                script.sh "vagrant destroy -f"
            }
        }
    }

    private def withVagrantContainer(Closure body) {
        // The container runs as the agent's UID; ssh, which vagrant uses for ssh/scp/rsync, aborts without a passwd entry for it
        return script.docker.image(IMAGE).inside("--entrypoint='' -v /etc/passwd:/etc/passwd:ro") {
            body()
        }
    }

    // Returns the body's result, which sshOut() relies on
    private def withVagrantCredentials(Closure body) {
        return script.withCredentials([script.file(credentialsId: gcloudCredentials, variable: 'GCLOUD_SA_KEY'),
                                script.sshUserPrivateKey(credentialsId: sshCredentials, keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USERNAME')]) {
            body()
        }
    }

}
