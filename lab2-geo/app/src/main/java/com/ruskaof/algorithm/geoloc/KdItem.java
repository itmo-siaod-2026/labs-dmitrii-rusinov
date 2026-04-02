package com.ruskaof.algorithm.geoloc;

import java.util.Objects;

public final class KdItem<T> {

    private final double x;
    private final double y;
    private final T payload;

    public KdItem(double x, double y, T payload) {
        if (Double.isNaN(x) || Double.isNaN(y)) {
            throw new IllegalArgumentException("Coordinates must not be NaN");
        }
        this.x = x;
        this.y = y;
        this.payload = Objects.requireNonNull(payload, "payload must not be null");
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public T payload() {
        return payload;
    }
}
