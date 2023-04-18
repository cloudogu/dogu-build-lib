package com.cloudogu.ces.dogubuildlib

class Trivy {
    Vagrant vagrant

    Trivy(EcoSystem ecosystem) {
        this.vagrant = ecosystem.vagrant
    }

    void scanCritical(image) {
        this.scan("CRITICAL", image)
    }

    void scanHighOrCritical(image) {
        this.scan("HIGH,CRITICAL", image)
    }

    void scanAll(image) {
        this.scan("UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL", image)
    }

    private void scan(vulnerabilities, image) {
        this.vagrant.ssh("sudo mkdir -p /vagrant/trivy/output")
        this.vagrant.ssh("sudo mkdir -p /vagrant/trivy/cache")
        exitCode = this.vagrant.sshOut("sudo docker run --rm " +
                "-v /vagrant/trivy/output:/output " +
                "-v /vagrant/trivy/cache:/root/.cache/ " +
                "-v /var/run/docker.sock:/var/run/docker.sock " +
                "aquasec/trivy image " +
                "--output /output/trivyscan " +
                "--exit-code 1 " +
                "--severity ${vulnerabilities} " +
                "${image} &> /dev/null && echo \$?"
        )
        println exitCode
    }
}
