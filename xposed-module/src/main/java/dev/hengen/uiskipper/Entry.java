package dev.hengen.uiskipper;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * HyperOS UI Skipper - Hook Entry Point
 *
 * Removes the artificial wait on the HyperOS "Danger" confirmation screens
 * in com.miui.securitycenter.
 *
 * Designed with dynamic fallbacks and UI-traversal to survive Proguard/R8
 * obfuscation changes across different HyperOS/MIUI builds and ROM regions.
 */
public class Entry implements IXposedHookLoadPackage {
    private static final String TAG = "[HyperOSUiSkipper] ";
    private static final String PKG = "com.miui.securitycenter";
    private static final String PERM = "com.miui.permcenter.";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!PKG.equals(lp.packageName)) return;
        ClassLoader cl = lp.classLoader;

        hookInterceptTimer(cl);
        hookInterceptAutoConfirm(cl);
        hookStepHandler(cl, PERM + "install.AdbInputApplyActivity$a", "a", "h");
        hookStepHandler(cl, PERM + "root.RootApplyActivity$a", "a", "h");
        hookAdbInstallHandler(cl);
    }

    // =========================================================================
    // 1. Permission "Danger" Screen Timer (Accessibility, Unknown Sources, etc.)
    // =========================================================================
    private void hookInterceptTimer(ClassLoader cl) {
        try {
            Class<?> baseFragmentClass = null;
            try {
                baseFragmentClass = XposedHelpers.findClass(PERM + "privacymanager.InterceptBaseFragment", cl);
            } catch (Throwable ignored) {}

            Class<?> timerClass = null;
            // 1a. Try known obfuscated inner class name ($b)
            try {
                timerClass = XposedHelpers.findClass(PERM + "privacymanager.InterceptBaseFragment$b", cl);
            } catch (Throwable t) {
                // 1b. Fallback: inspect inner classes of InterceptBaseFragment for a Handler
                if (baseFragmentClass != null) {
                    for (Class<?> inner : baseFragmentClass.getDeclaredClasses()) {
                        if (Handler.class.isAssignableFrom(inner)) {
                            timerClass = inner;
                            XposedBridge.log(TAG + "resolved timer Handler via reflection: " + inner.getName());
                            break;
                        }
                    }
                }
            }

            if (timerClass == null) {
                XposedBridge.log(TAG + "timer class could not be found");
                return;
            }

            XC_MethodHook zeroArg = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    for (int i = 0; i < p.args.length; i++) {
                        if (p.args[i] instanceof Integer) {
                            p.args[i] = 0;
                        }
                    }
                }
            };

            XposedBridge.hookAllConstructors(timerClass, zeroArg);

            // Hook known method 'b(int)'
            try {
                XposedHelpers.findAndHookMethod(timerClass, "b", int.class, zeroArg);
            } catch (Throwable t) {
                // Fallback: Hook any method in timerClass taking single int parameter
                for (Method m : timerClass.getDeclaredMethods()) {
                    Class<?>[] pTypes = m.getParameterTypes();
                    if (pTypes.length == 1 && (pTypes[0] == int.class || pTypes[0] == Integer.class)) {
                        XposedBridge.hookMethod(m, zeroArg);
                        XposedBridge.log(TAG + "fallback hooked timer method: " + m.getName());
                    }
                }
            }
            XposedBridge.log(TAG + "intercept timer hooked successfully");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "intercept timer hook failed: " + t);
        }
    }

    // =========================================================================
    // 2. Mode 2: Auto-Confirm (FULL_AUTO)
    // =========================================================================
    private void hookInterceptAutoConfirm(ClassLoader cl) {
        try {
            Class<?> baseFragmentClass = XposedHelpers.findClass(
                    PERM + "privacymanager.InterceptBaseFragment", cl);

            XposedHelpers.findAndHookMethod(baseFragmentClass, "onInflateView",
                    LayoutInflater.class, ViewGroup.class, Bundle.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(final MethodHookParam p) {
                            final View view = (View) p.getResult();
                            if (view == null) return;

                            int mode = Mode.SKIP_WAIT;
                            try {
                                mode = android.provider.Settings.Global.getInt(
                                        view.getContext().getContentResolver(),
                                        Mode.KEY,
                                        Mode.SKIP_WAIT);
                            } catch (Throwable ignored) {}

                            if (mode != Mode.FULL_AUTO) return;

                            final Object fragment = p.thisObject;
                            view.post(new Runnable() {
                                @Override
                                public void run() {
                                    triggerAutoConfirm(fragment, view);
                                }
                            });
                        }
                    });
            XposedBridge.log(TAG + "intercept auto-confirm hooked successfully");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "intercept auto-confirm hook failed: " + t);
        }
    }

    /**
     * Executes auto-confirm safely using both UI event synthesis and method reflection.
     */
    private static void triggerAutoConfirm(Object fragment, View root) {
        boolean clicked = false;
        try {
            // Step 1: Find and check any un-ticked CheckBox / CompoundButton in the view tree
            List<CompoundButton> checkBoxes = new ArrayList<CompoundButton>();
            findViewsByType(root, CompoundButton.class, checkBoxes);
            for (CompoundButton cb : checkBoxes) {
                if (!cb.isChecked()) {
                    cb.setChecked(true);
                    XposedBridge.log(TAG + "auto-checked consent CheckBox");
                }
            }

            // Step 2: Find positive action button in the view tree and click it
            List<Button> buttons = new ArrayList<Button>();
            findViewsByType(root, Button.class, buttons);
            for (Button btn : buttons) {
                // If button is disabled due to timer, enable it
                if (!btn.isEnabled()) {
                    btn.setEnabled(true);
                }
                // Check if button text matches common confirmation keywords
                CharSequence text = btn.getText();
                if (text != null) {
                    String s = text.toString().toLowerCase();
                    if (s.contains("ok") || s.contains("tamam") || s.contains("确定") || s.contains("agree") || s.contains("accept")) {
                        btn.performClick();
                        clicked = true;
                        XposedBridge.log(TAG + "auto-clicked confirmation button: " + text);
                        break;
                    }
                }
            }

            // If no named button matched, try clicking the last button (standard Android positive button position)
            if (!clicked && !buttons.isEmpty()) {
                Button lastBtn = buttons.get(buttons.size() - 1);
                lastBtn.setEnabled(true);
                lastBtn.performClick();
                clicked = true;
                XposedBridge.log(TAG + "auto-clicked last button in layout");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "UI traversal auto-confirm failed: " + t);
        }

        // Step 3: Direct method fallback (calls k0(true) or equivalent) if UI click didn't finish it
        if (!clicked && fragment != null) {
            try {
                XposedHelpers.callMethod(fragment, "k0", true);
                XposedBridge.log(TAG + "called fragment.k0(true) directly");
            } catch (Throwable t) {
                // If k0 is renamed in newer builds, look for single boolean param method
                boolean methodFound = false;
                for (Method m : fragment.getClass().getDeclaredMethods()) {
                    Class<?>[] pTypes = m.getParameterTypes();
                    if (pTypes.length == 1 && pTypes[0] == boolean.class && m.getReturnType() == void.class) {
                        try {
                            m.setAccessible(true);
                            m.invoke(fragment, true);
                            methodFound = true;
                            XposedBridge.log(TAG + "called fallback method: " + m.getName() + "(true)");
                            break;
                        } catch (Throwable ignored) {}
                    }
                }
                if (!methodFound) {
                    XposedBridge.log(TAG + "auto confirm method invocation failed: " + t);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends View> void findViewsByType(View view, Class<T> type, List<T> results) {
        if (type.isInstance(view)) {
            results.add((T) view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int count = group.getChildCount();
            for (int i = 0; i < count; i++) {
                findViewsByType(group.getChildAt(i), type, results);
            }
        }
    }

    // =========================================================================
    // 3. Multi-Step Screens (USB Debugging Security Settings, Root Request)
    // =========================================================================
    private void hookStepHandler(ClassLoader cl, final String handlerClass, final String activityField, final String counterField) {
        try {
            XposedHelpers.findAndHookMethod(handlerClass, cl, "handleMessage", Message.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    Object rawActivity = null;
                    try {
                        rawActivity = XposedHelpers.getObjectField(p.thisObject, activityField);
                    } catch (Throwable ignored) {}

                    // Defensive check: unwraps WeakReference if wrapped
                    Object activity = (rawActivity instanceof WeakReference)
                            ? ((WeakReference<?>) rawActivity).get()
                            : rawActivity;

                    if (activity == null) {
                        // Fallback: search handler's fields for the Activity reference
                        activity = findActivityInHandler(p.thisObject);
                    }

                    if (activity == null) return;

                    // Try known counter field first
                    boolean clamped = false;
                    try {
                        int current = XposedHelpers.getIntField(activity, counterField);
                        if (current > 1) {
                            XposedHelpers.setIntField(activity, counterField, 1);
                            clamped = true;
                        }
                    } catch (Throwable ignored) {}

                    // Fallback: search all integer fields with values between 2 and 10 (countdown value)
                    if (!clamped) {
                        clampCounterFields(activity, 1);
                    }
                }
            });
            XposedBridge.log(TAG + "step handler hooked: " + handlerClass);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "step handler hook failed (" + handlerClass + "): " + t);
        }
    }

    // =========================================================================
    // 4. USB Install Confirmation Screen
    // =========================================================================
    private void hookAdbInstallHandler(ClassLoader cl) {
        final String handlerClass = PERM + "install.AdbInstallActivity$a";
        try {
            XposedHelpers.findAndHookMethod(handlerClass, cl, "handleMessage", Message.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    Object rawActivity = null;
                    try {
                        rawActivity = XposedHelpers.getObjectField(p.thisObject, "a");
                    } catch (Throwable ignored) {}

                    Object activity = (rawActivity instanceof WeakReference)
                            ? ((WeakReference<?>) rawActivity).get()
                            : rawActivity;

                    if (activity == null) {
                        activity = findActivityInHandler(p.thisObject);
                    }

                    if (activity == null) return;

                    boolean zeroed = false;
                    try {
                        int current = XposedHelpers.getIntField(activity, "r");
                        if (current > 0) {
                            XposedHelpers.setIntField(activity, "r", 0);
                            zeroed = true;
                        }
                    } catch (Throwable ignored) {}

                    // Fallback: clamp countdown field to 0
                    if (!zeroed) {
                        clampCounterFields(activity, 0);
                    }
                }
            });
            XposedBridge.log(TAG + "adb install handler hooked");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "adb install handler hook failed: " + t);
        }
    }

    // =========================================================================
    // Helper Reflection Methods
    // =========================================================================
    private static Object findActivityInHandler(Object handler) {
        if (handler == null) return null;
        for (Field f : handler.getClass().getDeclaredFields()) {
            try {
                f.setAccessible(true);
                Object val = f.get(handler);
                if (val instanceof WeakReference) {
                    val = ((WeakReference<?>) val).get();
                }
                if (val != null && android.app.Activity.class.isAssignableFrom(val.getClass())) {
                    return val;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static void clampCounterFields(Object target, int targetVal) {
        if (target == null) return;
        for (Field f : target.getClass().getDeclaredFields()) {
            if (f.getType() == int.class && !Modifier.isStatic(f.getModifiers()) && !Modifier.isFinal(f.getModifiers())) {
                try {
                    f.setAccessible(true);
                    int val = f.getInt(target);
                    // Match countdown counters (typically initialized between 2 and 15 seconds)
                    if (val > 1 && val <= 15) {
                        f.setInt(target, targetVal);
                    }
                } catch (Throwable ignored) {}
            }
        }
    }
}
