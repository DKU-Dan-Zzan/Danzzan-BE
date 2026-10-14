package com.danzzan.infra.azure;

import com.azure.core.exception.AzureException;
import com.azure.storage.common.sas.SasProtocol;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.azure.storage.blob.specialized.BlobInputStream;
import com.azure.storage.blob.models.UserDelegationKey;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Private Blob operations used by the Azure image-storage adapters.
 *
 * <p>Only {@code azure:container/key} references are persisted. Browser URLs always receive a
 * short-lived user-delegation SAS, and account keys are never used.</p>
 */
public class AzureBlobStorage {
    private static final int MAX_POSTER_BYTES = 5 * 1024 * 1024;
    private static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;
    private static final Duration USER_DELEGATION_KEY_LIFETIME = Duration.ofHours(1);
    private static final Duration USER_DELEGATION_KEY_START_SKEW = Duration.ofMinutes(5);
    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp");
    private static final Pattern ENCODED_TRAVERSAL = Pattern.compile("(?i)%(?:2e|2f|5c|25(?:2e|2f|5c))");

    private final BlobServiceClient serviceClient;
    private final BlobContainerClient containerClient;
    private final AzureBlobStorageProperties properties;
    private final Clock clock;
    private final URI endpoint;
    private final String referencePrefix;
    private final Object delegationKeyLock = new Object();
    private volatile CachedDelegationKey cachedDelegationKey;

    AzureBlobStorage(BlobServiceClient serviceClient, AzureBlobStorageProperties properties, Clock clock) {
        this.serviceClient = serviceClient;
        this.properties = properties;
        this.clock = clock;
        this.endpoint = normalizeEndpoint(properties.endpoint());
        this.referencePrefix = "azure:" + properties.container() + "/";
        this.containerClient = serviceClient.getBlobContainerClient(properties.container());
    }

    public String reference(String key) {
        return referencePrefix + validateKey(key);
    }

    /** Converts only our Blob URLs/references; unrelated legacy URLs remain unchanged. */
    public String normalizeReference(String value) {
        if (value == null || value.isBlank()) return value;
        if (value.startsWith("azure:")) return canonicalReference(value.substring("azure:".length()));

        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw invalidReference();
        }
        if (!uri.isAbsolute()) return value;
        if (isConfiguredOrigin(uri)) return reference(keyFromConfiguredUrl(uri));
        if (isAzureBlobOrigin(uri) || hasSasQuery(uri)) throw invalidReference();
        return value;
    }

    /** Returns a five-minute-by-default HTTPS-only read SAS, or preserves a legacy URL. */
    public String readUrl(String reference) {
        String normalized = normalizeReference(reference);
        if (normalized == null || normalized.isBlank() || !normalized.startsWith(referencePrefix)) return normalized;
        BlobClient blobClient = blobClient(keyFromReference(normalized));
        return blobClient.getBlobUrl() + "?" + blobClient.generateUserDelegationSas(
                readSasValues(), userDelegationKeyForSas());
    }

    /** Uploads an owned key once. Existing blobs are never overwritten. */
    public String upload(String key, MultipartFile file) {
        String canonicalKey = validateKey(key);
        String contentType = validatedContentType(file == null ? null : file.getContentType());
        if (file == null || file.isEmpty() || file.getSize() > MAX_UPLOAD_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a non-empty image file up to 10 MB");
        }
        try (InputStream input = file.getInputStream()) {
            BlobParallelUploadOptions options = new BlobParallelUploadOptions(input)
                    .setHeaders(new BlobHttpHeaders().setContentType(contentType))
                    .setRequestConditions(new BlobRequestConditions().setIfNoneMatch("*"));
            blobClient(canonicalKey).uploadWithResponse(options, null, null);
            return reference(canonicalKey);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to read the image upload", exception);
        } catch (AzureException exception) {
            throw storageUnavailable("Unable to upload the image", exception);
        }
    }

    /** Downloads only a current festival poster and enforces the existing five-megabyte limit. */
    public byte[] readPoster(Long festivalId, String reference) {
        String key = posterKey(festivalId, reference);
        try {
            BlobClient blobClient = blobClient(key);
            Long length = blobClient.getProperties().getBlobSize();
            if (length != null && length > MAX_POSTER_BYTES) throw invalidPoster();
            try (BlobInputStream input = blobClient.openInputStream()) {
                byte[] bytes = input.readNBytes(MAX_POSTER_BYTES + 1);
                if (bytes.length == 0 || bytes.length > MAX_POSTER_BYTES) throw invalidPoster();
                return bytes;
            }
        } catch (IOException exception) {
            throw storageUnavailable("Unable to read the saved poster image", exception);
        } catch (AzureException exception) {
            throw storageUnavailable("Unable to read the saved poster image", exception);
        }
    }

    /** Deletes only a canonical reference from this configured private container. */
    public boolean delete(String reference) {
        String normalized = normalizeReference(reference);
        if (normalized == null || !normalized.startsWith(referencePrefix)) throw invalidReference();
        try {
            return blobClient(keyFromReference(normalized)).deleteIfExists();
        } catch (AzureException exception) {
            throw storageUnavailable("Unable to delete the image", exception);
        }
    }

    /**
     * Confirms that a staged ticketing background is an existing object created in this storage
     * namespace.  Accepting the current SAS URL here is safe because it is normalized before any
     * caller persists it.
     */
    public void validateTicketingBackground(String reference) {
        validateStoredImage("festival/ticketing-background/", reference, MAX_UPLOAD_BYTES);
    }

    /** Confirms that a staged home banner belongs to the requested festival. */
    public void validateHomeImage(Long festivalId, String reference) {
        if (festivalId == null || festivalId <= 0) throw invalidBackground();
        validateStoredImage("festival/" + festivalId + "/home/", reference, MAX_POSTER_BYTES);
    }

    private void validateStoredImage(String prefix, String reference, long maxBytes) {
        String key;
        try {
            String normalized = normalizeReference(reference);
            if (normalized == null || !normalized.startsWith(referencePrefix)) throw invalidBackground();
            key = keyFromReference(normalized);
            String suffix = key.startsWith(prefix) ? key.substring(prefix.length()) : null;
            if (suffix == null || !isGeneratedImageSuffix(suffix)) throw invalidBackground();
        } catch (IllegalArgumentException | ResponseStatusException exception) {
            throw invalidBackground();
        }

        try {
            BlobProperties properties = blobClient(key).getProperties();
            Long size = properties.getBlobSize();
            if (size == null || size <= 0 || size > maxBytes
                    || !isAllowedImageContentType(properties.getContentType())) {
                throw invalidBackground();
            }
        } catch (BlobStorageException exception) {
            if (exception.getStatusCode() == 404) throw invalidBackground();
            throw storageUnavailable("Unable to verify the uploaded image", exception);
        } catch (AzureException exception) {
            throw storageUnavailable("Unable to verify the uploaded image", exception);
        }
    }

    private BlobClient blobClient(String key) {
        return containerClient.getBlobClient(key);
    }

    private String canonicalReference(String path) {
        int separator = path.indexOf('/');
        if (separator <= 0 || !properties.container().equals(path.substring(0, separator))) throw invalidReference();
        return reference(path.substring(separator + 1));
    }

    private String keyFromReference(String reference) {
        if (!reference.startsWith(referencePrefix)) throw invalidReference();
        return validateKey(reference.substring(referencePrefix.length()));
    }

    private String keyFromConfiguredUrl(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawFragment() != null) throw invalidReference();
        String rawPath = uri.getRawPath();
        String prefix = "/" + properties.container() + "/";
        if (rawPath == null || !rawPath.startsWith(prefix)) throw invalidReference();
        // The SDK encodes blob-name slashes as %2F. Decode once, then validate
        // segments and reject any remaining encoded traversal in validateKey.
        String decodedPath = uri.getPath();
        return validateKey(decodedPath.substring(prefix.length()));
    }

    private String posterKey(Long festivalId, String reference) {
        if (festivalId == null || festivalId <= 0) throw invalidPoster();
        String normalized = normalizeReference(reference);
        if (normalized == null || !normalized.startsWith(referencePrefix)) throw invalidPoster();
        String key = keyFromReference(normalized);
        if (!key.startsWith("festival/" + festivalId + "/poster/")) throw invalidPoster();
        return key;
    }

    private String validateKey(String key) {
        if (key == null || key.isBlank() || key.startsWith("/") || key.indexOf('\\') >= 0
                || key.indexOf('?') >= 0 || key.indexOf('#') >= 0 || ENCODED_TRAVERSAL.matcher(key).find()
                || key.chars().anyMatch(Character::isISOControl)) {
            throw invalidReference();
        }
        for (String segment : key.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) throw invalidReference();
        }
        return key;
    }

    private static boolean isGeneratedImageSuffix(String suffix) {
        return suffix.matches("[0-9]{8}/[a-fA-F0-9-]{36}_[A-Za-z0-9._-]+");
    }

    private static boolean isAllowedImageContentType(String contentType) {
        return contentType != null && ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType.trim().toLowerCase(Locale.ROOT));
    }

    private String validatedContentType(String contentType) {
        if (contentType == null || !ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType.trim().toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only JPEG, PNG, and WebP image uploads are allowed");
        }
        return contentType.trim().toLowerCase(Locale.ROOT);
    }

    private BlobServiceSasSignatureValues readSasValues() {
        BlobSasPermission permissions = new BlobSasPermission().setReadPermission(true);
        return new BlobServiceSasSignatureValues(
                OffsetDateTime.ofInstant(sasExpiry(properties.readSasDuration()), ZoneOffset.UTC), permissions)
                .setProtocol(SasProtocol.HTTPS_ONLY);
    }

    private Instant sasExpiry(Duration requestedDuration) {
        Instant now = clock.instant();
        CachedDelegationKey cached = userDelegationKeyForSasCached();
        Instant requested = now.plus(requestedDuration);
        return requested.isBefore(cached.expiresAt()) ? requested : cached.expiresAt().minusSeconds(1);
    }

    private UserDelegationKey userDelegationKeyForSas() {
        return userDelegationKeyForSasCached().key();
    }

    private CachedDelegationKey userDelegationKeyForSasCached() {
        CachedDelegationKey current = cachedDelegationKey;
        Instant now = clock.instant();
        if (current != null && now.plus(keyRenewalBuffer()).isBefore(current.expiresAt())) return current;
        synchronized (delegationKeyLock) {
            current = cachedDelegationKey;
            if (current != null && now.plus(keyRenewalBuffer()).isBefore(current.expiresAt())) return current;
            Instant start = now.minus(USER_DELEGATION_KEY_START_SKEW);
            Instant expiresAt = now.plus(USER_DELEGATION_KEY_LIFETIME);
            try {
                UserDelegationKey key = serviceClient.getUserDelegationKey(
                        OffsetDateTime.ofInstant(start, ZoneOffset.UTC), OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
                current = new CachedDelegationKey(key, expiresAt);
                cachedDelegationKey = current;
                return current;
            } catch (AzureException exception) {
                throw storageUnavailable("Unable to authorize the image URL", exception);
            }
        }
    }

    private boolean isConfiguredOrigin(URI uri) {
        return endpoint.getScheme().equalsIgnoreCase(uri.getScheme())
                && endpoint.getHost().equalsIgnoreCase(uri.getHost())
                && effectivePort(endpoint) == effectivePort(uri)
                && (uri.getUserInfo() == null || uri.getUserInfo().isEmpty());
    }

    private static URI normalizeEndpoint(URI endpoint) {
        try {
            return new URI(endpoint.getScheme().toLowerCase(Locale.ROOT), null,
                    endpoint.getHost().toLowerCase(Locale.ROOT), endpoint.getPort(), "/", null, null);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid azure.storage.endpoint", exception);
        }
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() == -1 ? 443 : uri.getPort();
    }

    private static boolean isAzureBlobOrigin(URI uri) {
        String host = uri.getHost();
        if (host == null) return false;
        String lowercaseHost = host.toLowerCase(Locale.ROOT);
        return lowercaseHost.contains(".blob.") || lowercaseHost.endsWith(".blob.core.windows.net");
    }

    private static boolean hasSasQuery(URI uri) {
        String query = uri.getRawQuery();
        return query != null && query.toLowerCase(Locale.ROOT).matches(".*(?:^|&)sig=.*");
    }

    private Duration keyRenewalBuffer() {
        return properties.readSasDuration().plusSeconds(30);
    }

    private static ResponseStatusException invalidReference() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Azure image reference");
    }

    private static ResponseStatusException invalidPoster() {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Saved poster URL does not identify a valid Azure festival poster");
    }

    private static ResponseStatusException invalidBackground() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "업로드한 이미지를 선택해 주세요.");
    }

    private static ResponseStatusException storageUnavailable(String message, Exception cause) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message, cause);
    }

    private record CachedDelegationKey(UserDelegationKey key, Instant expiresAt) {
    }
}
