# Including the Trivy Scanner in Dogu Builds

In order for the Trivy scanner to work, an EcoSystem object must be created. The declaration in the Jenkinsfile can look like this:

```groovy
EcoSystem ecoSystem = new EcoSystem(this, "gcloud-ces-operations-internal-packer", "jenkins-gcloud-ces-operations-internal")
Trivy trivy = new Trivy(this, ecoSystem)
```

Then, the provision function of the EcoSystem must be called to create the Vagrant machine.
Otherwise, it is not possible to perform trivy scans:

```groovy
ecoSystem.provision("/dogu")
```

After that, the trivy scan can be invoked in two ways.

## Scan any image in the Vagrant machine.
Scan of any image.
Except for `image-name`, all other parameters are optional with default values.
The generated reports are automatically archived in Jenkins.

```
trivy.scan(<image-name>, <format>, <level>, <strategy>, <filename>)
```

### image-name
The name of an arbitrary Docker image. For example: `registry.cloudogu.com/official/nginx:1.23.2-4`

### format
The format in which the trivy report must be created.
Valid values are: `html`, `json`, `plain`
respectively: `TrivyScanFormat.HTML`, `TrivyScanFormat.JSON`, `TrivyScanFormat.PLAIN`
Default value: `TrivyScanFormat.HTML`.

### level
Specifies the severity level of the vulnerabilities to be scanned.
A comma-separated list of the desired severity levels. Possible values: `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`, `UNKNOWN`.
Example value: `CRITICAL,HIGH,MEDIUM`
or: `TrivyScanLevel.CRITICAL`, `TrivyScanLevel.HIGH`, `TrivyScanLevel.MEDIUM`, `TrivyScanLevel.ALL`
The values in `TrivyScanLevel` are set up to scan the selected value or higher (`TrivyScanLevel.HIGH=HIGH,CRITICAL`)
Default value: `TrivyScanLevel.CRITICAL`

### strategy
The strategy how to deal with found vulnerabilities.
Valid values are: `fail`, `unstable`, `ignore`
respectively `TrivyScanStrategy.FAIL`, `TrivyScanStrategy.UNSTABLE`, `TrivyScanStrategy.IGNORE`
default value: `TrivyScanStrategy.FAIL`.

#### fail
If a vulnerability is found, the build fails.

#### unstable
If a vulnerability is found, the build becomes unstable.

#### ignore
No matter how many vulnerabilities are found, it will not affect the build.

### filename
The filename of the report to build. If left blank, a default name will be selected.
Please note: If the selected filename does not match the `trivyscan.*` format, the file will not be archived automatically.
In this case `archiveArtifacts <myfilepattern>` should be used to archive the file.
Default value: `null`


## Scan of a Dogu image in the Vagrant machine

Here all parameters except the first one are identical compared to the `scan function`.
The first parameter, `dogu-path`, describes the path to the dogu-source code in the Vagrant machine. This is usually `/dogu`.
The image-name is then made up of
