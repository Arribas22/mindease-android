# MindEase Android

Professional Android app focused on mental wellness with on-device AI, privacy-first decisions, and production-minded architecture.

## Why this repository exists
This repository is public to demonstrate engineering quality for recruiters and technical teams:
- Clean Kotlin architecture with explicit responsibility boundaries
- Room persistence model and domain-focused ViewModels
- On-device AI integration approach (without shipping private model binaries)
- Real-world monetization and product constraints (Billing + Ads)

## Technical highlights
- Kotlin + Jetpack Compose
- Room (DAO/entity separation)
- WorkManager for background routines
- Billing and rewarded ad management
- Local-first data strategy

## Run locally
1. Open in Android Studio (latest stable)
2. Create local.properties with Android SDK path
3. Sync Gradle
4. Build and run on Android 8+

## On-device model note
Large/private model assets are intentionally excluded from this public repository.
You can plug your own compatible model file in app/src/main/assets for local testing.

## Copilot workflow
This project was built with GitHub Copilot as an engineering copilot:
- rapid boilerplate generation
- refactor assistance across modules
- test/debug iteration support
All architectural and product decisions were validated manually.

## Recruiter quick map
- App bootstrap: app/src/main/java/com/bravosix/mindease/App.kt
- Main entrypoint: app/src/main/java/com/bravosix/mindease/MainActivity.kt
- State/business flow: app/src/main/java/com/bravosix/mindease/ChatViewModel.kt
- Data model: app/src/main/java/com/bravosix/mindease/db/
