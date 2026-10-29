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
        horas();
        System.out.println("ThemesCheck ok");
    }

    /**
     * As faixas por hora. Fora do aparelho o BitmapFactory é um stub, então todo brilho cai no
     * valor de reserva e a ordem fica a de entrada — o que se checa aqui é a conta das fatias.
     */
    static void horas() {
        java.util.List<java.io.File> poucas = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) poucas.add(new java.io.File("f" + i + ".jpg"));
        if (Themes.forHour(poucas, 3).size() != 4) throw new AssertionError("tema pequeno usa tudo");

        java.util.List<java.io.File> muitas = new java.util.ArrayList<>();
        for (int i = 0; i < 14; i++) muitas.add(new java.io.File("f" + i + ".jpg"));
        java.util.List<java.io.File> noite = Themes.forHour(muitas, 23);
        java.util.List<java.io.File> dia = Themes.forHour(muitas, 12);
        java.util.List<java.io.File> tarde = Themes.forHour(muitas, 17);
        for (java.util.List<java.io.File> f : java.util.Arrays.asList(noite, dia, tarde)) {
            if (f.isEmpty() || f.size() > muitas.size()) throw new AssertionError("faixa fora do tamanho");
        }
        if (!java.util.Collections.disjoint(noite, dia)) throw new AssertionError("noite e meio-dia se cruzam");
        if (Themes.forHour(muitas, 3).size() != noite.size()) throw new AssertionError("madrugada é noite");
    }
}
