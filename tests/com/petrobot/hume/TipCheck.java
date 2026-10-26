package com.petrobot.hume;

/** Self-check for Weather.tip(): run it off-device, no Android runtime needed. */
public class TipCheck {
    static Weather.Hour hour(long inMillis, int temp, int code) {
        Weather.Hour h = new Weather.Hour();
        h.time = System.currentTimeMillis() + inMillis;
        h.temp = temp;
        h.code = code;
        h.day = true;
        return h;
    }

    static void is(String got, String want) {
        if (got == null ? want != null : !got.equals(want)) {
            throw new AssertionError("got [" + got + "] want [" + want + "]");
        }
    }

    public static void main(String[] a) {
        Weather.Data d = new Weather.Data();
        d.temp = 33;
        d.max = 35;
        d.min = 24;

        // nothing to say yet: no hours
        is(Weather.tip(d), null);

        // heat, when the hours are calm
        d.hours.add(hour(3600_000L, 33, 0));
        d.hours.add(hour(7200_000L, 32, 1));
        is(Weather.tip(d), "Dia quente · beba água, máxima de 35°");

        // a real drop wins over the heat
        Weather.Data cool = new Weather.Data();
        cool.temp = 30;
        cool.max = 31;
        cool.min = 21;
        cool.hours.add(hour(3600_000L, 29, 0));
        cool.hours.add(hour(7200_000L, 24, 2));
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(cool.hours.get(1).time);
        is(Weather.tip(cool), "Esfria para 24° às " + c.get(java.util.Calendar.HOUR_OF_DAY) + "h");

        // rain beats everything
        Weather.Data wet = new Weather.Data();
        wet.temp = 34;
        wet.max = 35;
        wet.min = 24;
        wet.hours.add(hour(3600_000L, 33, 0));
        wet.hours.add(hour(7200_000L, 28, 95));
        c.setTimeInMillis(wet.hours.get(1).time);
        is(Weather.tip(wet), "Leva guarda-chuva · trovoadas por volta das "
                + c.get(java.util.Calendar.HOUR_OF_DAY) + "h");

        // snow is not wet weather here
        if (Weather.wet(71)) throw new AssertionError("snow counted as rain");
        if (!Weather.wet(61)) throw new AssertionError("rain not counted");

        System.out.println("tip(): ok");
    }
}
