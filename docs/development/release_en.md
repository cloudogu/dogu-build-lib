# Releasing the dogu-build-lib

- Review and merge a new feature into the `develop` branch.
- Check out the `master` and `develop` branch,
  to make sure these long-lived branches are up to date:
    - `git fetch origin`
    - `git switch master && git pull`
    - `git switch develop && git pull`
- Prepare the git flow with `git flow init -d`,
  this is only required once after creating a local clone of the repository.
- Start git flow release, e.g. `git flow release start v1.6.0`.
- Update the changelog and pom.xml to the new version, then commit.
- Finish release, e.g. `git flow release finish -s v1.6.0`.
- Push the changes to the remote repository:
    - `git push origin master`
    - `git push origin develop`
    - `git push origin --tags`
- Create a new GitHub release: https://github.com/cloudogu/dogu-build-lib
