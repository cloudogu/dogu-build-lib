import { defineConfig } from "cypress";
// @ts-ignore
import createBundler from "@bahmutov/cypress-esbuild-preprocessor";
import { addCucumberPreprocessorPlugin } from "@badeball/cypress-cucumber-preprocessor";
import createEsbuildPlugin from "@badeball/cypress-cucumber-preprocessor/esbuild";
import doguTestLibrary from "@cloudogu/dogu-integration-test-library";

async function setupNodeEvents(
    on: Cypress.PluginEvents,
    config: Cypress.PluginConfigOptions
): Promise<Cypress.PluginConfigOptions> {
    // This is required for the preprocessor to be able to generate JSON reports after each run, and more,
    await addCucumberPreprocessorPlugin(on, config);

    on(
        "file:preprocessor",
        createBundler({
            plugins: [createEsbuildPlugin(config)],
        })
    );

    config = doguTestLibrary.configure(config)

    // Make sure to return the config object as it might have been modified by the plugin.
    return config;
}

export default defineConfig({
    e2e: {
        baseUrl: 'https://192.168.56.2',
        env: {
            "DoguName": "usermgt",
            "MaxLoginRetries": 3,
            "AdminUsername":  "ces-admin",
            "AdminPassword":  "ecosystem2016",
            "AdminGroup":  "myNewAdminGroupYeah"
        },
        videoCompression: false,
        specPattern: ["cypress/e2e/**/*.feature"],
        setupNodeEvents,
    },
});