package com.danzzan.global.jwt;

import com.danzzan.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
class CommittedUserVersionReader {

    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<Integer> findActiveTokenVersion(Long userId) {
        return userRepository.findActiveById(userId).map(user -> user.getTokenVersion());
    }
}
