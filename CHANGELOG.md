# Changelog

The section of the tagged version is used as the release notes (see `RELEASING.md`).

## 0.0.7

- Translate the trade-in offer pending and third-party inspection statuses in French and English.
- Show the trade-in inspection status separately from the offer status, including completed inspections.
- Record inspection progress in history and notifications even when the offer status does not change.

## 0.0.6

- Widget: the progress line is now four segments (done, current, to come) with the current step in plain words ("Step 3/4 · Waiting for the appointment"), instead of a single line of text that could be cut off.
- Widget: follows the phone's light/dark theme (light background in light mode), with the same colors as the app.
- Widget: once the order is delivered it says "Delivered!" instead of keeping the old delivery window and countdown.
- The delivery appointment is also read from the date, time and address text Tesla sends when no dedicated field is given.
- Widget: the "what's new" banner no longer uses jargon ("Car assigned (VIN)" instead of "VIN assigned") and shows the new value when the title alone says too little (status, delivery place, trade-in, completed task).

## 0.0.5

- The app version is shown on the main screen and on the sign-in screen.
- Clearer errors: "no connection" and "Tesla's servers are not responding" are shown as temporary (the app retries by itself); when Tesla refuses the request or the answer is unreadable, the app says Tesla may have changed its interface and links to the new version and to the bug report.
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
