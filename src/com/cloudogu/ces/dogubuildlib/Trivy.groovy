package com.cloudogu.ces.dogubuildlib


class Trivy {
    EcoSystem ecoSystem
    def script

    Trivy(script, EcoSystem ecosystem) {
        this.ecoSystem = ecosystem
        this.script = script
    }

    private Vagrant vagrant() {
        return this.ecoSystem.vagrant
    }

    boolean scanDogu(String doguPath, String format = TrivyScanFormat.HTML, String level = TrivyScanLevel.CRITICAL, String strategy = TrivyScanStrategy.FAIL) {
        return this.scan(getDoguImage(doguPath), level, format, strategy)
    }

    boolean scan(String image, String format = "html", String level = TrivyScanLevel.CRITICAL, String strategy = TrivyScanStrategy.FAIL) {
        this.vagrant().ssh("sudo mkdir -p /vagrant/trivy/output")
        this.vagrant().ssh("sudo mkdir -p /vagrant/trivy/cache")
        String command = "sudo docker run --rm " +
                "-v /vagrant/trivy/output:/output " +
                "-v /vagrant/trivy/cache:/root/.cache/ " +
                "-v /var/run/docker.sock:/var/run/docker.sock " +
                "aquasec/trivy image " +
                formatFlags(format) + " " +
                "--exit-code 1 " +
                "--severity ${level} " +
                "${image} &> /dev/null; echo \\\$?"
        def exitCode = this.vagrant().sshOut(command)
        this.vagrant().scp(":/vagrant/trivy/output", "trivy")
        this.script.archiveArtifacts "trivy/output/trivyscan.*"

        boolean ok = exitCode == "0"
        if (!ok && strategy == TrivyScanStrategy.FAIL) {
            throw new TrivyScanException("The trivy scan found vulnerabilities")
        }
        if (!ok && strategy == TrivyScanStrategy.UNSTABLE) {
            this.script.unstable("The trivy scan found vulnerabilities")
        }

        return ok
    }


    /**
     * Extracts the image and the version from the dogu.json in a doguPath to get the exact image name.
     * @param doguPath The path of the dogu sources
     * @return
     */
    private String getDoguImage(String doguPath) {
        String image = this.vagrant().sshOut("jq .Image ${doguPath}/dogu.json")
        String version = this.vagrant().sshOut("jq .Version ${doguPath}/dogu.json")

        return "${image}:${version}";
    }

    private static String formatFlags(String format = "html") {
        switch (format.toLowerCase()) {
            case TrivyScanFormat.HTML.toLowerCase():
                return "--format template --template \"@contrib/html.tpl\" --output /output/trivyscan.html"
            case TrivyScanFormat.JSON.toLowerCase():
                return "-f json --output /output/trivyscan.json"
            default:
                return "--output /output/trivyscan.txt"
        }
    }
}
