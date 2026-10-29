package com.petrobot.hume;

/** Off-device check of the screensaver label read from the file name. Run by check.sh. */
public class ThemesCheck {
    static void eq(String[] got, String title, String artist) {
        String want = title == null ? "null" : title + " | " + artist;
        String was = got == null ? "null" : got[0] + " | " + got[1];
        if (!want.equals(was)) throw new AssertionError("expected " + want + ", got " + was);
    }

    public static void main(String[] a) {
        eq(Themes.caption("Claude Monet - Water Lilies (1906).jpg"), "Water Lilies (1906)", "Claude Monet");
        eq(Themes.caption("Utagawa Hiroshige - Mishima - Morning Mist (c. 1833).jpg"), "Mishima - Morning Mist (c. 1833)", "Utagawa Hiroshige");
        eq(Themes.caption("paisagem-1015.jpg"), null, null);
        eq(Themes.caption("Sem título - .jpg"), null, null);
        System.out.println("ThemesCheck ok");
    }
}
