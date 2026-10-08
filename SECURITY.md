# Security policy

## Reporting a vulnerability

Please **do not open a public issue** for security problems. Use GitHub's private reporting
instead: on this repository, go to **Security → Report a vulnerability**. You will get an answer
as soon as possible; please allow some time for a fix before any public disclosure.

Only the latest release is supported.

## What the app handles

- **Tesla access and refresh tokens** — encrypted with AES-GCM, key in the Android Keystore
  (non-exportable), app-private storage, `allowBackup=false`.
- **Order data** returned by Tesla (status, delivery window, amounts, names on the registration…) —
  app-private storage, excluded from backups, deleted on sign-out.
- **Network** — HTTPS only (cleartext disabled), to Tesla hosts only. No third-party server,
  analytics or tracking.
- **Sign-in** — OAuth 2.0 authorization code with PKCE (S256) and `state` verification, in the
  user's browser; credentials are never seen by the app.

## Release integrity

Release APKs are built and signed by GitHub Actions from version tags. The workflow refuses to
publish an APK whose signing certificate does not match:

```
SHA-256: c2774a9c025ebb6b41318c9ed3e2b477fc5687d1ec8fe20adb5ab15138bf97c7
```

Each release also ships the APK's SHA-256 checksum. You can check the certificate of a downloaded
APK with `apksigner verify --print-certs DeliveryDay-X.Y.Z.apk`.
