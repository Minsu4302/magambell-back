package com.magambell.server.notification.adapter.out.persistence;

import com.magambell.server.common.annotation.Adapter;
import com.magambell.server.notification.app.port.out.NotificationCommandPort;
import com.magambell.server.notification.domain.entity.FcmToken;
import com.magambell.server.notification.domain.repository.FcmTokenRepository;
import com.magambell.server.user.domain.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Adapter
public class NotificationCommandAdapter implements NotificationCommandPort {

    private final FcmTokenRepository fcmTokenRepository;

    @Override
    public void save(final FcmToken fcmToken) {
        fcmTokenRepository.save(fcmToken);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void removeToken(final Long fcmTokenId) {
        fcmTokenRepository.deleteById(fcmTokenId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void deleteUserAndStoreIsNull(final User user) {
        fcmTokenRepository.deleteByUserIdAndStoreIsNull(user.getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void delete(final FcmToken fcmToken) {
        fcmTokenRepository.delete(fcmToken);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public long deleteByStoreIdAndUserId(final Long storeId, final Long userId) {
        return fcmTokenRepository.deleteByStoreIdAndUserId(storeId, userId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public long deleteByStoreIdAndToken(final Long storeId, final String token) {
        return fcmTokenRepository.deleteByStoreIdAndToken(storeId, token);
    }
}
