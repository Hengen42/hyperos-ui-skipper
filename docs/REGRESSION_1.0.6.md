# v1.0.6 hook-loading regression and v1.0.8 repair

Investigated on 2026-11-07 using the connected Xiaomi 14T Pro (rothko),
HyperOS / Android 16, Vector v2.2 (3112-ddeed8ca).

## Cause

The v1.0.5 to v1.0.6 diff added `META-INF/xposed/java_init.list`,
`module.prop` (`targetApiVersion=102`), and `scope.list`. The entry class
remained a legacy `IXposedHookLoadPackage` implementation. The hook Java
code did not change between v1.0.5 and the affected releases.

The device's existing Vector logs contain:

```text
Failed to instantiate class dev.hengen.uiskipper.Entry
NoClassDefFoundError: Failed resolution of: .../IXposedHookLoadPackage;
ClassNotFoundException: ...IXposedHookLoadPackage is unavailable to modules targeting Xposed API 102 or higher
No entry class of dev.hengen.uiskipper could be instantiated
```

This is an API/loading mismatch, not a missing framework activation or scope.
The repair removes the modern declarations, preserving the working classic
entry and package filter. It does not claim modern LibXposed compatibility
or a manager-enforced static scope lock.

## Checks performed

- Gradle release build and release lint passed.
- Packaged DEX contains `Entry`, no Xposed stub class definitions, and only
  the classic `assets/xposed_init` registration. The new CI checker passes
  the repaired APK and rejects an affected APK's modern metadata.
- The standalone PowerShell build also passed, including the APK checker;
  its manifest reads the configured version (now 1.0.8 / 108) rather than hard-coded 1.0.4 / 104.
- Installed the repaired Gradle APK and activated it in Vector. New
  SecurityCenter process logs confirm timer, auto-confirm, both step
  handlers, and ADB install handler registration.
- Before automatic verification was added, with SecurityCenter stopped,
  the repaired status script returned
  `state=no_hook`, `hook=0`, even with old success records present.
- After a cold launch of `AdbInputApplyActivity`, UI capture completed in
  3.241 seconds with `accept` enabled and text `Sonraki`, before the normal
  five-second wait would finish. No confirmation button was clicked.
- Status with a running, hooked process returns `state=ok`, `hook=1`.

The phone was left in Mode 1. Full automatic approval behavior, every
permission screen, other ROMs, and LSPosed were not exercised in this test.
Device baseline APKs and test artifacts are saved under ignored
`out/diagnostics/`.

## v1.0.8 clean installation

The previous module was removed and the device rebooted before a clean
v1.0.8 installation. The package includes the hook-loading repair, with
all current release definitions updated to 1.0.8 / 108.

The v1.0.8 / 108 Gradle build, release lint, APK structure check, ZIP
version check, and shell LF checks passed. KernelSU Next's standard module
installer completed successfully after Play Protect approval. No app or
test screen was launched during the clean installation.

## Automatic verification added to v1.0.8

Full status checks now acquire SecurityCenter's Settings search provider
using a read-only `content gettype` request if current hook proof is absent.
This starts the main process without opening an Activity or granting a
permission. The request can return an unsupported-type error on the tested
ROM; only a matching current-process hook success log counts as success.
Verification waits at most five samples, and the provider command has a
five-second timeout. No persistent watcher or automatic force-stop was added.
The existing one-shot boot check, installer check, and WebUI refresh all
use this path. Fast status checks do not start the process.

On the connected device, with SecurityCenter initially stopped:

- `--fast` returned `state=checking` and left the process stopped.
- A full check started it silently and returned `state=ok`, `hook=1` in
  2.514 seconds without an Activity launch.
- A test copy with inaccessible log paths returned `state=no_hook`,
  `hook=0` after the bounded retry instead of inventing success.
- The WebUI JavaScript syntax check passed.

The previous manual-open instructions were removed from both languages
and the module action output. Version remains v1.0.8 / 108.

## Follow-up: Vector scope lock and description

The published v1.0.8 restored loading but its classic scope recommendation
did not restrict the manager's picker. A subsequent device experiment on
Vector 3112 verified a fixed SecurityCenter-only picker with API 93 scope
metadata and an EMPTY modern Java entry marker. The user confirmed that
only SecurityCenter is listed, and fresh-process hook registration remained
successful. The legacy `assets/xposed_init` entry remains the runtime entry.
This is scope metadata, not a modern API port. Other framework versions
have not been device-tested with this follow-up.

Vector reads `ApplicationInfo.loadDescription` in this metadata view, so
the manifest now declares `android:description`. Classic
`xposeddescription` references the same string resource. Gradle release
build/lint and current-process hook checks pass with this description fix.
The packaging checker requires the API 93/empty-marker/single-target
combination and rejects API 102, a nonempty modern entry, and extra targets.

These follow-up changes are included in the republished v1.0.8 package.
Version code remains 108; existing installations need to reinstall the
updated ZIP because the version number has not increased.

## Follow-up: WebUI startup

Startup no longer performs a separate synchronous settings read. The page
paints before the fast status request, uses that response to initialize the
saved mode, and paints again before starting full verification. Mode controls
remain disabled until their real value is known; pending checks do not appear
as failures or as verified success. Activation and scope queries now run as
two bounded read-only workers with a shared prepared Java environment.

On-device alternating warm checks completed in 1.27/0.72/1.04 seconds for the
previous script and 0.53/0.51/0.75 seconds for the optimized script. An initial
pre-change full check took 2.98 seconds; the fast check took 0.09 seconds.
These are shell status timings, not end-to-end KernelSU WebView launch times.
The host application's WebView startup time is outside this page's control.

Three dependency-free Node tests cover first paint before root work, saved
Mode 2 availability before full verification, and safe failures. Live target
hook verification still succeeds, and the missing-log test still fails safely.
No persistent monitor or cached healthy result was introduced.
