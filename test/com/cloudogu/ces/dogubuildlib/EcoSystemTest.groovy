package com.cloudogu.ces.dogubuildlib

import groovy.json.JsonSlurper
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

import static org.mockito.Mockito.*

@RunWith(MockitoJUnitRunner.class)
class EcoSystemTest {

    def mockedScript = [
            writeFile: { args ->
                mockedScript.writenFile = args.text
            }
    ]

    @Mock
    Vagrant vagrant

    private EcoSystem ecoSystem = new EcoSystem(mockedScript, "", "")

    @Before
    void setup() {
        ecoSystem.vagrant = vagrant
    }

    @Test
    void shouldSetupWithDefaults() {
        ecoSystem.setup()
        assert mockedScript.writenFile.contains(
                '"official/registrator", "official/ldap", "official/cas", "official/nginx", "official/postfix", "official/usermgt"')
        assert mockedScript.writenFile.contains('"registryConfig": {}')
    }

    @Test
    void shouldSetupWithCustomParams() {
        ecoSystem.setup(additionalDependencies: 'official/postgresql')
        assert mockedScript.writenFile.contains(
                '"official/registrator", "official/ldap", "official/cas", "official/nginx", "official/postfix", "official/usermgt", "official/postgresql"')
    }

    @Test
    void shouldFormatDependencies() {
        String formatted = ecoSystem.formatDependencies(["registrator", "ldap", "cas"])
        assert formatted == '"registrator", "ldap", "cas"'
    }

    @Test
    void shouldFormatDependenciesToEmptyString() {
        String formatted = ecoSystem.formatDependencies([])
        assert formatted == ''
    }

    @Test
    void shouldFormatStringDependency() {
        String formatted = ecoSystem.formatDependencies(["registrator"])
        assert formatted == '"registrator"'
    }

    @Test
    void shouldJoinDependencies() {
        def config = [dependencies: ["registrator", "ldap"], additionalDependencies: ["cas"]]
        def deps = ecoSystem.joinDependencies(config)
        assert deps == ["registrator", "ldap", "cas"]
    }

    @Test
    void shouldSetupWithCustomRegistryConfig() {
        String expectedRegistryConfig = '''"jenkins":{
            "updateSiteUrl":{
                "default":"http://defaultUpdateSite.de",
            }'''
        ecoSystem.setup(registryConfig: expectedRegistryConfig)
        assert mockedScript.writenFile.contains("\"registryConfig\": {${expectedRegistryConfig}}")
    }

    @Test
    void shouldSetupWithCustomRegistryConfigEncrypted() {
        String expectedRegistryConfig = '''"cas":{
            "registryConfigEncrypted":{
                "oidc":"hahaha",
            }'''
        ecoSystem.setup(registryConfigEncrypted: expectedRegistryConfig)
        assert mockedScript.writenFile.contains("\"registryConfigEncrypted\": {${expectedRegistryConfig}}")
    }

    @Test
    void testIncreaseVersion() {
        String start = "2.222.4-1"
        String expected = "2.222.4-2"
        String result = EcoSystem.increaseDoguReleaseVersionByOne("\"Version\": \"${start}\",")
        assert expected == result

        start = "2.222.4-9"
        expected = "2.222.4-10"
        result = EcoSystem.increaseDoguReleaseVersionByOne("\"Version\": \"${start}\",")
        assert expected == result
    }

    @Test
    void testparseAdditionalIntegrationTestArgs() {
        def input = ['ARG1=value1', 'ARG2=value2']
        String expected = "-e ARG1=value1 -e ARG2=value2"
        String result = EcoSystem.parseAdditionalIntegrationTestArgs(input)
        assert expected == result

        input = []
        expected = ""
        result = EcoSystem.parseAdditionalIntegrationTestArgs(input)
        assert expected == result
    }

    @Test
    void test_EcoSystem_verify_with_default_timeout(){
        // given
        def scriptMock = new ScriptMock()

        def vagrantMock = mock(Vagrant.class)
        doNothing().when(vagrantMock).ssh(any())

        EcoSystem sut = new EcoSystem(scriptMock, "gCloudCred", "sshCred")
        sut.externalIP = "192.168.56.2"
        sut.vagrant = vagrantMock

        // when
        sut.verify("/dogu")

        // then
        verify(vagrantMock).ssh("mkdir -p /tmp/reports")
        verify(vagrantMock).sshOut("cat /tmp/reports/*.xml")
        verify(vagrantMock).ssh("sudo cesapp verify --health-timeout 600 --keep-container --ci --report-directory=/tmp/reports /dogu")
        verifyNoMoreInteractions(vagrantMock)
    }

    @Test
    void test_EcoSystem_verify_with_custom_timeout(){
        // given
        def scriptMock = new ScriptMock()

        def vagrantMock = mock(Vagrant.class)
        doNothing().when(vagrantMock).ssh(any())

        EcoSystem sut = new EcoSystem(scriptMock, "gCloudCred", "sshCred")
        sut.externalIP = "192.168.56.2"
        sut.vagrant = vagrantMock

        // when
        sut.verify("/dogu", 1200)

        // then
        verify(vagrantMock).ssh("mkdir -p /tmp/reports")
        verify(vagrantMock).sshOut("cat /tmp/reports/*.xml")
        verify(vagrantMock).ssh("sudo cesapp verify --health-timeout 1200 --keep-container --ci --report-directory=/tmp/reports /dogu")
        verifyNoMoreInteractions(vagrantMock)
    }

    @Test
    void test_EcoSystem_restartDogu_waitForDogu() {
        // given
        def scriptMock = new ScriptMock()
        scriptMock.expectedShRetValueForScript.put("curl --insecure --silent --head https://192.168.56.2/jenkins | head -n 1", "302")

        def vagrantMock = mock(Vagrant.class)
        doNothing().when(vagrantMock).ssh(any())

        EcoSystem sut = new EcoSystem(scriptMock, "gCloudCred", "sshCred")
        sut.externalIP = "192.168.56.2"
        sut.vagrant = vagrantMock

        // when
        sut.restartDogu("jenkins")

        // then
        verify(vagrantMock).ssh("sudo docker restart jenkins")
        verify(vagrantMock).ssh("sudo cesapp healthy --wait --timeout 1200 --fail-fast jenkins")
        verifyNoMoreInteractions(vagrantMock)
    }

    @Test
    void test_EcoSystem_restartDogu_noWaitForDogu() {
        // given
        def scriptMock = new ScriptMock()
        scriptMock.expectedShRetValueForScript.put("curl --insecure --silent --head https://192.168.56.2/jenkins | head -n 1", "302")

        def vagrantMock = mock(Vagrant.class)
        doNothing().when(vagrantMock).ssh(any())

        EcoSystem sut = new EcoSystem(scriptMock, "gCloudCred", "sshCred")
        sut.externalIP = "192.168.56.2"
        sut.vagrant = vagrantMock

        // when
        sut.restartDogu("jenkins", false)

        // then
        verify(vagrantMock).ssh("sudo docker restart jenkins")
        verifyNoMoreInteractions(vagrantMock)
    }

    @Test
    void test_EcoSystem_upgrade() {
        // given
        def scriptMock = new ScriptMock()
        scriptMock.expectedShRetValueForScript.put("grep .Version dogu.json", "\"Version\": \"1.2.3-4\",")
        def jsonSlurper = new JsonSlurper()
        def doguJson = jsonSlurper.parseText('{ "Name": "testing/dogu", "Version": "1.2.3-4" }')
        scriptMock.jsonFiles.put("dogu.json", doguJson)

        EcoSystem sut = new EcoSystem(scriptMock, "gCloudCred", "sshCred")

        def vagrantMock = mock(Vagrant.class)
        doNothing().when(vagrantMock).sync()
        doNothing().when(vagrantMock).ssh(any())

        sut.vagrant = vagrantMock

        // when
        sut.upgradeDogu()

        // then
        verify(vagrantMock).sync()
        verify(vagrantMock).ssh("sudo cesapp build /dogu")
        verifyNoMoreInteractions(vagrantMock)
    }
}
