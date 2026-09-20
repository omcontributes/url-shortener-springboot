package com.example.urlshortener.dto;

import jakarta.validation.constraints.*;

public record ShortenRequest(
        @NotBlank(message = "url is required")
        @Size(max = 2048, message = "url is too long")
        String url,

        @Pattern(regexp = "^[a-zA-Z0-9_-]{4,20}$", message = "alias must be 4-20 chars: letters, digits, _ or -")
        String customAlias,

        @Positive(message = "expiryDays must be positive")
        Integer expiryDays
) {}