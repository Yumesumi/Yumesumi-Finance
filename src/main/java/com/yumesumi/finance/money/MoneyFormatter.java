package com.yumesumi.finance.money;

public final class MoneyFormatter {
    private MoneyFormatter() {
    }

    public static String format(long cents) {
        long yuan = cents / 100;
        long remain = Math.abs(cents % 100);
        return yuan + "." + (remain < 10 ? "0" : "") + remain + " 梦澄元";
    }

    public static long parseCents(String raw) {
        String text = raw.trim();
        if (text.isEmpty()) {
            throw new NumberFormatException("金额为空");
        }

        int dot = text.indexOf('.');
        if (dot < 0) {
            return Math.multiplyExact(Long.parseLong(text), 100L);
        }

        String yuan = text.substring(0, dot);
        String cents = text.substring(dot + 1);
        if (cents.length() > 2) {
            cents = cents.substring(0, 2);
        }
        while (cents.length() < 2) {
            cents += "0";
        }
        return Math.addExact(Math.multiplyExact(Long.parseLong(yuan), 100L), Long.parseLong(cents));
    }
}
