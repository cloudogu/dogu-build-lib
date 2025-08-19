package com.cloudogu.ces.dogubuildlib

class MultiNodeEcoSystem extends EcoSystem {

    def CODER_SUFFIX = UUID.randomUUID().toString().substring(0,12)
    def MN_CODER_TEMPLATE = 'k8s-ces-cluster'
    def MN_CODER_WORKSPACE = 'test-am-mn-' + CODER_SUFFIX

    String coderCredentials
    String coder_workspace

    boolean mnWorkspaceCreated

    MultiNodeEcoSystem(Object script, String gcloudCredentials, String coderCredentials) {
        super(script, gcloudCredentials, "")
        this.coderCredentials = coderCredentials
        this.coder_workspace = MN_CODER_WORKSPACE
    }

    void provision(String mountPath, machineType = "n1-standard-4", int timeoutInMinutes = 5) {
        // isEmpty
        return
    }

    void setup(config = [:]) {
        // Merge default config with the one passed as parameter
        currentConfig = defaultSetupConfig << config

        // setup go
        script.sh "sudo apt update && sudo apt install -y golang"

        // setup yq
        script.sh "make install-yq"

        // setup coder
        script.withCredentials([script.string(credentialsId: "${this.coderCredentials}", variable: 'token')]) {
            script.sh "curl -L https://coder.com/install.sh | sh"
            script.sh "coder login https://coder.cloudogu.com --token ${script.env.token}"
        }

        // patch mn-Parameter
        createMNParameter(config.dependencies, [])

        if (config.clustername.isEmpty()) {
            script.withCredentials([script.string(credentialsId: "${this.coderCredentials}", variable: 'token')]) {
                script.sh """
                   coder create  \
                       --template $MN_CODER_TEMPLATE \
                       --stop-after 1h \
                       --preset none \
                       --verbose \
                       --rich-parameter-file 'integrationTests/mn_params_modified.yaml' \
                       --yes \
                       --token ${script.env.token} \
                       $coder_workspace
                """
            }
            // wait for all dogus to get healthy
            def counter = 0
            while(counter < 360) {
                def setupStatus = "init"
                try {
                    setupStatus = script.sh(returnStdout: true, script: "coder ssh $coder_workspace \"kubectl get pods -l app.kubernetes.io/name=k8s-ces-setup --namespace=ecosystem -o jsonpath='{.items[*].status.phase}'\"")
                    if (setupStatus.isEmpty()) {
                        break
                    }
                } catch (Exception ignored) {
                    // this is okay
                }
                if (setupStatus.contains("Failed")) {
                    script.error("Failed to set up mn workspace. K8s-ces-setup failed")
                }
                script.sleep(time: 10, unit: 'SECONDS')
                counter++
            }
            coder_workspace = script.sh(returnStdout: true, script: "coder ssh $coder_workspace \"curl -H 'Metadata-Flavor: Google' http://metadata.google.internal/computeMetadata/v1/instance/attributes/cluster-name\"")
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
                script.echo setupStatus
                if (setupStatus == "available") {
                    break
                }
            } catch (Exception err) {
                script.echo err
                // this is okay
            }
            script.sleep(time: 10, unit: 'SECONDS')
            counter++
        }
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
MN-CES Node Count: "4"
CES Namespace: "ecosystem"
CES Setup Chart Namespace: "k8s"
CES Setup Chart Version: "3.4.0"
Necessary dogus:
  - official/postfix
  - k8s/nginx-static
  - k8s/nginx-ingress
  - official/ldap
  - official/cas
Additional dogus: []
Component-Operator: "k8s/k8s-component-operator:latest"
Component-Operator-CRD: "k8s/k8s-component-operator-crd:latest"
Necessary components:
  - k8s/k8s-dogu-operator
  - k8s/k8s-dogu-operator-crd
  - k8s/k8s-service-discovery
Additional components: []
Increase max map count on Nodes: "false"
Enable Platform Login: "false"
Enforce Platform Login: "false"
Allowed oidc groups: []
Initial oidc admin usernames: []
        """

        def outputFile = 'integrationTests/mn_params_modified.yaml'

        def yamlData = script.readYaml text: defaultMNParams

        // Listen initialisieren, falls null
        yamlData['Additional dogus'] = yamlData['Additional dogus'] ?: []
        yamlData['Additional components'] = yamlData['Additional components'] ?: []

        // Elemente hinzufügen, ohne Duplikate
        dogusToAdd.each { d ->
            if (!yamlData['Additional dogus'].contains(d)) {
                yamlData['Additional dogus'] << d
            }
        }
        componentsToAdd.each { c ->
            if (!yamlData['Additional components'].contains(c)) {
                yamlData['Additional components'] << c
            }
        }

        // Vorherige Datei löschen, falls existiert
        script.sh "rm -f ${outputFile}"

        // YAML schreiben
        script.writeYaml file: outputFile, data: yamlData

        script.echo "Modified YAML written to ${outputFile}"
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