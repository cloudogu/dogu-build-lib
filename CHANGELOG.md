# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
