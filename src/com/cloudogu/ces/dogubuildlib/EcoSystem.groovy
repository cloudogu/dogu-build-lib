package com.cloudogu.ces.dogubuildlib

class EcoSystem {

    def script
    String gcloudCredentials
    String sshCredentials

    def defaultSetupConfig = [
            adminUsername: "ces-admin",
            adminPassword: "ecosystem2016",
            adminGroup: "CesAdministrators",
            dependencies : ["official/registrator",
                    "official/ldap",
                    "official/cas",
                    "official/nginx",
                    "official/postfix",
                    "official/usermgt"],
            additionalDependencies: [],
            registryConfig: ""
    ]

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

    void provision(String mountPath) {
        script.dir ('ecosystem') {
            script.git branch: 'develop', url: 'https://github.com/cloudogu/ecosystem', changelog: false, poll: false
        }
        script.timeout(5) {
            vagrant = createVagrant(mountPath)
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
        config = defaultSetupConfig << config

        writeSetupStagingJSON(config)

        vagrant.sync()

        vagrant.ssh "sudo mv ${mountPath}/setup.staging.json /etc/ces/setup.staging.json"
        vagrant.ssh "sudo mv /etc/ces/setup.staging.json /etc/ces/setup.json"
        vagrant.ssh "while sudo pgrep -u root ces-setup > /dev/null; do sleep 1; done"
        vagrant.ssh "sudo journalctl -u ces-setup -n 100"
    }

    void waitForDogu(String dogu) {
        vagrant.ssh "sudo cesapp healthy --wait --timeout 1200 --fail-fast ${dogu}"
    }

    void build(String doguPath) {
        vagrant.ssh "sudo cesapp build ${doguPath}"
    }

    void verify(String doguPath) {
        if (script.fileExists('verify.xml')) {
            script.sh 'rm -f verify.xml'
        }
        try {
            vagrant.ssh "mkdir -p /tmp/reports"
            vagrant.ssh "sudo cesapp verify --health-timeout 600 --keep-container --ci --report-directory=/tmp/reports ${doguPath}"
            String verifyReport = vagrant.sshOut "cat /tmp/reports/*.xml"
            script.writeFile encoding: 'UTF-8', file: 'verify.xml', text: verifyReport
        } finally {
            script.junit allowEmptyResults: true, testResults: 'verify.xml'
        }
    }

    void push(String doguPath) {
        vagrant.ssh "sudo cesapp push ${doguPath}"
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

            if ((i+1) < deps.size()) {
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
  "registryConfig": {${config.registryConfig}}
}"""
    }

    private Vagrant createVagrant(String mountPath) {
        writeVagrantConfiguration(mountPath)
        return new Vagrant(script, gcloudCredentials, sshCredentials)
    }

    private void writeVagrantConfiguration(String mountPath) {
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
    google.machine_type = "n1-standard-4"

    # preemptible
    google.preemptible = true
    google.auto_restart = false
    google.on_host_maintenance = "TERMINATE"

    google.name = "ces-dogu-" + Time.now.to_i.to_s

    google.tags = ["http-server", "https-server", "setup"]
    
    google.disk_size = 64

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

}
