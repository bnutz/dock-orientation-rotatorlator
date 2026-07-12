# Dock Orientation Rotatorlator

**Your phone's rotation lock, set automatically by whether it's plugged in.**

Drop the phone in the car dock and it unlocks to landscape. Take it off the charger at
bedtime and it locks back to portrait. No wake, swipe, tap, swipe again — it just
happens.

Android for Android 12+ · Kotlin · Jetpack Compose · [Play Store](https://play.google.com/store/apps/details?id=com.justbnutz.dockorientationrotatorlator)

---

## What it actually does

The app watches one thing — **the power state of your phone** — and, when that changes,
applies **the rotation setting you chose for that state**.

There are three power states, and each one gets its own independent configuration:

| Power state | When it triggers |
|---|---|
| **Unplugged** | Nothing connected — on battery |
| **Plugged in** | USB cable connected, *or* sitting in a dock |
| **Wirelessly charging** | On a wireless charging pad *(hideable — not every phone has it)* |

For each of those, you pick **one of six rotation modes**:

| Mode | What happens when you enter that power state |
|---|---|
| **Do nothing** | Leave the rotation setting exactly as it is. Use this for a state you don't care about. |
| **Portrait** | Lock upright. |
| **Portrait (Inverted)** | Lock upside-down. |
| **Landscape** | Lock sideways. |
| **Landscape (Inverted)** | Lock sideways, the other way. |
| **Auto-rotate** | Unlock rotation entirely — let the phone follow gravity. |

Tap a card to cycle it through the six modes.

### The setup most people actually want

> **Unplugged → Portrait**, **Plugged in → Auto-rotate**
>
> Your phone stays locked upright in your pocket and in bed, and the moment it goes on
> the car dock or desk stand it's free to rotate. Unplug it and it snaps back to
> portrait.

Some other combinations that make sense:

- **Plugged in → Landscape**: a dock that's *always* horizontal (car mount, kitchen
  stand), where you want landscape regardless of how the phone is sitting.
- **Wirelessly charging → Landscape**, **Unplugged → Do nothing**: a bedside wireless
  stand that becomes a clock, but no opinion about rotation the rest of the time.
- **Landscape (Inverted)**: for docks where the charging port is on the "wrong" side and
  the phone ends up upside-down.
- **Everything → Do nothing** except one state: the app only ever acts on the states you
  gave a mode to. It is perfectly reasonable to only configure one of the three.

### The bits worth knowing

- **"Inverted" is relative to your device's natural orientation.** A phone is naturally
  portrait; a tablet may be naturally landscape. The app works out which and adjusts, so
  "Landscape" means the same thing to you on either.
- **Not every app honours a landscape lock.** If an app is portrait-only, it stays
  portrait no matter what the system rotation setting says. (Quick test: turn on
  auto-rotate and hold your phone sideways in that app — if it doesn't rotate, this app
  can't make it.)
- **Monitoring needs a persistent notification.** Android only lets an app watch power
  events continuously if it runs a foreground service, and those must show a
  notification. It's set to the lowest priority, so it sits silently at the bottom of the
  shade, and it has a **Stop Monitoring** button. Nothing runs after you switch the
  monitor off.
- **The permission is unusual.** Changing the rotation lock is a system setting, so
  Android needs *Modify system settings* — a "special access" grant you flip in Settings
  rather than a normal pop-up. The app asks once, on first run.
- **Nothing leaves your phone.** No network permission, no analytics, no accounts.

---

## Why this exists

> *"Actually this is just a really over-engineered excuse for me to play with Animated
> Vector Drawables..."*
> — the original 2018 README, being honest

That's still true. The rotation-mode button on each card doesn't just swap icons, it
*morphs* between them — the arrows fold into a phone, the phone tips into landscape —
using a set of hand-built AnimatedVectorDrawables. That was the entire point of building
the thing, and everything else is scaffolding around it.

Which is why, in the 2026 rewrite, those seven animations are the one place the app
deliberately drops out of Compose and back into a plain `ImageView`: Compose's own
`AnimatedImageVector` couldn't play them faithfully, and the animations weren't
negotiable. (See `AvdCycleButton` in
[`RotatorlatorScreens.kt`](app/src/main/java/com/justbnutz/dockorientationrotatorlator/ui/RotatorlatorScreens.kt).)

---

## The 2026 rewrite

Built in 2018, in Java, with XML layouts and a support-library toolchain that stopped
being maintainable. Rather than let it rot, it was rebuilt from the inside out — the same
app, in the idioms it would have been written in today:

| | 2018 | 2026 |
|---|---|---|
| Language | Java (~2,900 lines) | Kotlin (~1,700) |
| UI | XML layouts, Fragment, RecyclerView + Adapter | Jetpack Compose, single Activity |
| Look | Fixed teal, light mode only | Material 3 + **Material You** (follows your wallpaper), **dark theme** |
| State | SharedPreferences + LocalBroadcastManager | DataStore + Kotlin Flows + ViewModel |
| Assets | 16 PNGs + vectors | **Vector only**, incl. a themed (monochrome) launcher icon |
| Min Android | 5.0 | 12 |
| Release APK | ~5 MB | **~1.5 MB** |

The code is organised in four layers, one-way dependencies (`ui` → `data` → `model`,
`service` → `data` → `model`):

```
model/    the domain vocabulary — PowerStatus, RotationMode. Pure Kotlin, no Android.
data/     repositories — DataStore prefs, and the power/rotation state of the device.
service/  the foreground service that does the watching and the switching.
ui/       the single Activity, the theme, the ViewModel, and all the Compose screens.
```

If you're coming from XML layouts and ViewBinding and want to see how that maps onto
Compose, [`RotatorlatorScreens.kt`](app/src/main/java/com/justbnutz/dockorientationrotatorlator/ui/RotatorlatorScreens.kt)
is commented as a walkthrough — it opens with a translation table and explains what each
composable replaced.

---

## Building

Standard Gradle Android project — no special setup:

```bash
./gradlew assembleDebug
```

Debug builds install alongside a Play-installed copy (they use a `.debug` application ID
and are labelled *"(Dev)"*), so you can run both at once.

---

## Contact

Brian Lau — via this repo's issues.
