package com.danzzan.infra.image;

import com.danzzan.global.security.UserAdminAuthorizationService;
import com.danzzan.infra.azure.AzureBlobStorage;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ImageReadUrlServiceTest {
    private final AzureBlobStorage storage = mock(AzureBlobStorage.class);
    private final PublicImageReferenceLookup publicReferences = mock(PublicImageReferenceLookup.class);
    private final UserAdminAuthorizationService authorization = mock(UserAdminAuthorizationService.class);
    private final Authentication authentication = mock(Authentication.class);
    private final ImageReadUrlService service = new ImageReadUrlService(storage, publicReferences, authorization);

    @Test
    void publicStoredReferenceRenewsAnExpiredSasWithoutAuthentication() {
        String expiredUrl = "https://danzzandemo2026.blob.core.windows.net/images/ads/20261012/id.png?sig=expired";
        String reference = "azure:images/ads/20261012/id.png";
        when(storage.normalizeReference(expiredUrl)).thenReturn(reference);
        when(publicReferences.isPublic(reference)).thenReturn(true);
        when(storage.readUrl(reference)).thenReturn("https://blob/read?sig=fresh");

        assertEquals("https://blob/read?sig=fresh", service.renew(expiredUrl, null));

        verify(storage).normalizeReference(expiredUrl);
        verify(storage).readUrl(reference);
        verifyNoInteractions(authorization);
    }

    @Test
    void operationsManagerCanRenewAStagedManagedImage() {
        String reference = "azure:images/notices/images/20261012/id.png";
        when(storage.normalizeReference(reference)).thenReturn(reference);
        when(publicReferences.isPublic(reference)).thenReturn(false);
        when(authorization.hasOperationsRole(authentication)).thenReturn(true);
        when(storage.readUrl(reference)).thenReturn("https://blob/read?sig=fresh");

        assertEquals("https://blob/read?sig=fresh", service.renew(reference, authentication));

        verify(storage).readUrl(reference);
    }

    @Test
    void ticketingManagerCanRenewOnlyTicketingBackground() {
        String allowed = "azure:images/festival/ticketing-background/20261012/id.png";
        when(storage.normalizeReference(allowed)).thenReturn(allowed);
        when(publicReferences.isPublic(allowed)).thenReturn(false);
        when(authorization.hasTicketingRole(authentication)).thenReturn(true);
        when(storage.readUrl(allowed)).thenReturn("https://blob/read?sig=fresh");

        assertEquals("https://blob/read?sig=fresh", service.renew(allowed, authentication));

        String disallowed = "azure:images/ads/20261012/id.png";
        when(storage.normalizeReference(disallowed)).thenReturn(disallowed);
        when(publicReferences.isPublic(disallowed)).thenReturn(false);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.renew(disallowed, authentication));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(storage, never()).readUrl(disallowed);
    }

    @Test
    void unregisteredManagedReferenceIsForbiddenWithoutAStaffRole() {
        String reference = "azure:images/artists/20261012/id.png";
        when(storage.normalizeReference(reference)).thenReturn(reference);
        when(publicReferences.isPublic(reference)).thenReturn(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.renew(reference, null));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(storage, never()).readUrl(anyString());
    }

    @Test
    void legacyOrForeignValueCannotBecomeAReadUrl() {
        String legacyUrl = "https://legacy.example/image.png";
        when(storage.normalizeReference(legacyUrl)).thenReturn(legacyUrl);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.renew(legacyUrl, null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(publicReferences, authorization);
        verify(storage, never()).readUrl(anyString());
    }
}
