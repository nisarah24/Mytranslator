# Screen Translator — Android App (Java)

A floating-bubble screen translator for Android, similar to Hi Translate.
Built entirely in Java with native Android APIs + Google ML Kit.

---

## Core Architecture

### 1. `ScreenTextAccessibilityService.java`
Extends `android.accessibilityservice.AccessibilityService`.

**How it reads text from other apps:**
- On `onServiceConnected()`, configures `AccessibilityServiceInfo` with:
  - `FLAG_RETRIEVE_INTERACTIVE_WINDOWS` — reads from ALL windows (dialogs, split-screen, Chrome WebViews, WhatsApp, Twitter, etc.)
  - `canRetrieveWindowContent = true` — the critical flag without which nothing works
- `extractScreenText()` is called on-demand (not on every event) when the bubble is tapped
- Walks the entire `AccessibilityNodeInfo` tree of every `AccessibilityWindowInfo`
- Skips invisible nodes (`isVisibleToUser() == false`) and off-screen nodes (empty bounds)
- Collects `getText()` first, falls back to `getContentDescription()` for icon labels
- Deduplicates and joins results into a readable paragraph

### 2. `FloatingBubbleService.java`
Extends `android.app.Service` (foreground service).

**WindowManager overlay — two views:**

| View | Type | Flags |
|---|---|---|
| `bubbleView` | `TYPE_APPLICATION_OVERLAY` | `FLAG_NOT_FOCUSABLE \| FLAG_LAYOUT_NO_LIMITS` |
| `overlayView` | `TYPE_APPLICATION_OVERLAY` | `FLAG_NOT_FOCUSABLE \| FLAG_NOT_TOUCH_MODAL \| FLAG_WATCH_OUTSIDE_TOUCH` |

`FLAG_NOT_TOUCH_MODAL` on the overlay means touches **outside** the card pass through to the underlying app — the user can still scroll Twitter while reading the translation.

**Drag logic:**
- `ACTION_DOWN` — records start position of both bubble params and raw touch
- `ACTION_MOVE` — computes delta, clamps to screen bounds (no going off-screen), calls `updateViewLayout()`
- `ACTION_UP` — triggers translation on both tap and drag-release (matching Hi Translate behaviour)

### 3. `TranslationManager.java`
Wraps Google ML Kit Translation.
- Auto-detects source language with ML Kit Language ID
- Downloads models on first use (Wi-Fi required for first download, offline after that)
- Supports: English ↔ Urdu, Hindi, Arabic (all combinations)
- Callbacks run on ML Kit's thread → `FloatingBubbleService` posts results to main thread via `Handler`

---

## Project Structure

```
ScreenTranslator/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/screentranslator/app/
│   │   ├── services/
│   │   │   ├── FloatingBubbleService.java          ← WindowManager overlay + drag
│   │   │   └── ScreenTextAccessibilityService.java ← Reads text from other apps
│   │   ├── ui/
│   │   │   └── MainActivity.java                   ← Permission setup screen
│   │   └── utils/
│   │       └── TranslationManager.java             ← ML Kit translation
│   └── res/
│       ├── layout/
│       │   ├── activity_main.xml                   ← Setup screen
│       │   ├── layout_floating_bubble.xml          ← Draggable bubble
│       │   └── layout_translation_overlay.xml      ← Result card
│       ├── values/
│       │   ├── strings.xml
│       │   ├── colors.xml
│       │   └── themes.xml
│       ├── drawable/                               ← Vector icons
│       └── xml/
│           └── accessibility_service_config.xml   ← A11y service config
├── build.gradle
├── settings.gradle
└── gradle.properties
```

---

## Setup in Android Studio

1. Open Android Studio → **Open** → select the `ScreenTranslator` folder
2. Wait for Gradle sync to complete (downloads ML Kit dependencies)
3. Run on a **real Android device** — API 26+ (Android 8.0+)
   - Emulators don't support `SYSTEM_ALERT_WINDOW` overlays reliably

---

## First-Run Steps (in the app)

### Step 1 — Overlay Permission (`SYSTEM_ALERT_WINDOW`)
Tap **"Grant Overlay Permission"** → toggle on for Screen Translator.
Without this, `WindowManager.addView()` will throw a `WindowManager$BadTokenException`.

### Step 2 — Enable Accessibility Service
Tap **"Enable Accessibility"** → find **Screen Translator** → toggle on.
Without this, `AccessibilityService.getInstance()` returns null and no text can be read.

### Step 3 — Choose Language
Pick Urdu, Hindi, Arabic, or English as the translation target.

### Step 4 — Download Offline Models (Optional)
Tap **"Download Offline Models"** while on Wi-Fi to enable offline translation.

### Step 5 — Start
Tap **"Start Floating Bubble"** — the app goes to the home screen showing a floating blue bubble.
Open WhatsApp, Twitter, Chrome, or any app → tap or drag the bubble → translation appears.

---

## Permissions

| Permission | Declared in | Purpose |
|---|---|---|
| `SYSTEM_ALERT_WINDOW` | AndroidManifest | Draw floating views on top of all apps |
| `BIND_ACCESSIBILITY_SERVICE` | `<service>` tag | Read View hierarchy from other apps |
| `FOREGROUND_SERVICE` | AndroidManifest | Keep FloatingBubbleService alive when switching apps |
| `INTERNET` | AndroidManifest | Download ML Kit language models |

---

## Dependencies

```gradle
// Google ML Kit Translation (online + offline)
implementation 'com.google.mlkit:translate:17.0.2'

// ML Kit Text Recognition
implementation 'com.google.android.gms:play-services-mlkit-text-recognition:19.0.0'

// Language auto-detection
implementation 'com.google.mlkit:language-id:17.0.5'

// UI
implementation 'androidx.appcompat:appcompat:1.6.1'
implementation 'com.google.android.material:material:1.11.0'
implementation 'androidx.cardview:cardview:1.0.0'
implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
```

---

## Notes

- **Min SDK**: API 26 (Android 8.0) — required for `TYPE_APPLICATION_OVERLAY`
- **Target SDK**: 34 (Android 14)
- Urdu and Arabic text is displayed right-to-left automatically
- The Accessibility Service reads text **only when you tap the bubble** — not continuously
- No user data is stored or transmitted; translation uses on-device ML Kit models after first download
