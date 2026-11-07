# Changelog

## 1.0.8
- **Critical hook loading fix:** v1.0.6 introduced modern Xposed metadata with `targetApiVersion=102` while the entry class still used the legacy `IXposedHookLoadPackage` API. Vector blocks that API for modules targeting API 102, so the module could appear enabled with the correct scope while no hooks loaded. Removed the incompatible modern declarations and restored classic Xposed loading.
- **Status accuracy:** Only report an active hook when a running SecurityCenter process has a matching hook success log; old logs no longer count as current injection proof.
- **Automatic hook verification:** When needed, a full status check starts SecurityCenter silently through a read-only provider request and verifies the current process's hook log. Users no longer need to open SecurityCenter manually. Verification is bounded and does not run a background monitoring loop.
- **Local APK builds:** Read the APK version from the build configuration instead of hard-coding v1.0.4.

## 1.0.6
- **Fixed Scope Restriction (LSPosed):** Added static scope configuration (`staticScope=true`, `scope.list`, and `module.prop` in `META-INF/xposed/`) inspired by DisableFlagSecure. Strictly locks the scope in LSPosed Manager to target only `com.miui.securitycenter`, preventing users from selecting unrelated applications.
- **Known regression:** The modern API metadata was incompatible with the legacy entry class and prevented hook loading in Vector. Fixed in v1.0.8.

## 1.0.5
- **English Action Button:** Action button execution outputs (`action.sh`) are now fully in English.
- **English Module Status:** Dynamic module status in root managers (KernelSU, Magisk, APatch) is now displayed in English (e.g. `✅ Active (Vector) — Skip Countdown Only`).
- **Automated Release Notes:** GitHub release notes are now automatically parsed and populated directly from `CHANGELOG.md`.
- **Repository Line-Ending Rules:** Added `.gitattributes` to enforce LF line endings across all scripts and GitHub Actions workflows.

## 1.0.4
- **Installer Resilience:** Added automatic recovery for `INSTALL_FAILED_UPDATE_INCOMPATIBLE` during in-app module updates, ensuring seamless upgrades even if keystore signatures differ.
- **Workflow Automation:** Release creation, release notes generation, and ZIP asset uploading are now fully automated on every push.

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
