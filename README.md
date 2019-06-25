<img src="./resources/images/icon.png" alt="dogu build lib" height="100px">


# Dogu-build-lib

## About
dogu-build-lib is a shared library for [jenkins pipelines](https://jenkins.io/doc/book/pipeline/). It adds functionality to interact with the [cloudogu ecosystem](https://github.com/cloudogu/ecosystem) and [vagrant](https://www.vagrantup.com/) in general. Furthermore it gives easy integration for [dockerlint](https://github.com/projectatomic/dockerfile_lint) and [shellcheck](https://github.com/koalaman/shellcheck).

## Get started

- Install Pipeline: [GitHub Groovy Libraries](https://wiki.jenkins.io/display/JENKINS/Pipeline+GitHub+Library+Plugin)
- Use in any Jenkinsfile as followed:
```groovy
@Library('github.com/cloudogu/dogu-build-lib@<COMMIT-ID>')
import com.cloudogu.ces.dogubuildlib.*
```



## Features
- [Ecosystem](#Ecosystem)
- [Vagrant](#Vagrant)
- [Dockerlint](#DockerLint)
- [ShellCheck](#ShellCheck)
### Ecosystem
#### Get Started
```groovy
Ecosystem es = new EcoSystem(script, String gcloudCredentials, String sshCredentials) 
// eg.
EcoSystem es = new EcoSystem(this, "gcloud-ces-operations-internal-packer", "jenkins-gcloud-ces-operations-internal")
```
#### Functions
- provision(String mountPath) | provision to a specific path 
- loginBackend(String credentialsId)  | login with credentials
- setup(config = [:]) | setup a ces instance based on a setup.json
- waitForDogu(String dogu) | wait until a dogu is ready for interaction
- build(String doguPath) | build a dogu 
- verify(String doguPath) | executes the goss tests  
- destroy() | removes the generated ces instance
- collectLogs() | adds logs as artifact to jenkins build


### Vagrant
#### Get Started

```groovy
Vagrant vm = new Vagrant(script, gcloudCredentials, sshCredentials)
```


#### Functions
- installPlugins(String plguin) | install additional vagrant plugins
- scp(String source, String target) | copy files to remote machine
- sync() | sync with host and remote
- up() | start vm 
- ssh() | connect to vm
- getExternalIP() | get accessable ip
- sshOut(String command) | execute command on vm
- destroy() | remove vm with all data

### DockerLint

```groovy
lintDockerfile() // uses Dockerfile as default; optional parameter
```

### ShellCheck
```groovy
shellCheck() // search for all .sh files in folder and runs shellcheck
shellCheck(fileList) // fileList="a.sh b.sh" execute shellcheck on a custom list
```


### Samples

#### Sample Pipeline Script
¹ see [ces-build-lib](https://github.com/cloudogu/ces-build-lib) for further functions

² this example shows the usage of Ecosystem, lintDockerfile and shellcheck (based on cloudogu/ldap jenkinsfile)

```groovy
#!groovy
@Library(['github.com/cloudogu/ces-build-lib@c622273', 'github.com/cloudogu/dogu-build-lib@f8cca7c9b101ed0bcdde8df556c13711d4cfd5a5'])
import com.cloudogu.ces.cesbuildlib.*
import com.cloudogu.ces.dogubuildlib.*

node('docker'){
        stage('Checkout') {
            checkout scm
        }

        stage('Lint') {
            lintDockerfile()
        }

        stage('Shellcheck'){
           shellCheck()
    }
}
node('vagrant') {

    timestamps{
        properties([
                // Keep only the last x builds to preserve space
                buildDiscarder(logRotator(numToKeepStr: '10')),
                // Don't run concurrent builds for a branch, because they use the same workspace directory
                disableConcurrentBuilds()
        ])

        EcoSystem ecoSystem = new EcoSystem(this, "gcloud-ces-operations-internal-packer", "jenkins-gcloud-ces-operations-internal")


        try {

            stage('Provision') {
                ecoSystem.provision("/dogu");
            }

            stage('Setup') {
                ecoSystem.loginBackend('cesmarvin-setup')
                ecoSystem.setup()
            }

            stage('Build') {
                ecoSystem.build("/dogu")
            }

            stage('Verify') {
                ecoSystem.verify("/dogu")
            }

        } finally {
            stage('Clean') {
                ecoSystem.destroy()
            }
        }
    }
}
```

Icon based on: https://www.kissclipart.com/construction-helmet-icon-clipart-hard-hats-helmet-nv6hoi/
