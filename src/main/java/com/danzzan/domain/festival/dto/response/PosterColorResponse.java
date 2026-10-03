package com.danzzan.domain.festival.dto.response;

import java.util.List;

public record PosterColorResponse(List<ColorCandidate> colors) {
    public record ColorCandidate(String hex, double population) {}
}
