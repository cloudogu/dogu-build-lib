# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [v3.3.0] - 2025-08-22
### Added
- EcoSystem for Multinodecluster to run Integrationtests on MN

## [v3.2.0] - 2025-04-10
### Changed
- Add timeout parameter to provision function 

## [v3.1.0] - 2025-01-22
### Changed
- Use Ubuntu 24.04 based image family for EcoSystem provisioning; #62

## [v3.0.0] - 2024-12-12
### Added
- [#58] Add function to copy Dogu images to the Jenkins worker

This release contains a **breaking change**. All Trivy-specific functionality has been removed.
See [Scan Dogu image with Trivy](https://github.com/cloudogu/ces-build-lib/tree/main?tab=readme-ov-file#scan-dogu-image-with-trivy) for migration.

### Removed
- Removed all Trivy-specific functionality. Please use the Trivy class of the [ces-build-lib](https://github.com/cloudogu/ces-build-lib)

## [v2.6.0] - 2024-11-25
### Added
- [#56] function to push a pre release to registry

## [v2.5.1] - 2024-11-22
### Fixed
- [#54] print verify output to jenkins console and append the result as build artifact

## [v2.5.0] - 2024-09-27
### Added
- [#49] Add feature to use a .trivyignore file to ignore cve that are false positives 

## [v2.4.0] - 2024-09-18
### Changed
- Relicense to AGPL-3.0-only

## [v2.3.1] - 2023-09-13
### Changed
- [#45] Changed the default password to comply with the default password-rules of the ecosystem
  - Since CAS 7.0 the password-compliance is checked on every login and forces a password-change if a password is not compliant
  - This prevents these "weak password"-warnings and allows the integration-tests to be executed without problems

## [v2.3.0] - 2023-09-13
### Removed
- [#43] Argument of type `EcoSystem` from `update()`-method

### Added
- [#43] New dogu version as overloaded argument to `update()`-method

## [v2.2.0] - 2023-07-05
### Changed
- Update the correct  Cypress configuration file when changing the admin group (#40)
  - previously only the cypress.json file was changed which has been replaced in cypress version 10+ with the cypress.config.js
  - now depending on which of the files exists, the correct file is changed
- Allow the configuration of the timeout for the verify command

## [v2.1.0] - 2023-04-24
### Added
- Trivy scan feature to scan Dogus for possible security issues (#38)

## [v2.0.0] - 2023-01-31
### Removed
- [#36] Remove functions `lintDockerfile()` and `shellCheck` to `ces-build-lib`
  - see [ces-build-lib](https://github.com/cloudogu/ces-build-lib)

## [v1.11.0] - 2023-01-30 (Deprecated)
> This Release is deemed deprecated in favor of [v2.0.0] because of wrong semantic versioning.
### Removed
- [#36] Remove functions `lintDockerfile()` and `shellCheck` to `ces-build-lib`
  - see [ces-build-lib](https://github.com/cloudogu/ces-build-lib)

## [v1.10.0] - 2022-10-19
### Added
- [#34] Option to configure the namespace used in upgrade test.

## [v1.9.0] - 2022-10-19
### Added
- [#32] Option to configure the machine type for the provisioned machine. 

## [v1.8.0] - 2022-10-12
### Added
- Add upgradeFromPreviousRelease function to support dogu upgrade tests; #29

## [v1.7.0] - 2022-10-12
### Added
- [#28] Add `changeAdminGroup` to `EcoSystem`-class to change the admin group of the EcoSystem.
- [#28] Add `restartDogu` to `EcoSystem`-class to restart a dogu.
- [#28] Add `updateCypressConfiguration` to `Cypress`-class to update the current configuration with the newest admin 
   group.

## [v1.6.0] - 2021-12-14
### Added
- Option for additional purge parameters; #26

## [v1.5.1] - 2021-11-17
### Fixed
- Calling the sleep function without the script executor, making `waitUntilAvailable` unusable in production

## [v1.5.0] - 2021-11-16
### Added
- Function `waitUntilAvailable(doguName, timeout)` to wait until the dogu is ready for interaction

## [v1.4.1] - 2021-09-20
### Fixed
- A new EcoSystem image has been released which is not compatible with the previous version.
  The disk size has increased from 64 GB to 100 GB.

## [v1.4.0] - 2021-09-09
### Added
- allows the passing of `registryConfigEncrypted` entries for the setup configuration; #20

## [v1.3.0] - 2021-05-05
### Added
- method to run integration tests based on cypress. See [README](README.md) - Section Cypress for more information (#18)

## [v1.2.0] - 2021-01-11
### Added
- Add method to purge dogu without removing container or image
  
### Fixed
- Fix integration test steps for yarn and maven

## [v1.1.1] - 2020-12-14
### Added
- Add methods to install, purge, upgrade and start dogus
- Add methods to run maven and yarn integration tests
- Add parameters to test exection with yarn and maven
  - boolean flag to control video recording
  - argument list to pass additional environment variables

## [v1.0.0] - 2020-06-09
First release of the dogu-build-lib!
