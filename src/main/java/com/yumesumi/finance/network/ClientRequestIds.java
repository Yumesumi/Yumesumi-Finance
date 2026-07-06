package com.yumesumi.finance.network;

public final class ClientRequestIds {
    private static long counter = System.nanoTime() & Long.MAX_VALUE;

    private ClientRequestIds() {
    }

    public static synchronized long next() {
        counter++;
        if (counter <= 0) {
            counter = 1;
        }
        return counter;
    }
}
