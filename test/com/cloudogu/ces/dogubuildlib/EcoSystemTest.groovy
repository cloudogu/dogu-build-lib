package com.cloudogu.ces.dogubuildlib

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

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
        def config = [ dependencies: ["registrator", "ldap"], additionalDependencies: ["cas"] ]
        def deps = ecoSystem.joinDependencies(config)
        assert deps == [ "registrator", "ldap", "cas" ]
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
    void testIncreaseVersion(){
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
    void testparseAdditionalIntegrationTestArgs(){
        def input = ['ARG1=value1', 'ARG2=value2']
        String expected = "-e ARG1=value1 -e ARG2=value2"
        String result = EcoSystem.parseAdditionalIntegrationTestArgs(input)
        assert expected == result

        input = []
        expected = ""
        result = EcoSystem.parseAdditionalIntegrationTestArgs(input)
        assert expected == result
    }

}
