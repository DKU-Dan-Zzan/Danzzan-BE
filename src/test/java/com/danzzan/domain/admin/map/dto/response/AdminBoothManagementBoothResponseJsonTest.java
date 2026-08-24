package com.danzzan.domain.admin.map.dto.response;

import com.danzzan.domain.boothmap.model.entity.BoothOperationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminBoothManagementBoothResponseJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesLocationFieldsAndPlacedFlag() throws Exception {
        AdminBoothManagementBoothResponse response = new AdminBoothManagementBoothResponse(
                1L,
                "EXPERIENCE",
                "테스트",
                null,
                127.123,
                37.456,
                true,
                true,
                BoothOperationStatus.OPEN,
                "10:00",
                "16:00",
                List.of("2026-05-12"),
                "Test",
                null,
                false
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.get("locationX").asDouble()).isEqualTo(127.123);
        assertThat(json.get("locationY").asDouble()).isEqualTo(37.456);
        assertThat(json.get("placed").asBoolean()).isTrue();
    }
}
