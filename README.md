![logo](resources/images/icon.png)

[![GitHub license](https://img.shields.io/github/license/cloudogu/dogu-build-lib.svg)](https://github.com/cloudogu/dogu-build-lib/blob/master/LICENSE)


# Dogu-build-lib

## About
dogu-build-lib is a shared library for [Jenkins pipelines](https://jenkins.io/doc/book/pipeline/). It adds functionality to interact with the [Cloudogu EcoSystem](https://github.com/cloudogu/ecosystem) and [Vagrant](https://www.vagrantup.com/) in general. Furthermore it supports easy integration of [dockerlint](https://github.com/projectatomic/dockerfile_lint) and [shellcheck](https://github.com/koalaman/shellcheck).

## Get started

- Install Plugin: [GitHub Groovy Libraries](https://wiki.jenkins.io/display/JENKINS/Pipeline+GitHub+Library+Plugin)
- Use in any Jenkinsfile as follows:
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
Ecosystem ecosystem = new EcoSystem(script, String gcloudCredentials, String sshCredentials)
// eg.
EcoSystem ecosystem = new EcoSystem(this, "gcloud-ces-operations-internal-packer", "jenkins-gcloud-ces-operations-internal")
```
#### Functions
- provision(String mountPath) | provision to a specific path 
- loginBackend(String credentialsId)  | login with credentials
- setup(config = [:]) | setup a ces instance based on a setup.json
- waitForDogu(String dogu) | wait until a dogu is ready for interaction
- build(String doguPath) | build a dogu 
- verify(String doguPath) | execute the goss tests
- destroy() | remove the generated ces instance
- collectLogs() | add logs as artifact to jenkins build


### Vagrant
#### Get Started

```groovy
Vagrant vm = new Vagrant(script, gcloudCredentials, sshCredentials)
```


#### Functions
- installPlugins(String plugin) | install additional vagrant plugins
- scp(String source, String target) | copy files to remote machine
- sync() | sync host and remote
- up() | start vm 
- ssh() | connect to vm
- getExternalIP() | get ip for connection
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

This example shows the usage of Ecosystem, lintDockerfile and shellcheck (based on cloudogu/ldap jenkinsfile)

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

See [ces-build-lib](https://github.com/cloudogu/ces-build-lib) for further functions


Icon based on: https://www.kissclipart.com/construction-helmet-icon-clipart-hard-hats-helmet-nv6hoi/ (Creative Commons)
