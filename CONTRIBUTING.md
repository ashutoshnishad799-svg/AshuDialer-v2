# Contributing

Thank you for helping. By sending a pull request you agree that your change is
released under the same license as the project, **GPL-3.0-or-later**, and that
you have the right to contribute it.

## Ground rules

- Keep the license header at the top of every source file, and **never remove
  or change an existing copyright or "adapted from" line.** New files should
  start with the same header (see any `.kt` file).
- Do not add code you cannot license under GPL-3.0-or-later (no copied
  proprietary code, no incompatible licenses).
- Do not add analytics, ads, trackers or new network endpoints without opening an
  issue first. Privacy is a core promise of this app.
- Never commit secrets: `google-services.json`, keystores, passwords, API keys,
  `local.properties`.
- Security problems go through `SECURITY.md`, not a public issue.

## How to build

1. Install JDK 17 and Android Studio.
2. Create **your own** Firebase project, add an Android app to it, and put its
   `google-services.json` in `app/`. It is git-ignored.
3. `./gradlew assembleDebug`

Debug and local builds skip the official-signature check, so you can run and
test your changes freely.

## Pull requests

- One topic per pull request, with a clear description.
- Pull requests are built by CI **without any secrets**. Your change is compiled
  and tested, never signed or published from a pull request.
