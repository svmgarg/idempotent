package com.idempotent.service;

public interface ApiKeyProvider {
    boolean isValid(String apiKey);
}
