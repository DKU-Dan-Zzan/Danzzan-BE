package com.danzzan.domain.user.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordConfirmationValidator.class)
public @interface ValidPasswordConfirmation {

    String message() default "비밀번호 확인이 일치하지 않습니다.";

    String passwordField();

    String confirmPasswordField();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
