package com.danzzan.infra.image;

import com.danzzan.global.security.UserAdminAuthorizationService;
import com.danzzan.infra.azure.AzureBlobStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Issues a fresh, short-lived read SAS only for an image the caller may see. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "image.storage", name = "provider", havingValue = "azure")
public class ImageReadUrlService {
    private static final String AZURE_REFERENCE_PREFIX = "azure:";
    private static final String TICKETING_BACKGROUND_PREFIX = "festival/ticketing-background/";

    private final AzureBlobStorage azureBlobStorage;
    private final PublicImageReferenceLookup publicImageReferenceLookup;
    private final UserAdminAuthorizationService authorizationService;

    public String renew(String value, Authentication authentication) {
        String reference = azureBlobStorage.normalizeReference(value);
        if (reference == null || !reference.startsWith(AZURE_REFERENCE_PREFIX)) {
            throw invalidReference();
        }

        if (publicImageReferenceLookup.isPublic(reference)) {
            return azureBlobStorage.readUrl(reference);
        }

        String key = keyOf(reference);
        if (authorizationService.hasOperationsRole(authentication) && isManagedImageKey(key)) {
            return azureBlobStorage.readUrl(reference);
        }
        if (authorizationService.hasTicketingRole(authentication)
                && key.startsWith(TICKETING_BACKGROUND_PREFIX)) {
            return azureBlobStorage.readUrl(reference);
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This image URL cannot be renewed");
    }

    private static String keyOf(String reference) {
        int separator = reference.indexOf('/');
        if (separator < 0 || separator == reference.length() - 1) throw invalidReference();
        return reference.substring(separator + 1);
    }

    /** All server-generated image keys belong to one of these roots. */
    private static boolean isManagedImageKey(String key) {
        return key.startsWith("festival/")
                || key.startsWith("notices/images/")
                || key.startsWith("ads/")
                || key.startsWith("pub-images/")
                || key.startsWith("artists/");
    }

    private static ResponseStatusException invalidReference() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Azure image reference");
    }
}
