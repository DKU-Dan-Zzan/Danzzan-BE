package com.danzzan.domain.timetable.controller;

import com.danzzan.domain.timetable.model.dto.TimetableResponseDto;
import com.danzzan.domain.timetable.model.dto.ContentImageDto;
import com.danzzan.domain.timetable.service.TimetableService;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/timetable")
public class TimetableController {
    private final TimetableService timetableService;

    @GetMapping("/performances")
    public TimetableResponseDto getPerformances(
            @RequestParam("date") 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        return timetableService.getPerformances(date);
    }

    @GetMapping("/content-images")
    public List<ContentImageDto> getContentImages() {
        return timetableService.getContentImages();
    }
}
