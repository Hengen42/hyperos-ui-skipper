package dev.hengen.uiskipper;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Pure-reflection payload, loaded into com.miui.securitycenter via an
 * InMemoryDexClassLoader from the Zygisk native module. No Xposed/hook API is
 * used: Activity.ActivityLifecycleCallbacks plus a short-lived poll loop is
 * enough to neutralise the artificial countdowns documented in docs/RESEARCH.md.
 *
 * Every reflective access is wrapped so a HyperOS update that renames a class
 * or field only disables the matching feature, never crashes the host app.
 */
public final class Injector {

    private static final String TAG = "HyperOSUiSkipper";

    public static final String MODE_KEY = "hyperos_uiskipper_mode";
    public static final int MODE_SKIP_WAIT = 1;
    public static final int MODE_FULL_AUTO = 2;

    private static final String PERM = "com.miui.permcenter.";
    private static final String INTERCEPT_BASE_FRAGMENT = PERM + "privacymanager.InterceptBaseFragment";
    private static final String SPECIAL_PERMISSION_ACTIVITY = PERM + "privacymanager.SpecialPermissionInterceptActivity";
    private static final String DEVICE_MANAGER_APPLY_ACTIVITY = PERM + "privacymanager.DeviceManagerApplyActivity";
    private static final String ADB_INPUT_ACTIVITY = PERM + "install.AdbInputApplyActivity";
    private static final String ROOT_APPLY_ACTIVITY = PERM + "root.RootApplyActivity";
    private static final String ADB_INSTALL_ACTIVITY = PERM + "install.AdbInstallActivity";

    private static final long POLL_INTERVAL_MS = 250;
    private static final int MAX_RETRIES = 40; // ~10s waiting for a fragment to attach

    private static final Map<Activity, Boolean> ACTIVE =
            Collections.synchronizedMap(new WeakHashMap<Activity, Boolean>());
    private static final Map<Activity, Boolean> CONFIRMED =
            Collections.synchronizedMap(new WeakHashMap<Activity, Boolean>());

    private Injector() {
    }

    /** Called from native code once ActivityThread.currentApplication() is available. */
    public static void init(final Context base) {
        try {
            final Application app = (Application) base.getApplicationContext();
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    install(app);
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "init failed", t);
        }
    }

    private static void install(Application app) {
        try {
            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                }

                @Override
                public void onActivityStarted(Activity activity) {
                }

                @Override
                public void onActivityResumed(Activity activity) {
                    onResumed(activity);
                }

                @Override
                public void onActivityPaused(Activity activity) {
                    ACTIVE.remove(activity);
                }

                @Override
                public void onActivityStopped(Activity activity) {
                    ACTIVE.remove(activity);
                }

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                }

                @Override
                public void onActivityDestroyed(Activity activity) {
                    ACTIVE.remove(activity);
                    CONFIRMED.remove(activity);
                }
            });
            Log.i(TAG, "activity lifecycle callbacks registered");
        } catch (Throwable t) {
            Log.e(TAG, "install failed", t);
        }
    }

    private static void onResumed(final Activity activity) {
        final String name = activity.getClass().getName();
        if (!(SPECIAL_PERMISSION_ACTIVITY.equals(name) || DEVICE_MANAGER_APPLY_ACTIVITY.equals(name)
                || ADB_INPUT_ACTIVITY.equals(name) || ROOT_APPLY_ACTIVITY.equals(name)
                || ADB_INSTALL_ACTIVITY.equals(name))) {
            return;
        }
        if (ACTIVE.containsKey(activity)) {
            return; // already being watched
        }
        ACTIVE.put(activity, Boolean.TRUE);

        final Handler main = new Handler(Looper.getMainLooper());
        final int[] retries = {0};
        final Runnable[] loop = new Runnable[1];
        loop[0] = new Runnable() {
            @Override
            public void run() {
                if (!Boolean.TRUE.equals(ACTIVE.get(activity)) || isGone(activity)) {
                    ACTIVE.remove(activity);
                    return;
                }
                boolean keepPolling = patch(activity, name);
                retries[0]++;
                if (keepPolling && retries[0] < MAX_RETRIES) {
                    main.postDelayed(loop[0], POLL_INTERVAL_MS);
                } else {
                    ACTIVE.remove(activity);
                }
            }
        };
        main.post(loop[0]);
    }

    private static boolean isGone(Activity activity) {
        try {
            return activity.isFinishing() || activity.isDestroyed();
        } catch (Throwable t) {
            return false;
        }
    }

    /** @return true if the caller should keep polling this activity. */
    private static boolean patch(Activity activity, String name) {
        try {
            if (ADB_INPUT_ACTIVITY.equals(name) || ROOT_APPLY_ACTIVITY.equals(name)) {
                // Multi-step wizard: every click re-arms a 5s counter in field "h".
                // There is no click hook here, so keep clamping it for as long as
                // the screen is visible.
                clampIntField(activity, "h", 1);
                return true;
            }
            if (ADB_INSTALL_ACTIVITY.equals(name)) {
                clampIntField(activity, "r", 0);
                return false;
            }
            // Intercept family (accessibility / unknown sources / auto-start "danger" screens).
            Object fragment = findInterceptFragment(activity);
            if (fragment == null) {
                return true; // fragment not attached yet, retry shortly
            }
            patchIntercept(activity, fragment);
            return false;
        } catch (Throwable t) {
            Log.e(TAG, "patch failed for " + name, t);
            return false;
        }
    }

    private static void patchIntercept(Activity activity, Object fragment) {
        try {
            int mode = Settings.Global.getInt(activity.getContentResolver(), MODE_KEY, MODE_SKIP_WAIT);
            if (mode == MODE_FULL_AUTO) {
                if (Boolean.TRUE.equals(CONFIRMED.get(activity))) {
                    return;
                }
                Method k0 = findMethod(fragment.getClass(), "k0", boolean.class);
                if (k0 != null) {
                    CONFIRMED.put(activity, Boolean.TRUE);
                    k0.invoke(fragment, true);
                    Log.i(TAG, "mode 2: auto-confirmed " + fragment.getClass().getName());
                }
                return;
            }
            // Mode 1: only zero the timer, user still ticks the box and taps OK.
            Field handlerField = findField(fragment.getClass(), "c");
            if (handlerField != null) {
                Object handler = handlerField.get(fragment);
                if (handler != null) {
                    clampIntField(handler, "a", 0);
                    if (handler instanceof Handler) {
                        ((Handler) handler).removeMessages(1);
                    }
                }
            }
            Method q0 = findMethod(fragment.getClass(), "q0", int.class);
            if (q0 != null) {
                q0.invoke(fragment, 0);
            }
            Log.i(TAG, "mode 1: timer zeroed for " + fragment.getClass().getName());
        } catch (Throwable t) {
            Log.e(TAG, "patchIntercept failed", t);
        }
    }

    private static Object findInterceptFragment(Activity activity) {
        try {
            Method getFm = findMethod(activity.getClass(), "getSupportFragmentManager");
            if (getFm == null) {
                return null;
            }
            Object fm = getFm.invoke(activity);
            if (fm == null) {
                return null;
            }
            Method getFragments = findMethod(fm.getClass(), "getFragments");
            if (getFragments == null) {
                return null;
            }
            Object result = getFragments.invoke(fm);
            if (!(result instanceof List)) {
                return null;
            }
            for (Object fragment : (List<?>) result) {
                if (fragment != null && isSubclassOf(fragment.getClass(), INTERCEPT_BASE_FRAGMENT)) {
                    return fragment;
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "findInterceptFragment failed", t);
        }
        return null;
    }

    private static void clampIntField(Object obj, String name, int max) throws Exception {
        Field f = findField(obj.getClass(), name);
        if (f == null) {
            return;
        }
        int cur = f.getInt(obj);
        if (cur > max) {
            f.setInt(obj, max);
        }
    }

    private static Field findField(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> cls, String name, Class<?>... params) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static boolean isSubclassOf(Class<?> cls, String ancestorName) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            if (ancestorName.equals(c.getName())) {
                return true;
            }
        }
        return false;
    }
}
