package com.danzzan.domain.festival.theme;

import com.danzzan.domain.festival.theme.dto.ThemeData;
import com.danzzan.domain.festival.theme.dto.ThemeUpsertRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeRequestValidationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejectsEveryMissingCoreColor() throws Exception {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            for (String group : new String[]{"brand", "status", "category"}) {
                ObjectNode groupNode = (ObjectNode) objectMapper.valueToTree(validRequest().themeData()).get(group);
                for (String field : new String[]{"primary", "secondary", "accent", "textEmphasis",
                        "success", "warning", "danger", "pending", "pub", "foodTruck",
                        "experience", "event", "restArea", "facility"}) {
                    if (!groupNode.has(field)) continue;
                    ObjectNode dataNode = objectMapper.valueToTree(validRequest().themeData());
                    ((ObjectNode) dataNode.get(group)).remove(field);
                    ThemeData missing = objectMapper.treeToValue(dataNode, ThemeData.class);
                    assertFalse(validator.validate(new ThemeUpsertRequest("Orange", missing)).isEmpty(),
                            group + "." + field + " must be required");
                }
            }
        }
    }

    @Test
    void rejectsInvalidHexAndBlankColor() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            ThemeData invalid = new ThemeData(
                    new ThemeData.Brand("#FFF", " ", "#GGGGGG", "#E13600"),
                    validRequest().themeData().status(), validRequest().themeData().category());
            assertTrue(validator.validate(new ThemeUpsertRequest("Orange", invalid)).size() >= 3);
        }
    }

    @Test
    void rejectsBlankOrOversizedNameAndMissingGroup() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            assertFalse(validator.validate(new ThemeUpsertRequest("  ", validRequest().themeData())).isEmpty());
            assertFalse(validator.validate(new ThemeUpsertRequest("x".repeat(101), validRequest().themeData())).isEmpty());
            assertFalse(validator.validate(new ThemeUpsertRequest("Orange", null)).isEmpty());
            ThemeData missingBrand = new ThemeData(null, validRequest().themeData().status(),
                    validRequest().themeData().category());
            assertFalse(validator.validate(new ThemeUpsertRequest("Orange", missingBrand)).isEmpty());
        }
    }

    private ThemeUpsertRequest validRequest() {
        return new ThemeUpsertRequest("Orange", new ThemeData(
                new ThemeData.Brand("#FF642D", "#F5E3A3", "#FF642D", "#E13600"),
                new ThemeData.Status("#2E7D32", "#B26A00", "#B3261E", "#E3776A"),
                new ThemeData.Category("#FF642D", "#EC4899", "#10B981", "#F6CA3B", "#8B5CF6", "#3B82F6")
        ));
    }
}
