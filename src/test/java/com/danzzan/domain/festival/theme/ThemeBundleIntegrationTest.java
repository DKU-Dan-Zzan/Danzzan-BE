package com.danzzan.domain.festival.theme;

import com.danzzan.domain.festival.entity.FestivalSetting;
import com.danzzan.domain.festival.repository.FestivalSettingRepository;
import com.danzzan.domain.festival.theme.dto.ThemeData;
import com.danzzan.domain.festival.theme.entity.Theme;
import com.danzzan.domain.festival.theme.repository.ThemeRepository;
import com.danzzan.domain.user.model.entity.*;
import com.danzzan.domain.user.repository.UserRepository;
import com.danzzan.global.jwt.JwtRevocationService;
import com.danzzan.global.jwt.JwtTokenProvider;
import com.danzzan.infra.image.ImageStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.danzzan.domain.festival.entity.FestivalTicketingRound;
import com.danzzan.domain.festival.repository.FestivalTicketingRoundRepository;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:theme-bundle;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "jwt.secret=test-secret-key-for-jwt-at-least-32-characters-long",
        "jwt.access-token-expiration=3600000", "jwt.refresh-token-expiration=604800000",
        "local-image.public-base-url=http://localhost:8080/uploads", "octomo.api-key=local-test"
})
@ActiveProfiles("local-storage")
@AutoConfigureMockMvc
class ThemeBundleIntegrationTest {
    @TempDir static Path directory;
    @DynamicPropertySource static void storageRoot(DynamicPropertyRegistry registry) {
        registry.add("local-image.root-directory", () -> directory.toString());
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ThemeRepository themes;
    @Autowired FestivalTicketingRoundRepository rounds;
    @MockitoSpyBean FestivalSettingRepository settings;
    @Autowired UserRepository users;
    @Autowired JwtTokenProvider tokens;
    @MockitoSpyBean ImageStorage storage;
    @Autowired com.danzzan.domain.festival.service.FestivalBrandingService branding;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @MockitoBean JwtRevocationService revocations;
    String admin;
    Long previousTheme;
    Long existingRound;
    Map<String, Object> body;
    final String poster = "http://localhost:8080/uploads/festival/1/poster/current.png";

    @BeforeEach void setUp() {
        rounds.deleteAll();
        themes.deleteAll();
        settings.deleteAll();
        users.deleteAll();
        var setting = FestivalSetting.create("school", "festival", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), true);
        setting.updatePosterImageUrl(poster);
        setting.updateTicketCardBackgroundImageUrl("old-ticket");
        setting.updateTicketingBackgroundImageUrl("old-closed");
        setting = settings.saveAndFlush(setting);
        previousTheme = themes.saveAndFlush(Theme.create(setting, "Original", data())).getId();
        setting.activateTheme(previousTheme);
        settings.saveAndFlush(setting);
        existingRound = rounds.saveAndFlush(FestivalTicketingRound.create(
                LocalDate.of(2026, 8, 31).atTime(18, 0), 150, LocalDate.of(2026, 9, 1), 1)).getId();
        admin = token(UserRole.ROLE_ADMIN);
        var image = new MockMultipartFile("file", "background.jpg", "image/jpeg", new byte[]{1, 2, 3});
        body = new LinkedHashMap<>(Map.of("name", "Bundle", "themeData", data(), "sourcePosterUrl", poster,
                "sourceColors", List.of("#FF642D", "#F5E3A3"), "selectedBaseColor", "#FF642D",
                "ticketCardBackgroundImageUrl", storage.uploadTicketingBackground(1L, image).url(),
                "ticketingBackgroundImageUrl", storage.uploadTicketingBackground(1L, image).url()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void createsNewActiveThemeAndPublishesBothBackgroundsWithProvenance(boolean enabled) throws Exception {
        var before = settings.findById(1L).orElseThrow();
        before.update(before.getSchoolName(), before.getFestivalName(), before.getStartDate(), before.getEndDate(), enabled);
        settings.saveAndFlush(before);
        var result = apply(admin).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.sourcePosterUrl").value(poster))
                .andExpect(jsonPath("$.selectedBaseColor").value("#FF642D"))
                .andExpect(jsonPath("$.sourceColors[1]").value("#F5E3A3")).andReturn();
        long id = mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        assertNotEquals(previousTheme, id);
        var saved = settings.findById(1L).orElseThrow();
        assertEquals(id, saved.getActiveThemeId());
        assertEquals(body.get("ticketCardBackgroundImageUrl"), saved.getTicketCardBackgroundImageUrl());
        assertEquals(body.get("ticketingBackgroundImageUrl"), saved.getTicketingBackgroundImageUrl());
        assertEquals(enabled, saved.isTicketingEnabled());
        unchangedRounds();
        assertEquals(LocalDate.of(2026, 9, 1), saved.getStartDate());
        assertEquals("Original", themes.findById(previousTheme).orElseThrow().getName());
        assertEquals(2, themes.count());
    }

    @Test void requiresBothPermissionsAndAllowsDualPermissionManager() throws Exception {
        for (var permission : ManagerPermission.values()) {
            apply(token(UserRole.ROLE_MANAGER, permission)).andExpect(status().isForbidden());
            unchanged();
        }
        apply(token(UserRole.ROLE_MANAGER, ManagerPermission.OPERATIONS, ManagerPermission.TICKETING))
                .andExpect(status().isOk());
    }

    @Test void stalePosterReturnsConflictWithoutPublishingAnything() throws Exception {
        var setting = settings.findById(1L).orElseThrow();
        setting.updatePosterImageUrl(poster + "-new"); settings.saveAndFlush(setting);
        apply(admin).andExpect(status().isConflict());
        unchanged();
    }

    @Test void rejectsExternalMissingAndWrongDirectoryBackgroundsInEitherField() throws Exception {
        for (String field : List.of("ticketCardBackgroundImageUrl", "ticketingBackgroundImageUrl")) {
            Object good = body.get(field);
            for (String bad : List.of(good.toString().replace("localhost:8080", "evil.example"),
                    good.toString().replace("/1/", "/2/"), good + "?x=1", good + "#fragment",
                    good.toString().replace("ticketing-background", "poster"), good + "missing")) {
                body.put(field, bad);
                apply(admin).andExpect(status().isBadRequest());
                unchanged();
            }
            body.put(field, good);
        }
    }

    @Test void validatesRequiredFieldsAndNestedPaletteBeforeAnyMutation() throws Exception {
        for (String field : List.copyOf(body.keySet())) {
            Object good = body.remove(field);
            apply(admin).andExpect(status().isBadRequest()); unchanged(); body.put(field, good);
        }
        body.put("selectedBaseColor", "red");
        apply(admin).andExpect(status().isBadRequest()); unchanged();
        body.put("selectedBaseColor", "#FF642D");
        body.put("themeData", Map.of("brand", Map.of("primary", "invalid")));
        apply(admin).andExpect(status().isBadRequest()); unchanged();
    }

    @Test void failureAfterThemeInsertAndSettingMutationRollsBackEntireBundle() throws Exception {
        doAnswer(invocation -> {
            FestivalSetting pending = invocation.getArgument(0);
            assertNotEquals(previousTheme, pending.getActiveThemeId());
            assertEquals(body.get("ticketingBackgroundImageUrl"), pending.getTicketingBackgroundImageUrl());
            assertEquals(body.get("ticketCardBackgroundImageUrl"), pending.getTicketCardBackgroundImageUrl());
            assertEquals(2, themes.count());
            throw new IllegalStateException("injected persistence failure");
        }).when(settings).save(any(FestivalSetting.class));
        apply(admin).andExpect(status().isBadRequest());
        verify(settings).save(any(FestivalSetting.class));
        unchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"poster", "logo"})
    void delayedBrandingUploadPreservesConcurrentBundleAndTicketingUpdate(String kind) throws Exception {
        var started = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var uploadInTransaction = new java.util.concurrent.atomic.AtomicBoolean();
        org.mockito.stubbing.Answer<Object> delayed = invocation -> {
            uploadInTransaction.set(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            started.countDown();
            if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("upload not released");
            return invocation.callRealMethod();
        };
        if (kind.equals("poster")) doAnswer(delayed).when(storage).uploadPoster(eq(1L), any());
        else doAnswer(delayed).when(storage).uploadLogo(eq(1L), any());
        var bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", bytes);
        var file = new MockMultipartFile("file", "branding.png", "image/png", bytes.toByteArray());
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var upload = executor.submit(() -> kind.equals("poster") ? branding.uploadPoster(1L, file) : branding.uploadLogo(1L, file));
        try {
            assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS));
            var applied = executor.submit(() -> apply(admin).andExpect(status().isOk()).andReturn());
            long id = mapper.readTree(applied.get(5, java.util.concurrent.TimeUnit.SECONDS)
                    .getResponse().getContentAsString()).get("id").asLong();
            new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                var current = settings.findByIdForUpdate(1L).orElseThrow();
                current.update("changed school", current.getFestivalName(), current.getStartDate(), current.getEndDate(), false);
            });
            release.countDown();
            var result = upload.get(5, java.util.concurrent.TimeUnit.SECONDS);
            var saved = settings.findById(1L).orElseThrow();
            assertEquals(id, saved.getActiveThemeId());
            assertEquals(body.get("ticketCardBackgroundImageUrl"), saved.getTicketCardBackgroundImageUrl());
            assertEquals(body.get("ticketingBackgroundImageUrl"), saved.getTicketingBackgroundImageUrl());
            assertFalse(saved.isTicketingEnabled());
            assertEquals("changed school", saved.getSchoolName());
            assertEquals(kind.equals("poster") ? result.posterImageUrl() : poster, saved.getPosterImageUrl());
            if (kind.equals("logo")) assertEquals(result.logoImageUrl(), saved.getLogoImageUrl());
            assertFalse(uploadInTransaction.get(), "storage upload must not hold a database transaction");
            unchangedRounds();
        } finally {
            release.countDown(); executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    @Test void slowBackgroundVerificationDoesNotLockFestivalAndRechecksPosterAfterwards() throws Exception {
        var started = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var verificationInTransaction = new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(invocation -> {
            verificationInTransaction.set(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            started.countDown();
            if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("verification not released");
            return invocation.callRealMethod();
        }).when(storage).validateTicketingBackground(1L, body.get("ticketCardBackgroundImageUrl").toString());
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var applying = executor.submit(() -> apply(admin).andReturn());
        try {
            assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS));
            var changingPoster = executor.submit(() ->
                new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    settings.findByIdForUpdate(1L).orElseThrow().updatePosterImageUrl(poster + "-new");
                }));
            changingPoster.get(3, java.util.concurrent.TimeUnit.SECONDS);
            release.countDown();
            assertEquals(409, applying.get(5, java.util.concurrent.TimeUnit.SECONDS).getResponse().getStatus());
            assertFalse(verificationInTransaction.get(), "storage verification must not hold a database transaction");
            unchanged();
            assertEquals(poster + "-new", settings.findById(1L).orElseThrow().getPosterImageUrl());
        } finally {
            release.countDown(); executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    void unchanged() {
        var saved = settings.findById(1L).orElseThrow();
        assertEquals(previousTheme, saved.getActiveThemeId());
        assertEquals("old-ticket", saved.getTicketCardBackgroundImageUrl());
        assertEquals("old-closed", saved.getTicketingBackgroundImageUrl());
        assertTrue(saved.isTicketingEnabled()); assertEquals(1, themes.count());
        unchangedRounds();
    }
    void unchangedRounds() {
        assertEquals(1, rounds.count());
        var round = rounds.findById(existingRound).orElseThrow();
        assertEquals(150, round.getCapacity());
        assertEquals(LocalDate.of(2026, 8, 31).atTime(18, 0), round.getTicketingAt());
        assertEquals(LocalDate.of(2026, 9, 1), round.getPerformanceDate());
        assertEquals(1, round.getDisplayOrder());
        assertNull(round.getEventId());
    }
    ResultActions apply(String token) throws Exception {
        return mvc.perform(post("/admin/festival/1/themes/apply-bundle").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)));
    }
    String token(UserRole role, ManagerPermission... permissions) {
        var user = User.builder().studentId(UUID.randomUUID().toString()).password("encoded").name("test")
                .college("college").major("major").academicStatus(AcademicStatus.ENROLLED).role(role).build();
        if (role == UserRole.ROLE_MANAGER) user.changeManagerPermissions(List.of(permissions));
        user = users.saveAndFlush(user);
        return tokens.createAccessToken(user.getId(), user.getStudentId(), role.name(), user.getTokenVersion(),
                user.getManagerPermissions().stream().map(Enum::name).toList());
    }
    static ThemeData data() {
        return new ThemeData(new ThemeData.Brand("#FF642D", "#F5E3A3", "#FF642D", "#E13600"),
                new ThemeData.Status("#2E7D32", "#B26A00", "#B3261E", "#E3776A"),
                new ThemeData.Category("#FF642D", "#EC4899", "#10B981", "#F6CA3B", "#8B5CF6", "#3B82F6"));
    }
}
