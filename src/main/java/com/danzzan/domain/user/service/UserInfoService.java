package com.danzzan.domain.user.service;

import com.danzzan.domain.user.exception.UserNotFoundException;
import com.danzzan.domain.user.model.UserInfo;
import com.danzzan.domain.user.model.entity.User;
import com.danzzan.domain.user.repository.UserInfoMemoryRepository;
import com.danzzan.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 사용자 정보 캐싱 서비스
// DB 조회를 줄이기 위해 메모리에 사용자 정보 캐싱
@Service
@RequiredArgsConstructor
public class UserInfoService {

    private final UserRepository userRepository;
    private final UserInfoMemoryRepository memoryRepository;

    // 권한은 역할 변경 직후에도 최신이어야 하므로 DB를 기준으로 캐시를 갱신한다.
    @Transactional(readOnly = true)
    public UserInfo getUserInfo(Long userId) {
        User user = userRepository.findActiveById(userId)
                .orElseThrow(UserNotFoundException::new);
        UserInfo userInfo = new UserInfo(user);
        memoryRepository.setUserInfo(userId, userInfo);
        return userInfo;
    }

    // 사용자 정보 캐시에 저장 (로그인 성공 시 호출)
    public void cacheUserInfo(Long userId, User user) {
        UserInfo userInfo = new UserInfo(user);
        memoryRepository.setUserInfo(userId, userInfo);
    }

    // 사용자 정보 캐시 삭제 (정보 변경 시 호출)
    public void invalidateUserInfo(Long userId) {
        memoryRepository.removeUserInfo(userId);
    }
}
