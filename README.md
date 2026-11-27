# Android 1-shots

Small one-off Android apps and games. Each one lives in its own folder and is built into an APK by GitHub.

## Gate Runner

An endless crowd-runner: steer your squad through math gates (+, ×, −, ÷), outnumber the red squads, and dodge the giant popstar's boots.

- **Download:** grab the newest APK from the [Releases](../../releases) page.
- **Game code:** `gate-runner/app/src/main/assets/index.html` (the whole game is that one page).
- **Android shell:** `gate-runner/app/src/main/java/com/gaterunner/app/MainActivity.java`.

Any push that changes `gate-runner/` rebuilds the APK and publishes a new release.

## Punch Line

A tap-to-punch clicker: an endless line of people (plus horses, feet, piñatas and stacks of dishes) slides in at the theme park, grocery store, DMV, library, cemetery, restaurant, coffee shop and trick-or-treat street. Four hits and they're down. No way to lose.

- **Download:** grab the newest `Punch Line` APK from the [Releases](../../releases) page.
- **Game code:** `punch-line/app/src/main/assets/index.html` (the whole game is that one page).
- **Android shell:** `punch-line/app/src/main/java/com/punchline/app/MainActivity.java`.

Any push that changes `punch-line/` rebuilds the APK and publishes a new release.

## SwipeKey

A system keyboard for every app, wearing the AIM for Android phone skins: Brick, Ice, Night Glow, the flip phones (they flip open when the keyboard appears), Wide Flip, Console TV, Pocket Mail, Music Player, Pocket Brick, Swivel, Pop-Tilt, Compact, Taco, Lipstick, Two-Way Pager, Clear Cordless, Calculator Watch, Rotator, Jelly Slider, Egg Pet, Wide T9 (the 8×2 keypad) and a plain SwipeKey skin.

- **Typing:** swipe or tap on QWERTY with word suggestions, or a phone keypad with T9 word guessing or multi-tap. Phones switch between keypad and QWERTY with the T9 / ABC button in the strip; the other gadgets use their own keys, like in AIM.
- **Light:** no Google Play Services, no internet permission, about 0.4 MB of words. Made for small Android 9 phones with 2 GB RAM, works from Android 4.4 up.
- **Setup:** install the APK, open SwipeKey, tap *Turn on SwipeKey*, then *Switch to SwipeKey*. Tap ◐ on the keyboard to change skins.
- **Download:** grab the newest `SwipeKey` APK from the [Releases](../../releases) page.
- **Code:** `swipekey/app/src/main/java/com/swipekey/app/` — `KeyboardView.java` draws the skins, `SwipeKeyService.java` does the typing, `SwipeDecoder.java` reads swipes.
- **Word list:** SCOWL words ranked with wordfreq data (CC BY-SA 4.0); see `swipekey/app/src/main/assets/words_LICENSE.txt`.

Any push that changes `swipekey/` rebuilds the APK and publishes a new release.
