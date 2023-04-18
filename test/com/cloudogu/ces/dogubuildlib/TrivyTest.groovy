package com.cloudogu.ces.dogubuildlib

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

import static org.mockito.ArgumentMatchers.matches
import static org.mockito.Mockito.doReturn

@RunWith(MockitoJUnitRunner.class)
class TrivyTest {
    def mockedScript = [
            writeFile       : { args ->
                mockedScript.writenFileRes = args.text
            },
            sh              : { args ->
                mockedScript.shRes = args
            },
            archiveArtifacts: { args ->
                mockedScript.archiveArtifactsRes = args
            }
    ]

    @Mock
    Vagrant vagrant

    private EcoSystem ecoSystem = new EcoSystem(mockedScript, "", "")
    private Trivy trivy = new Trivy(mockedScript, ecoSystem)

    @Before
    void setup() {
        ecoSystem.vagrant = vagrant
        ecoSystem.setup()
    }


    @Test
    void scanShouldScanHtmlPerDefaultAndAsParam() {
        doReturn("1")
                .when(vagrant)
                .sshOut(matches(/.*trivyscan\.txt.*/))
        doReturn("1")
                .when(vagrant)
                .sshOut(matches(/.*trivyscan\.json.*/))
        doReturn("0")
                .when(vagrant)
                .sshOut(matches(/.*trivyscan\.html.*/))
        trivy.scan("myimage")
        trivy.scan("myimage", "html")
        trivy.scan("myimage", "HtMl")
        trivy.scan("myimage", "HTML")
        trivy.scan("myimage", TrivyScanFormat.HTML)
    }

    @Test
    void scanShouldAcceptJsonAsParam() {
        doReturn("1")
                .when(vagrant)
                .sshOut(matches(/.*trivyscan\.txt.*/))
        doReturn("0")
                .when(vagrant)
                .sshOut(matches(/.*trivyscan\.json.*/))
        doReturn("1")
                .when(vagrant)
                .sshOut(matches(/.*trivyscan\.html.*/))
        trivy.scan("myimage", "json")
        trivy.scan("myimage", "jSoN")
        trivy.scan("myimage", "JSON")
        trivy.scan("myimage", TrivyScanFormat.JSON)
    }

    @Test
    void scanShouldAcceptPlainAsParamAndFallback() {
        doReturn("0")
                .when(vagrant)
                .sshOut(matches(/.*trivyscan\.txt.*/))
        doReturn("1")
                .when(vagrant)
                .sshOut(matches(/.*trivyscan\.json.*/))
        doReturn("1")
                .when(vagrant)
                .sshOut(matches(/.*trivyscan\.html.*/))
        trivy.scan("myimage", "plain")
        trivy.scan("myimage", "pLaIn")
        trivy.scan("myimage", "PLAIN")
        trivy.scan("myimage", TrivyScanFormat.PLAIN)
        trivy.scan("myimage", "random")
    }

}
