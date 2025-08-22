# Releasing the dogu-build-lib

- Review and merge a new feature to develop branch
- Check out the `master` branch, then subsequently check out the `develop` branch.
- Prepare the git flow with `git flow init`.
- Start git flow release, e.g. `git flow release start v1.6.0`
- Update changelog and pom.xml to new version, commit
- Finish release, e.g. `git flow release finish -s v1.6.0`
- Push: `git push origin master` & `git push origin develop --tags`
- Create a new Github release: https://github.com/cloudogu/dogu-build-lib/releases
