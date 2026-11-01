# Research notes (Phase 1)

Target: `com.miui.securitycenter` on HyperOS `OS3.0.302.0.WNNTWXM` (rothko, Android 16).
Class and field names are obfuscated and may change in other builds.

| Screen | Class | Counter | Mechanism |
|---|---|---|---|
| Permission "Danger" (accessibility, unknown sources, auto-start...) | `permcenter.privacymanager.InterceptPermissionFragment` / `InterceptMIUIFragment` (base `InterceptBaseFragment`) | 10 s / 5 s via `m0()` | `InterceptBaseFragment$b` Handler, 1 s ticks, `q0(int)` enables OK at <= 0. OK also requires the checkbox. |
| USB debugging (security settings) | `permcenter.install.AdbInputApplyActivity` (+`$a`) | 5 s per step, 3 steps | field `h` |
| Root request | `permcenter.root.RootApplyActivity` (+`$a`) | 5 s per step, 5 steps | field `h` |
| USB install | `permcenter.install.AdbInstallActivity` (+`$a`) | 10 s | field `r`, released when < 0 |

Settings.apk has no equivalent countdown; all flows live in SecurityCenter.

Hooks (`Entry.java`):
- `InterceptBaseFragment$b` constructor and `b(int)`: initial value forced to 0.
- `AdbInputApplyActivity$a` / `RootApplyActivity$a` `handleMessage`: counter `h` clamped to 1 so the next tick enables the button.
- `AdbInstallActivity$a.handleMessage`: counter `r` set to 0 so the next tick drops below zero.
- `InterceptBaseFragment.onInflateView` (afterHookedMethod): when `Settings.Global.hyperos_uiskipper_mode == 2` (Mode 2 / `FULL_AUTO`), calls `k0(true)` once the view is attached — auto-confirms the screen with no wait, no checkbox tick and no OK tap. Mode 1 (default) leaves the checkbox and OK button to the user.

## Injection path: why Vector, not pure Zygisk

A hookless native Zygisk module (no Vector/LSPosed dependency) was built and tested with
unconditional diagnostic logging in `onLoad`/`preAppSpecialize`/`postAppSpecialize`. Findings:
- The module loaded and ran correctly in ordinary apps (uid 10000+) and even in `system_server`
  and `com.android.systemui` (both uid 1000, `android.uid.system`, same zygote64).
- It **never** ran for `com.miui.securitycenter` (uid 1000, SELinux domain `system_app`), nor for
  `com.miui.notification`, `mi_connect_service`, or `com.android.dynsystem` (also uid 1000).
- Ruled out: 32-bit zygote (device has none), SELinux denials (none found involving zygisk), the
  app crashing/dying (it ran stably), lock-screen/Dozing interference (confirmed separately), and
  uid 1000 itself (system_server/systemui on the same uid were hookable).
- Vector's own log confirmed it *does* inject into system_server and systemui the same way, so the
  blocker is specific to how `com.miui.securitycenter` is forked/specialized, not to NeoZygisk's
  ptrace mechanism in general.
- Working conclusion (unproven): HyperOS likely forks/starts this specific security component
  through a hardened or non-standard path that NeoZygisk's injection does not intercept.

## Why not a static `classes.dex` patch

`pm path com.miui.securitycenter` and `dumpsys package` on-device showed the **running** copy is
not a plain `/system` app:
- Factory stub: `/product/priv-app/MIUISecurityCenterGlobal/MIUISecurityCenterGlobal.apk`,
  versionCode `40001261` (v12.6.1).
- Live, running copy: `/data/app/~~.../com.miui.securitycenter-.../base.apk`, versionCode
  `40001286` (v12.8.6), `signatures=PackageSignatures{... version:3 ...}` — i.e. an app-market-style
  signed **update** over the factory stub, not a loose system-partition file.

Replacing this file via a systemless mount with a dex patched and re-signed under our own key would
present PackageManagerService with a certificate that doesn't match the one recorded for this
already-installed system package. The safe failure mode is PMS silently rejecting the replacement
(reverting to the unmodified app); the unsafe one is the package's data/granted-permissions being
reset, which is unacceptable for a component this central to HyperOS security. This path was
abandoned in favor of runtime injection via Vector (see `CHANGELOG.md` 3.0.0).
