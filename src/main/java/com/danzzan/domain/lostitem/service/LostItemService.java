package com.danzzan.domain.lostitem.service;

import com.danzzan.domain.lostitem.dto.request.CreateLostItemRequest;
import com.danzzan.domain.lostitem.dto.request.UpdateLostItemRequest;
import com.danzzan.domain.lostitem.dto.response.LostItemResponse;
import com.danzzan.domain.lostitem.entity.LostItem;
import com.danzzan.domain.lostitem.repository.LostItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LostItemService {

    private final LostItemRepository lostItemRepository;

    @Transactional(readOnly = true)
    public Page<LostItemResponse> getLostItems(String status, Pageable pageable) {
        Page<LostItem> page;
        if (status == null || status.isBlank() || status.equalsIgnoreCase("ALL")) {
            page = lostItemRepository.findByIsActiveTrue(pageable);
        } else if (status.equalsIgnoreCase("UNCLAIMED")) {
            page = lostItemRepository.findByIsActiveTrueAndIsClaimed(false, pageable);
        } else if (status.equalsIgnoreCase("CLAIMED")) {
            page = lostItemRepository.findByIsActiveTrueAndIsClaimed(true, pageable);
        } else {
            page = lostItemRepository.findByIsActiveTrue(pageable);
        }
        return page.map(LostItemResponse::from);
    }

    @Transactional(readOnly = true)
    public LostItemResponse getLostItem(Long id) {
        LostItem item = lostItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("분실물을 찾을 수 없습니다. id=" + id));
        return LostItemResponse.from(item);
    }

    @Transactional
    public LostItemResponse create(CreateLostItemRequest request) {
        LostItem item = new LostItem();
        item.setItemName(request.getItemName());
        item.setImageUrl(request.getImageUrl());
        item.setFoundLocation(request.getFoundLocation());
        item.setFoundDate(request.getFoundDate());

        boolean claimed = Boolean.TRUE.equals(request.getIsClaimed());
        item.setIsClaimed(claimed);
        if (claimed) {
            item.setReceiverName(request.getReceiverName());
            item.setReceiverNote(request.getReceiverNote());
        }

        return LostItemResponse.from(lostItemRepository.save(item));
    }

    @Transactional
    public LostItemResponse update(Long id, UpdateLostItemRequest request) {
        LostItem item = lostItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("분실물을 찾을 수 없습니다. id=" + id));

        item.setItemName(request.getItemName());
        item.setImageUrl(request.getImageUrl());
        item.setFoundLocation(request.getFoundLocation());
        item.setFoundDate(request.getFoundDate());

        boolean claimed = Boolean.TRUE.equals(request.getIsClaimed());
        item.setIsClaimed(claimed);
        if (claimed) {
            item.setReceiverName(request.getReceiverName());
            item.setReceiverNote(request.getReceiverNote());
        } else {
            item.setReceiverName(null);
            item.setReceiverNote(null);
        }

        return LostItemResponse.from(lostItemRepository.save(item));
    }

    @Transactional
    public void delete(Long id) {
        LostItem item = lostItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("분실물을 찾을 수 없습니다. id=" + id));
        item.setIsActive(false);
        lostItemRepository.save(item);
    }
}

