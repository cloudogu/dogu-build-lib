package com.cloudogu.ces.dogubuildlib

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurper
import groovy.json.JsonSlurperClassic

class MultiNodeEcoSystem extends EcoSystem {

    def CODER_SUFFIX = UUID.randomUUID().toString().substring(0,12)
    def MN_CODER_TEMPLATE = 'k8s-ces-cluster'
    def MN_CODER_WORKSPACE = 'test-mn-'
    def ECOSYSTEM_BLUEPRINT = 'blueprint-ces-module'

    def MN_PARAMETER_FILE = 'integrationTests/mn_params_modified.yaml'

    String coderCredentials
    String coder_workspace
    String ecosystem_blueprint

    def coderRichParameters

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
        script.error "provisioning is not supported in Multinode-Ecosystem - use setup(config = [:]) instead"
    }

    def multinodeConfig = [
            additionalDogus: [],
            additionalComponents: [],
            machineType: "n2-standard-4",
            nodeCount: "1",
            namespace: "ecosystem"
    ]

    /**
     * Create Coder-cluster from template (MN_CODER_TEMPLATE)
     */
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


        // get default Values
        coderRichParameters = this.getRichParameters("https://coder.cloudogu.com", "default", MN_CODER_TEMPLATE)

        // patch mn-Parameter
        createMNParameter(currentConfig)

        if (config.clustername == null || config.clustername.isEmpty()) {
            script.sh "coder version"
            script.withCredentials([script.string(credentialsId: "${this.coderCredentials}", variable: 'token')]) {
                script.sh """
                   yes '' | coder create  \
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
                    setupStatus = script.sh(returnStdout: true, script: "coder ssh $coder_workspace \"kubectl get blueprint $ecosystem_blueprint --namespace=${currentConfig.namespace} -o jsonpath='{.status.conditions[?(@.type==\\\"EcosystemHealthy\\\")].status}{\\\" \\\"}{.status.conditions[?(@.type==\\\"Completed\\\")].status}'\"")
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
            script.sh "echo \"deb [signed-by=/usr/share/keyrings/cloud.google.gpg] https://packages.cloud.google.com/apt cloud-sdk main\" | sudo tee /etc/apt/sources.list.d/google-cloud-sdk.list"
            script.sh "curl -f https://packages.cloud.google.com/apt/doc/apt-key.gpg | sudo gpg --dearmor --yes -o /usr/share/keyrings/cloud.google.gpg"
            script.sh "sudo apt update"
            script.sh "sudo apt install -y google-cloud-cli-gke-gcloud-auth-plugin"
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
            def ip = script.sh(returnStdout: true, script: "coder ssh $coder_workspace \"kubectl get services --namespace=${currentConfig.namespace} ces-loadbalancer -o jsonpath='{.status.loadBalancer.ingress[0].ip}'\"")
            return "$ip"
        }
    }

    void build(String doguPath) {
        script.env.NAMESPACE=currentConfig.namespace
        script.env.RUNTIME_ENV="remote"

        script.sh "make build"  // target from k8s-dogu.mk
    }

    /**
     * Same contract as EcoSystem.waitForDogu():
     * Waits up to 20 minutes and fails the build
     * if the dogu never becomes healthy in that time.
     */
    void waitForDogu(String dogu) {
        int counter = 0
        int maxAttempts = 120 // 120 * 10s = 1200s = 20 minutes
        while (counter < maxAttempts) {
            def setupStatus = "init"
            try {
                setupStatus = script.sh(returnStdout: true, script: "coder ssh $coder_workspace \"kubectl get dogus --namespace=${currentConfig.namespace} $dogu -o jsonpath='{.status.health}'\"")
                if (setupStatus == "available") {
                    return
                }
            } catch (InterruptedException interrupted) {
                // A Jenkins build abort throws FlowInterruptedException, which extends
                // InterruptedException - must not be swallowed by the catch-all below, or the loop
                // keeps polling for up to 20 minutes after the user hit "Abort".
                throw interrupted
            } catch (Exception err) {
                // A failed sh call (dogu not found yet, coder ssh hiccup) is okay, keep polling.
            }
            script.sleep(time: 10, unit: 'SECONDS')
            counter++
        }
        script.error "Dogu ${dogu} did not become healthy within 20 minutes"
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
            def podname = script.sh(returnStdout: true, script: """kubectl get pod -l dogu.name=$dogu --namespace=${currentConfig.namespace} -o jsonpath='{.items[0].metadata.name}'""")

            def gosspath = '/tmp/gossbin'

            script.sh "rm -rf ./tmp_goss && mkdir ./tmp_goss && wget -qO ./tmp_goss/gossbin https://github.com/goss-org/goss/releases/download/v0.4.6/goss-linux-amd64"
            script.sh "kubectl -n ${currentConfig.namespace} cp ./tmp_goss/gossbin $podname:$gosspath -c $dogu"
            script.sh "kubectl -n ${currentConfig.namespace} exec -i $podname -c $dogu -- sh -c 'chmod +x $gosspath'"

            script.sh "kubectl -n ${currentConfig.namespace} cp ./spec/goss/goss.yaml $podname:/tmp/goss.yaml -c $dogu"

            def cmd = "kubectl -n ${currentConfig.namespace} exec -i $podname -c $dogu -- $gosspath -g /tmp/goss.yaml validate --format junit"
            def status = script.sh(
                    returnStatus: true,
                    script: """
    set +e
    ${cmd} > ${veriFile}
    exit \$?
  """
            )
            def verifyReport = script.readFile(file: veriFile)
            script.echo "Report:\n ${verifyReport}"
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
  namespace: ${currentConfig.namespace}
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

    /**
     * Installs a dogu by applying its Dogu CR directly.
     * The dogu-operator pulls the image once the CR is applied.
     *
     * @param doguFullName e.g. "official/cockpit".
     * @param doguVersion explicit version to install, e.g. "2.5.0-1".
     * If empty (default), the latest version is looked up from the registry.
     */
    void installDogu(String doguFullName, String doguVersion = "") {
        if (!doguFullName.contains("/")) {
            script.echo "Could not install ${doguFullName}. Please use full name, e.g. official/jenkins"
            return
        }
        String namespace = doguFullName.split("/")[0]
        String name = doguFullName.split("/")[1]
        String version = doguVersion ?: latestReleasedVersion(namespace, name)

        script.sh """kubectl apply -f - << 'EOF'
apiVersion: k8s.cloudogu.com/v2
kind: Dogu
metadata:
  name: ${name}
  namespace: ${currentConfig.namespace}
  labels:
    app: ces
spec:
  name: ${namespace}/${name}
  version: "${version}"
EOF
"""
    }

    /**
     * Deletes the dogu's CR, causing the dogu-operator to tear down the dogu via its finalizer.
     */
    void purgeDogu(String doguName, parameters = "") {
        script.sh "kubectl delete dogu ${doguName} --namespace=${currentConfig.namespace} --ignore-not-found ${parameters}"
    }

    /**
     * No-op in MultiNode.
     * Applying the Dogu CR triggers the dogu-operator's full install-and-start reconciliation.
     * There is no separate "start" phase like in the classic CES.
     */
    void startDogu(String doguName) {
        script.echo "Doing nothing. startDogu is a no-op in MultiNode. installDogu already starts the dogu via the dogu-operator."
    }

    /**
     * Upgrades the dogu by rebuilding from local source with a new version.
     * Same contract as EcoSystem.upgradeDogu(String), but via build() (make build) instead of vagrant/cesapp.
     */
    void upgradeDogu(String newDoguVersion) {
        this.setVersion(newDoguVersion)
        this.build("/dogu")
        // build() only applies the updated Dogu CR (kubectl apply) and returns immediately;
        // give the dogu-operator a moment to pick up the version change before callers poll health,
        // otherwise waitForDogu can see the still-healthy pre-upgrade status and return early.
        script.sleep(time: 20, unit: 'SECONDS')
    }

    /**
     * Overrides EcoSystem.waitUntilAvailable(), which reads the inherited "externalIP" field.
     * MultiNodeEcoSystem never sets that field, it resolves the cluster's IP lazily via getExternalIP() instead.
     */
    void waitUntilAvailable(String doguName, int timeout = 30) {
        String ip = getExternalIP()
        for (int i = 0; i < timeout; i++) {
            // curl already writes "000" to --write-out on a connection failure before exiting non-zero;
            // "|| true" just keeps that non-zero exit from failing the sh step outright, which would
            // otherwise abort the whole wait instead of retrying. --connect-timeout/--max-time cap how
            // long one stuck attempt can hang, so a silently-dropping LB doesn't eat the whole timeout loop.
            String response = script.sh(
                    script: "curl --insecure --silent --connect-timeout 5 --max-time 10 --output /dev/null --write-out '%{http_code}' https://${ip}/${doguName} || true",
                    returnStdout: true
            ).trim()
            if (response == "200" || response == "302") {
                return
            }
            if (response == "000") {
                script.echo "Dogu ${doguName} not reachable yet at https://${ip}/${doguName} (connection failed), retrying..."
            }
            script.sleep 1
        }
        script.echo "Dogu ${doguName} did not become available at https://${ip}/${doguName} within ${timeout} attempts"
    }

    /**
     * Resolves the highest released version tag for a dogu image in the CES registry.
     * Same approach as ces-build-lib K3d.getLatestVersion().
     */
    private String latestReleasedVersion(String namespace, String name) {
        String tags = "{}"
        script.withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "cesmarvin-setup", usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_PASSWORD']]) {
            tags = script.sh(returnStdout: true, script: "curl -s https://registry.cloudogu.com/v2/${namespace}/${name}/tags/list -u ${script.env.REGISTRY_USER}:${script.env.REGISTRY_PASSWORD}").trim()
        }
        def obj = new JsonSlurper().parseText(tags)
        // Drop tags that aren't a dogu version (e.g. "latest") before picking the max,
        // otherwise a registry with no real version tags would resolve to one of them.
        String version = obj.tags?.findAll { it ==~ /^\d+(\.\d+){0,2}(-\d+)?$/ }?.max { t -> sortableDoguVersion("${t}") }
        if (!version) {
            script.error "Could not resolve latest released version for ${namespace}/${name}"
        }
        return version
    }

    /**
     * Zero-pads a dogu version (e.g. "2.222.4-1") into a lexicographically sortable string,
     * for use with Groovy's max{}. Same approach as ces-build-lib K3d.parseTag().
     */
    private static String sortableDoguVersion(String tag) {
        def m = (tag =~ /^(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:-(\d+))?$/)
        if (!m.matches()) {
            return "00000.00000.00000.00000"
        }
        def major = (m[0][1] ?: "0") as int
        def minor = (m[0][2] ?: "0") as int
        def patch = (m[0][3] ?: "0") as int
        def build = (m[0][4] ?: "0") as int
        return sprintf("%05d.%05d.%05d.%05d", major, minor, patch, build)
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
          kubectl get configmap global-config -n ${currentConfig.namespace} -o yaml \
          | sed "s/^\\([[:space:]]*admin_group:\\).*/\\1 $newGlobalAdminGroup/" \
          | kubectl apply -n ${currentConfig.namespace} -f -
        """
    }

    void runCypressIntegrationTests(config = [:]) {
        Cypress cypress = new Cypress(this.script, config)

        def ip = getExternalIP()
        def newUrl = "https://$ip"

        def adminPW = script.sh(returnStdout: true, script: "coder ssh $coder_workspace \"kubectl get secret ldap-config -n ${currentConfig.namespace} -o jsonpath='{.data.config\\.yaml}' | base64 --decode | sed 's/^admin_password:[[:space:]]*//'\"").trim().replaceAll(/^'+|'+$/, "")
        def adminUN = script.sh(returnStdout: true, script: "coder ssh $coder_workspace \"kubectl get configmap ldap-config -n ${currentConfig.namespace} -o jsonpath='{.data.config\\.yaml}' | grep '^admin_username:' | cut -d':' -f2 | xargs\"").trim().replaceAll(/^'+|'+$/, "")
        script.withEnv([
                "NEW_URL=${newUrl}",
                "ADMIN_UN=${adminUN}",
                "ADMIN_PW=${adminPW}"
        ]) {
            script.sh '''#!/usr/bin/env bash
              set -eu
              set +x

              sed -i "s|baseUrl: .*|baseUrl: \\"${NEW_URL}\\",|" ./integrationTests/cypress.config.*
              sed -i "s|\\"AdminUsername\\": .*|\\"AdminUsername\\": \\"${ADMIN_UN}\\",|" ./integrationTests/cypress.config.*
              sed -i "s|\\"AdminGroup\\": .*|\\"AdminGroup\\": \\"cesAdmin\\",|" ./integrationTests/cypress.config.*

              pw_escaped=$(printf '%s' "$ADMIN_PW" | sed -e 's/[\\\\&|]/\\\\&/g' -e 's/"/\\\\\\"/g')
              sed -i "s|\\"AdminPassword\\": .*|\\"AdminPassword\\": \\"${pw_escaped}\\",|" ./integrationTests/cypress.config.*
            '''
        }
        try {
            cypress.preTestWork()
            cypress.runIntegrationTests(this)
        } finally {
            cypress.archiveVideosAndScreenshots()
        }
    }

    void createMNParameter(config = [:]) {

        List dogusToAdd = config.additionalDogus
        List componentsToAdd = config.additionalComponents
        def defaultMNParams = """
MN-CES Machine Type: "${config.machineType}"
MN-CES Node Count: "${config.nodeCount}"
CES Namespace: "${config.namespace}"
Ecosystem-Core Chart Namespace: "k8s"
Ecosystem Core Chart Version: "${config.versionEcosystemCore ? config.versionEcosystemCore : getDefaultValueByName("Ecosystem Core Chart Version")}"
Blueprint: ""
Necessary dogus:
  - official/postfix
  - official/ldap
  - official/cas
  - official/usermgt
Additional dogus: []
Component-Operator-CRD: "${config.versionK8SComponentOperatorCrd ? "k8s/k8s-component-operator-crd:${config.versionK8SComponentOperatorCrd}" : getDefaultValueByName("Component-Operator-CRD")}"
Blueprint-Operator-CRD: "${config.versionK8SBlueprintOperatorCrd ? "k8s/k8s-blueprint-operator-crd:${config.versionK8SBlueprintOperatorCrd}" : getDefaultValueByName("Blueprint-Operator-CRD")}"
Enable Backup: ${config.enableBackup ? config.enableBackup : "false"}
Backup components: []
Enable Monitoring: ${config.enableMonitoring ? config.enableMonitoring : "false"}
Monitoring components: []
Base components: []
Disabled components:
${getDefaultValueByNameAsList("Disabled components")}
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

    // coder default-values:
    def getRichParameters(String baseUrl, String orgId, String templateName) {
        def result = []
        script.withCredentials([script.string(credentialsId: "${this.coderCredentials}", variable: 'token')]) {
            def jsonSlurper = new JsonSlurper()

            // 1) get active template_ID (inkl. active_version_id)
            def templateUrl = "${baseUrl}/api/v2/organizations/${orgId}/templates/${templateName}"

            def url = new URL(templateUrl)
            def conn = (HttpURLConnection) url.openConnection()
            conn.setRequestMethod("GET")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Coder-Session-Token", "${script.env.token}")

            if (conn.responseCode != 200) {
                script.error("can not get Template-Metadata: HTTP ${conn.responseCode}")
            }
            def responseText = conn.inputStream.text
            def templateJson = new JsonSlurperClassic().parseText(responseText)

            def versionId = templateJson.active_version_id
            if (!versionId) {
                script.error "Can not get active_version_id for template '${templateName}'"
            }

            // 2) get rich parameter for version
            def paramsUrl = "${baseUrl}/api/v2/templateversions/${versionId}/rich-parameters"
            script.echo "${paramsUrl}"
            url = new URL(paramsUrl)
            conn = (HttpURLConnection) url.openConnection()
            conn.setRequestMethod("GET")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Coder-Session-Token", "${script.env.token}")

            if (conn.responseCode != 200) {
                script.error("can not get rich parameters: HTTP ${conn.responseCode}")
            }
            responseText = conn.inputStream.text
            def paramsJson = new JsonSlurperClassic().parseText(responseText)
            result = paramsJson
        }
        return result
    }

    @NonCPS
    def getDefaultValueByName(String name) {
        def param = coderRichParameters.find { it.name == name }
        return param?.default_value
    }

    @NonCPS
    def getDefaultValueByNameAsList(String name) {
        def param = coderRichParameters.find { it.name == name }
        def listvalue = new JsonSlurperClassic().parseText(param?.default_value)
        return listvalue.collect { "  - ${it}" }.join("\n")
    }

}
