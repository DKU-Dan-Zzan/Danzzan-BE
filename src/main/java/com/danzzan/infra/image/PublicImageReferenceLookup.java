package com.danzzan.infra.image;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Finds Azure references that can already be returned by an unauthenticated API.
 *
 * <p>Only these persisted references exposed by existing public APIs can be refreshed without
 * authentication. Theme recommendation source images are deliberately excluded:
 * they are administrative provenance, not public image content.</p>
 */
@Component
@ConditionalOnProperty(prefix = "image.storage", name = "provider", havingValue = "azure")
public class PublicImageReferenceLookup {
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public boolean isPublic(String reference) {
        if (reference == null || reference.isBlank()) {
            return false;
        }
        return exists("""
                select count(n) from Notice n left join n.images image
                where n.isActive = true and (n.thumbnailImageUrl = :reference or image = :reference)
                """, reference)
                || exists("""
                select count(a) from Advertisement a
                where a.isActive = true and a.deletedAt is null and a.imageUrl = :reference
                """, reference)
                || exists("select count(h) from HomeImage h where h.imageUrl = :reference", reference)
                || exists("select count(l) from LineupImage l where l.imageUrl = :reference", reference)
                || exists("select count(c) from ContentImage c where c.previewImageUrl = :reference or c.detailImageUrl = :reference", reference)
                || exists("select count(p) from Performance p where p.artist.imageUrl = :reference", reference)
                // Booth detail is a public route even when no operation row has been created yet.
                || exists("select count(b) from Booth b where b.imageUrl = :reference", reference)
                // PubService.getPubDetail requires an operation but does not filter by displayDays.
                // Keep renewal aligned with that public detail route, not only the filtered pub list.
                || exists("""
                select count(i) from PubImage i
                where i.imageUrl = :reference
                  and exists (select po from PubOperation po)
                """, reference)
                || exists("""
                select count(f) from FestivalSetting f
                where f.posterImageUrl = :reference or f.logoImageUrl = :reference
                   or f.ticketingBackgroundImageUrl = :reference or f.ticketCardBackgroundImageUrl = :reference
                """, reference);
    }

    private boolean exists(String jpql, String reference) {
        Long count = entityManager.createQuery(jpql, Long.class)
                .setParameter("reference", reference)
                .getSingleResult();
        return count != null && count > 0;
    }
}
