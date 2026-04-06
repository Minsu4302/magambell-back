package com.magambell.server.user.app.port.out;

import com.magambell.server.auth.domain.ProviderType;
import com.magambell.server.user.app.port.out.dto.MyPageStatsDTO;
import com.magambell.server.user.app.port.out.dto.UserInfoDTO;
import com.magambell.server.user.domain.entity.User;
import com.magambell.server.user.domain.enums.UserRole;
import java.util.List;
import java.util.Optional;

public interface UserQueryPort {

    boolean existsByEmail(String email);

    User getUser(String email, String password);

    Optional<User> findUserBySocial(ProviderType providerType, String providerId);

    UserInfoDTO getUserInfo(Long userId);

    User findById(Long userId);

    boolean existsUserBySocial(ProviderType providerType, String providerId);

    boolean existsByNickName(String nickName);

    MyPageStatsDTO getMyPageData(User user);

    List<Long> findActiveUserIdsByRole(UserRole userRole);
}
