# HyperOS UI Skipper (`hyperos-ui-skipper`)

[English Documentation](README.md) | [Türkçe Dokümantasyon](README_TR.md)

![License](https://img.shields.io/badge/License-MIT-green.svg)
![Platform](https://img.shields.io/badge/Platform-HyperOS%201%20%7C%202%20%7C%203%20%7C%20MIUI-blue.svg)
![Version](https://img.shields.io/badge/Version-v1.0.7-brightgreen.svg)
![Root](https://img.shields.io/badge/Root-KernelSU%20%7C%20KernelSU--Next%20%7C%20APatch%20%7C%20Magisk-orange.svg)
![Hook](https://img.shields.io/badge/Hook-Vector%20%7C%20LSPosed%20(Xposed%20API%2093%2B)-informational.svg)

**Author:** Hengen  
**License:** MIT  
**Category:** Open-Source UI Optimization & Developer Workflow Module

---

## 1. Requirements & Prerequisites

The following components are **strictly required** for this module to operate:
- **Root Solution:** [KernelSU-Next](https://github.com/KernelSU-Next/KernelSU-Next), [KernelSU](https://github.com/tiann/KernelSU), [Magisk](https://github.com/topjohnwu/magisk), or [APatch](https://github.com/bmax121/APatch)
- **Zygisk Implementation:** Built-in Magisk Zygisk, [NeoZygisk](https://github.com/JingMatrix/NeoZygisk), or [ZygiskNext](https://github.com/Dr-TSNG/ZygiskNext)
- **Xposed Framework:** [Vector](https://github.com/JingMatrix/Vector) or [LSPosed](https://github.com/LSPosed/LSPosed)

### Tested & Verified Environment (Developer Setup)
Tested and verified to be 100% working on the developer's primary device:
- **Device & ROM:** Xiaomi 14T Pro (`rothko`) — HyperOS 3.0 (Android 16)
- **Root Solution:** [KernelSU-Next](https://github.com/KernelSU-Next/KernelSU-Next)
- **Zygisk:** [NeoZygisk](https://github.com/JingMatrix/NeoZygisk)
- **Hook Framework:** [Vector](https://github.com/JingMatrix/Vector)

---

## 2. Overview & Problem Statement

On Xiaomi's **HyperOS** and **MIUI** (`com.miui.securitycenter`), modifying developer or sensitive accessibility settings triggers an unavoidable full-screen warning with a 5 or 10-second artificial countdown timer (**see `dangers.jpeg`**).

Common triggers include:
- **Developer Options:** USB Debugging, Install via USB, and USB Debugging (Security Settings).
- **Accessibility:** Granting accessibility permissions to automation or assistive applications.
- **Install Unknown Apps:** Sideloading developer or external APK packages.
- **Auto-start & Special App Access:** Removing background execution limits.

**HyperOS UI Skipper** eliminates this artificial `CountDownTimer` delay while giving you flexible choices on how confirmation dialogs are handled.

---

## 2. Visual Reference (`dangers.jpeg`)

The file **`dangers.jpeg`** in the repository root shows the targeted interface:
- **Header:** Warning icon and "Danger" title.
- **Confirmation Checkbox (`CheckBox`):** *"I am aware of the possible risks..."*
- **Action Button (`Button`):** An **"OK (10s)"** button held disabled until the timer completes.

---

## 3. Operating Behaviors

The module provides two distinct options tailored to your preference:

| Option | Badge | Behavior |
|---|---|---|
| **Skip Countdown Only** | `Recommended` | The 10-second wait ends instantly. You retain complete control over reviewing risks, checking the consent box, and tapping OK manually. |
| **Full Automatic** | `Instant` | Completely automated: countdown is eliminated, the risk checkbox is ticked automatically, and OK is pressed instantly without any manual action. |

---

## 4. Architecture & Design

Everything is packaged and delivered through a single flashable `.zip` file:

1. **Xposed Hook Engine (`dev.hengen.uiskipper`):**
   - Headless and invisible: no launcher icon, no activity, **zero root permissions requested**.
   - Injected into `com.miui.securitycenter` at runtime.
   - **Obfuscation-Resilient UI Traversal:** Discovers and interacts with `CompoundButton` and `Button` views in the active layout, remaining immune to R8/Proguard method renames across HyperOS updates.
2. **Flashable Root Module & WebUI (`hyperos_ui_skipper`):**
   - Compatible with **KernelSU**, **KernelSU Next**, **APatch**, and **Magisk**.
   - Elegant, bilingual WebUI (English & Turkish) embedded inside your root manager.
   - Automatically provisions and enables the module in **Vector** (`zygisk_vector`) or **LSPosed** (`zygisk_lsposed`).

---

## 5. End-User Installation

Users do not need to install any APK manually:

1. **Prerequisites:** Ensure your device has the following active components:
   - **Root:** [KernelSU-Next](https://github.com/KernelSU-Next/KernelSU-Next), [KernelSU](https://github.com/tiann/KernelSU), [Magisk](https://github.com/topjohnwu/magisk), or [APatch](https://github.com/bmax121/APatch)
   - **Zygisk:** Built-in Magisk Zygisk, [NeoZygisk](https://github.com/JingMatrix/NeoZygisk), or [ZygiskNext](https://github.com/Dr-TSNG/ZygiskNext)
   - **Xposed:** [Vector](https://github.com/JingMatrix/Vector) or [LSPosed](https://github.com/LSPosed/LSPosed)
2. Download the latest **`HyperOS-UI-Skipper-v*.zip`** package from the [Releases](../../releases) tab.
3. Flash the `.zip` file via **KernelSU**, **APatch**, or **Magisk**.
   > [!NOTE]  
   > The installer script (`customize.sh`) automatically installs the bundled hook APK, activates it in Vector/LSPosed, and configures the target scope. **No reboot is required.**
4. Open the module's **WebUI** in your root manager to select your desired behavior.

---

## 6. Building from Source

### Using Android Studio / Gradle
```bash
./gradlew :xposed-module:assembleRelease
```

### Using Local PowerShell Scripts (Windows)
```powershell
# 1. Compile hook APK:
.\xposed-module\build.ps1

# 2. Package flashable module ZIP:
.\zygisk-module\build.ps1
```
The final flashable zip will be generated at `zygisk-module\HyperOS-UI-Skipper-v*.zip`.

---

## 7. Contributing, Pull Requests & Bug Reports

Contributions from the developer community are warmly welcomed!

### Submitting Code & Improvements (Pull Requests)
If you have written a fix, added support for another HyperOS version, or improved performance:
1. **Fork** this repository to your own GitHub account.
2. Create a dedicated feature branch:
   ```bash
   git checkout -b feature/my-enhancement
   ```
3. Commit your changes with clear, descriptive messages.
4. Push to your branch and open a **Pull Request (PR)** against the `main` branch. We will review and merge it!

### Bug Reports & Feature Requests (GitHub Issues)
If you encounter a bug or have a suggestion without writing code:
1. Visit the [GitHub Issues](https://github.com/Hengen42/hyperos-ui-skipper/issues) tracker.
2. Click **New Issue** and describe what happened or what you would like to see.
3. Please include your device model, ROM version (e.g. HyperOS 1/2/3), and root setup.

---

## 8. Frequently Asked Questions (FAQ)

### Q: Why do I keep seeing a persistent "Vector loaded" notification?
**Answer:** The "Vector loaded" (*Vector yüklendi*) alert is a built-in foreground status notification generated by the **Vector framework itself**, not by HyperOS UI Skipper.
- **Why Vector shows it:** Vector displays this notification to prevent Android/HyperOS from killing its background daemon process (`vectord`) and to provide quick access to the Vector Manager.
- **How to disable it:** Open **Vector Manager** &rarr; tap the **Settings** gear icon (bottom right) &rarr; toggle off **"Status notification"** (or long-press the notification in your notification shade and turn off the `vector_status` channel).

---

## 9. License & Credits
- **Author:** Hengen
- **License:** MIT License
