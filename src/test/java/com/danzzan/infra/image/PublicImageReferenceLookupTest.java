package com.danzzan.infra.image;

import com.danzzan.domain.advertisement.Advertisement;
import com.danzzan.domain.advertisement.AdvertisementPlacement;
import com.danzzan.domain.notice.entity.Notice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "image.storage.provider=azure"
})
@Import(PublicImageReferenceLookup.class)
class PublicImageReferenceLookupTest {
    @Autowired private PublicImageReferenceLookup lookup;
    @Autowired private TestEntityManager entityManager;

    @Test
    void activeNoticeAndAdvertisementArePublicButHiddenAndDeletedRowsAreNot() {
        String activeNoticeReference = "azure:images/notices/images/20261012/active.png";
        String hiddenNoticeReference = "azure:images/notices/images/20261012/hidden.png";
        String activeAdvertisementReference = "azure:images/ads/20261012/active.png";
        String hiddenAdvertisementReference = "azure:images/ads/20261012/hidden.png";
        String deletedAdvertisementReference = "azure:images/ads/20261012/deleted.png";

        entityManager.persist(notice(activeNoticeReference, true));
        entityManager.persist(notice(hiddenNoticeReference, false));
        entityManager.persist(advertisement(activeAdvertisementReference, true, null));
        entityManager.persist(advertisement(hiddenAdvertisementReference, false, null));
        entityManager.persist(advertisement(deletedAdvertisementReference, true, LocalDateTime.now()));
        entityManager.flush();
        entityManager.clear();

        assertThat(lookup.isPublic(activeNoticeReference)).isTrue();
        assertThat(lookup.isPublic(activeAdvertisementReference)).isTrue();
        assertThat(lookup.isPublic(hiddenNoticeReference)).isFalse();
        assertThat(lookup.isPublic(hiddenAdvertisementReference)).isFalse();
        assertThat(lookup.isPublic(deletedAdvertisementReference)).isFalse();
    }

    @Test
    void anUnregisteredReferenceEvaluatesEveryPublicImageQueryAndRemainsPrivate() {
        assertThat(lookup.isPublic("azure:images/ads/20261012/not-in-db.png")).isFalse();
        assertThat(lookup.isPublic(null)).isFalse();
        assertThat(lookup.isPublic(" ")).isFalse();
    }

    private static Notice notice(String imageUrl, boolean active) {
        Notice notice = Notice.create("title", "content", "author", false);
        notice.setThumbnailImageUrl(imageUrl);
        notice.setIsActive(active);
        return notice;
    }

    private static Advertisement advertisement(String imageUrl, boolean active, LocalDateTime deletedAt) {
        Advertisement advertisement = new Advertisement();
        advertisement.setTitle("title");
        advertisement.setImageUrl(imageUrl);
        advertisement.setPlacement(AdvertisementPlacement.HOME_BOTTOM);
        advertisement.setIsActive(active);
        advertisement.setDeletedAt(deletedAt);
        return advertisement;
    }
}
