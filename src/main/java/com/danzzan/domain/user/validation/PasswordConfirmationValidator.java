package com.danzzan.domain.user.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapperImpl;

public class PasswordConfirmationValidator implements ConstraintValidator<ValidPasswordConfirmation, Object> {

    private String passwordField;
    private String confirmPasswordField;

    @Override
    public void initialize(ValidPasswordConfirmation constraintAnnotation) {
        passwordField = constraintAnnotation.passwordField();
        confirmPasswordField = constraintAnnotation.confirmPasswordField();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        BeanWrapperImpl beanWrapper = new BeanWrapperImpl(value);
        Object passwordValue = beanWrapper.getPropertyValue(passwordField);
        Object confirmPasswordValue = beanWrapper.getPropertyValue(confirmPasswordField);

        if (!(passwordValue instanceof String password) || !(confirmPasswordValue instanceof String confirmPassword)) {
            return true;
        }

        if (password.isBlank() || confirmPassword.isBlank()) {
            return true;
        }

        if (!password.matches(PasswordPolicy.REGEX) || password.equals(confirmPassword)) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode(confirmPasswordField)
                .addConstraintViolation();
        return false;
    }
}
