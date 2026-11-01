# Changelog

## 1.0.3
- **In-App Module Updates:** Added `updateJson` support so KernelSU, Magisk, and APatch can directly notify users and update the module with a single tap.
- **Dynamic Release Asset Naming:** Release ZIP archives are now dynamically named with the version tag (`HyperOS-UI-Skipper-vX.X.X.zip`).
- **Cleaned Codebase:** Removed legacy unused Zygisk native C++ code and IDE settings.

## 1.0.2
- **Fix (Critical):** Resolved Xposed hook failure when compiling via Gradle / CI. Strip compile-time stub classes (`de.robv.**`) prior to DEX generation so the runtime Xposed framework (Vector/LSPosed) provides the real implementation instead of no-op stubs.
- **UI Bugfix:** Fixed CSS layout where the Mode 2 risk consent checkbox overflowed outside the option card container.
- **Documentation:** Added clear prerequisites section explicitly stating requirement of Root (KernelSU / KernelSU-Next / APatch / Magisk) and Xposed Framework (Vector / LSPosed).

## 1.0.1
- Initial public release of HyperOS UI Skipper.
