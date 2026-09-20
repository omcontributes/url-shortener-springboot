package com.example.urlshortener.service;

import com.example.urlshortener.dto.ShortenRequest;
import com.example.urlshortener.dto.ShortenResponse;
import com.example.urlshortener.dto.StatsResponse;
import com.example.urlshortener.exception.BadRequestException;
import com.example.urlshortener.exception.ConflictException;
import com.example.urlshortener.exception.NotFoundException;
import com.example.urlshortener.model.UrlMapping;
import com.example.urlshortener.repository.UrlMappingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class UrlShortenerService {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 7;
    private static final int MAX_ATTEMPTS = 10;

    private final SecureRandom random = new SecureRandom();
    private final UrlMappingRepository repository;
    private final String baseUrl;

    public UrlShortenerService(UrlMappingRepository repository,
                               @Value("${app.base-url}") String baseUrl) {
        this.repository = repository;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Transactional
    public ShortenResponse shorten(ShortenRequest request) {
        String url = request.url().trim();
        validateUrl(url);

        String code;
        if (request.customAlias() != null && !request.customAlias().isBlank()) {
            code = request.customAlias();
            if (repository.existsByShortCode(code)) {
                throw new ConflictException("Alias '" + code + "' is already taken");
            }
        } else {
            code = generateUniqueCode();
        }

        Instant expiresAt = request.expiryDays() == null
                ? null
                : Instant.now().plus(request.expiryDays(), ChronoUnit.DAYS);

        UrlMapping saved = repository.save(new UrlMapping(code, url, expiresAt));
        return new ShortenResponse(saved.getShortCode(), baseUrl + "/" + saved.getShortCode(),
                saved.getOriginalUrl(), saved.getExpiresAt());
    }

    @Transactional
    public String resolveAndTrack(String code) {
        UrlMapping mapping = repository.findByShortCode(code)
                .orElseThrow(() -> new NotFoundException("Short URL not found"));
        if (mapping.isExpired()) {
            throw new NotFoundException("Short URL has expired");
        }
        repository.incrementClicks(code);
        return mapping.getOriginalUrl();
    }

    @Transactional(readOnly = true)
    public StatsResponse stats(String code) {
        UrlMapping m = repository.findByShortCode(code)
                .orElseThrow(() -> new NotFoundException("Short URL not found"));
        return new StatsResponse(m.getShortCode(), m.getOriginalUrl(), m.getClickCount(),
                m.getCreatedAt(), m.getExpiresAt());
    }

    private String generateUniqueCode() {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int j = 0; j < CODE_LENGTH; j++) {
                sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            String code = sb.toString();
            if (!repository.existsByShortCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique short code");
    }

    private void validateUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid URL format");
        }
        String scheme = uri.getScheme();
        boolean httpScheme = scheme != null
                && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
        if (!httpScheme || uri.getHost() == null) {
            throw new BadRequestException("URL must start with http:// or https:// and have a valid host");
        }
    }
}