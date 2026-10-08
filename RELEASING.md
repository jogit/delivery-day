# Releasing (maintainers)

Every push and pull request runs the tests and builds a debug APK (`.github/workflows/ci.yml`).

To publish a version, push a tag:

```sh
git tag v0.0.2
git push origin v0.0.2
```

`.github/workflows/release.yml` then:

1. takes the version from the tag (`vX.Y.Z` → version `X.Y.Z`, versionCode `X*10000 + Y*100 + Z`,
   so it always increases);
2. runs the tests and builds the release APK, signed with the key from the repository secrets;
3. fails if the APK is not signed with the expected certificate (fingerprint in the workflow);
4. publishes `DeliveryDay-X.Y.Z.apk` and its SHA-256 on the Releases page, with generated notes.

Only people with write access can push tags; workflows triggered by pull requests from forks have no
access to the secrets.

## Signing key

The release key is never committed. Repository secrets: `KEYSTORE_BASE64` (the keystore file,
base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. To build a signed release locally, set
`deliveryday.storeFile`, `deliveryday.storePassword`, `deliveryday.keyAlias` and
`deliveryday.keyPassword` in `~/.gradle/gradle.properties`, then run
`./gradlew assembleRelease -Pdeliveryday.version=X.Y.Z`.

Keep a backup of the keystore and its passwords: without them, existing installs can no longer be
updated (users would have to uninstall).
