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
@Constraint(validatedBy = FieldConfirmationValidator.class)
public @interface ValidFieldConfirmation {

    String message() default "입력값 확인이 일치하지 않습니다.";

    String field();

    String confirmField();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
