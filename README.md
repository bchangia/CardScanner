# Card Scanner (Android)

Scan a visiting card -> auto-extract details -> edit -> save to phone contacts.

## Run it
1. Install Android Studio (Koala or newer) and open this folder.
2. Let Gradle sync (needs internet the first time).
3. Plug in a phone (USB debugging on) or use an emulator, press Run.

## How it works
- Camera (or gallery) -> ML Kit on-device text recognition
- CardParser.kt guesses name / company / title / phone / email / website / address
- Screen splits 50/50: card photo on top, editable form below
- "Save to contacts" writes directly to the phone's contacts (asks WRITE_CONTACTS permission once)

## Tweak
- Parsing rules: app/src/main/java/com/example/cardscanner/CardParser.kt
- Layout: app/src/main/res/layout/activity_main.xml

## Get an installable APK

**Option A - Android Studio:** Build > Build Bundle(s)/APK(s) > Build APK(s).
The file is at app/build/outputs/apk/debug/app-debug.apk

**Option B - no install, GitHub builds it for you:**
1. Create a free GitHub account and a new repository.
2. Upload the CONTENTS of this folder (including the hidden .github folder) to the repo root.
3. Open the Actions tab > "Build APK" > wait ~5 minutes for the green tick.
4. Open that run, download the "CardScanner-APK" artifact, unzip it to get app-debug.apk.

## Install on your phone
Copy app-debug.apk to the phone, open it, and allow "Install unknown apps" when asked.
(It is a debug-signed build, fine for personal use.)
