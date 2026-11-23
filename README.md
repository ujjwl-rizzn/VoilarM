# VoilarM

VoilarM is a free Android alarm clock app built for offline use on your phone. It runs on-device without servers, accounts, ads, or tracking, and is released under the MIT license.

## Features

- Voice recording alarm: Record your own voice (15 to 60 seconds) and use it as the alarm tone.
- Text-to-speech alarm: Type a sentence and convert it offline using Android's built-in text-to-speech engine.
- Voice effects & preview: Apply voice effects (deep, very deep, high, or chipmunk) and preview before saving.
- Locked-screen ringing: Scheduled with Android alarms and a foreground service so it can ring when the screen is locked or the app is closed, with full-screen and notification Stop/Snooze actions.
- Gradual volume ramp: Steps volume up every 10 seconds (`20% -> 30% -> 40% -> 50% -> 100% -> 120% -> 150%`), rings for up to 10 minutes unless stopped, and supports a 5-minute snooze.
- Daily repeat & reboot survival: Supports daily repeat alarms and restores alarms after a phone restart.
- Dark UI & custom visuals: Dark interface with mint-green controls over a custom navy-to-violet animated wave background (shifting to faster red and amber waves while ringing), using original vector artwork.

## Privacy & Licensing

- All recordings, text-to-speech audio, and alarm data stay on your device. No external server or analytics tracking is used.
- Licensed under the MIT License (`LICENSE`).
- Before any future store listing or paid release, check trademark availability (for example, via the IP India public trademark search) and store policies.

## Getting the APK

1. Push this repository to GitHub and run the **Build APK** workflow in the **Actions** tab.
2. Download the built artifact (`VoilarM-APK`), extract `app-debug.apk`, copy it to your phone, and install it (allowing **Install unknown apps** when prompted).
3. On first launch, tap **1. Allow mic & notifications** and **2. Allow background ringing** (set to **Unrestricted** or **Don't optimize** so battery savers do not stop the alarm).
