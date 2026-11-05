package com.example.helpdesk.api;

import com.example.helpdesk.api.dto.AssetDto;
import com.example.helpdesk.dto.AssetForm;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetDtoValidationTest {

    private static final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void assetDtoLimitsMatchDatabaseColumns() {
        assertMaxLength(AssetDto.class, "assetTag", 30);
        assertMaxLength(AssetDto.class, "modelName", 120);
        assertMaxLength(AssetDto.class, "category", 50);
        assertMaxLength(AssetDto.class, "manufacturer", 100);
        assertMaxLength(AssetDto.class, "serialNumber", 100);
    }

    @Test
    void assetFormLimitsMatchDatabaseColumns() {
        assertMaxLength(AssetForm.class, "assetTag", 30);
        assertMaxLength(AssetForm.class, "name", 120);
        assertMaxLength(AssetForm.class, "category", 50);
        assertMaxLength(AssetForm.class, "manufacturer", 100);
        assertMaxLength(AssetForm.class, "serialNumber", 100);
    }

    private static void assertMaxLength(Class<?> type, String property, int max) {
        assertTrue(validator.validateValue(type, property, "x".repeat(max)).isEmpty(),
                property + " should accept its database maximum of " + max);
        assertFalse(validator.validateValue(type, property, "x".repeat(max + 1)).isEmpty(),
                property + " should reject values longer than " + max);
    }
}
