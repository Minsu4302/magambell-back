package com.magambell.server.notification.app.port.out;

import com.magambell.server.notification.domain.entity.FcmToken;
import com.magambell.server.user.domain.entity.User;

public interface NotificationCommandPort {
    void save(FcmToken fcmToken);

    void removeToken(Long fcmTokenId);

    void deleteUserAndStoreIsNull(User user);

    void delete(FcmToken fcmToken);

    long deleteByStoreIdAndUserId(Long storeId, Long userId);

    long deleteByStoreIdAndToken(Long storeId, String token);
}
