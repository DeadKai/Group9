# Pouch - Group 9

A group expense tracker for Android. Scan a receipt, split it between friends, and see who owes whom.

## Team Members

- Kaido Kossas
- Nele Taisi Järv
- Rico Akiro Kaio
- Erik Reinart
- Jaagup Sass

## Project Structure

```
Group9/
├── app/
│   ├── build.gradle.kts            # App build config
│   ├── google-services.json        # Firebase config
│   └── src/
│       ├── main/kotlin/com/pouch/
│       │   ├── MainActivity.kt     # App entry point
│       │   ├── PouchApp.kt         # Shared app dependencies
│       │   ├── data/               # Firebase repositories (auth, users, friends, groups)
│       │   ├── domain/             # Models, receipt parser, split calculator
│       │   ├── ocr/                # Receipt text recognition
│       │   └── ui/
│       │       ├── auth/           # Sign in / sign up
│       │       ├── home/           # Group list, create group
│       │       ├── group/          # Receipts, balances, members
│       │       ├── scan/           # Scan a receipt
│       │       ├── history/        # Bill history
│       │       ├── profile/        # Profile and friends
│       │       ├── settings/       # Settings
│       │       ├── navigation/     # Navigation graph
│       │       ├── common/         # Shared UI components
│       │       └── theme/          # Colors, typography
│       └── test/                   # Unit tests
├── build.gradle.kts                # Root build file
├── settings.gradle.kts             # Gradle settings
├── firebase.json                   # Firebase CLI config
└── firestore.rules                 # Firestore security rules
```
