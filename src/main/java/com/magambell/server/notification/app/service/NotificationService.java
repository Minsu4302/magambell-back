package com.magambell.server.notification.app.service;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.magambell.server.common.enums.ErrorCode;
import com.magambell.server.common.exception.DuplicateException;
import com.magambell.server.notification.adapter.in.web.CheckStoreOpenServiceRequest;
import com.magambell.server.notification.app.port.in.NotificationUseCase;
import com.magambell.server.notification.app.port.in.request.DeleteStoreOpenFcmTokenServiceRequest;
import com.magambell.server.notification.app.port.in.request.NotifyStoreOpenRequest;
import com.magambell.server.notification.app.port.in.request.SaveFcmTokenServiceRequest;
import com.magambell.server.notification.app.port.in.request.SaveStoreOpenFcmTokenServiceRequest;
import com.magambell.server.notification.app.port.out.NotificationCommandPort;
import com.magambell.server.notification.app.port.out.NotificationQueryPort;
import com.magambell.server.notification.app.port.out.dto.FcmTokenDTO;
import com.magambell.server.notification.domain.entity.FcmToken;
import com.magambell.server.notification.infra.FirebaseNotificationSender;
import com.magambell.server.order.app.port.out.OrderQueryPort;
import com.magambell.server.order.domain.entity.Order;
import com.magambell.server.order.domain.entity.OrderGoods;
import com.magambell.server.store.app.port.out.StoreQueryPort;
import com.magambell.server.store.domain.entity.Store;
import com.magambell.server.user.app.port.out.UserQueryPort;
import com.magambell.server.user.domain.entity.User;
import com.magambell.server.user.domain.enums.UserRole;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class NotificationService implements NotificationUseCase {

    private static final String TYPE_KEY = "type";
    private static final String STORE_ID_KEY = "storeId";
    private static final String ORDER_TYPE = "ORDER";
    private static final String STORE_OPEN_TYPE = "STORE_OPEN";

    private static final String UNIFIED_NOTIFICATION_TITLE = "바이트픽";
    private static final String STORE_APPROVED_TITLE = "🎉 바이트픽 가입 심사 완료!";
    private static final String STORE_APPROVED_BODY = "지금 바로 바이트백 판매를 시작해보세요 ✅";
    private static final String NEW_SIGNUP_REVIEW_BODY = "새 가입 매장을 검토해주세요!";

    private final NotificationCommandPort notificationCommandPort;
    private final NotificationQueryPort notificationQueryPort;
    private final FirebaseNotificationSender firebaseNotificationSender;
    private final StoreQueryPort storeQueryPort;
    private final UserQueryPort userQueryPort;
    private final OrderQueryPort orderQueryPort;

    @Transactional
    @Override
    public void saveStoreOpenToken(final SaveStoreOpenFcmTokenServiceRequest request) {
        log.info("매장 오픈 알림 토큰 저장 시작 - storeId: {}, userId: {}", request.storeId(), request.userId());
        
        User user = userQueryPort.findById(request.userId());
        Store store = storeQueryPort.findById(request.storeId());

        if (notificationQueryPort.existsByUserAndStore(user, store)) {
            log.warn("매장 오픈 알림 중복 구독 시도 - storeId: {}, userId: {}", request.storeId(), request.userId());
            throw new DuplicateException(ErrorCode.DUPLICATE_NOTIFICATION_STORE);
        }

        FcmToken fcmToken = FcmToken.create(request.fcmToken(), user, store);
        notificationCommandPort.save(fcmToken);
        log.info("매장 오픈 알림 토큰 저장 완료 - storeId: {}, userId: {}, tokenId: {}", request.storeId(), request.userId(), fcmToken.getId());
    }

    @Transactional
    @Override
    public void deleteStoreOpenToken(final DeleteStoreOpenFcmTokenServiceRequest request) {
        FcmToken fcmToken = notificationQueryPort.findByUserIdAndStoreId(request.storeId(), request.userId());
        notificationCommandPort.delete(fcmToken);
    }

    @Override
    public boolean checkUserStoreOpen(final CheckStoreOpenServiceRequest request) {
        User user = userQueryPort.findById(request.userId());
        Store store = storeQueryPort.findById(request.storeId());

        return notificationQueryPort.existsByUserAndStore(user, store);
    }

    @Transactional
    @Override
    public void saveToken(final SaveFcmTokenServiceRequest request) {
        User user = userQueryPort.findById(request.userId());

        try {
            notificationCommandPort.deleteUserAndStoreIsNull(user);
        } catch (Exception e) {
            log.warn("FCM 토큰 삭제 중 동시성 에러 (무시) - userId: {}", request.userId());
        }
        notificationCommandPort.save(FcmToken.create(request.fcmToken(), user));
    }

    @Override
    public void notifyApproveOrder(final User user, final LocalDateTime pickupTime) {
        FcmTokenDTO token = notificationQueryPort.findWithAllByUserIdAndStoreIsNull(user);
        if (token != null) {
            String message = "주문이 수락됐어요. [" + pickupTime.toLocalTime() + "]에 바이트백을 픽업 해주세요!";
            send(message, token, Map.of(TYPE_KEY, ORDER_TYPE));
        }
    }

    @Override
    public void notifyRejectOrder(final User user) {
        FcmTokenDTO token = notificationQueryPort.findWithAllByUserIdAndStoreIsNull(user);
        if (token != null) {
            String message = "주문이 거절됐어요.";
            send(message, token, Map.of(TYPE_KEY, ORDER_TYPE));
        }
    }

    @Override
    public void notifyPaidOrder(final Set<User> orderStoreOwnerList) {
        List<Long> ownerList = orderStoreOwnerList.stream()
                .map(User::getId).toList();
        log.info("주문 완료 알림 전송 시작 - 사장님 수: {}, ownerIds: {}", ownerList.size(), ownerList);

        List<FcmTokenDTO> tokens = notificationQueryPort.findWithAllByOwnerIdsAndStoreIsNull(ownerList);
        log.info("주문 완료 알림 FCM 토큰 조회 완료 - 토큰 수: {}", tokens.size());
        tokens.forEach(token -> {
            log.info("주문 완료 알림 전송 - ownerId: {}, message: 새 주문이 들어왔어요!", token.userId());
            send("새 주문이 들어왔어요!", token, Map.of(TYPE_KEY, ORDER_TYPE));
        });
        log.info("주문 완료 알림 전송 완료 - 총 {}명", tokens.size());
    }

    @Override
    public void testSendToken(final Long userId) {
        List<FcmToken> tokens = notificationQueryPort.findByUserId(userId);
        tokens.forEach(token -> send("FCM 테스트",
                new FcmTokenDTO(token.getId(), token.getToken(), token.getUser().getId(), "", "")));
    }


    @Override
    public void notifyStoreOpen(final NotifyStoreOpenRequest request) {
        List<FcmTokenDTO> tokens = notificationQueryPort.findWithAllByStoreId(request.store());
        log.info("오픈 알림 대상 사용자 수: {} - storeId: {}", tokens.size(), request.store().getId());

        tokens.forEach(token -> {
            String nickname = token.nickName();
            String storeName = token.storeName();
            String message = "[" + nickname + "]님이 기다리던 [" + storeName + "]의 바이트백 판매가 시작됐어요!";
            log.info("오픈 알림 전송 - userId: {}, message: {}", token.userId(), message);

            send(message, token, Map.of(
                    TYPE_KEY, STORE_OPEN_TYPE,
                    STORE_ID_KEY, String.valueOf(request.store().getId())
            ));
        });
        log.info("오픈 알림 전송 완료 - 총 {}명", tokens.size());
    }

    @Transactional
    @Override
    public void notifyPickup(final LocalDateTime pickupTime) {
        List<Order> orders = orderQueryPort.findOrdersToNotifyByPickupTime(pickupTime);

        Set<User> customers = orders.stream()
                .map(Order::getUser)
                .collect(Collectors.toSet());

        Set<User> owners = orders.stream()
                .flatMap(order -> order.getOrderStoreOwner().stream())
                .collect(Collectors.toSet());

        List<FcmTokenDTO> customerTokens = notificationQueryPort.findByUsers(customers);
        List<FcmTokenDTO> ownerTokens = notificationQueryPort.findByUsers(owners);

        Map<Long, FcmTokenDTO> tokenByUserId = Stream.concat(customerTokens.stream(), ownerTokens.stream())
                .collect(Collectors.toMap(
                        FcmTokenDTO::userId,
                        Function.identity(),
                        (t1, t2) -> t1
                ));

        orders.forEach(order -> {
            User customer = order.getUser();
            FcmTokenDTO customerToken = tokenByUserId.get(customer.getId());

            if (customerToken != null) {
                String storeNames = order.getOrderGoodsList().stream()
                        .map(og -> og.getGoods().getStore().getName())
                        .distinct()
                        .collect(Collectors.joining(", ")); // 여러 매장이라면 쉼표로

                String message = "[" + storeNames + "] 에서 바이트백을 픽업해주세요!";
                send(message, customerToken, Map.of(TYPE_KEY, ORDER_TYPE));
            }

            order.getOrderStoreOwner().forEach(owner -> {
                FcmTokenDTO ownerToken = tokenByUserId.get(owner.getId());
                if (ownerToken != null) {
                    Set<String> storesOwnedByThisOwner = order.getOrderGoodsList().stream()
                            .map(OrderGoods::getGoods)
                            .filter(goods -> goods.getStore().getUser().equals(owner))
                            .map(goods -> goods.getStore().getName())
                            .collect(Collectors.toSet());

                    storesOwnedByThisOwner.forEach(storeName -> {
                        String message = "[" + storeName + "]의 픽업 가능 시간이 시작되었습니다.";
                        send(message, ownerToken, Map.of(TYPE_KEY, ORDER_TYPE));
                    });
                }
            });

            order.markPickupNotificationSent();
        });
    }

    @Override
    public void notifyNewSignupStoreReview(final UserRole userRole) {
        if (userRole != UserRole.OWNER) {
            return;
        }

        List<Long> adminUserIds = userQueryPort.findActiveUserIdsByRole(UserRole.ADMIN);
        if (adminUserIds.isEmpty()) {
            log.info("신규 매장 가입 검토 알림 대상 관리자 없음");
            return;
        }

        List<FcmTokenDTO> tokens = notificationQueryPort.findWithAllByOwnerIdsAndStoreIsNull(adminUserIds);
        log.info("신규 매장 가입 검토 알림 전송 시작 - 관리자 수: {}, 토큰 수: {}", adminUserIds.size(), tokens.size());

        tokens.forEach(token -> send(UNIFIED_NOTIFICATION_TITLE, NEW_SIGNUP_REVIEW_BODY, token));
    }

    @Override
    public void notifyStoreApproved(final User user) {
        FcmTokenDTO token = notificationQueryPort.findWithAllByUserIdAndStoreIsNull(user);
        if (token == null) {
            log.info("매장 승인 완료 알림 대상 토큰 없음 - userId: {}", user.getId());
            return;
        }

        send(STORE_APPROVED_TITLE, STORE_APPROVED_BODY, token);
    }

    private void send(final String message, final FcmTokenDTO token) {
        send(UNIFIED_NOTIFICATION_TITLE, message, token, Map.of());
    }

    private void send(final String title, final String message, final FcmTokenDTO token) {
        send(title, message, token, Map.of());
    }

    private void send(final String message, final FcmTokenDTO token, final Map<String, String> data) {
        send(UNIFIED_NOTIFICATION_TITLE, message, token, data);
    }

    private void send(final String title, final String message, final FcmTokenDTO token,
            final Map<String, String> data) {
        try {
            firebaseNotificationSender.send(token.token(), title, message, data);
        } catch (FirebaseMessagingException e) {
            fcmFail(e, token);
        }
    }

    private void fcmFail(final FirebaseMessagingException e, final FcmTokenDTO token) {
        log.warn("FCM 알림 전송 실패. tokenId={}, reason={}", token.fcmTokenId(), e.getMessage());
        try {
            notificationCommandPort.removeToken(token.fcmTokenId());
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException | org.hibernate.StaleObjectStateException ex) {
            // 다른 스레드가 이미 삭제한 경우 - 정상 동작으로 간주
            log.debug("FCM 토큰이 다른 트랜잭션에서 이미 삭제됨. tokenId={}", token.fcmTokenId());
        } catch (Exception ex) {
            // 기타 예외는 로그만 남기고 계속 진행
            log.warn("FCM 토큰 삭제 실패 (무시). tokenId={}, error={}", token.fcmTokenId(), ex.getMessage());
        }
    }
}
