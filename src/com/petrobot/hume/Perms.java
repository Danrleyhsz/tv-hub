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
 * - Notification access (Continue Watching, Do Not Disturb): the system's notification-access
 *   screen (Android 11+ opens Hume's own switch directly).
 * Some TVs (e.g. Android 9 builds) have no screen for the last two; only then is the
 * one-time computer command shown.
 */
final class Perms {
    static final int REQ_LOCATION = 41;

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

    static void requestLocation(Activity a) {
        if (!hasLocation(a)) a.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
    }

    /** Opens the TV's accessibility list so the user can switch the launcher's service on. */
    static void requestAccessibility(Activity a, String why) {
        Intent acc = accessibilityScreen(a);
        if (acc == null) {
            new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Não existe nesta TV")
                    .setMessage("Esta TV não tem a tela de acessibilidade, então não dá para ligar o serviço.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Ligar o serviço")
                .setMessage(why + "\n\nNa próxima tela, escolha \u201cTV Hub \u2013 Desligar\u201d e ligue.")
                .setPositiveButton("Abrir acessibilidade", (d, w) -> tryStart(a, acc))
                .setNegativeButton("Cancelar", null).show();
    }

    static void requestAdmin(Activity a) {
        if (hasAdmin(a)) return;
        if (!adminSupported(a)) {
            new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Não existe nesta TV")
                    .setMessage("Esta TV não deixa um app apagar a tela, então Desligar e o timer de sono não funcionam "
                            + "aqui. Use o botão de liga/desliga do controle.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        if (!adminWorks(a)) {
            // Device admin is switched off on this TV: use Hume's accessibility switch instead.
            Intent acc = accessibilityScreen(a);
            new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Ligar o Desligar")
                    .setMessage("Na próxima tela, escolha \u201cHume Launcher \u2013 Power Off\u201d e ligue. "
                            + "Isso só deixa o launcher suspender a TV.")
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
            new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Não existe nesta TV")
                    .setMessage("Esta TV tem pouca memória, então o Android não deixa um app ler o que está tocando. "
                            + "Continuar assistindo e Não perturbe não funcionam aqui.")
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
        lastResort(a, "Continuar assistindo e Não perturbe",
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
        lastResort(a, "Tela inicial",
                "adb shell cmd package set-home-activity com.petrobot.hume/.MainActivity\n"
                        + "(on Google TV also: adb shell pm disable-user --user 0 com.google.android.tvlauncher)");
    }

    /** Only when the TV has no screen for it: explain plainly, with the command for whoever installs the TV. */
    private static void lastResort(Activity a, String what, String cmd) {
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(what)
                .setMessage("Os ajustes desta TV não têm essa permissão. Quem configurou a TV pode ligar "
                        + "uma vez, de um computador na mesma rede:\n\n" + cmd)
                .setPositiveButton("OK", null)
                .setNeutralButton("Sobre o app", (d, w) -> tryStart(a, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + a.getPackageName()))))
                .show();
    }
}
