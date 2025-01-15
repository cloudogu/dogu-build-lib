package com.cloudogu.ces.dogubuildlib

class EcoSystem {

    def script
    String gcloudCredentials
    String sshCredentials

    def defaultSetupConfig = [
            adminUsername          : "ces-admin",
            adminPassword          : "Ecosystem2016!",
            adminGroup             : "CesAdministrators",
            dependencies           : ["official/registrator",
                                      "official/ldap",
                                      "official/cas",
                                      "official/nginx",
                                      "official/postfix",
                                      "official/usermgt"],
            additionalDependencies : [],
            registryConfig         : "",
            registryConfigEncrypted: ""
    ]
    def currentConfig = defaultSetupConfig

    Vagrant vagrant
    String externalIP
    String mountPath

    EcoSystem(script, String gcloudCredentials, String sshCredentials) {
        this.script = script
        this.gcloudCredentials = gcloudCredentials
        this.sshCredentials = sshCredentials
    }

    void changeNamespace(String namespace, doguPath = null) {
        def doguJson = script.readJSON file: 'dogu.json'
        def doguName = doguJson.Name.split('/')[1]
        def newDoguName = namespace + '/' + doguName

        doguJson.Image = doguJson.Image.replace(doguJson.Name, newDoguName)
        doguJson.Name = newDoguName

        script.writeJSON file: 'dogu.json', json: doguJson

        if (vagrant != null && doguPath != null) {
            vagrant.scp("dogu.json", "${doguPath}/dogu.json")
        }
    }

    void setVersion(String version, doguPath = null) {
        def doguJson = script.readJSON file: 'dogu.json'
        doguJson.Version = version

        script.writeJSON file: 'dogu.json', json: doguJson

        if (vagrant != null && doguPath != null) {
            vagrant.scp("dogu.json", "${doguPath}/dogu.json")
        }
    }

    void provision(String mountPath, machineType = "n1-standard-4") {
        script.dir('ecosystem') {
            script.git branch: 'develop', url: 'https://github.com/cloudogu/ecosystem', changelog: false, poll: false
        }
        script.timeout(5) {
            vagrant = createVagrant(mountPath, machineType)
            this.mountPath = mountPath

            vagrant.up()
            externalIP = vagrant.externalIP
        }
    }

    void loginBackend(String credentialsId) {
        script.withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: credentialsId, usernameVariable: 'TOKEN_ID', passwordVariable: 'TOKEN_SECRET']]) {
            vagrant.ssh "sudo cesapp login ${script.env.TOKEN_ID} ${script.env.TOKEN_SECRET}"
        }
    }

    void setup(config = [:]) {
        // Merge default config with the one passed as parameter
        currentConfig = defaultSetupConfig << config

        writeSetupStagingJSON(currentConfig)

        vagrant.sync()

        vagrant.ssh "sudo mv ${mountPath}/setup.staging.json /etc/ces/setup.staging.json"
        vagrant.ssh "sudo mv /etc/ces/setup.staging.json /etc/ces/setup.json"
        vagrant.ssh "while sudo pgrep -u root ces-setup > /dev/null; do sleep 1; done"
        vagrant.ssh "sudo journalctl -u ces-setup -n 100"
    }

    void waitForDogu(String dogu) {
        vagrant.ssh "sudo cesapp healthy --wait --timeout 1200 --fail-fast ${dogu}"
    }

    void waitUntilAvailable(String doguName, int timeout = 30) {
        for (int i = 0; i < timeout; i++) {
            def response = script.sh(script: "curl --insecure --silent --head https://${externalIP}/${doguName} | head -n 1", returnStdout: true)
            if (response.contains("302")) {
                break
            }
            script.sleep 1
        }
    }

    void build(String doguPath) {
        vagrant.ssh "sudo cesapp build ${doguPath}"
    }

    void purgeDogu(String doguName, parameters = "") {
        vagrant.ssh "sudo cesapp purge ${parameters} ${doguName}"
    }

    void installDogu(String doguFullName) {
        if (doguFullName.contains("/")) {
            vagrant.ssh "sudo cesapp install ${doguFullName}"
        } else {
            script.echo "Could not install ${doguFullName}. Please use full name, e.g. official/jenkins"
        }
    }

    /**
     * This method restarts a dogu and by default waits for it to be available again. This waiting behaviour can be
     * disabled.
     *
     * @param doguName Name of the dogu to restart.
     * @param waitUntilAvailable Determines whether this method blocks until the dogu is available again. Default: true
     *
     * @see EcoSystem#waitForDogu(java.lang.String)
     * @see EcoSystem#waitUntilAvailable(java.lang.String)
     */
    void restartDogu(String doguName, boolean waitUntilAvailable = true) {
        script.echo "Restarting ${doguName}..."
        this.vagrant.ssh "sudo docker restart ${doguName}"

        if (waitUntilAvailable) {
            script.echo "Waiting for dogu (${doguName}) to become available"
            this.waitForDogu(doguName)
            this.waitUntilAvailable(doguName)
        }
    }

    /**
     * Changes the global admin group of the ecosystem. This requires a restart of the dogu of interest.
     * This can be done with the `restartDogu` method.
     *
     * @param newGlobalAdminGroup The name of the new global admin group.
     *
     * @see EcoSystem#restartDogu(java.lang.String, boolean)
     */
    void changeGlobalAdminGroup(String newGlobalAdminGroup) {
        script.echo "Change global admin group to ($newGlobalAdminGroup)."
        def adminUsername = currentConfig.adminUsername
        def adminPassword = currentConfig.adminPassword

        script.echo "Creating the new admin group ($newGlobalAdminGroup) in usermgt and adding the user $adminUsername to it"
        script.sh 'curl -u ' + adminUsername + ':' + adminPassword + ' --insecure -X POST https://' + this.externalIP + '/usermgt/api/groups -H \'accept: */*\' -H \'Content-Type: application/json\' -d \'{"description": "New admin group for testing", "members": ["' + adminUsername + '"], "name": "' + newGlobalAdminGroup + '"}\''

        script.echo "Changing /config/_global/admin_group to $newGlobalAdminGroup"
        this.vagrant.ssh "etcdctl set /config/_global/admin_group $newGlobalAdminGroup"
    }

    /**
     * Upgrades the dogu by building again with a new version.
     * @param newDoguVersion the version to be set for the built dogu.
     */
    void upgradeDogu(String newDoguVersion) {
        this.setVersion(newDoguVersion)
        this.vagrant.sync()
        // Build/Upgrade dogu
        this.build("/dogu")
    }

    /**
     * Upgrades the dogu by building again with a new version incremented by one.
     * E.g. version 2.222.4-1 upgrades to 2.222.4-2.
     */
    void upgradeDogu() {
        // currentDoguVersionString, e.g. "Version": "2.222.4-1",
        String currentDoguVersionString = script.sh(returnStdout: true, script: 'grep .Version dogu.json').trim()
        // newDoguVersion, e.g. 2.222.4-2
        String newDoguVersion = increaseDoguReleaseVersionByOne(currentDoguVersionString)
        this.upgradeDogu(newDoguVersion)
    }

    void startDogu(String doguName) {
        vagrant.ssh "sudo cesapp start ${doguName}"
    }

    void verify(String doguPath, int timeout=600) {
        if (script.fileExists('verify.xml')) {
            script.sh 'rm -f verify.xml'
        }
        try {
            vagrant.ssh "mkdir -p /tmp/reports"
            vagrant.ssh "sudo cesapp verify --health-timeout ${timeout} --keep-container --ci --report-directory=/tmp/reports ${doguPath}"
            String verifyReport = vagrant.sshOut "cat /tmp/reports/*.xml"
            script.echo "Report:\n ${verifyReport}"
            script.writeFile encoding: 'UTF-8', file: 'verify.xml', text: verifyReport
        } finally {
            script.junit allowEmptyResults: true, testResults: 'verify.xml'
            script.archiveArtifacts artifacts: 'verify.xml', allowEmptyArchive: true
        }
    }

    void push(String doguPath) {
        vagrant.ssh "sudo cesapp push ${doguPath}"
    }

    /**
     * Push Dogu as prerelease to custom namespace
     */
    @SuppressWarnings("java:S1181")
    void pushPreRelease(String doguPath) {
        script.withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "harborrobotprerelease", usernameVariable: 'TOKEN_ID', passwordVariable: 'TOKEN_SECRET']]) {
            script.echo "Push Dogu as Prerelease"
            // escaping $ in user variable
            def escToken = script.env.TOKEN_ID.replaceAll("\\\$", '\\\\\\\$')
            vagrant.ssh "sudo cesapp login '${escToken}' '${script.env.TOKEN_SECRET}'"
            try {
                vagrant.ssh "sudo cesapp push ${doguPath}"
            } catch (Exception ex) { // we catch exception, because we do not want to fail the build
                script.unstable "Failed to push Dogu to Prerelease-Namespace: ${ex.message}"
            }
        }
    }

    void purge(String dogu) {
        vagrant.ssh "sudo cesapp purge --keep-container --keep-image ${dogu}"
    }

    void destroy() {
        if (vagrant != null) {
            try {
                collectLogs()
            } catch (Exception ex) { // we catch exception, because we do not want to fail the build
                script.echo "failed to collect logs: ${ex.message}"
            }
            vagrant.destroy()
        }
    }

    /**
     * Installs the given dogu and performs an upgrade on it.
     * Before installation, the dogu is purged to ensure a clean slate.
     *
     * @param oldDoguVersionForUpgradeTest the current version of the dogu to install before performing the upgrade.
     * @param doguName the name of the dogu the upgrade should be performed for.
     * @param namespace the dogu namespace in which the dogu resides. Defaults to official.
     */
    void upgradeFromPreviousRelease(String oldDoguVersionForUpgradeTest, String doguName, String namespace = "official") {
        // Remove new dogu that has been built and tested above
        this.purgeDogu(doguName)

        if (oldDoguVersionForUpgradeTest != '' && !oldDoguVersionForUpgradeTest.contains('v')) {
            script.echo "Installing user defined version of dogu: " + oldDoguVersionForUpgradeTest
            this.installDogu(namespace + "/" + doguName + " " + oldDoguVersionForUpgradeTest)
        } else {
            script.echo "Installing latest released version of dogu..."
            this.installDogu(namespace + "/" + doguName)
        }
        this.startDogu(doguName)
        this.waitForDogu(doguName)
        this.upgradeDogu()

        // Wait for upgraded dogu to get healthy
        this.waitForDogu(doguName)
        waitUntilAvailable(doguName)
    }

    void runYarnIntegrationTests(int timeoutInMinutes, String nodeImage, ArrayList<String> additionalArgs = [], boolean enableVideoRecording = false) {
        script.sh 'rm -f integrationTests/it-results.xml'
        def additionalContainerRunArgs = "${parseAdditionalIntegrationTestArgs(additionalArgs)} "

        script.timeout(time: timeoutInMinutes, unit: 'MINUTES') {
            try {
                def customConfig = [videoRecordingEnabled: enableVideoRecording]
                script.withDockerNetwork { zaleniumNetwork ->
                    this.startYarnIntegrationTests(zaleniumNetwork, nodeImage, customConfig, additionalContainerRunArgs)
                }
            } finally {
                // archive test results
                script.junit allowEmptyResults: true, testResults: 'integrationTests/it-results.xml'
            }
        }
    }

    /**
     * Copies the built Dogu image from the CES machine to the Jenkins worker and imports it into Docker
     * @param doguPath The path of the dogu sources
     */
    void copyDoguImageToJenkinsWorker(String doguPath) {
        String savedImageFileName = "savedImage.tar"
        String savedImageFilePath = "${doguPath}/${savedImageFileName}"
        String image = getDoguImage(doguPath)
        vagrant.ssh("sudo docker save -o ${savedImageFilePath} ${image}")
        vagrant.ssh("sudo chown jenkins:jenkins ${savedImageFilePath}")
        vagrant.scp(":${savedImageFilePath}", "${savedImageFileName}")
        script.sh("sudo docker image load -i ${savedImageFileName}")
        script.sh("rm ${savedImageFileName}")
    }

    /**
     * Extracts the image and the version from the dogu.json in a doguPath to get the exact image name.
     * @param doguPath The path of the dogu sources
     * @return
     */
    private String getDoguImage(String doguPath){
        String image = vagrant.sshOut("jq .Image ${doguPath}/dogu.json")
        String version = vagrant.sshOut("jq .Version ${doguPath}/dogu.json")

        return "${image}:${version}";
    }

    /**
     * Runs integration tests based on cypress. Is it necessary that the cypress integration test are inside a
     * relative path named `integrationTests` from the root.
     * The configuration can contain the following values:
     *
     * <li>cypressImage - The cypress image to use for running the integration tests. Default: "cypress/included:7.1.0".</li>
     * <li>enableVideo - Determines whether cypress should record videos. Default: true.</li>
     * <li>enableScreenshots - Determines whether cypress should record screenshots. Default: true.</li>
     * <li>timeoutInMinutes - Determines the complete timeout in minutes for the tests. Default: 15.</li>
     * <li>additionalDockerArgs - A list containing arguments that are given to docker.</li>
     * <li>additionalCypressArgs - A list containing argument that are given to cypress.</li>
     */
    void runCypressIntegrationTests(config = [:]) {
        Cypress cypress = new Cypress(this.script, config)

        try {
            cypress.updateCypressConfiguration(vagrant)
            cypress.preTestWork()
            cypress.runIntegrationTests(this)
        } finally {
            cypress.archiveVideosAndScreenshots()
        }
    }

    private void startYarnIntegrationTests(def zaleniumNetwork, String nodeImage, def customConfig, def additionalContainerRunArgs) {
        script.withZalenium(customConfig, zaleniumNetwork) { zaleniumContainer, zaleniumIp, uid, gid ->
            script.dir('integrationTests') {
                script.docker.image(nodeImage).inside("--net ${zaleniumNetwork} ${additionalContainerRunArgs}-e WEBDRIVER=remote -e CES_FQDN=${externalIP} -e SELENIUM_BROWSER=chrome -e SELENIUM_REMOTE_URL=http://${zaleniumIp}:4444/wd/hub") {
                    script.sh 'yarn install'
                    script.sh 'yarn run ci-test'
                }
            }
        }
    }

    static parseAdditionalIntegrationTestArgs(ArrayList<String> args = []) {
        def parsedArgs = []
        args.each {
            parsedArgs << "-e $it"
        }
        return parsedArgs.join(' ')
    }

    void runMavenIntegrationTests(int timeoutInMinutes, ArrayList<String> additionalArgs = [], boolean enableVideoRecording = false) {
        script.sh 'rm -f integrationTests/target/*.xml'
        def additionalContainerRunArgs = "${parseAdditionalIntegrationTestArgs(additionalArgs)} "

        script.timeout(time: timeoutInMinutes, unit: 'MINUTES') {
            try {
                def customConfig = [videoRecordingEnabled: enableVideoRecording]
                script.withDockerNetwork { zaleniumNetwork ->
                    this.startMavenIntegrationTests(zaleniumNetwork, customConfig, additionalContainerRunArgs)
                }
            } finally {
                // archive test results
                script.junit allowEmptyResults: true, testResults: 'integrationTests/target/*.xml'
            }
        }
    }

    private void startMavenIntegrationTests(def zaleniumNetwork, def customConfig, String additionalContainerRunArgs) {
        script.withZalenium(customConfig, zaleniumNetwork) { zaleniumContainer, zaleniumIp, uid, gid ->
            script.dir('integrationTests') {
                script.docker.image('maven:3-jdk-11-slim')
                        .inside("--net ${zaleniumNetwork} -v ${script.PWD}:/usr/src/app -w /usr/src/app ${additionalContainerRunArgs} -e CES_FQDN=${externalIP} -e SELENIUM_REMOTE_URL=http://${zaleniumIp}:4444/wd/hub") {
                            script.sh('mvn clean test')
                        }
            }
        }
    }

    void collectLogs() {
        vagrant.ssh "sudo tar cvfz /tmp/logs.tar.gz /var/log/docker"
        vagrant.scp(":/tmp/logs.tar.gz", "logs.tar.gz")
        script.archiveArtifacts "logs.tar.gz"
    }

    List<String> joinDependencies(config) {
        return config.dependencies + config.additionalDependencies
    }

    String formatDependencies(List<String> deps) {
        String formatted = ""

        for (int i = 0; i < deps.size(); i++) {
            formatted += "\"${deps[i]}\""

            if ((i + 1) < deps.size()) {
                formatted += ', '
            }
        }

        return formatted
    }

    private void writeSetupStagingJSON(config) {
        List<String> deps = joinDependencies(config)
        String formattedDeps = formatDependencies(deps)

        script.writeFile file: 'setup.staging.json', text: """
{
  "token":{
    "ID":"",
    "Secret":"",
    "Completed":true
  },
  "region":{
    "locale":"en_US.utf8",
    "timeZone":"Europe/Berlin",
    "completed":true
  },
  "naming":{
    "fqdn":"${externalIP}",
    "hostname":"ces",
    "domain":"ces.local",
    "certificateType":"selfsigned",
    "certificate":"",
    "certificateKey":"",
    "relayHost":"mail.ces.local",
    "completed":true
  },
  "dogus":{
    "defaultDogu":"cockpit",
    "install":[
       ${formattedDeps}
    ],
    "completed":true
  },
  "admin":{
    "username":"${config.adminUsername}",
    "mail":"ces-admin@cloudogu.com",
    "password":"${config.adminPassword}",
    "confirmPassword":"${config.adminPassword}",
    "adminGroup":"${config.adminGroup}",
    "adminMember":true,
    "completed":true
  },
  "userBackend":{
    "port":"389",
    "useUserConnectionToFetchAttributes":true,
    "dsType":"embedded",
    "attributeID":"uid",
    "attributeFullname":"cn",
    "attributeMail":"mail",
    "attributeGroup":"memberOf",
    "searchFilter":"(objectClass=person)",
    "host":"ldap",
    "completed":true
  },
  "unixUser":{
    "Name":"",
    "Password":""
  },
  "registryConfig": {${config.registryConfig}},
  "registryConfigEncrypted": {${config.registryConfigEncrypted}}
}"""
    }

    private Vagrant createVagrant(String mountPath, machineType = "n1-standard-4") {
        writeVagrantConfiguration(mountPath, machineType)
        return new Vagrant(script, gcloudCredentials, sshCredentials)
    }

    private void writeVagrantConfiguration(String mountPath, machineType = "n1-standard-4") {
        script.writeFile file: 'Vagrantfile', text: """
Vagrant.require_version ">= 1.9.0"

gcloud_key = ENV["GCLOUD_SA_KEY"]

file = File.read(gcloud_key)
data_hash = JSON.parse(file)

project_id = data_hash["project_id"]

Vagrant.configure(2) do |config|

  config.vm.box = "google/gce"

  config.vm.provider :google do |google, override|
    google.google_project_id = project_id
    google.google_json_key_location = gcloud_key
    
    google.image_family = 'ces-development'
    google.zone = "europe-west3-a"
    google.machine_type = "${machineType}"

    # preemptible
    google.preemptible = true
    google.auto_restart = false
    google.on_host_maintenance = "TERMINATE"

    google.name = "ces-dogu-" + Time.now.to_i.to_s

    google.tags = ["http-server", "https-server", "setup"]
    
    google.disk_size = 100

    override.ssh.username = ENV["SSH_USERNAME"]
    override.ssh.private_key_path = ENV["SSH_KEY"]
  end

  config.vm.synced_folder ".", "/vagrant", disabled: true
  config.vm.synced_folder "ecosystem", "/vagrant", type: "rsync", rsync__exclude: [".git/", "images/"]
  config.vm.synced_folder ".", "${mountPath}"
  config.vm.provision "shell",
    inline: "mkdir -p /etc/ces && echo 'gcloud-vagrant' > /etc/ces/type && /vagrant/install.sh"

end
"""
    }

    static String increaseDoguReleaseVersionByOne(String currentDoguVersionString) {
        // releaseNumber, e.g. 1
        int releaseNumber = (currentDoguVersionString.split('-')[1] - "\",").toInteger()
        // newReleaseNumber, e.g. 2
        int newReleaseNumber = releaseNumber + 1
        // currentDoguVersion, e.g. 2.222.4-1
        String currentDoguVersion = currentDoguVersionString.split("\"")[3]
        // newDoguVersion, e.g. 2.222.4-2
        return currentDoguVersion.split("-")[0] + "-" + newReleaseNumber
    }
}
