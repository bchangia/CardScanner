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
