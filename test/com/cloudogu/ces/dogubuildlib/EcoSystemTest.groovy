package com.cloudogu.ces.dogubuildlib

import org.junit.Test

class EcoSystemTest {

    private EcoSystem ecoSystem = new EcoSystem([], "", "")

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

}
