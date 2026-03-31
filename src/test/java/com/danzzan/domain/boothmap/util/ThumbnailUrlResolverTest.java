package com.danzzan.domain.boothmap.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ThumbnailUrlResolverTest {

    @Test
    void 원본_url을_썸네일_url로_변환한다() {
        String imageUrl = "https://cdn.example.com/software/2.png";

        String thumbnailUrl = ThumbnailUrlResolver.toThumbnailUrl(imageUrl);

        assertEquals("https://cdn.example.com/software/thumb/2.webp", thumbnailUrl);
    }

    @Test
    void 쿼리스트링이_있어도_유지한다() {
        String imageUrl = "https://cdn.example.com/software/2.png?v=1";

        String thumbnailUrl = ThumbnailUrlResolver.toThumbnailUrl(imageUrl);

        assertEquals("https://cdn.example.com/software/thumb/2.webp?v=1", thumbnailUrl);
    }

    @Test
    void 빈_url은_null을_반환한다() {
        assertNull(ThumbnailUrlResolver.toThumbnailUrl(null));
        assertNull(ThumbnailUrlResolver.toThumbnailUrl(" "));
    }
}
