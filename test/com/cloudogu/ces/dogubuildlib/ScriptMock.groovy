package com.cloudogu.ces.dogubuildlib

import groovy.json.JsonSlurper

class ScriptMock {
    def env = [WORKSPACE: "", HOME: ""]

    boolean expectedIsPullRequest = false
    boolean unstable = false
    String unstableMsg = ""
    def expectedQGate
    def expectedPwd

    /** Used when no value in expectedShRetValueForScript matches **/
    def expectedDefaultShRetValue
    def expectedShRetValueForScript = [:]
    Map<String, Throwable> expectedShCommandToThrow = [:]

    String actualSonarQubeEnv

    Map actualTimeoutParams = [:]

    List<Map> actualUsernamePasswordArgs = []
    List<String> actualShStringArgs = new LinkedList<>()
    List<String> allActualArgs = new LinkedList<>()
    List<String> actualEcho = new LinkedList<>()
    float milliSecondsSleeped = 0

    LinkedHashMap<Object, Object> actualJUnitFlags = new LinkedHashMap<>()

    List<String> actualShMapArgs = new LinkedList<>()

    List<Map<String, String>> writeFileParams = new LinkedList<>()
    Map actualFileArgs
    Map actualStringArgs
    Map files = new HashMap<String, String>()
    List<String> existingFiles = new LinkedList<String>()
    List<List<String>> actualWithEnv = []

    Map<String, Object> jsonFiles = new HashMap<>()

    String actualDir
    def actualGitArgs

    ScriptMock() {
    }

    String sh(String args) {
        actualShStringArgs.add(args.toString())
        allActualArgs.add(args.toString())
        return getReturnValueFor(args)
    }

    void junit(LinkedHashMap<Object, Object> map = [:]) {
        actualJUnitFlags = map
    }

    String sh(Map<String, Object> args) {
        actualShMapArgs.add(args.script.toString())
        allActualArgs.add(args.script.toString())

        return getReturnValueFor(args.get('script'))
    }

    private Object getReturnValueFor(Object arg) {
        // toString() to make Map also match GStrings
        def error = expectedShCommandToThrow.get(arg.toString().trim())
        if (error != null) {
            throw error
        }

        def value = expectedShRetValueForScript.get(arg.toString().trim())
        if (value == null) {
            return expectedDefaultShRetValue
        }
        if (value instanceof List) {
            // If an exception is thrown here that means that less list items have been passed to
            // expectedShRetValueForScript.put('shell command', List) than actual calls to 'shell command'.
            // That is, you have to add more items!
            return ((List) value).removeAt(0)
        } else {
            return value
        }
    }

    boolean isPullRequest() {
        return expectedIsPullRequest
    }

    def timeout(Map params, closure) {
        actualTimeoutParams = params
        return closure.call()
    }

    def waitForQualityGate() {
        return expectedQGate
    }

    def unstable() {
        unstable = true
    }

    void withSonarQubeEnv(String sonarQubeEnv, Closure closure) {
        actualSonarQubeEnv = sonarQubeEnv
        closure.call()
    }

    void withEnv(List<String> env, Closure closure) {
        actualWithEnv.add(env)
        closure.call()
    }

    static def withCredentials(Closure closure) {
        closure.call()
    }

    void usernamePassword(Map args) {
        actualUsernamePasswordArgs.add(args)
    }

    void file(Map args) {
        actualFileArgs = args
    }

    void string(Map args) {
        actualStringArgs = args
    }

    static void error(String args) {
        throw new RuntimeException(args)
    }

    void echo(String msg) {
        actualEcho.add(msg)
    }

    String pwd() { expectedPwd }

    def git(def args) {
        actualGitArgs = args
        return args
    }

    void writeFile(Map<String, String> params) {
        writeFileParams.add(params)
    }

    String readFile(String file) {
        return files.get(file)
    }

    boolean fileExists(String file) {
        return existingFiles.contains(file)
    }

    String readFile(Map<String, String> args) {
        String path = args.get("file")
        if (path != null) {
            return files.get(path)
        }

        throw new InputMismatchException()
    }

    void sleep(seconds) {
        milliSecondsSleeped += seconds
    }

    Object readJSON(Map<String, Object> args) {
        String text = args.get('text')
        if (text != null) {
            def slurper = new JsonSlurper()
            return slurper.parseText(text)
        }

        String path = args.get("file")
        if (path != null) {
            return jsonFiles.get(path)
        }

        throw new InputMismatchException()
    }

    void writeJSON(Map<String, Object> args) {
        String path = args.get("file")
        String json = args.get('json')
        if (json != null && path != null) {
            jsonFiles.put(path, json)
            return
        }

        throw new InputMismatchException()
    }

    void dir(String dir, Closure closure) {
        actualDir = dir
        closure.call()
    }

    def getActualWithEnv() {
        actualWithEnv.isEmpty() ? null : actualWithEnv[actualWithEnv.size() - 1]
    }

    Map<String, String> actualWithEnvAsMap(int index = actualWithEnv.size() - 1) {
        if (index < 0) {
            return null
        }
        actualWithEnv[index].collectEntries { [it.split('=')[0], it.split('=')[1]] }
    }
}