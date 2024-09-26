package com.cloudogu.ces.dogubuildlib


/**
 * The trivy scanner for an ecoSystem.
 * In order to work, an EcoSystem object is needed. The provision in the ecoSystem must be done to execute a trivy scan.
 */
class Trivy {
    EcoSystem ecoSystem
    def script

    Trivy(script, EcoSystem ecosystem) {
        this.ecoSystem = ecosystem
        this.script = script
    }

    /**
     * Gets the current vagrant object from the internal EcoSystem.
     * @return
     */
    private Vagrant vagrant() {
        return this.ecoSystem.vagrant
    }

    /**
     * Scans a dogu image for security issues.
     * Note:
     * - The dogu build must already be finished in order to execute this function.#
     * - The dogu-sources must be available inside the vagrant machine
     *
     * @param doguPath The mount path of the dogu inside the vagrant machine (usually '/dogu').
     * @param format The format of the output file (@see TrivyScanFormat)
     * @param level The vulnerability level to scan. Can be a member of TrivyScanLevel or a custom String (e.g. 'CRITICAL,LOW')
     * @param strategy The strategy to follow after scan. Should the build become unstable? Should the build fail? Or Should any vulnerability be ignored? (@see TrivyScanStrategy)
     * @param fileName The output file name for trivy scan. Leave empty for 'trivyscan.<format>'
     * @return Returns true if the scan was ok (no vulnerability found) or false if any vulnerability was found.
     */
    boolean scanDogu(String doguPath, String format = TrivyScanFormat.HTML, String level = TrivyScanLevel.CRITICAL, String strategy = TrivyScanStrategy.FAIL, String fileName = null) {
        return this.scan(getDoguImage(doguPath), format, level, strategy, fileName)
    }

    /**
     * Scans a dogu image for security issues.
     * Note:
     * - The dogu build must already be finished in order to execute this function.
     * - The dogu-sources must be available inside the vagrant machine
     *
     * @param image The image inside the vagrant machine which should be scanned.
     * @param format The format of the output file (@see TrivyScanFormat)
     * @param level The vulnerability level to scan. Can be a member of TrivyScanLevel or a custom String (e.g. 'CRITICAL,LOW')
     * @param strategy The strategy to follow after scan. Should the build become unstable? Should the build fail? Or Should any vulnerability be ignored? (@see TrivyScanStrategy)
     * @param fileName The output file name for trivy scan. Leave empty for 'trivyscan.<format>'
     * @return Returns true if the scan was ok (no vulnerability found) or false if any vulnerability was found.
     */
    boolean scan(String image, String format = TrivyScanFormat.HTML, String level = TrivyScanLevel.CRITICAL, String strategy = TrivyScanStrategy.FAIL, String fileName = null) {
        this.vagrant().ssh("sudo mkdir -p /vagrant/trivy/output")
        this.vagrant().ssh("sudo mkdir -p /vagrant/trivy/cache")
        String command = "sudo docker run --rm " +
                "-v /vagrant/trivy/output:/output " +
                "-v /vagrant/trivy/cache:/root/.cache/ " +
                "-v /var/run/docker.sock:/var/run/docker.sock " +
                "-v /dogu/.trivyignore:/trivy/.trivyignore " +
                "aquasec/trivy image " +
                formatFlags(format, fileName) + " " +
                "--exit-code 1 " +
                "--severity ${level} " +
                "--debug " +
                "--ignorefile /trivy/.trivyignore " +
                "${image} &>> ./trivyscan.log; echo \\\$?"

        def exitCode = this.vagrant().sshOut(command)

        boolean ok = exitCode == "0"

        this.vagrant().scp(":/vagrant/trivy/output", "trivy")
        this.vagrant().scp(":./trivyscan.log", "trivy/output")
        this.script.archiveArtifacts artifacts: 'trivy/output/trivyscan.*', allowEmptyArchive: true

        if (!ok && strategy == TrivyScanStrategy.UNSTABLE) {
            this.script.unstable("The trivy scan found vulnerabilities")
        } else if (!ok && strategy == TrivyScanStrategy.FAIL) {
            throw new TrivyScanException("The trivy scan found vulnerabilities")
        }

        return ok
    }

    String includeIgnoreFile() {
        //File ignoreFile = new File(".trivyignore")
        //if (ignoreFile.isFile()) {
        return "-v .trivyignore:.trivyignore "
        //}
        //return ""
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

    /**
     * Generates the necessary flags for the trivy command to generate the wanted fileType
     * @param format The format of the output file (@see TrivyScanFormat)
     * @param fileName The output file name for trivy scan. Leave empty for 'trivyscan.<format>'
     * @return
     */
    private static String formatFlags(String format = "html", String fileName = null) {
        String response = ""
        String actualFileName = null
        if (fileName != null) {
            actualFileName = "/output/" + fileName
        }

        switch (format.toLowerCase()) {
            case TrivyScanFormat.HTML.toLowerCase():
                response = "--format template --template \"@contrib/html.tpl\" "
                if (actualFileName == null) {
                    actualFileName = "/output/trivyscan.html"
                }
                break
            case TrivyScanFormat.JSON.toLowerCase():
                response = "-f json "
                if (actualFileName == null) {
                    actualFileName = "/output/trivyscan.json"
                }
                break
            default:
                if (actualFileName == null) {
                    actualFileName = "/output/trivyscan.txt"
                }
        }

        return response + "--output " + actualFileName
    }
}
