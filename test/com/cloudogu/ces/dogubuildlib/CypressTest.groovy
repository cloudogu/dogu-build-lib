package com.cloudogu.ces.dogubuildlib

import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import static org.mockito.Mockito.*
import static org.assertj.core.api.Assertions.*

@RunWith(MockitoJUnitRunner.class)
class CypressTest {
    def cypressConfigJs = new File('./testdata/exampleCypressjs.config.js').text
    def cypressConfigJsExpected = new File('./testdata/exampleCypressjs.expected.config.js').text
    def cypressConfigTs = new File('./testdata/exampleCypressts.config.ts').text
    def cypressConfigTsExpected = new File('./testdata/exampleCypressts.expected.config.ts').text

    def mockedScript = [
            echoList              : [],
            echo                  : { args ->
                mockedScript.echoList <<= args
            },
            errorList             : [],
            error                 : { args ->
                mockedScript.errorList <<= args
            },
            shList                : [],
            sh                    : { args ->
                if (args instanceof HashMap && args.script) {
                    mockedScript.shList <<= args.script
                    if (args.returnStdout) {
                        if (args.script == "whoami") {
                            return "jenkins"
                        } else if (args.script == "cat /etc/passwd | grep jenkins") {
                            return "jenkins:x:1000:1000::/home/jenkins:/bin/sh"
                        }
                        return args.script
                    }
                }
                mockedScript.shList <<= args
                return ""
            },
            junitConfig           : [:],
            junit                 : { args ->
                mockedScript.junitConfig <<= args
            },
            archiveArtifactsConfig: [[:]],
            archiveArtifacts      : { args ->
                mockedScript.archiveArtifactsConfig <<= args
            },
            writenFileInfo        : [:],
            writeFile             : { args ->
                mockedScript.writenFileInfo <<= args
            },
            pwdCalled             : 0,
            pwd                   : { ->
                mockedScript.pwdCalled++
                return "/home/jenkins"
            },
            timeoutInfo           : [],
            timeout               : { args, Closure c ->
                mockedScript.timeoutInfo <<= args
                c.call()
            },
            docker                : [
                    dockerUsedImage: "",
                    image          : { args ->
                        mockedScript.docker.dockerUsedImage = args
                        return mockedScript.docker
                    },
                    dockerArgs     : "",
                    inside         : { args, Closure c ->
                        mockedScript.docker.dockerArgs = args
                        c.call()
                    },
            ]
    ]

    @Mock
    EcoSystem ecoSystem


    @Test
    void testDefaultConfig() {
        // given
        //when
        Cypress cypress = new Cypress(mockedScript)

        // then
        assert cypress.config == Cypress.defaultIntegrationTestsConfig
    }

    @Test
    void testMergeConfig() {
        // given
        def config = [
                "enableVideo"      : false,
                "enableScreenshots": false]

        // when
        Cypress cypress = new Cypress(mockedScript, config)

        // then
        assert cypress.config != Cypress.defaultIntegrationTestsConfig
        assert cypress.config.enableVideo != Cypress.defaultIntegrationTestsConfig.enableVideo
        assert cypress.config.enableScreenshots != Cypress.defaultIntegrationTestsConfig.enableScreenshots
    }

    @Test
    void testPreWork() {
        // given
        Cypress cypress = new Cypress(mockedScript)

        // when
        cypress.preTestWork()

        // then
        assert mockedScript.echoList[0] == "cleaning up previous test results..."
        assert mockedScript.shList[0] == "rm -rf integrationTests/cypress/videos"
        assert mockedScript.shList[1] == "rm -rf integrationTests/cypress/screenshots"
        assert mockedScript.shList[2] == "rm -rf integrationTests/cypress-reports"
    }

    @Test
    void testAchieveArtifactsWithEmptyConfig() {
        // given
        def config = [:]
        Cypress cypress = new Cypress(mockedScript, config)

        // when
        cypress.archiveVideosAndScreenshots()

        // then
        assert mockedScript.echoList[0] == "archiving videos and screenshots from test execution..."
        assert mockedScript.junitConfig.allowEmptyResults == true
        assert mockedScript.junitConfig.testResults == "integrationTests/cypress-reports/TEST-*.xml"
        assert mockedScript.archiveArtifactsConfig[1].allowEmptyArchive == true
        assert mockedScript.archiveArtifactsConfig[1].artifacts == "integrationTests/cypress/videos/**/*.mp4"
        assert mockedScript.archiveArtifactsConfig[2].allowEmptyArchive == true
        assert mockedScript.archiveArtifactsConfig[2].artifacts == "integrationTests/cypress/screenshots/**/*.png"
    }

    @Test
    void testAchieveArtifactsWithNoVideoAndScreenshots() {
        // given
        def config = [
                "enableVideo"      : false,
                "enableScreenshots": false]
        Cypress cypress = new Cypress(mockedScript, config)

        // when
        cypress.archiveVideosAndScreenshots()

        // then
        assert mockedScript.echoList[0] == "archiving videos and screenshots from test execution..."
        assert mockedScript.junitConfig.allowEmptyResults == true
        assert mockedScript.junitConfig.testResults == "integrationTests/cypress-reports/TEST-*.xml"
        assert mockedScript.archiveArtifactsConfig.size() == 1
    }

    @Test
    void testAchieveArtifactsWithOnlyScreenshots() {
        // given
        def config = [
                "enableVideo": false]
        Cypress cypress = new Cypress(mockedScript, config)

        // when
        cypress.archiveVideosAndScreenshots()

        // then
        assert mockedScript.echoList[0] == "archiving videos and screenshots from test execution..."
        assert mockedScript.junitConfig.allowEmptyResults == true
        assert mockedScript.junitConfig.testResults == "integrationTests/cypress-reports/TEST-*.xml"
        assert mockedScript.archiveArtifactsConfig.size() == 2
        assert mockedScript.archiveArtifactsConfig[1].allowEmptyArchive == true
        assert mockedScript.archiveArtifactsConfig[1].artifacts == "integrationTests/cypress/screenshots/**/*.png"
    }

    @Test
    void testReadJenkinsUserFromEtcPasswd() {
        // given
        Cypress cypress = new Cypress(mockedScript)

        // when
        def value = cypress.readJenkinsUserFromEtcPasswd()

        // then
        assert value == "jenkins:x:1000:1000::/home/jenkins:/bin/sh"
        assert mockedScript.shList[0] == "whoami"
        assert mockedScript.shList[1] == "cat /etc/passwd | grep jenkins"
    }

    @Test
    void testReadJenkinsUserFromEtcPasswdCutOffAfterGroupId() {
        // given
        Cypress cypress = new Cypress(mockedScript)
        def etcPasswd = "test:x:900:1001::/home/test:/bin/sh"
        def expectedValue = "test:x:900:1001:"

        // when
        def value = cypress.readJenkinsUserFromEtcPasswdCutOffAfterGroupId(etcPasswd)

        // then
        assert value == expectedValue
    }

    @Test
    void testFailReadJenkinsUserFromEtcPasswdCutOffAfterGroupId() {
        // given
        Cypress cypress = new Cypress(mockedScript)
        def etcPasswd = "This is an invalid input"
        def expectedError = "/etc/passwd entry for current user does not match user:x:uid:gid:"

        // when
        def value = cypress.readJenkinsUserFromEtcPasswdCutOffAfterGroupId(etcPasswd)

        // then
        assert mockedScript.errorList[0] == expectedError
        assert value == ""
    }

    @Test
    void testWritePasswd() {
        // given
        Cypress cypress = new Cypress(mockedScript)

        // when
        cypress.writePasswd()

        // then
        assert mockedScript.shList[0] == "whoami"
        assert mockedScript.shList[1] == "cat /etc/passwd | grep jenkins"
        assert mockedScript.pwdCalled == 1
        assert mockedScript.writenFileInfo.file == ".jenkins/etc/passwd"
        assert mockedScript.writenFileInfo.text == "jenkins:x:1000:1000::/home/jenkins:/bin/sh"
    }

    @Test
    void testRunCypressIntegrationTests() {
        // given
        Cypress cypress = new Cypress(mockedScript)
        when(ecoSystem.getExternalIP()).thenReturn("192.168.56.2")

        // when
        cypress.runIntegrationTests(ecoSystem)

        // then
        assert mockedScript.timeoutInfo.time == [15]
        assert mockedScript.timeoutInfo.unit == ["MINUTES"]
        assert mockedScript.shList[0] == "whoami"
        assert mockedScript.shList[1] == "cat /etc/passwd | grep jenkins"
        assert mockedScript.pwdCalled == 2
        assert mockedScript.writenFileInfo.file == ".jenkins/etc/passwd"
        assert mockedScript.writenFileInfo.text == "jenkins:x:1000:1000::/home/jenkins:/bin/sh"
        assert mockedScript.docker.dockerUsedImage == Cypress.defaultIntegrationTestsConfig.cypressImage
        assert mockedScript.docker.dockerArgs == "--ipc=host -e CYPRESS_BASE_URL=https://192.168.56.2 --entrypoint='' -v /home/jenkins/.jenkins/etc/passwd:/etc/passwd:ro "
        assert mockedScript.shList[2].contains("cd integrationTests/ && yarn install && yarn cypress run -q --headless --config screenshotOnRunFailure=true --config video=true --reporter junit --reporter-options mochaFile=cypress-reports/")
    }

    //--ipc=host -e CYPRESS_BASE_URL=https://192.168.56.2 --entrypoint='' -v /home/jenkins/.jenkins/etc/passwd:/etc/passwd:ro
    //--ipc=host -e CYPRESS_BASE_URL=https://192.168.56.2 --entrypoint='' -v /home/jenkins/.jenkins/etc/passwd:/etc/passwd:ro
    @Test
    void testRunCypressIntegrationTestsWithCustomConfig() {
        // given
        String expectedImage = "cypress/base:1.0.0"
        boolean expectedRecordScreenshot = false
        boolean expectedRecordVideo = false
        int expectedTimeoutInMinutes = 10
        String expectedCypressArgs = "-e NEW_ENVIRONMENT_VARIABLE='TestContent'"
        String expectedDockerArgs = "--testArg"
        def config = [
                "cypressImage": expectedImage,
                "enableVideo"          : expectedRecordVideo,
                "enableScreenshots"    : expectedRecordScreenshot,
                "timeoutInMinutes"     : expectedTimeoutInMinutes,
                "additionalDockerArgs" : expectedDockerArgs,
                "additionalCypressArgs": expectedCypressArgs
        ]
        Cypress cypress = new Cypress(mockedScript, config)
        when(ecoSystem.getExternalIP()).thenReturn("192.168.56.2")

        // when
        cypress.runIntegrationTests(ecoSystem)

        // then
        assert mockedScript.timeoutInfo.time == [expectedTimeoutInMinutes]
        assert mockedScript.timeoutInfo.unit == ["MINUTES"]
        assert mockedScript.shList[0] == "whoami"
        assert mockedScript.shList[1] == "cat /etc/passwd | grep jenkins"
        assert mockedScript.pwdCalled == 2
        assert mockedScript.writenFileInfo.file == ".jenkins/etc/passwd"
        assert mockedScript.writenFileInfo.text == "jenkins:x:1000:1000::/home/jenkins:/bin/sh"
        assert mockedScript.docker.dockerUsedImage == expectedImage
        assert mockedScript.docker.dockerArgs == "--ipc=host -e CYPRESS_BASE_URL=https://192.168.56.2 --entrypoint='' -v /home/jenkins/.jenkins/etc/passwd:/etc/passwd:ro ${expectedDockerArgs}"
        assert mockedScript.shList[2].contains("cd integrationTests/ && yarn install && yarn cypress run -q --headless")
        assert mockedScript.shList[2].contains(" --reporter junit --reporter-options mochaFile=cypress-reports/")
        assert mockedScript.shList[2].contains(" --config screenshotOnRunFailure=${expectedRecordScreenshot}")
        assert mockedScript.shList[2].contains(" --config video=${expectedRecordVideo}")
        assert mockedScript.shList[2].contains(" ${expectedCypressArgs}")
    }

    @Test
    void test_Cypress_updateCypressConfiguration_noCypressAvailable() {
        // given
        ScriptMock scriptMock = new ScriptMock()
        Cypress cypress = new Cypress(scriptMock)
        Vagrant vagrantMock= mock(Vagrant.class)

        // when
        cypress.updateCypressConfiguration(vagrantMock)

        // then
        assertThat(scriptMock.actualEcho).isEmpty()
        assertThat(scriptMock.existingFiles).isEmpty()
        assertThat(scriptMock.actualShMapArgs).isEmpty()
        assertThat(scriptMock.actualShStringArgs).isEmpty()
    }

    @Test
    void test_Cypress_updateCypressConfiguration() {
        // given
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
        ScriptMock scriptMock = new ScriptMock()
        scriptMock.existingFiles.add("integrationTests/cypress.json")
        scriptMock.jsonFiles.put("integrationTests/cypress.json", cypressJson)
        scriptMock.files.put("integrationTests/cypress.json", cypressJson.toString())

        Cypress cypress = new Cypress(scriptMock)

        def vagrantMock = mock(Vagrant.class)
        doReturn("myNewAdminGroupYeah").when(vagrantMock).sshOut("etcdctl get /config/_global/admin_group")

        // when
        cypress.updateCypressConfiguration(vagrantMock)

        // then
        assertThat(scriptMock.writeFileParams.get(0)["text"]).isEqualTo("[baseUrl:https://192.168.56.2, env:[DoguName:jenkins, MaxLoginRetries:3, AdminUsername:ces-admin, AdminPassword:ecosystem2016, AdminGroup:myNewAdminGroupYeah]]")

        verify(vagrantMock).sshOut("etcdctl get /config/_global/admin_group")
        verifyNoMoreInteractions(vagrantMock)
    }

    @Test
    void test_Cypress_updateCypressConfiguration_newCypress_JS() {
        // given
        ScriptMock scriptMock = new ScriptMock()
        scriptMock.existingFiles.add("integrationTests/cypress.config.js")
        scriptMock.files.put("integrationTests/cypress.config.js", cypressConfigJs)

        Cypress cypress = new Cypress(scriptMock)

        def vagrantMock = mock(Vagrant.class)
        doReturn("myNewAdminGroupYeah").when(vagrantMock).sshOut("etcdctl get /config/_global/admin_group")

        // when
        cypress.updateCypressConfiguration(vagrantMock)

        // then
        assertThat(scriptMock.writeFileParams.get(0)["text"]).isEqualTo(cypressConfigJsExpected)

        verify(vagrantMock).sshOut("etcdctl get /config/_global/admin_group")
        verifyNoMoreInteractions(vagrantMock)
    }

    @Test
    void test_Cypress_updateCypressConfiguration_newCypress_TS() {
        // given
        ScriptMock scriptMock = new ScriptMock()
        scriptMock.existingFiles.add("integrationTests/cypress.config.ts")
        scriptMock.files.put("integrationTests/cypress.config.ts", cypressConfigTs)

        Cypress cypress = new Cypress(scriptMock)

        def vagrantMock = mock(Vagrant.class)
        doReturn("myNewAdminGroupYeah").when(vagrantMock).sshOut("etcdctl get /config/_global/admin_group")

        // when
        cypress.updateCypressConfiguration(vagrantMock)

        // then
        assertThat(scriptMock.writeFileParams.get(0)["text"]).isEqualTo(cypressConfigTsExpected)

        verify(vagrantMock).sshOut("etcdctl get /config/_global/admin_group")
        verifyNoMoreInteractions(vagrantMock)
    }
}
