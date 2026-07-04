# GitHub Manager App

A Kotlin + Jetpack Compose Android application for managing GitHub repositories from a mobile device.

## Phase 1 — Foundation

Phase 1 delivers a crash-free, installable APK that establishes the app skeleton:

| Feature | Status |
|---|---|
| Top App Bar (title + 3-dot menu icon) | ✅ Done |
| AdMob Banner Ad at bottom (test ID) | ✅ Done |
| "Import File" button → Phase 2 Snackbar | ✅ Done |
| "Import Folder" button → Phase 2 Snackbar | ✅ Done |
| Compose Navigation (NavHost + Screen sealed class) | ✅ Done |
| Single Activity architecture | ✅ Done |

## Project Structure

```
my-github-manager-app/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/yourname/githubmanager/
│           ├── GitHubManagerApp.kt       ← AdMob SDK init
│           ├── MainActivity.kt           ← Single Activity
│           ├── navigation/
│           │   ├── AppNavigator.kt
│           │   └── Screen.kt
│           ├── ui/
│           │   ├── theme/ (Color, Theme, Type)
│           │   ├── components/
│           │   │   ├── TopAppBar.kt
│           │   │   └── BottomAdBanner.kt
│           │   └── screens/workspace/
│           │       ├── MainWorkspaceScreen.kt
│           │       └── MainWorkspaceViewModel.kt
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/gradle-wrapper.properties
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## ⚠️ Manual Steps Before Building

1. **AdMob App ID** — Replace the test App ID in `app/src/main/res/values/strings.xml`:
   ```xml
   <string name="admob_app_id">ca-app-pub-YOUR_REAL_APP_ID~YOUR_APP_CODE</string>
   ```
   The current value (`ca-app-pub-3940256099942544~3347511713`) is Google's official **test** App ID.

2. **Package Name** — If you want a custom package name, update all three places:
   - `app/build.gradle.kts` → `applicationId`
   - `app/build.gradle.kts` → `namespace`
   - Rename the source directory `java/com/yourname/githubmanager/` to match

3. **Gradle Wrapper binary** — This repo contains only `gradle-wrapper.properties`.
   Run the following once to download the Gradle wrapper JAR:
   ```bash
   gradle wrapper --gradle-version 8.7
   ```
   Or open the project in Android Studio — it will prompt to download automatically.

4. **Keystore (release build)** — For a signed release APK, configure signing in
   `app/build.gradle.kts` under `signingConfigs`. Not needed for debug builds.

## Building

```bash
# Debug APK (no signing required)
./gradlew assembleDebug

# APK location after build:
# app/build/outputs/apk/debug/app-debug.apk
```

## What's Coming in Phase 2

- Real SAF-based file + folder picker
- Zip extraction via WorkManager
- GitHub API integration (upload / commit / push)
- Settings screen + Privacy Policy dialog
- DataStore preferences
- Encrypted token storage

---

> **Phase 1 pass criteria:** Banner ad loads, both buttons show "Feature coming in Phase 2" Snackbar, app does not crash.
