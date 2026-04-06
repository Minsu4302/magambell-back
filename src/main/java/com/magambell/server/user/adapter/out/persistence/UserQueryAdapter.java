package com.magambell.server.user.adapter.out.persistence;

import static com.magambell.server.user.domain.enums.UserStatus.ACTIVE;

import com.magambell.server.auth.domain.ProviderType;
import com.magambell.server.common.annotation.Adapter;
import com.magambell.server.common.enums.ErrorCode;
import com.magambell.server.common.exception.NotFoundException;
import com.magambell.server.user.app.port.out.UserQueryPort;
import com.magambell.server.user.app.port.out.dto.MyPageStatsDTO;
import com.magambell.server.user.app.port.out.dto.UserInfoDTO;
import com.magambell.server.user.domain.entity.User;
import com.magambell.server.user.domain.enums.UserRole;
import com.magambell.server.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Adapter
public class UserQueryAdapter implements UserQueryPort {

    private final UserRepository userRepository;

    @Override
    public boolean existsByEmail(final String email) {
        return userRepository.existsByEmailAndUserStatus(email, ACTIVE);
    }

    @Override
    public User getUser(final String email, final String password) {
        return userRepository.findByEmailAndPasswordAndUserStatus(email, password, ACTIVE)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    public Optional<User> findUserBySocial(final ProviderType providerType, final String providerId) {
        return userRepository.findUserBySocial(providerType, providerId);
    }

    @Override
    public UserInfoDTO getUserInfo(final Long userId) {
        return userRepository.getUserInfo(userId);
    }

    @Override
    public User findById(final Long userId) {
        return userRepository.findByIdAndUserStatus(userId, ACTIVE)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    public boolean existsUserBySocial(final ProviderType providerType, final String providerId) {
        return userRepository.existsUserBySocial(providerType, providerId);
    }

    @Override
    public boolean existsByNickName(final String nickName) {
        return userRepository.existsByNickNameAndUserStatus(nickName, ACTIVE);
    }

    @Override
    public MyPageStatsDTO getMyPageData(final User user) {
        return userRepository.getMyPageData(user.getId());
    }

    @Override
    public List<Long> findActiveUserIdsByRole(final UserRole userRole) {
        return userRepository.findActiveUserIdsByRole(userRole);
    }
}
