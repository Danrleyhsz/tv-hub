package com.petrobot.hume;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The phone remote: what the web page on the phone can press on the TV.
 *
 * <p>Three kinds of button, because Android allows three different things to an app like this:
 * <ul>
 *   <li>volume and opening an app work always;</li>
 *   <li>Home works always too: the launcher is the home app, so the button is the home intent;</li>
 *   <li>Back and Recents work in any app, through the accessibility service the user turns
 *       on once (the same one the Power Off button already uses);</li>
 *   <li>the arrows and OK only reach the launcher's own screen. Android only lets an accessibility
 *       service press a D-pad key from Android 13 ({@code GLOBAL_ACTION_DPAD_*}, API 33) and this
 *       TV is on Android 12, so inside Netflix and friends the arrows are the TV's remote job.</li>
 * </ul>
 */
public final class Remote {
    private Remote() {
    }

    static final String PREFS = "remote", K_ENABLED = "enabled";

    public static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context c) {
        return prefs(c).getBoolean(K_ENABLED, false);
    }

    public static void setEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean(K_ENABLED, on).apply();
        if (on) com.petrobot.hume.present.Presenter.ensureRunning(c);
    }

    /** Home, Back and Recents need the accessibility service; everything else does not. */
    public static boolean serviceOn() {
        return PowerService.instance != null;
    }

    /** The arrows only move the launcher's own focus, so they need it on screen. */
    public static boolean launcherOnScreen() {
        MainActivity a = MainActivity.instance;
        return a != null && a.resumed;
    }

    private static final Map<String, Integer> DPAD = new LinkedHashMap<>();

    static {
        DPAD.put("up", KeyEvent.KEYCODE_DPAD_UP);
        DPAD.put("down", KeyEvent.KEYCODE_DPAD_DOWN);
        DPAD.put("left", KeyEvent.KEYCODE_DPAD_LEFT);
        DPAD.put("right", KeyEvent.KEYCODE_DPAD_RIGHT);
        DPAD.put("ok", KeyEvent.KEYCODE_DPAD_CENTER);
    }

    /**
     * Presses one button. Returns null when it worked, or a short reason in Portuguese when the TV
     * cannot do it right now, which the phone shows.
     */
    public static String press(Context c, String key) {
        if (key == null) return "sem tecla";
        switch (key) {
            case "volup":
                return volume(c, AudioManager.ADJUST_RAISE);
            case "voldown":
                return volume(c, AudioManager.ADJUST_LOWER);
            case "mute":
                return volume(c, AudioManager.ADJUST_TOGGLE_MUTE);
            case "home":
                return home(c);
            case "back":
                return global(AccessibilityService.GLOBAL_ACTION_BACK);
            case "recents":
                return global(AccessibilityService.GLOBAL_ACTION_RECENTS);
            default:
                Integer code = DPAD.get(key);
                if (code == null) return "tecla desconhecida";
                return dpad(code);
        }
    }

    private static String volume(Context c, int direction) {
        AudioManager am = c.getSystemService(AudioManager.class);
        if (am == null) return "sem controle de volume";
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
        return null;
    }

    /**
     * The launcher is the home app, so the Home button is just the home intent: no accessibility
     * service involved (performGlobalAction(GLOBAL_ACTION_HOME) does nothing on this TV).
     */
    private static String home(Context c) {
        try {
            c.startActivity(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return null;
        } catch (Exception e) {
            return global(AccessibilityService.GLOBAL_ACTION_HOME);
        }
    }

    private static String global(int action) {
        PowerService s = PowerService.instance;
        if (s == null) return "ligue o serviço de acessibilidade do TV Hub";
        return s.performGlobalAction(action) ? null : "a TV recusou";
    }

    /** Sends the key into the launcher's own window; it goes nowhere else. */
    private static String dpad(int code) {
        MainActivity a = MainActivity.instance;
        if (a == null || !a.resumed) return "as setas só mexem na tela do TV Hub";
        a.runOnUiThread(() -> {
            long t = SystemClock.uptimeMillis();
            boolean taken = a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
            a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
            // Moving the focus on an unhandled arrow is ViewRootImpl's job, and it only sees keys
            // that come from the system. Dispatching by hand skips it, so do that part here.
            if (!taken) moveFocus(a, code);
        });
        return null;
    }

    private static void moveFocus(MainActivity a, int code) {
        int dir;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: dir = android.view.View.FOCUS_UP; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: dir = android.view.View.FOCUS_DOWN; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: dir = android.view.View.FOCUS_LEFT; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: dir = android.view.View.FOCUS_RIGHT; break;
            default: return;  // OK is handled by the focused view itself
        }
        android.view.View cur = a.getCurrentFocus();
        if (cur == null) {
            a.getWindow().getDecorView().requestFocus();
            return;
        }
        android.view.View next = cur.focusSearch(dir);
        if (next != null) next.requestFocus(dir);
    }

    /** Opens an app by package name. */
    public static String launch(Context c, String pkg) {
        if (pkg == null || pkg.isEmpty()) return "sem app";
        Intent i = leanback(c, pkg);
        if (i == null) i = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return "app não encontrado";
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return null;
        } catch (Exception e) {
            return "não deu pra abrir";
        }
    }

    private static Intent leanback(Context c, String pkg) {
        try {
            return c.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    /** The apps the phone can open, in the same order the dock and grid show them. */
    public static JSONArray apps(Context c) throws Exception {
        JSONArray out = new JSONArray();
        MainActivity a = MainActivity.instance;
        List<String[]> list = new ArrayList<>();
        if (a != null && !a.apps.isEmpty()) {
            for (MainActivity.AppEntry e : a.apps) {
                if (e.pkg != null && !e.pkg.equals(c.getPackageName())) list.add(new String[]{e.pkg, e.label});
            }
        } else {
            // The launcher may not be on screen yet: ask the package manager instead.
            PackageManager pm = c.getPackageManager();
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            for (ResolveInfo r : pm.queryIntentActivities(q, 0)) {
                String pkg = r.activityInfo.packageName;
                if (pkg.equals(c.getPackageName())) continue;
                list.add(new String[]{pkg, r.loadLabel(pm).toString()});
            }
            Collections.sort(list, (x, y) -> x[1].compareToIgnoreCase(y[1]));
        }
        for (String[] e : list) out.put(new JSONObject().put("pkg", e[0]).put("label", e[1]));
        return out;
    }

    /** What the phone needs to know to grey the right buttons out. */
    public static JSONObject state(Context c) throws Exception {
        return new JSONObject()
                .put("service", serviceOn())
                .put("launcher", launcherOnScreen())
                .put("dpadEverywhere", android.os.Build.VERSION.SDK_INT >= 33);
    }
}
