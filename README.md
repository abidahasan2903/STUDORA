# Studora (স্টুডোরা)

**A student companion for organizing study, learning, and everyday campus life.**

Studora is a native Android app built with Kotlin and Jetpack Compose. It combines local study organization and productivity tools with optional AI-assisted learning features powered by Firebase AI Logic.

> **Download:** Open [GitHub Releases](https://github.com/abidahasan2903/STUDORA/releases) and install the latest `Studora-<version>.apk` on an Android phone. If no release is listed yet, use the [Build APK workflow](https://github.com/abidahasan2903/STUDORA/actions/workflows/android.yml) or build locally. AI features need a Firebase project configured by the app maintainer; see [Firebase setup](#firebase-setup-for-ai-features).

## Features

- Organize subjects, topics, written notes, documents, and recordings.
- Create tasks with priorities, reminders, and optional image attachments.
- Plan study sessions and track weekly routines.
- Scan pages into PDFs, view PDFs, and import learning material.
- Generate AI study notes, quizzes, flashcards, and study plans from text or documents.
- Keep personal data in a local Room database, with offline access to locally stored content.
- Use student utilities for bills, wallet tracking and calculator, nutrition chart review, class routines, bus schedules, emergency contacts, and campus portals.

Some features need device permissions, connectivity, or Firebase configuration. AI output requires internet access and is subject to the configured Firebase project and its quotas.

## Install on Android

1. On the repository page, open **Releases** (or visit [all releases](https://github.com/abidahasan2903/STUDORA/releases)).
2. Download `Studora-<version>.apk` from the latest release assets.
3. Open the downloaded APK on your Android phone. If prompted, allow your browser or file manager to **Install unknown apps**, then confirm **Install**.
4. Launch **Studora** from your app list.

Only install APKs from releases you trust. Android may show an additional warning because the APK is distributed outside Google Play. The app requires Android 7.0 (API 24) or later.

### No release available yet?

The repository does not promise a ready-to-download APK until a release workflow has completed. A maintainer can:

- Download the `studora-debug-apk` artifact from a successful run of [Build Android APK](https://github.com/abidahasan2903/STUDORA/actions/workflows/android.yml). Workflow artifacts expire according to GitHub's retention policy and are intended for testing.
- Create a signed release by configuring the signing secrets below, pushing a version tag such as `v1.0.0`, and downloading the APK attached to the resulting GitHub Release.

Debug APKs are for testing and may not upgrade over an APK signed with a different key. Use signed release APKs for updates.

## Firebase setup for AI features

The repository does not contain a Firebase Android configuration file. Without one, the project can compile, but Firebase AI features will not be ready for end users.

To enable them for your own Firebase project:

1. Create or select a Firebase project and register an Android app with package name `com.aistudio.studora.ahsnkp`.
2. Enable **Firebase AI Logic** and configure its model/provider and usage controls in that project.
3. Configure Firebase App Check for the app and the release signing certificate. The current app code initializes App Check; verify its provider and site key in `app/src/main/java/com/example/core/AppCheckHelper.kt` and `app/src/main/res/values/firebase_applet_config.xml` against your Firebase project.
4. Download that app's `google-services.json` into `app/google-services.json`. This file is ignored by Git. Never commit private service-account credentials or signing keys.
5. Build and test AI features on a device registered with your App Check configuration. App Check debug tokens must be registered in Firebase for debug builds.

Firebase client configuration is project-specific. The existing Recaptcha site key in this repository may not belong to your Firebase project; replace it with the correct configuration before enabling production AI access.

## Build locally

### Requirements

- Android Studio with Android SDK Platform 36.1 and Build-Tools 36.1.0 installed.
- JDK 17.
- Internet access for Gradle and dependency downloads.

Open the repository in Android Studio and let Gradle sync, or run the commands below from the repository root.

**Windows PowerShell:**

```powershell
.\gradlew.bat assembleDebug
```

**macOS / Linux:**

```bash
chmod +x gradlew
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install it on a connected device with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

To run unit tests, use `./gradlew testDebugUnitTest` (or `.\gradlew.bat testDebugUnitTest` in PowerShell). To build a signed release locally, set `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`, then run `assembleRelease`.

## Create signed GitHub releases

The **Build Android APK** workflow builds a debug APK for pushes and pull requests to `main`, and uploads it as a temporary Actions artifact. To publish a signed, installable release APK:

1. Create a dedicated Android upload keystore and keep a secure backup. Never commit it.
   For example, with JDK 17's `keytool`: `keytool -genkeypair -v -keystore studora-upload-key.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000`. Store the passwords securely. On Windows PowerShell, encode the file for the GitHub secret with `[Convert]::ToBase64String([IO.File]::ReadAllBytes('studora-upload-key.jks')) | Set-Clipboard`; on macOS/Linux, use `base64 -w 0 studora-upload-key.jks` (macOS: `base64 studora-upload-key.jks`).
2. In the GitHub repository, add these **Actions secrets**:
   - `ANDROID_KEYSTORE_BASE64`: the keystore file encoded as Base64.
   - `ANDROID_KEYSTORE_PASSWORD`: the keystore password.
   - `ANDROID_KEY_ALIAS`: the key alias.
   - `ANDROID_KEY_PASSWORD`: the key password.
3. Create and push a semantic version tag, for example `git tag v1.0.0` followed by `git push origin v1.0.0`. The release workflow sets the Android version name from the tag and calculates a version code so newer semantic versions can update earlier ones.
4. The **Release Android APK** workflow builds a signed APK and attaches `Studora-1.0.0.apk` to a GitHub Release named `Studora v1.0.0`.

Keep using the same keystore for every release; Android only accepts in-place app updates signed with the same key. If the signing key is lost, users generally need to uninstall the old app before installing a newly signed build.

## Tech stack

- Kotlin, Android Gradle Plugin, Jetpack Compose, Material 3, and Navigation Compose.
- Room for local structured data; Android storage APIs for files and document workflows.
- Firebase AI Logic for generative learning tools, with Firebase App Check.
- CameraX and Android notifications for scanning and reminders.

## Project structure

```text
.
├── app/
│   └── src/main/
│       ├── java/com/example/
│       │   ├── core/        # AI, storage, backup, PDF, connectivity utilities
│       │   ├── data/        # Room database, entities, repository
│       │   ├── notifications/
│       │   └── ui/          # Compose screens, navigation, components, theme
│       └── res/             # Android strings, themes, icons, fonts
├── gradle/                  # Version catalog and Gradle wrapper
├── .github/workflows/       # Android build and release automation
└── metadata.json            # App Studio project metadata
```

## Version and package

- App version: `1.0` (`versionCode` 1) for local builds; tagged releases take their version from the tag.
- Android application ID: `com.aistudio.studora.ahsnkp`
- Minimum Android version: Android 7.0 (API 24)

## Contributing

Issues and pull requests are welcome. For changes that affect Firebase, signing, or Android permissions, include the configuration and device testing steps in the PR description. Do not commit credentials, signing keys, generated build output, or your local `google-services.json`.
