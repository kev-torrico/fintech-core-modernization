package com.modularbank.shared.domain;

import java.util.UUID;

public record UserId(UUID value) {

    public static UserId of(UUID value) {
        return new UserId(value);
    }

    public static UserId random() {
        return new UserId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
