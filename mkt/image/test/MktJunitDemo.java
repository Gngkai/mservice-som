package mkt.image.test;

import common.fnd.FndMsg;

public class MktJunitDemo {
    public static Object demo(Object obj) {
        return obj;
    }

    private long n = 0;

    public long add(long x) {
        n = n + x;
        return n;
    }

    public long sub(long x) {
        n = n - x;
        return n;
    }
}
