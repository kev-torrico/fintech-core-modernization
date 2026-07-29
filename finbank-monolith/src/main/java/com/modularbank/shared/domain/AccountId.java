package com.modularbank.shared.domain;

import java.util.UUID;

public record AccountId(UUID value) {

    public static AccountId of(UUID value) {
        return new AccountId(value);
    }

    public static AccountId random() {
        return new AccountId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
