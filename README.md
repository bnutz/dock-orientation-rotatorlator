# Dock Orientation Rotatorlator #

### Description ###

Automatically lock / unlock the rotation setting of your Android device when you plug / unplug the power cable.

Can set the device to either lock or unlock the display rotation setting when the USB cable is plugged in or unplugged, or when entering / exiting wireless charging.

Handy for setting in cars and docks in landscape mode, and then can auto-set to lock to portrait when taking off the dock.

No more wake, swipe, tap, swipe again each time you take your phone off the charger.


With support for locking to inverted and landscape orientations.

Note that not all apps support landscape-mode and will revert to portrait when switching over to them (you can check this by enabling Auto-Rotate and then holding your device sideways when on the target app - if the app doesn't go sideways to match your device; then landscape-lock won't work on that app either).


(Actually this is just a really over-engineered excuse for me to play with Animated Vector Drawables...)

### 2026 Rewrite ###

Originally a 2018 Java / XML-View app, fully overhauled in 2026:

* 100% Kotlin, single-Activity **Jetpack Compose** UI (Material 3 + **Material You** dynamic colour, dark theme)
* DataStore + Kotlin Flows architecture (ViewModel / repositories / foreground service)
* Fully vector assets, including an adaptive launcher icon with an Android 13+ themed (monochrome) layer
* The original Animated Vector Drawables survived the rewrite — still the whole point
* Requires Android 12+

### Play Store ###

* https://play.google.com/store/apps/details?id=com.justbnutz.dockorientationrotatorlator

### Contact ###

* Brian Lau — via this repo's issues
