package com.magambell.server.user.domain.repository;

import com.magambell.server.auth.domain.ProviderType;
import com.magambell.server.user.app.port.out.dto.MyPageStatsDTO;
import com.magambell.server.user.app.port.out.dto.UserInfoDTO;
import com.magambell.server.user.domain.entity.User;
import com.magambell.server.user.domain.enums.UserRole;
import java.util.List;
import java.util.Optional;

public interface UserRepositoryCustom {
    Optional<User> findUserBySocial(final ProviderType providerType, final String providerId);

    UserInfoDTO getUserInfo(Long userId);

    boolean existsUserBySocial(ProviderType providerType, String providerId);

    MyPageStatsDTO getMyPageData(Long userId);

    List<Long> findActiveUserIdsByRole(UserRole userRole);
}
