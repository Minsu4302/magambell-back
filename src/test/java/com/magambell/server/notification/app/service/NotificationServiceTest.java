package com.magambell.server.notification.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.magambell.server.auth.domain.ProviderType;
import com.magambell.server.goods.domain.repository.GoodsRepository;
import com.magambell.server.notification.app.port.in.request.SaveFcmTokenServiceRequest;
import com.magambell.server.notification.app.port.in.request.DeleteStoreOpenFcmTokenServiceRequest;
import com.magambell.server.notification.app.port.in.request.SaveStoreOpenFcmTokenServiceRequest;
import com.magambell.server.notification.adapter.in.web.CheckStoreOpenServiceRequest;
import com.magambell.server.notification.domain.entity.FcmToken;
import com.magambell.server.notification.domain.repository.FcmTokenRepository;
import com.magambell.server.stock.domain.repository.StockHistoryRepository;
import com.magambell.server.stock.domain.repository.StockRepository;
import com.magambell.server.store.app.port.in.dto.RegisterStoreDTO;
import com.magambell.server.store.domain.enums.Approved;
import com.magambell.server.store.domain.enums.Bank;
import com.magambell.server.store.domain.entity.Store;
import com.magambell.server.store.domain.repository.StoreRepository;
import com.magambell.server.user.app.port.in.dto.UserSocialAccountDTO;
import com.magambell.server.user.domain.enums.UserRole;
import com.magambell.server.user.domain.entity.User;
import com.magambell.server.user.domain.repository.UserRepository;
import com.magambell.server.user.domain.repository.UserSocialAccountRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class NotificationServiceTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private FcmTokenRepository fcmTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSocialAccountRepository userSocialAccountRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private GoodsRepository goodsRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockHistoryRepository stockHistoryRepository;

    private User user;
    private Store store;

    @BeforeEach
    void setUp() {
        UserSocialAccountDTO userSocialAccountDTO = new UserSocialAccountDTO("test@test.com", "테스트이름", "닉네임",
                "01012341234",
                ProviderType.KAKAO,
                "testId", UserRole.OWNER);
        user = userSocialAccountDTO.toUser();
        user.addUserSocialAccount(userSocialAccountDTO.toUserSocialAccount());

        RegisterStoreDTO registerStoreDTO = new RegisterStoreDTO(
                "테스트 매장",
                "서울 강서구 테스트 211",
                1238.123213,
                5457.123213,
                "대표이름",
                "01012345678",
                "123491923",
                Bank.KB국민,
                "102391485",
                List.of(),
                Approved.APPROVED,
                user,
                null,
                "주차장"
        );
        store = registerStoreDTO.toEntity();
        user.addStore(store);
        user = userRepository.save(user);
    }

    @AfterEach
    void tearDown() {
        fcmTokenRepository.deleteAllInBatch();
        stockHistoryRepository.deleteAllInBatch();
        stockRepository.deleteAllInBatch();
        goodsRepository.deleteAllInBatch();
        storeRepository.deleteAllInBatch();
        userSocialAccountRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @DisplayName("매장 오픈 FCM 토큰을 저장한다.")
    @Test
    void saveStoreOpenToken() {
        // given
        SaveStoreOpenFcmTokenServiceRequest request = new SaveStoreOpenFcmTokenServiceRequest(store.getId(),
                "testToken", user.getId());

        // when
        notificationService.saveStoreOpenToken(request);

        // then
        List<FcmToken> fcmTokenList = fcmTokenRepository.findAll();
        assertThat(fcmTokenList.size()).isEqualTo(1);
        assertThat(fcmTokenList.get(0).getToken()).isEqualTo("testToken");
    }

    @DisplayName("FCM용 토큰을 저장한다.")
    @Test
    void saveToken() {
        // given
        SaveFcmTokenServiceRequest request = new SaveFcmTokenServiceRequest("testToken", user.getId());

        // when
        notificationService.saveToken(request);

        // then
        List<FcmToken> fcmTokenList = fcmTokenRepository.findAll();
        assertThat(fcmTokenList.size()).isEqualTo(1);
        assertThat(fcmTokenList.get(0).getToken()).isEqualTo("testToken");
    }

    @DisplayName("구독 여부가 false여도 동일 디바이스 토큰의 매장 구독은 취소된다.")
    @Test
    void deleteStoreOpenTokenWhenSubscribedFalseButSameDeviceTokenExists() {
        // given
        String sharedToken = "sharedDeviceToken";
        notificationService.saveToken(new SaveFcmTokenServiceRequest(sharedToken, user.getId()));

        User anotherUser = createAndSaveUser("other@test.com", "otherSocialId", "다른닉네임", "01056781234");
        notificationService.saveStoreOpenToken(
            new SaveStoreOpenFcmTokenServiceRequest(store.getId(), sharedToken, anotherUser.getId()));

        boolean subscribed = notificationService.checkUserStoreOpen(
            new CheckStoreOpenServiceRequest(store.getId(), user.getId()));
        assertThat(subscribed).isFalse();

        // when
        notificationService.deleteStoreOpenToken(
            new DeleteStoreOpenFcmTokenServiceRequest(store.getId(), user.getId()));

        // then
        long remainStoreOpenSubscriptions = fcmTokenRepository.findAll().stream()
            .filter(token -> token.getStore() != null && token.getStore().getId().equals(store.getId()))
            .count();

        assertThat(remainStoreOpenSubscriptions).isZero();
    }

    @DisplayName("매장 오픈 알림 신청자 수를 집계한다.")
    @Test
    void getStoreOpenSubscriberCount() {
        // given
        notificationService.saveStoreOpenToken(
                new SaveStoreOpenFcmTokenServiceRequest(store.getId(), "token-1", user.getId()));

        User anotherUser = createAndSaveUser("count@test.com", "countSocialId", "카운트닉", "01011112222");
        notificationService.saveStoreOpenToken(
                new SaveStoreOpenFcmTokenServiceRequest(store.getId(), "token-2", anotherUser.getId()));

        // when
        long subscriberCount = notificationService.getStoreOpenSubscriberCount(store.getId());

        // then
        assertThat(subscriberCount).isEqualTo(2L);
    }

    @DisplayName("매장 오픈 알림 신청자 수는 ACTIVE 여부와 관계없이 token row 기준으로 집계한다.")
    @Test
    void getStoreOpenSubscriberCountIncludesWithdrawnUser() {
        // given
        notificationService.saveStoreOpenToken(
                new SaveStoreOpenFcmTokenServiceRequest(store.getId(), "token-1", user.getId()));

        User withdrawnUser = createAndSaveUser("withdrawn@test.com", "withdrawnSocialId", "탈퇴닉", "01033334444");
        notificationService.saveStoreOpenToken(
                new SaveStoreOpenFcmTokenServiceRequest(store.getId(), "token-2", withdrawnUser.getId()));

        withdrawnUser.withdraw();
        userRepository.save(withdrawnUser);

        // when
        long subscriberCount = notificationService.getStoreOpenSubscriberCount(store.getId());

        // then
        assertThat(subscriberCount).isEqualTo(2L);
    }

    private User createAndSaveUser(final String email, final String socialId, final String nickName,
            final String phoneNumber) {
        UserSocialAccountDTO userSocialAccountDTO = new UserSocialAccountDTO(
            email,
            "테스트이름",
            nickName,
            phoneNumber,
            ProviderType.KAKAO,
            socialId,
            UserRole.OWNER);

        User anotherUser = userSocialAccountDTO.toUser();
        anotherUser.addUserSocialAccount(userSocialAccountDTO.toUserSocialAccount());
        return userRepository.save(anotherUser);
    }
}
