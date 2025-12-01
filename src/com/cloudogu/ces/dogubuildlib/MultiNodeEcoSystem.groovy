package com.cloudogu.ces.dogubuildlib

class MultiNodeEcoSystem extends EcoSystem {

    def CODER_SUFFIX = UUID.randomUUID().toString().substring(0,12)
    def MN_CODER_TEMPLATE = 'k8s-ces-cluster'
    def MN_CODER_WORKSPACE = 'test-mn-'
    def ECOSYSTEM_BLUEPRINT = 'blueprint-ces-module'

    def MN_PARAMETER_FILE = 'integrationTests/mn_params_modified.yaml'

    String coderCredentials
    String coder_workspace
    String ecosystem_blueprint

    private static String VERSION_ECOSYSTEM_CORE = "1.2.0"
    private static String VERSION_K8S_COMPONENT_OPERATOR_CRD = "1.10.1"
    private static String VERSION_K8S_DOGU_OPERATOR = "3.15.0"
    private static String VERSION_K8S_DOGU_OPERATOR_CRD = "2.10.0"
    private static String VERSION_K8S_BLUEPRINT_OPERATOR_CRD = "3.1.0"

    boolean mnWorkspaceCreated

    MultiNodeEcoSystem(Object script, String gcloudCredentials, String coderCredentials, String clusterSuffix = "") {
        super(script, gcloudCredentials, "")
        MN_CODER_WORKSPACE = MN_CODER_WORKSPACE + clusterSuffix + CODER_SUFFIX
        MN_CODER_WORKSPACE = MN_CODER_WORKSPACE.substring(0,Math.min(32, MN_CODER_WORKSPACE.length()))
        this.coderCredentials = coderCredentials
        this.coder_workspace = MN_CODER_WORKSPACE
        this.ecosystem_blueprint = ECOSYSTEM_BLUEPRINT
    }

    void provision(String mountPath, machineType = "n1-standard-4", int timeoutInMinutes = 5) {
        // isEmpty
        return
    }

    def multinodeConfig = [
            additionalDogus: [],
            additionalComponents: []
    ]

    void setup(config = [:]) {
        // Merge default config with the one passed as parameter
        currentConfig = defaultSetupConfig << multinodeConfig
        currentConfig << config


        // setup go
        script.sh "sudo apt update && sudo apt install -y golang"

        // setup yq
        script.sh "make install-yq"

        // setup coder
        script.withCredentials([script.string(credentialsId: "${this.coderCredentials}", variable: 'token')]) {
            script.sh "curl -L https://coder.cloudogu.com/install.sh | sh"
            script.sh "coder login https://coder.cloudogu.com --token ${script.env.token}"
        }

        // patch mn-Parameter
        createMNParameter(currentConfig.additionalDogus, currentConfig.additionalComponents)

        if (config.clustername == null || config.clustername.isEmpty()) {
            script.sh "coder version"
            script.withCredentials([script.string(credentialsId: "${this.coderCredentials}", variable: 'token')]) {
                script.sh """
                   coder create  \
                       --template $MN_CODER_TEMPLATE \
                       --stop-after 1h \
                       --verbose \
                       --rich-parameter-file '$MN_PARAMETER_FILE' \
                       --preset 'none' \
                       --yes \
                       --token ${script.env.token} \
                       $coder_workspace
                """
            }
            // wait for blueprint to be ready
            def counter = 0
            while(counter < 360) {
                def setupStatus = "init"
                try {
                    setupStatus = script.sh(returnStdout: true, script: "coder ssh $coder_workspace \"kubectl get blueprint $ecosystem_blueprint --namespace=ecosystem -o jsonpath='{.status.conditions[?(@.type==\\\"EcosystemHealthy\\\")].status}{\\\" \\\"}{.status.conditions[?(@.type==\\\"Completed\\\")].status}'\"")
                    if (setupStatus == "True True") {
                        break
                    }
                } catch (Exception ignored) {
                    // this is okay
                }
                if (setupStatus.contains("Failed")) {
                    script.error("Failed to set up mn workspace. ecosystem-core failed")
                }
                script.echo "Blueprint not ready, waiting 10 seconds: '${setupStatus}'"
                script.sleep(time: 10, unit: 'SECONDS')
                counter++
            }
            if (counter >= 360) {
                script.error("Failed to set up mn workspace. ecosystem-core failed")
            }
            mnWorkspaceCreated = true
        } else {
            coder_workspace = config.clustername
        }

        // install kubectl and gcloud plugins
        script.withCredentials([script.file(credentialsId: "${this.gcloudCredentials}", variable: 'SERVICE_ACCOUNT_JSON')]) {
            script.sh "gcloud auth activate-service-account --key-file=${script.env.SERVICE_ACCOUNT_JSON}"
            script.sh "curl -LO \"https://dl.k8s.io/release/\$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl\""
            script.sh "chmod +x kubectl"
            script.sh "sudo mv kubectl /usr/local/bin/"
            script.sh "echo \"deb [signed-by=/usr/share/keyrings/cloud.google.gpg] http://packages.cloud.google.com/apt cloud-sdk main\" | sudo tee /etc/apt/sources.list.d/google-cloud-sdk.list"
            script.sh "curl https://packages.cloud.google.com/apt/doc/apt-key.gpg | sudo apt-key --keyring /usr/share/keyrings/cloud.google.gpg add -"
            script.sh "sudo apt update"
            script.sh "sudo apt install -y google-cloud-sdk-gke-gcloud-auth-plugin"
        }

        // docker login
        script.withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "cesmarvin-setup", usernameVariable: 'TOKEN_ID', passwordVariable: 'TOKEN_SECRET']]) {
            script.sh "docker login -u ${escapeToken(script.env.TOKEN_ID)} -p ${escapeToken(script.env.TOKEN_SECRET)} registry.cloudogu.com"
        }

        // connect to MN-Cluster via gcloud
        script.withCredentials([script.string(credentialsId: "${this.coderCredentials}", variable: 'token')]) {
            def command = script.sh(returnStdout: true, script: "coder ls --search team-ces/$coder_workspace -o json --token ${script.env.token} | .bin/yq '.0.latest_build.resources.0.metadata[] | select(.key == \"Cluster Connection Command\") | .value'")
            script.sh "$command"
        }
    }

    public String getExternalIP() {
        script.withCredentials([script.string(credentialsId: "${this.coderCredentials}", variable: 'token')]) {
            def ip = script.sh(returnStdout: true, script: "coder ssh $coder_workspace \"kubectl get services --namespace=ecosystem ces-loadbalancer -o jsonpath='{.spec.loadBalancerIP}'\"")
            return "$ip"
        }
    }

    void build(String doguPath) {
        script.env.NAMESPACE="ecosystem"
        script.env.RUNTIME_ENV="remote"

        script.sh "make build"  // target from k8s-dogu.mk
    }

    void waitForDogu(String dogu) {
        def counter = 0
        while(counter < 30) {
            def setupStatus = "init"
            try {
                setupStatus = script.sh(returnStdout: true, script: "coder ssh $coder_workspace \"kubectl get dogus --namespace=ecosystem $dogu -o jsonpath='{.status.health}'\"")
                if (setupStatus == "available") {
                    break
                }
            } catch (Exception err) {
                // this is okay
            }
            script.sleep(time: 10, unit: 'SECONDS')
            counter++
        }
    }

    void verify(String dogu) {
        def veriFile = 'verify_mn.xml'
        def gossFile = './spec/goss/goss.yaml'
        if (!script.fileExists(gossFile)) {
            script.echo "No goss-specification found. Skip verify"
            return
        }
        if (script.fileExists(veriFile)) {
            script.sh "rm -f $veriFile"
        }
        try {
            def podname = script.sh(returnStdout: true, script: """kubectl get pod -l dogu.name=$dogu --namespace=ecosystem -o jsonpath='{.items[0].metadata.name}'""")

            def gosspath = '/tmp/gossbin'

            script.sh "mkdir ./tmp_goss && wget -qO ./tmp_goss/gossbin https://github.com/goss-org/goss/releases/download/v0.4.6/goss-linux-amd64"
            script.sh "kubectl -n ecosystem cp ./tmp_goss/gossbin $podname:$gosspath -c $dogu"
            script.sh "kubectl -n ecosystem exec -i $podname -c $dogu -- sh -c 'chmod +x $gosspath'"

            script.sh "kubectl -n ecosystem cp ./spec/goss/goss.yaml $podname:/tmp/goss.yaml -c $dogu"


            def verifyReport = script.sh(returnStdout: true, script: "kubectl -n ecosystem exec -i $podname -c $dogu -- $gosspath -g /tmp/goss.yaml validate --format junit")
            script.echo "Report:\n ${verifyReport}"
            script.writeFile encoding: 'UTF-8', file: "$veriFile", text: verifyReport
        } finally {
            script.junit allowEmptyResults: true, testResults: "$veriFile"
            script.archiveArtifacts artifacts: "$veriFile", allowEmptyArchive: true
        }
    }

    void restartDogu(String doguName, boolean waitUntilAvailable = true) {
        script.echo "Restarting ${doguName}..."
        def restartYaml = """
apiVersion: k8s.cloudogu.com/v2
kind: DoguRestart
metadata:
  generateName: $doguName-restart-
spec:
  doguName: $doguName
"""
        script.writeFile encoding: 'UTF-8', file: '/tmp/restartDogu.yaml', text: restartYaml
        script.sh "kubectl create -f /tmp/restartDogu.yaml"

        // wait for restart-cr to be reconciled
        script.sleep(time: 10, unit: 'SECONDS')

        if (waitUntilAvailable) {
            script.echo "Waiting for dogu (${doguName}) to become available"
            this.waitForDogu(doguName)
        }
    }

    void changeGlobalAdminGroup(String newGlobalAdminGroup) {
        script.echo "Change global admin group to ($newGlobalAdminGroup)."
        def adminUsername = currentConfig.adminUsername
        def adminPassword = currentConfig.adminPassword

        def externalIP = getExternalIP()

        script.echo "Creating the new admin group ($newGlobalAdminGroup) in usermgt and adding the user $adminUsername to it"
        script.sh 'curl -u ' + adminUsername + ':' + adminPassword + ' --insecure -X POST https://' + externalIP + '/usermgt/api/groups -H \'accept: */*\' -H \'Content-Type: application/json\' -d \'{"description": "New admin group for testing", "members": ["' + adminUsername + '"], "name": "' + newGlobalAdminGroup + '"}\''

        script.echo "Changing /config/_global/admin_group to $newGlobalAdminGroup"

        script.sh """
          kubectl get configmap global-config -n ecosystem -o yaml \
          | sed "s/^\\([[:space:]]*admin_group:\\).*/\\1 $newGlobalAdminGroup/" \
          | kubectl apply -n ecosystem -f -
        """
    }

    void runCypressIntegrationTests(config = [:]) {
        Cypress cypress = new Cypress(this.script, config)
        def ip = getExternalIP()
        def newUrl = "https://$ip"

        // Sed-Befehl für Linux/macOS
        script.sh """
        sed -i 's|baseUrl: .*|baseUrl: "${newUrl}",|' ./integrationTests/cypress.config.ts
        """
        try {
            cypress.preTestWork()
            cypress.runIntegrationTests(this)
        } finally {
            cypress.archiveVideosAndScreenshots()
        }
    }

    void createMNParameter(List dogusToAdd = [], List componentsToAdd = []) {

        def defaultMNParams = """
MN-CES Machine Type: "e2-standard-4"
MN-CES Node Count: "1"
CES Namespace: "ecosystem"
Ecosystem-Core Chart Namespace: "k8s"
Ecosystem Core Chart Version: "${VERSION_ECOSYSTEM_CORE}"
Necessary dogus:
  - official/postfix
  - official/ldap
  - official/cas
Additional dogus: []
Component-Operator: "cloudogu/k8s-component-operator:${VERSION_K8S_COMPONENT_OPERATOR_CRD}"
Component-Operator-CRD: "k8s/k8s-component-operator-crd:${VERSION_K8S_COMPONENT_OPERATOR_CRD}"
Blueprint-Operator-CRD: "k8s/k8s-blueprint-operator-crd:${VERSION_K8S_BLUEPRINT_OPERATOR_CRD}"
Enable Backup: false
Backup components: [] 
Enable Monitoring: false
Monitoring components: []
Base components:
  - k8s/k8s-dogu-operator-crd:${VERSION_K8S_DOGU_OPERATOR_CRD}
  - k8s/k8s-dogu-operator:${VERSION_K8S_DOGU_OPERATOR}
  - k8s/k8s-service-discovery
  - k8s/k8s-ces-gateway
  - k8s/k8s-ces-assets
  - k8s/k8s-debug-mode-operator-crd
  - k8s/k8s-debug-mode-operator
Increase max map count on Nodes: "false"
Enable Platform Login: "false"
Enforce Platform Login: "false"
Allowed oidc groups: []
Initial oidc admin usernames: []
        """


        def yamlData = script.readYaml text: defaultMNParams

        // init list to prevent null value
        yamlData['Additional dogus'] = yamlData['Additional dogus'] ?: []

        // add elements without duplicates
        dogusToAdd.each { d ->
            if (!yamlData['Necessary dogus'].contains(d)) {
                yamlData['Necessary dogus'] << d
            }
        }
        componentsToAdd.each { c ->
            if (!yamlData['Base components'].contains(c)) {
                yamlData['Base components'] << c
            }
        }

        // Vorherige Datei löschen, falls existiert
        script.sh "rm -f ${MN_PARAMETER_FILE}"

        // YAML schreiben
        script.writeYaml file: MN_PARAMETER_FILE, data: yamlData

        script.echo "Modified YAML written to ${MN_PARAMETER_FILE}"
    }

    void destroy() {
        script.withCredentials([script.string(credentialsId: 'automatic_migration_coder_token', variable: 'token')]) {
            if (mnWorkspaceCreated) {
                script.sh "coder delete --yes --token $script.env.token $coder_workspace"
            }
        }
    }

    static String escapeToken(String token) {
        token = token.replaceAll("\\\$", '\\\\\\\$')
        return token
    }
}