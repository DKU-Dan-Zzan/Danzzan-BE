package com.danzzan.domain.lostitem.controller;

import com.danzzan.domain.lostitem.dto.request.CreateLostItemRequest;
import com.danzzan.domain.lostitem.dto.request.UpdateLostItemRequest;
import com.danzzan.domain.lostitem.dto.response.LostItemResponse;
import com.danzzan.domain.lostitem.service.LostItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/lost-items")
@RequiredArgsConstructor
public class AdminLostItemController {

    private final LostItemService lostItemService;

    /** 분실물 목록 조회 (전체 / 미인수 / 인수완료) */
    @GetMapping
    public ResponseEntity<Page<LostItemResponse>> getLostItems(
            @RequestParam(name = "status", required = false) String status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(lostItemService.getLostItems(status, pageable));
    }

    /** 분실물 단건 조회 */
    @GetMapping("/{id}")
    public ResponseEntity<LostItemResponse> getLostItem(@PathVariable Long id) {
        return ResponseEntity.ok(lostItemService.getLostItem(id));
    }

    /** 분실물 등록 */
    @PostMapping
    public ResponseEntity<LostItemResponse> createLostItem(
            @Valid @RequestBody CreateLostItemRequest request
    ) {
        return ResponseEntity.ok(lostItemService.create(request));
    }

    /** 분실물 수정 */
    @PutMapping("/{id}")
    public ResponseEntity<LostItemResponse> updateLostItem(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLostItemRequest request
    ) {
        return ResponseEntity.ok(lostItemService.update(id, request));
    }

    /** 분실물 삭제 (소프트 삭제: isActive=false) */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLostItem(@PathVariable Long id) {
        lostItemService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

