package com.danzzan.domain.user.validation;

public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static final String REGEX = "^(?=.*[\\W_]).{8,200}$";
    public static final String MESSAGE = "비밀번호는 8자 이상이며 특수문자를 1자 이상 포함해야 합니다.";
}
