# Einbinden des Trivy-Scanners in Dogu-Builds

Damit der Trivy-Scanner funktioniert, muss ein EcoSystem-Objekt erstellt werden. Die Deklaration im Jenkinsfile kann so aussehen:

```groovy
EcoSystem ecoSystem = new EcoSystem(this, "gcloud-ces-operations-internal-packer", "jenkins-gcloud-ces-operations-internal")
Trivy trivy = new Trivy(this, ecoSystem)
```

Anschließend muss die provision-Funktion des EcoSystems aufgerufen werden, damit die Vagrant-Maschine erstellt wird.
Andernfalls ist es nicht möglich, trivy-Scans durchzuführen:

```groovy
ecoSystem.provision("/dogu")
```

Danach kann der Trivy-Scan auf zwei Arten aufgerufen werden.

## Scan eines beliebigen Images in der Vagrant-Maschine
Scan eines beliebigen Images.
Abgesehen von `image-name` sind alle weiteren Parameter optional mit Standardwerten.
Die erzeugten Berichte werden automatisch im Jenkins archiviert.

```
trivy.scan(<image-name>, <format>, <level>, <strategy>, <filename>)
```

### image-name
Der Name eines beliebigen Docker-Images. Zum Beispiel: `registry.cloudogu.com/official/nginx:1.23.2-4`

### format
Das Format, in dem der Trivy-Report erstellt werden muss.
Gültige Werte sind: `html`, `json`, `plain`
bzw.: `TrivyScanFormat.HTML`, `TrivyScanFormat.JSON`, `TrivyScanFormat.PLAIN`
Standardwert: `TrivyScanFormat.HTML`

### level
Bestimmt den Schweregrad der Schwachstellen, der überprüft werden soll.
Eine Kommaseparierte Liste aus den gewünschten Schweregraden. Mögliche Werte: `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`, `UNKNOWN`
Beispielwert: `CRITICAL,HIGH,MEDIUM`
bzw.: `TrivyScanLevel.CRITICAL`, `TrivyScanLevel.HIGH`, `TrivyScanLevel.MEDIUM`, `TrivyScanLevel.ALL`
Die Werte in `TrivyScanLevel` sind so aufgebaut, dass sie den ausgewählten Wert oder höher scannen (`TrivyScanLevel.HIGH=HIGH,CRITICAL`)
Standardwert: `TrivyScanLevel.CRITICAL`

### strategy
Die Strategie, wie mit gefundenen Schwachstellen umgegangen werden soll.
Gültige Werte sind: `fail`, `unstable`, `ignore`
bwz. `TrivyScanStrategy.FAIL`, `TrivyScanStrategy.UNSTABLE`, `TrivyScanStrategy.IGNORE`
Standardwert: `TrivyScanStrategy.FAIL`

#### fail
Bei einer gefundenen Schwachstelle schlägt das Build fehl.

#### unstable
Bei einer gefundenen Schwachstelle wird das Build unstable.

#### ignore
Egal wie viele Schwachstellen gefunden werden, es hat keinen Einfluss auf das Build.

### filename
Der Dateiname des zu erstellenden Reports. Wenn er leer gelassen wird, wird ein Standardname ausgewählt.
Bitte Beachten: Sollte der gewählte Dateiname nicht dem Format `trivyscan.*` entsprechen, wird die Datei nicht automatisch archiviert.
In diesem Fall sollte mit `archiveArtifacts <myfilepattern>` die Datei archiviert werden.
Standardwert: `null`


## Scan eines Dogu-Images in der Vagrant-Maschine

Hier sind alle Parameter außer dem ersten identisch im Vergleich zur `scan-Funktion`.
Der erste Parameter, `dogu-path`, beschreibt den Pfad zu dem Dogu-source-code in der Vagrant-Maschine. Das ist normalerweise `/dogu`.
Der Image-name wird dann aus der `dogu.json` geladen. Das Dogu muss aber zuvor gebaut worden sein, damit das Image vorhanden ist.
Abgesehen von `dogu-path` sind alle weiteren Parameter optional mit Standardwerten.
Die erzeugten Berichte werden automatisch im Jenkins archiviert.

```
trivy.scanDogu(<dogu-path>, <format>, <level>, <strategy>, <filename>)
```