# Releasing the dogu-build-lib

- Review and merge a new feature into the `develop` branch.
- Check out the `master` branch, then subsequently check out the `develop` branch:
    - `git switch master && git switch develop`
- Prepare the git flow with `git flow init`.
    - Select defaults for all required options.
- Start git flow release, e.g. `git flow release start v1.6.0`.
- Update the changelog and pom.xml to the new version, then commit.
- Finish release, e.g. `git flow release finish -s v1.6.0`.
- Push: `git push origin master && git push origin develop --tags`.
- Create a new GitHub release: https://github.com/cloudogu/dogu-build-lib/releases
