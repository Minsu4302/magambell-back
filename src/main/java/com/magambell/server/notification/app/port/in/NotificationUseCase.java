package com.magambell.server.notification.app.port.in;

import com.magambell.server.notification.adapter.in.web.CheckStoreOpenServiceRequest;
import com.magambell.server.notification.app.port.in.request.DeleteStoreOpenFcmTokenServiceRequest;
import com.magambell.server.notification.app.port.in.request.NotifyStoreOpenRequest;
import com.magambell.server.notification.app.port.in.request.SaveFcmTokenServiceRequest;
import com.magambell.server.notification.app.port.in.request.SaveStoreOpenFcmTokenServiceRequest;
import com.magambell.server.user.domain.entity.User;
import com.magambell.server.user.domain.enums.UserRole;
import java.time.LocalDateTime;
import java.util.Set;

public interface NotificationUseCase {
    void saveStoreOpenToken(SaveStoreOpenFcmTokenServiceRequest request);

    void notifyStoreOpen(NotifyStoreOpenRequest request);

    void saveToken(SaveFcmTokenServiceRequest request);

    void notifyApproveOrder(User user, LocalDateTime pickupTime);

    void notifyRejectOrder(User user);

    void notifyPaidOrder(Set<User> orderStoreOwnerList);

    void testSendToken(Long userId);

    void deleteStoreOpenToken(DeleteStoreOpenFcmTokenServiceRequest request);

    boolean checkUserStoreOpen(CheckStoreOpenServiceRequest request);

    void notifyPickup(LocalDateTime pickupTime);

    void notifyNewSignupStoreReview(UserRole userRole);

    void notifyStoreApproved(User user);
}
