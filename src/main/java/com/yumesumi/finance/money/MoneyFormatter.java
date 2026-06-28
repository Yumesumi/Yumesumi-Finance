package com.yumesumi.finance.money;

import java.math.BigInteger;
import net.minecraft.network.chat.Component;

public final class MoneyFormatter {
    private MoneyFormatter() {
    }

    public static String format(long cents) {
        return formatComponent(cents).getString();
    }

    public static String format(BigInteger cents) {
        return formatComponent(cents).getString();
    }

    public static Component formatComponent(long cents) {
        return formatComponent(BigInteger.valueOf(cents));
    }

    public static Component formatComponent(BigInteger cents) {
        return Component.translatable("format.yumesumi_finance.money", decimal(cents));
    }

    public static String decimal(long cents) {
        return decimal(BigInteger.valueOf(cents));
    }

    public static String decimal(BigInteger cents) {
        boolean negative = cents.signum() < 0;
        BigInteger abs = cents.abs();
        BigInteger[] parts = abs.divideAndRemainder(BigInteger.valueOf(100L));
        String remain = parts[1].intValue() < 10 ? "0" + parts[1] : parts[1].toString();
        return (negative ? "-" : "") + parts[0] + "." + remain;
    }

    public static String formatLegacy(long cents) {
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
