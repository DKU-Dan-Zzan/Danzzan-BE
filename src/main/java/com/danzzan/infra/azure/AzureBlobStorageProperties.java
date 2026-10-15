package com.danzzan.infra.azure;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;

/** Immutable, keyless configuration for the private Azure Blob image container. */
public record AzureBlobStorageProperties(
        URI endpoint,
        String container,
        Credential credential,
        Duration readSasDuration
) {
    public enum Credential {
        AZURE_CLI,
        MANAGED_IDENTITY;

        public static Credential from(String value) {
            if (value == null) return MANAGED_IDENTITY;
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "azure-cli" -> AZURE_CLI;
                case "managed-identity" -> MANAGED_IDENTITY;
                default -> throw new IllegalArgumentException(
                        "azure.storage.credential must be azure-cli or managed-identity");
            };
        }
    }

    public AzureBlobStorageProperties {
        if (endpoint == null || !"https".equalsIgnoreCase(endpoint.getScheme())
                || endpoint.getHost() == null || endpoint.getRawQuery() != null
                || endpoint.getRawFragment() != null) {
            throw new IllegalArgumentException("azure.storage.endpoint must be an HTTPS origin");
        }
        if (container == null || !container.matches("[a-z0-9](?:[a-z0-9-]{1,61}[a-z0-9])?")) {
            throw new IllegalArgumentException("azure.storage.container must be a valid Blob container name");
        }
        if (credential == null) credential = Credential.MANAGED_IDENTITY;
        readSasDuration = validateDuration(readSasDuration, "read-sas-expiration-seconds");
    }

    private static Duration validateDuration(Duration duration, String property) {
        if (duration == null || duration.isZero() || duration.isNegative() || duration.compareTo(Duration.ofMinutes(30)) > 0) {
            throw new IllegalArgumentException("azure.storage." + property + " must be between 1 second and 30 minutes");
        }
        return duration;
    }
}
