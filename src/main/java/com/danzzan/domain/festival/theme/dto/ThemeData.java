package com.danzzan.domain.festival.theme.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Only the 14 persisted Theme Core colors. All UI tints and opacity stay derived on the client. */
public record ThemeData(
        @NotNull @Valid Brand brand,
        @NotNull @Valid Status status,
        @NotNull @Valid Category category
) {
    private static final String HEX = "#[0-9A-Fa-f]{6}";

    public record Brand(
            @NotBlank @Pattern(regexp = HEX) String primary,
            @NotBlank @Pattern(regexp = HEX) String secondary,
            @NotBlank @Pattern(regexp = HEX) String accent,
            @NotBlank @Pattern(regexp = HEX) String textEmphasis
    ) {}

    public record Status(
            @NotBlank @Pattern(regexp = HEX) String success,
            @NotBlank @Pattern(regexp = HEX) String warning,
            @NotBlank @Pattern(regexp = HEX) String danger,
            @NotBlank @Pattern(regexp = HEX) String pending
    ) {}

    public record Category(
            @NotBlank @Pattern(regexp = HEX) String pub,
            @NotBlank @Pattern(regexp = HEX) String foodTruck,
            @NotBlank @Pattern(regexp = HEX) String experience,
            @NotBlank @Pattern(regexp = HEX) String event,
            @NotBlank @Pattern(regexp = HEX) String restArea,
            @NotBlank @Pattern(regexp = HEX) String facility
    ) {}
}
