# HyperOS UI Skipper (`hyperos-ui-skipper`)

[English Documentation](README.md) | [Türkçe Dokümantasyon](README_TR.md)

![License](https://img.shields.io/badge/License-MIT-green.svg)
![Platform](https://img.shields.io/badge/Platform-HyperOS%201%20%7C%202%20%7C%203%20%7C%20MIUI-blue.svg)
![Version](https://img.shields.io/badge/Version-v1.0.2-brightgreen.svg)
![Root](https://img.shields.io/badge/Root-KernelSU%20%7C%20KernelSU--Next%20%7C%20APatch%20%7C%20Magisk-orange.svg)
![Hook](https://img.shields.io/badge/Hook-Vector%20%7C%20LSPosed%20(Xposed%20API%2093%2B)-informational.svg)

**Author:** Hengen  
**License:** MIT  
**Category:** Open-Source UI Optimization & Developer Workflow Module

---

## 1. Requirements & Prerequisites

The following components are **strictly required** for this module to operate:
- **Root Solution:** [KernelSU-Next](https://github.com/KernelSU-Next/KernelSU-Next), [KernelSU](https://github.com/tiann/KernelSU), [Magisk](https://github.com/topjohnwu/magisk), or [APatch](https://github.com/bmax121/APatch)
- **Zygisk Implementation:** [NeoZygisk](https://github.com/JingMatrix/NeoZygisk) or [ZygiskNext](https://github.com/Dr-TSNG/ZygiskNext)
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

1. Ensure **Vector** or **LSPosed** is installed and active on your rooted device.
2. Download the latest **`HyperOS-UI-Skipper-v*.zip`** package from the [Releases](../../releases) tab.
3. Flash the `.zip` file via **KernelSU**, **APatch**, or **Magisk**.
   > [!NOTE]  
   > The installer script (`customize.sh`) automatically installs the bundled hook APK and configures framework scope for you.
4. Reboot your device.
5. Open the module's **WebUI** in your root manager to select your desired behavior.

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
The final flashable zip will be generated at `zygisk-module\HyperOS-UI-Skipper.zip`.

---

## 7. License & Credits
- **Author:** Hengen
- **License:** MIT License
