package dev.hengen.uiskipper;

public final class Mode {
    public static final String KEY = "hyperos_uiskipper_mode";
    /** Countdown removed; the user still ticks the checkbox and presses OK. */
    public static final int SKIP_WAIT = 1;
    /** Countdown, checkbox and OK press are all skipped on the permission "Danger" screen. */
    public static final int FULL_AUTO = 2;

    private Mode() {}
}
