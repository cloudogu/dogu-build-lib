package com.cloudogu.ces.dogubuildlib

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

import static org.mockito.ArgumentMatchers.any
import static org.mockito.ArgumentMatchers.matches
import static org.mockito.Mockito.doReturn
import static org.mockito.Mockito.times
import static org.mockito.Mockito.verify

@RunWith(MockitoJUnitRunner.class)
class TrivyTest {
    def mockedScript = [
            writenFileRes      : "",
            writeFile          : { args ->
                mockedScript.writenFileRes = args.text
            },
            shRes              : "",
            sh                 : { args ->
                mockedScript.shRes = args
            },
            archiveArtifactsRes: "",
            archiveArtifacts   : { args ->
                mockedScript.archiveArtifactsRes = args
            },
            unstableCalled     : false,
            unstableRes     : "",
            unstable           : { args ->
                mockedScript.unstableCalled = true
                mockedScript.unstableRes = args
            },
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

    @Test
    void scanShouldUseCorrectFileNameWhenArgPassed() {
        trivy.scan("myimage", "plain", "critical", "ignore", "myfilename.fileending")
        verify(vagrant, times(1)).sshOut(matches(/.*[^ ] --output \/output\/myfilename.fileending [^ ].*/))
        // --output should only occur once
        verify(vagrant, times(1)).sshOut(matches(/^(?:(?!\ --output\ ).)*\ --output\ (?!.*\ --output\ ).*$/))
    }

    @Test
    void scanFailsWithStrategyFail(){
        doReturn("1").when(vagrant).sshOut(any())
        try {
            mockedScript.unstableCalled = false
            mockedScript.unstableRes == ""
            trivy.scan("myimage", "plain", "critical", "fail")
            fail("The scan did not fail but should have failed")
        }
        catch (TrivyScanException e){
            assert e.message.equals("The trivy scan found vulnerabilities")
            assert mockedScript.unstableCalled == false
        }
    }

    @Test
    void scanMakesBuildUnstableWithScanStrategyUnstable(){
        doReturn("1").when(vagrant).sshOut(any())
        mockedScript.unstableCalled = false
        trivy.scan("myimage", "plain", "critical", "unstable")
        assert mockedScript.unstableCalled == true
        assert mockedScript.unstableRes == "The trivy scan found vulnerabilities"
    }

    @Test
    void scanIgnoresErrorsWithStrategyIgnore(){
        doReturn("1").when(vagrant).sshOut(any())
        mockedScript.unstableCalled = false
        trivy.scan("myimage", "plain", "critical", "ignore")
        assert mockedScript.unstableCalled == false
        assert mockedScript.unstableRes == ""
    }

    @Test
    void scanUsesScpAndArchiveWhenNoErrorOccurs(){
        doReturn("0").when(vagrant).sshOut(any())
        trivy.scan("myimage", "plain", "critical", "ignore")
        assert mockedScript.archiveArtifactsRes.equals("trivy/output/trivyscan.*")
        verify(vagrant, times(1)).scp(":/vagrant/trivy/output", "trivy")
    }
    @Test
    void scanUsesScpAndArchiveWhenErrorOccurs(){
        doReturn("1").when(vagrant).sshOut(any())
        trivy.scan("myimage", "plain", "critical", "ignore")
        assert mockedScript.archiveArtifactsRes.equals("trivy/output/trivyscan.*")
        verify(vagrant, times(1)).scp(":/vagrant/trivy/output", "trivy")
    }

    @Test
    void scanUsesProvidedSeverity(){
        trivy.scan("myimage", "plain", "critical,asdf,asdf123", "ignore")
        verify(vagrant, times(1)).sshOut(matches(/^.*\ --severity critical,asdf,asdf123\ .*$/))
    }

    @Test
    void scanUsesCorrectImage(){
        trivy.scan("myimage", "plain", "critical,asdf,asdf123", "ignore")
        verify(vagrant, times(1)).sshOut(matches(/^.*myimage &> \/dev\/null; echo \\\$\?$/))
    }

    @Test
    void scanDoguExtractsCorrectDoguImageName(){
        doReturn("registry.cloudogu.com/official/nginx").when(vagrant).sshOut("jq .Image /dogu/dogu.json")
        doReturn("1.0.0-1").when(vagrant).sshOut("jq .Version /dogu/dogu.json")
        trivy.scanDogu("/dogu", "plain", "critical,asdf,asdf123", "ignore")
        verify(vagrant, times(1)).sshOut(matches(/^.*registry.cloudogu.com\/official\/nginx:1.0.0-1 &> \/dev\/null; echo \\\$\?$/))
    }

    @Test
    void scanUsesVolumeOnCorrectPlace(){
        trivy.scanDogu("/dogu", "plain", "critical", "ignore")
        verify(vagrant, times(1)).sshOut(matches(/^.*-v \/vagrant\/trivy\/output:\/output.*$/))
    }
}
