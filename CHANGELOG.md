# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
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
