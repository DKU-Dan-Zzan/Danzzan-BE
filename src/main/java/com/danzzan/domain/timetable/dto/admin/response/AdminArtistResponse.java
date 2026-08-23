package com.danzzan.domain.timetable.dto.admin.response;

import com.danzzan.domain.timetable.model.entity.Artist;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AdminArtistResponse {
    private Integer artistId;
    private String name;
    private String description;
    private String imageUrl;
    private String nameEn;
    private String descriptionEn;
    private boolean enIsManual;

    public static AdminArtistResponse from(Artist artist) {
        return new AdminArtistResponse(
                artist.getId(),
                artist.getName(),
                artist.getDescription(),
                artist.getImageUrl(),
                artist.getNameEn(),
                artist.getDescriptionEn(),
                artist.isEnIsManual()
        );
    }
}
