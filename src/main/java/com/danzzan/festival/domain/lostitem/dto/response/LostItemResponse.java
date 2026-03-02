package com.danzzan.festival.domain.lostitem.dto.response;

import com.danzzan.festival.domain.lostitem.entity.LostItem;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class LostItemResponse {

    private Long id;
    private String itemName;
    private String imageUrl;
    private String foundLocation;
    private LocalDate foundDate;
    private Boolean isClaimed;
    private String receiverName;
    private String receiverNote;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static LostItemResponse from(LostItem entity) {
        LostItemResponse res = new LostItemResponse();
        res.id = entity.getId();
        res.itemName = entity.getItemName();
        res.imageUrl = entity.getImageUrl();
        res.foundLocation = entity.getFoundLocation();
        res.foundDate = entity.getFoundDate();
        res.isClaimed = entity.getIsClaimed();
        res.receiverName = entity.getReceiverName();
        res.receiverNote = entity.getReceiverNote();
        res.isActive = entity.getIsActive();
        res.createdAt = entity.getCreatedAt();
        res.updatedAt = entity.getUpdatedAt();
        return res;
    }
}

