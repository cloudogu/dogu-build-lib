package com.cloudogu.ces.dogubuildlib

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

import static org.assertj.core.api.Assertions.assertThat
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
    void test_EcoSystem_prepareGlobalAdminGroupChangeTest() {
        // given
        def scriptMock = new ScriptMock()
        def cypressJson = [
                "baseUrl": "https://192.168.56.2",
                "env"    : [
                        "DoguName"       : "jenkins",
                        "MaxLoginRetries": 3,
                        "AdminUsername"  : "ces-admin",
                        "AdminPassword"  : "ecosystem2016",
                        "AdminGroup"     : "CesAdministrators"
                ]
        ]
        scriptMock.jsonFiles.put("integrationTests/cypress.json", cypressJson)
        scriptMock.files.put("integrationTests/cypress.json", cypressJson.toString())
        scriptMock.expectedShRetValueForScript.put("curl --insecure --silent --head https://192.168.56.2/jenkins | head -n 1", "302")

        EcoSystem sut = new EcoSystem(scriptMock, "gCloudCred", "sshCred")
        sut.externalIP = "192.168.56.2"

        def vagrantMock = mock(Vagrant.class)
        doNothing().when(vagrantMock).ssh(any())
        sut.vagrant = vagrantMock

        // when
        sut.prepareGlobalAdminGroupChangeTest("jenkins")

        // then
        assertThat(scriptMock.allActualArgs.get(0)).isEqualTo("curl -u ces-admin:ecosystem2016 --insecure -X POST https://192.168.56.2/usermgt/api/groups -H 'accept: */*' -H 'Content-Type: application/json' -d '{\"description\": \"New admin group for testing\", \"members\": [\"ces-admin\"], \"name\": \"newTestingAdminGroup\"}'")
        assertThat(scriptMock.writeFileParams.get(0)["text"]).isEqualTo("[baseUrl:https://192.168.56.2, env:[DoguName:jenkins, MaxLoginRetries:3, AdminUsername:ces-admin, AdminPassword:ecosystem2016, AdminGroup:newTestingAdminGroup]]")

        verify(vagrantMock).ssh("etcdctl set /config/_global/admin_group newTestingAdminGroup")
        verify(vagrantMock).ssh("sudo docker restart jenkins")
        verify(vagrantMock).ssh("sudo cesapp healthy --wait --timeout 1200 --fail-fast jenkins")
        verifyNoMoreInteractions(vagrantMock)
    }

}
