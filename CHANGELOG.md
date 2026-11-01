# Changelog

## 1.0.1 (Initial Public Release)
- **Core Functionality:**
  - Instantly removes the artificial 5/10-second countdown on HyperOS / MIUI security warning screens (`com.miui.securitycenter`).
  - Supports two operational behaviors:
    - **Skip Countdown Only (Recommended):** Zeroes the timer, leaving checkbox selection and OK confirmation to the user.
    - **Full Automatic:** Bypasses countdown, automatically ticks the risk agreement checkbox, and taps OK immediately.
- **Obfuscation Resilience:**
  - Uses dynamic UI traversal to discover and interact with layout confirmation `CheckBox` and `Button` views, preventing breakage from R8/Proguard method renaming across HyperOS updates.
  - Multi-step countdown and timer handlers include reflection fallback searches.
- **Dual Framework Support:**
  - Built-in detection and automatic activation support for both **Vector** (`zygisk_vector`) and **LSPosed** (`zygisk_lsposed`).
- **Interactive Bilingual WebUI:**
  - Supports English and Turkish with automatic browser/device language detection and manual switching.
  - One-click framework activation and hook APK management.
  - Debounced actions to prevent accidental double-clicks.
- **Architecture & System Integration:**
  - Hook APK (`dev.hengen.uiskipper`) requires zero root permissions and runs headlessly.
  - Flashable ZIP for KernelSU, KernelSU-Next, APatch, and Magisk bundles the APK and provisions it seamlessly.
