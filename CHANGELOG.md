# Changelog

The section of the tagged version is used as the release notes (see `RELEASING.md`).

## 0.0.5

- The app version is shown on the main screen and on the sign-in screen.
- New menu entries: **Report a problem** (opens the issue form) and **About / project page**.

## 0.0.4

- Targets Android 16 (SDK 36): the app now draws edge-to-edge with proper system-bar padding.

## 0.0.3

- Notification when Tesla adds a task that needs your action.
- The main screen refreshes when the app comes back to the foreground.
- Sign-in: token refreshes are serialized, the session only expires on `invalid_grant`, and error bodies are kept out of messages.

## 0.0.2

- Updated OkHttp to 5.5.0.

## 0.0.1

- First release.
