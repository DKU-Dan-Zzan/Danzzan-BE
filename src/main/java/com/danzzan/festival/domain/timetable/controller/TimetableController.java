package com.danzzan.festival.domain.timetable.controller;

import com.danzzan.festival.domain.timetable.model.dto.TimetableResponseDto;
import com.danzzan.festival.domain.timetable.service.TimetableService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

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
}
