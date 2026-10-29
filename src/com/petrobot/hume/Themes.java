package com.petrobot.hume;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Theme packs: one folder of pictures per theme, in the app's own Pictures folder (pushed from the
 * PC with tools/baixar-pacotes.py, no storage permission involved):
 *   Pictures/fundo/<tema>/     background of the Home Screen ("Fotos")
 *   Pictures/protetor/<tema>/  screensaver
 * Loose pictures straight in Pictures/ (the first installs) still work as the background.
 */
final class Themes {
    static final String FUNDO = "fundo", PROTETOR = "protetor";
    /** saver_theme value meaning "the screensaver shows the background's pictures". */
    static final String SAME = "";

    private Themes() {}

    static File root(Context c) {
        return c.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
    }

    static List<File> images(File dir) {
        File[] fs = dir == null ? null : dir.listFiles();
        List<File> out = new ArrayList<>();
        if (fs == null) return out;
        for (File f : fs) {
            String n = f.getName().toLowerCase(Locale.US);
            if (f.isFile() && (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png"))) out.add(f);
        }
        Collections.sort(out);
        return out;
    }

    /** Theme folders of one kind that hold at least one picture, in alphabetical order. */
    static List<String> list(Context c, String kind) {
        File root = root(c);
        File[] ds = root == null ? null : new File(root, kind).listFiles();
        List<String> out = new ArrayList<>();
        if (ds != null) for (File d : ds) if (d.isDirectory() && !images(d).isEmpty()) out.add(d.getName());
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    static String key(String kind) {
        return FUNDO.equals(kind) ? "wall_theme" : "saver_theme";
    }

    /** The theme in use: the saved one while its folder is there, else the first; null when there is none. */
    static String current(Context c, String kind) {
        List<String> ts = list(c, kind);
        if (ts.isEmpty()) return null;
        String saved = Profiles.homePrefs(c).getString(key(kind), null);
        return ts.contains(saved) ? saved : ts.get(0);
    }

    static void choose(Context c, String kind, String theme) {
        Profiles.homePrefs(c).edit().putString(key(kind), theme).apply();
    }

    /** The screensaver follows the background when asked to, or when there is no screensaver pack. */
    static boolean saverSameAsBackground(Context c) {
        return SAME.equals(Profiles.homePrefs(c).getString(key(PROTETOR), null)) || current(c, PROTETOR) == null;
    }

    /**
     * Brilho médio de uma foto, 0 a 255, lido de uma miniatura. Serve só para separar foto de dia
     * de foto de noite, então um palpite grosseiro basta; fica guardado por arquivo.
     */
    private static final java.util.Map<String, Integer> BRILHO = new java.util.HashMap<>();

    static int brightness(File f) {
        String chave = f.getPath() + ":" + f.lastModified();
        Integer pronto = BRILHO.get(chave);
        if (pronto != null) return pronto;
        int media = 128;
        try {
            android.graphics.BitmapFactory.Options probe = new android.graphics.BitmapFactory.Options();
            probe.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(f.getPath(), probe);
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inSampleSize = Math.max(1, probe.outWidth / 24);
            Bitmap b = android.graphics.BitmapFactory.decodeFile(f.getPath(), o);
            if (b != null) {
                long soma = 0;
                int n = 0;
                for (int y = 0; y < b.getHeight(); y++) {
                    for (int x = 0; x < b.getWidth(); x++) {
                        int c = b.getPixel(x, y);
                        soma += ((c >> 16 & 0xFF) * 299 + (c >> 8 & 0xFF) * 587 + (c & 0xFF) * 114) / 1000;
                        n++;
                    }
                }
                if (n > 0) media = (int) (soma / n);
                b.recycle();
            }
        } catch (Throwable t) {
            // foto ilegível: fica no meio e entra em qualquer horário
        }
        BRILHO.put(chave, media);
        return media;
    }

    /**
     * As fotos que combinam com a hora: as mais claras no meio do dia, as mais escuras à noite,
     * as do meio no comecinho e no fim da tarde. Com poucas fotos no tema, usa todas.
     */
    static List<File> forHour(List<File> all, int hora) {
        if (all.size() < 6) return all;
        List<File> ordenadas = new ArrayList<>(all);
        Collections.sort(ordenadas, (a, b) -> brightness(a) - brightness(b));  // escuras primeiro
        int terco = Math.max(2, ordenadas.size() / 3);
        boolean noite = hora < 6 || hora >= 19;
        boolean meioDia = hora >= 10 && hora < 16;
        if (noite) return ordenadas.subList(0, terco);
        if (meioDia) return ordenadas.subList(ordenadas.size() - terco, ordenadas.size());
        int meio = ordenadas.size() / 2;
        return ordenadas.subList(Math.max(0, meio - terco / 2), Math.min(ordenadas.size(), meio + terco - terco / 2));
    }

    static List<File> background(Context c) {
        File root = root(c);
        String t = current(c, FUNDO);
        return images(t == null ? root : new File(new File(root, FUNDO), t));
    }

    static List<File> saver(Context c) {
        if (saverSameAsBackground(c)) return background(c);
        return images(new File(new File(root(c), PROTETOR), current(c, PROTETOR)));
    }

}
