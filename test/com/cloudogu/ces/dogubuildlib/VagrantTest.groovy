package com.cloudogu.ces.dogubuildlib

import org.junit.Test

class VagrantTest {

    def images = []
    def insideArgs = []
    def shInside = []
    boolean inContainer = false

    def mockedScript = [
            sh                 : { args ->
                if (args instanceof Map) {
                    assert inContainer
                    shInside << args.script
                    return "10.0.0.1"
                }
                assert inContainer
                shInside << args
            },
            file               : { args -> args },
            sshUserPrivateKey  : { args -> args },
            withCredentials    : { creds, Closure body -> body() },
            docker             : [
                    image: { String image ->
                        images << image
                        [inside: { String args, Closure body ->
                            insideArgs << args
                            inContainer = true
                            try {
                                return body()
                            } finally {
                                inContainer = false
                            }
                        }]
                    },
            ],
    ]

    @Test
    void runsVagrantInsideTheVagrantImage() {
        Vagrant vagrant = new Vagrant(mockedScript, "gcloud", "ssh")

        vagrant.up()
        vagrant.destroy()

        assert images == [Vagrant.IMAGE, Vagrant.IMAGE]
        assert insideArgs == ["--entrypoint=''"] * 2
        assert shInside == ["vagrant up", "vagrant destroy -f"]
    }

    @Test
    void sshOutReturnsTheTrimmedOutput() {
        Vagrant vagrant = new Vagrant(mockedScript, "gcloud", "ssh")

        assert vagrant.sshOut("hostname -I") == "10.0.0.1"
    }
}
