package com.danzzan.domain.user.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapperImpl;

public class FieldConfirmationValidator implements ConstraintValidator<ValidFieldConfirmation, Object> {

    private String field;
    private String confirmField;

    @Override
    public void initialize(ValidFieldConfirmation constraintAnnotation) {
        field = constraintAnnotation.field();
        confirmField = constraintAnnotation.confirmField();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        BeanWrapperImpl beanWrapper = new BeanWrapperImpl(value);
        Object fieldValue = beanWrapper.getPropertyValue(field);
        Object confirmFieldValue = beanWrapper.getPropertyValue(confirmField);

        if (!(fieldValue instanceof String rawField) || !(confirmFieldValue instanceof String rawConfirmField)) {
            return true;
        }

        String normalizedField = rawField.trim();
        String normalizedConfirmField = rawConfirmField.trim();

        if (normalizedField.isEmpty() || normalizedConfirmField.isEmpty() || normalizedField.equals(normalizedConfirmField)) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode(confirmField)
                .addConstraintViolation();
        return false;
    }
}
