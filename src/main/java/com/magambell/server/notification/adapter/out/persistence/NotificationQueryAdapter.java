package com.magambell.server.notification.adapter.out.persistence;

import com.magambell.server.common.annotation.Adapter;
import com.magambell.server.common.enums.ErrorCode;
import com.magambell.server.common.exception.NotFoundException;
import com.magambell.server.notification.app.port.out.NotificationQueryPort;
import com.magambell.server.notification.app.port.out.dto.FcmTokenDTO;
import com.magambell.server.notification.domain.entity.FcmToken;
import com.magambell.server.notification.domain.repository.FcmTokenRepository;
import com.magambell.server.store.domain.entity.Store;
import com.magambell.server.user.domain.entity.User;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Adapter
public class NotificationQueryAdapter implements NotificationQueryPort {

    private final FcmTokenRepository fcmTokenRepository;

    @Override
    public boolean existsByUserAndStore(final User user, final Store store) {
        return fcmTokenRepository.existsByUserIdAndStoreId(user.getId(), store.getId());
    }

    @Override
    public long countStoreOpenSubscribers(final Store store) {
        return fcmTokenRepository.countDistinctActiveUsersByStoreId(store.getId());
    }

    @Override
    public List<FcmTokenDTO> findWithAllByStoreId(final Store store) {
        return fcmTokenRepository.findWithAllByStoreId(store.getId());
    }

    @Override
    public FcmTokenDTO findWithAllByUserIdAndStoreIsNull(final User user) {
        return fcmTokenRepository.findWithAllByUserIdAndStoreIsNull(user.getId());
    }

    @Override
    public List<FcmTokenDTO> findWithAllByOwnerIdsAndStoreIsNull(final List<Long> ownerList) {
        return fcmTokenRepository.findWithAllByUsersIdsAndStoreIsNull(ownerList);
    }

    @Override
    public List<FcmToken> findByUserId(final Long userId) {
        return fcmTokenRepository.findByUserId(userId);
    }

    @Override
    public FcmToken findByUserIdAndStoreId(final Long storeId, final Long userId) {
        return fcmTokenRepository.findByStoreIdAndUserId(storeId, userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    public List<FcmTokenDTO> findByUsers(final Set<User> users) {
        List<Long> userIds = users.stream()
                .map(User::getId)
                .toList();

        return fcmTokenRepository.findWithAllByUsersIdsAndStoreIsNull(userIds);
    }
}
