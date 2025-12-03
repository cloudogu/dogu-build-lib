package com.cloudogu.ces.dogubuildlib

import groovy.yaml.YamlSlurper
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner

import static org.junit.Assert.*
import static org.mockito.ArgumentMatchers.anyMap
import static org.mockito.ArgumentMatchers.anyString
import static org.mockito.Mockito.doAnswer
import static org.mockito.Mockito.verify

@RunWith(MockitoJUnitRunner)
class MultiNodeEcoSystemTest {

    interface ScriptStub {
        Object readYaml(Map args);
        void writeYaml(Map args);
        void sh(String cmd);
        void echo(String msg);
        void error(String msg);
    }

    @Test
    void provisionShouldThrowErrorAndMentionSetup() {
        // Arrange
        ScriptStub script = Mockito.mock(ScriptStub.class)

        doAnswer({ invocation ->
            String msg = invocation.getArgument(0)
            throw new RuntimeException(msg)
        }).when(script).error(anyString())

        MultiNodeEcoSystem eco = new MultiNodeEcoSystem(script, "gcloudCreds", "coderCreds")

        // Act
        RuntimeException ex = assertThrows(RuntimeException) {
            eco.provision("/mnt")
        }

        // Assert
        assertTrue(ex.message.contains("provisioning is not supported in Multinode-Ecosystem"))
        assertTrue(ex.message.contains("setup(config = [:])"))
    }

    @Test
    void escapeTokenShouldEscapeDollarSigns() {
        assertEquals("abc\\\$def", MultiNodeEcoSystem.escapeToken("abc\$def"))
        assertEquals("\\\$TOKEN", MultiNodeEcoSystem.escapeToken("\$TOKEN"))
        assertEquals("noDollar", MultiNodeEcoSystem.escapeToken("noDollar"))
    }

    @Test
    void getDefaultValueByNameShouldReturnMatchingDefault() {
        // Arrange
        ScriptStub script = Mockito.mock(ScriptStub)
        MultiNodeEcoSystem eco = new MultiNodeEcoSystem(script, "gcloudCreds", "coderCreds")

        eco.coderRichParameters = [
                [name: "Ecosystem Core Chart Version", default_value: "1.2.3"],
                [name: "Foo", default_value: "bar"]
        ]

        // Act + Assert
        assertEquals("1.2.3", eco.getDefaultValueByName("Ecosystem Core Chart Version"))
        assertEquals("bar", eco.getDefaultValueByName("Foo"))
        assertNull(eco.getDefaultValueByName("Unknown"))
    }

    @Test
    void getDefaultValueByNameAsListShouldReturnIndentedDashList() {
        // Arrange
        ScriptStub script = Mockito.mock(ScriptStub)
        MultiNodeEcoSystem eco = new MultiNodeEcoSystem(script, "gcloudCreds", "coderCreds")

        eco.coderRichParameters = [
                [name: "Disabled components", default_value: '["comp1","comp2"]']
        ]

        // Act
        String result = eco.getDefaultValueByNameAsList("Disabled components")

        // Assert
        assertEquals("""  - comp1
  - comp2""", result)
    }

    @Test
    void createMNParameterShouldMergeAdditionalDogusAndComponentsIntoYaml() {
        // Arrange
        ScriptStub script = Mockito.mock(ScriptStub)

        // readYaml(text: ...) -> parse text of createMnParameter
        doAnswer({ invocation ->
            Map args = invocation.getArgument(0)
            String text = args.text as String
            return new YamlSlurper().parseText(text)
        }).when(script).readYaml(anyMap())

        // writeYaml(file: ..., data: ...) -> get yaml
        ArgumentCaptor<Map> writeYamlCaptor = ArgumentCaptor.forClass(Map)
        doAnswer({ invocation -> null }).when(script).writeYaml(anyMap())

        // sh("rm -f ...") fallthrough
        doAnswer({ invocation -> null }).when(script).sh(anyString())
        doAnswer({ invocation -> null }).when(script).echo(anyString())

        MultiNodeEcoSystem eco = new MultiNodeEcoSystem(script, "gcloudCreds", "coderCreds")

        // Predefine defaults
        eco.coderRichParameters = [
                [name: "Ecosystem Core Chart Version",   default_value: "1.2.3"],
                [name: "Component-Operator-CRD",        default_value: "k8s/k8s-component-operator-crd:1.0.0"],
                [name: "Blueprint-Operator-CRD",        default_value: "k8s/k8s-blueprint-operator-crd:1.0.0"],
                [name: "Disabled components",           default_value: '["compX","compY"]']
        ]

        Map config = [
                additionalDogus     : ["official/mydogu"],
                additionalComponents: ["k8s/my-component"]
        ]

        // Act
        eco.createMNParameter(config)

        // Assert – writeYaml called once
        verify(script).writeYaml(writeYamlCaptor.capture())
        Map args = writeYamlCaptor.value
        assertEquals("integrationTests/mn_params_modified.yaml", args.file)

        Map data = args.data as Map
        assertNotNull(data)

        // 1) Necessary dogus + mydogu
        List necessaryDogus = data["Necessary dogus"] as List
        assertTrue(necessaryDogus.contains("official/postfix"))
        assertTrue(necessaryDogus.contains("official/ldap"))
        assertTrue(necessaryDogus.contains("official/cas"))
        assertTrue(necessaryDogus.contains("official/mydogu"))

        // 2) Additional dogus is empty
        List additionalDogus = data["Additional dogus"] as List
        assertNotNull(additionalDogus)
        assertTrue(additionalDogus.isEmpty())

        // 3) Base components from lib
        List baseComponents = data["Base components"] as List
        assertTrue(baseComponents.contains("k8s/k8s-dogu-operator-crd"))
        assertTrue(baseComponents.contains("k8s/k8s-dogu-operator"))
        assertTrue(baseComponents.contains("k8s/k8s-service-discovery"))
        assertTrue(baseComponents.contains("k8s/k8s-ces-gateway"))
        assertTrue(baseComponents.contains("k8s/k8s-ces-assets"))
        assertTrue(baseComponents.contains("k8s/k8s-debug-mode-operator-crd"))
        assertTrue(baseComponents.contains("k8s/k8s-debug-mode-operator"))
        assertTrue(baseComponents.contains("k8s/my-component"))

        // 4) Disabled components contains passed values
        List disabledComponents = data["Disabled components"] as List
        assertEquals(["compX", "compY"], disabledComponents)

        // 5) Ecosystem Core Chart Version from default
        assertEquals("1.2.3", data["Ecosystem Core Chart Version"])

        // Optional: check for deleting file
        verify(script).sh("rm -f integrationTests/mn_params_modified.yaml")
    }
}
