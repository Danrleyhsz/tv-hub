package com.petrobot.hume;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/**
 * Permissions, asked for on screen wherever the TV allows it; adb is only the last resort.
 * - Location (Wi-Fi name): the normal runtime prompt.
 * - Device admin (Power Off, Sleep Timer): Android's "Activate device admin app?" screen.
 * - TV listings (Continue Watching from apps' Watch Next rows, Android TV 8+): the normal runtime prompt.
 * - Notification access (Do Not Disturb): the system's notification-access screen (Android 11+
 *   opens Hume's own switch directly).
 * Some TVs (e.g. Android 9 builds) have no screen for the last two; only then is the
 * one-time computer command shown.
 */
final class Perms {
    static final int REQ_LOCATION = 41;
    static final int REQ_TV_LISTINGS = 43;

    private Perms() {
    }

    static ComponentName admin(Activity a) {
        return new ComponentName(a, SleepReceiver.Admin.class);
    }

    static ComponentName listener(Activity a) {
        return new ComponentName(a, MediaWatchService.class);
    }

    static boolean hasLocation(Activity a) {
        return a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Some TVs (e.g. this office TV's Android 9 build) lack device admin entirely; nothing can enable it. */
    /** Power Off is possible via device admin, or via Hume's accessibility service (any TV with that screen). */
    static boolean adminSupported(Activity a) {
        return adminWorks(a) || accessibilityScreen(a) != null;
    }

    static Intent accessibilityScreen(Activity a) {
        Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        ComponentName cn = i.resolveActivity(a.getPackageManager());
        return cn == null || cn.getClassName().contains("Stub") ? null : i;
    }

    static boolean hasAdmin(Activity a) {
        return a.getSystemService(DevicePolicyManager.class).isAdminActive(admin(a)) || PowerService.instance != null;
    }

    /** Device admin actually works only when Android's device-policy service is on. */
    static boolean adminWorks(Activity a) {
        return a.getPackageManager().hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN);
    }

    /** Android turns notification listeners off for other apps on low-RAM (1 GB) TVs. */
    static boolean listenerSupported(Activity a) {
        return !a.getSystemService(android.app.ActivityManager.class).isLowRamDevice();
    }

    static boolean hasListener(Activity a) {
        return MediaWatchService.granted(a);
    }

    // ---------------------------------------------------------------- Continue Watching

    static boolean hasTvListings(Activity a) {
        return WatchNext.granted(a);
    }

    static void requestTvListings(Activity a) {
        if (hasTvListings(a)) return;
        if (!WatchNext.supported()) {
            Clicks.dialog(a)
                    .setTitle("Not available on this TV")
                    .setMessage("Continue Watching reads your apps' Watch Next lists, which need Android TV 8 or later.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        android.content.SharedPreferences p = a.getSharedPreferences("perms", Activity.MODE_PRIVATE);
        // Once refused with "Don't ask again", Android answers without showing the prompt.
        if (p.getBoolean("asked_tv_listings", false) && !a.shouldShowRequestPermissionRationale(WatchNext.PERM)) {
            lastResort(a, "Continue Watching",
                    "adb shell pm grant com.petrobot.hume android.permission.READ_TV_LISTINGS");
            return;
        }
        p.edit().putBoolean("asked_tv_listings", true).apply();
        a.requestPermissions(new String[]{WatchNext.PERM}, REQ_TV_LISTINGS);
    }

    /** Hume's accessibility switch is on (Power Off fallback, Clean, Home button, waking into an app). */
    static boolean hasAccessibility(Activity a) {
        return PowerService.instance != null;
    }

    /** Opens the TV's Accessibility settings with a short explanation of what the switch is for. */
    static void requestAccessibility(Activity a, String why) {
        if (hasAccessibility(a)) return;
        Intent acc = accessibilityScreen(a);
        if (acc == null) {
            lastResort(a, "chave do TV Hub", "adb shell settings put secure enabled_accessibility_services "
                    + "com.petrobot.hume/com.petrobot.hume.PowerService\n"
                    + "adb shell settings put secure accessibility_enabled 1");
            return;
        }
        // Switched on but not running: Android stops it when the app is force-stopped and only
        // restarts it on reboot or when the switch is turned off and on again.
        String on = Settings.Secure.getString(a.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean stale = on != null && on.contains(a.getPackageName() + "/");
        Clicks.dialog(a)
                .setTitle(stale ? "Reativar a chave do TV Hub" : "Ligar a chave do TV Hub")
                .setMessage(why + (stale
                        ? "\n\nEla est\u00e1 ligada, mas o Android pausou. Na pr\u00f3xima tela, escolha \u201cTV Hub\u201d, desligue e ligue de novo."
                        : "\n\nNa pr\u00f3xima tela, escolha \u201cTV Hub\u201d e ligue."))
                .setPositiveButton("Abrir acessibilidade", (d, w) -> tryStart(a, acc))
                .setNegativeButton("Cancelar", null).show();
    }

    static void requestLocation(Activity a) {
        if (!hasLocation(a)) a.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
    }

    static void requestAdmin(Activity a) {
        if (hasAdmin(a)) return;
        if (!adminSupported(a)) {
            Clicks.dialog(a)
                    .setTitle("Não existe nesta TV")
                    .setMessage("Esta TV não deixa um app apagar a tela, então Desligar e o timer de sono não funcionam "
                            + "aqui. Use o botão de liga/desliga do controle.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        if (!adminWorks(a)) {
            // Device admin is switched off on this TV: use Hume's accessibility switch instead.
            Intent acc = accessibilityScreen(a);
            Clicks.dialog(a)
                    .setTitle("Ligar o Desligar")
                    .setMessage("Na próxima tela, escolha “TV Hub” e ligue. "
                            + "Isso deixa o launcher suspender a TV (e cuidar do botão Início e do iniciar junto com a TV).")
                    .setPositiveButton("Abrir acessibilidade", (d, w) -> tryStart(a, acc))
                    .setNegativeButton("Cancelar", null).show();
            return;
        }
        Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(a))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Deixa o launcher apagar a tela no Desligar e no timer de sono. Mais nada.");
        if (tryStart(a, i)) return;
        lastResort(a, "Desligar e timer de sono",
                "adb shell dpm set-active-admin com.petrobot.hume/.SleepReceiver\\$Admin");
    }

    static void requestListener(Activity a) {
        if (hasListener(a)) return;
        if (!listenerSupported(a)) {
            Clicks.dialog(a)
                    .setTitle("Não existe nesta TV")
                    .setMessage("Esta TV tem pouca memória, então o Android não dá acesso às notificações. "
                            + "Não perturbe não funciona aqui.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            Intent detail = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener(a).flattenToString());
            if (tryStart(a, detail)) return;
        }
        if (tryStart(a, new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))) return;
        if (tryStart(a, new Intent("android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS"))) return;
        lastResort(a, "Não perturbe",
                "adb shell cmd notification allow_listener com.petrobot.hume/com.petrobot.hume.MediaWatchService");
    }

    /** Starts a system screen if the TV really has it (many TVs map settings actions to empty stubs). */
    private static boolean tryStart(Activity a, Intent i) {
        ComponentName cn = i.resolveActivity(a.getPackageManager());
        if (cn == null || cn.getClassName().contains("Stub")) return false;
        try {
            a.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- default home screen

    static boolean isDefaultHome(Activity a) {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        android.content.pm.ResolveInfo ri = a.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null && a.getPackageName().equals(ri.activityInfo.packageName);
    }

    /**
     * Become the home screen: Android 10+'s "Definir como tela inicial padrão?" prompt, else the TV's Home
     * app settings (when it is a real screen), else Android's chooser, and only then the command.
     */
    static void requestDefaultHome(Activity a) {
        if (isDefaultHome(a)) return;
        if (Build.VERSION.SDK_INT >= 29) {
            android.app.role.RoleManager rm = a.getSystemService(android.app.role.RoleManager.class);
            if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                try {
                    a.startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), 42);
                    return;
                } catch (Exception ignored) {
                    // fall through
                }
            }
        }
        if (tryStart(a, new Intent(Settings.ACTION_HOME_SETTINGS))) return;
        // With no default chosen yet, asking for Home shows Android's chooser ("Always" makes it stick).
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        android.content.pm.ResolveInfo def = a.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        boolean noDefault = def == null || def.activityInfo == null || "android".equals(def.activityInfo.packageName);
        if (noDefault && tryStart(a, home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return;
        // TVs que mantêm a própria tela inicial (Google TV): o launcher assume o botão Início.
        if (accessibilityScreen(a) != null || hasAccessibility(a)) {
            PowerService.HomeButton.setEnabled(a, true);
            requestAccessibility(a, "Esta TV não deixa escolher outro app de tela inicial. Com a chave do TV Hub "
                    + "ligada, ele abre sempre que a tela inicial de fábrica aparece, então o botão Início traz você para cá.");
            return;
        }
        lastResort(a, "Tela inicial",
                "adb shell cmd package set-home-activity com.petrobot.hume/.MainActivity\n"
                        + "(on Google TV also: adb shell pm disable-user --user 0 com.google.android.tvlauncher)");
    }

    /** Only when the TV has no screen for it: explain plainly, with the command for whoever installs the TV. */
    private static void lastResort(Activity a, String what, String cmd) {
        Clicks.dialog(a)
                .setTitle(what)
                .setMessage("Os ajustes desta TV não têm essa permissão. Quem configurou a TV pode ligar "
                        + "uma vez, de um computador na mesma rede:\n\n" + cmd)
                .setPositiveButton("OK", null)
                .setNeutralButton("Sobre o app", (d, w) -> tryStart(a, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + a.getPackageName()))))
                .show();
    }
}
