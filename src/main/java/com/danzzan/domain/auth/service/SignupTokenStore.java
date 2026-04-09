package com.danzzan.domain.auth.service;

import com.danzzan.domain.user.model.entity.AcademicStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SignupTokenStore {

    private final Map<String, StudentInfoCache> signupCache = new ConcurrentHashMap<>();

    public void cacheStudentInfo(String signupToken, String studentId, String name,
                                 String college, String major, AcademicStatus academicStatus) {
        signupCache.put(signupToken, new StudentInfoCache(studentId, name, college, major, academicStatus));
    }

    public StudentInfoCache getCachedStudentInfo(String signupToken) {
        StudentInfoCache cache = signupCache.get(signupToken);
        if (cache == null) {
            throw new IllegalArgumentException("유효하지 않은 회원가입 토큰입니다.");
        }
        return cache;
    }

    public void remove(String signupToken) {
        signupCache.remove(signupToken);
    }

    public record StudentInfoCache(
            String studentId,
            String name,
            String college,
            String major,
            AcademicStatus academicStatus
    ) {}
}
