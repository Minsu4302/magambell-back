package com.magambell.server.admin.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.magambell.server.admin.adapter.out.persistence.AdminEditStoreResponse;
import com.magambell.server.admin.app.port.in.request.AdminEditStoreServiceRequest;
import com.magambell.server.admin.app.port.out.AdminCommandPort;
import com.magambell.server.admin.app.port.out.response.AdminStatsResponse;
import com.magambell.server.auth.domain.ProviderType;
import com.magambell.server.goods.adapter.in.web.GoodsImagesRegister;
import com.magambell.server.goods.app.port.in.dto.RegisterGoodsDTO;
import com.magambell.server.goods.app.port.out.response.EditGoodsImageResponseDTO;
import com.magambell.server.goods.app.port.out.response.GoodsPreSignedUrlImage;
import com.magambell.server.goods.domain.entity.Goods;
import com.magambell.server.goods.domain.enums.SaleStatus;
import com.magambell.server.goods.domain.repository.GoodsRepository;
import com.magambell.server.stock.domain.entity.Stock;
import com.magambell.server.stock.domain.repository.StockHistoryRepository;
import com.magambell.server.stock.domain.repository.StockRepository;
import com.magambell.server.store.adapter.in.web.StoreImagesRegister;
import com.magambell.server.store.adapter.out.persistence.StoreAdminListResponse;
import com.magambell.server.store.app.port.in.dto.RegisterStoreDTO;
import com.magambell.server.store.app.port.out.response.EditStoreImageResponseDTO;
import com.magambell.server.store.app.port.out.response.StorePreSignedUrlImage;
import com.magambell.server.store.domain.entity.Store;
import com.magambell.server.store.domain.enums.Approved;
import com.magambell.server.store.domain.enums.Bank;
import com.magambell.server.store.domain.repository.StoreRepository;
import com.magambell.server.user.app.port.in.dto.UserSocialAccountDTO;
import com.magambell.server.user.domain.entity.User;
import com.magambell.server.user.domain.enums.UserRole;
import com.magambell.server.user.domain.repository.UserRepository;
import com.magambell.server.user.domain.repository.UserSocialAccountRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class AdminServiceTest {

    @Autowired private AdminService adminService;
    @MockBean  private AdminCommandPort adminCommandPort;

    @Autowired private UserRepository userRepository;
    @Autowired private UserSocialAccountRepository userSocialAccountRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private GoodsRepository goodsRepository;
    @Autowired private StockRepository stockRepository;
    @Autowired private StockHistoryRepository stockHistoryRepository;

    private Store store;

    @BeforeEach
    void setUp() {
        UserSocialAccountDTO dto = new UserSocialAccountDTO(
                "admin-svc-test@test.com", "오너이름", "오너닉네임", "01011112222",
                ProviderType.KAKAO, "admin-svc-test-id", UserRole.OWNER);
        User owner = dto.toUser();
        owner.addUserSocialAccount(dto.toUserSocialAccount());

        store = new RegisterStoreDTO(
                "관리자테스트매장", "서울 강서구 테스트 1", 37.5665, 127.0,
                "대표이름", "01012345678", "123456789", Bank.KB국민, "123456",
                List.of(), Approved.APPROVED, owner, "설명", "주차가능"
        ).toEntity();
        owner.addStore(store);

        RegisterGoodsDTO goodsDTO = new RegisterGoodsDTO(
                "상품명",
                LocalDateTime.of(2025, 1, 1, 9, 0),
                LocalDateTime.of(2025, 1, 1, 18, 0),
                10, 15000, 10, 13500,
                store, List.of()
        );
        Goods goods = goodsDTO.toGoods();
        store.addGoods(goods);
        goods.addStock(Stock.create(10));

        userRepository.save(owner);
    }

    @AfterEach
    void tearDown() {
        stockHistoryRepository.deleteAllInBatch();
        stockRepository.deleteAllInBatch();
        goodsRepository.deleteAllInBatch();
        storeRepository.deleteAllInBatch();
        userSocialAccountRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("전체 통계(사용자 수, 매장 수)를 조회한다 — countActiveUsers는 CUSTOMER만 집계")
    void getStats_returnsUserAndStoreCount() {
        UserSocialAccountDTO customerDto = new UserSocialAccountDTO(
                "customer-stats@test.com", "고객", "customerNick", "01055556666",
                ProviderType.KAKAO, "customer-stats-id", UserRole.CUSTOMER);
        User customer = customerDto.toUser();
        customer.addUserSocialAccount(customerDto.toUserSocialAccount());
        userRepository.save(customer);

        AdminStatsResponse stats = adminService.getStats();
        assertThat(stats.totalUserCount()).isEqualTo(1L);
        assertThat(stats.totalStoreCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("storeImages가 null이면 이미지 수정을 건너뛰고 빈 결과를 반환한다")
    void editStore_nullStoreImages_skipsImageUpdate() {
        AdminEditStoreServiceRequest request = new AdminEditStoreServiceRequest(
                "수정매장명", "서울 강서구 테스트 1", 37.5665, 127.0,
                "대표이름", "01012345678", "123456789", Bank.KB국민, "123456",
                "설명", "주차가능",
                null,
                LocalDateTime.of(2025, 1, 1, 9, 0), LocalDateTime.of(2025, 1, 1, 18, 0),
                15000, 13500, 10, 10, SaleStatus.OFF,
                null
        );

        AdminEditStoreResponse result = adminService.editStore(store.getId(), request);

        assertThat(result.getStorePreSignedUrlImages()).isEmpty();
        assertThat(result.getGoodsPreSignedUrlImages()).isEmpty();
    }

    @Test
    @DisplayName("storeImages가 있으면 이미지 수정 후 Pre-signed URL 목록을 반환한다")
    void editStore_withStoreImages_returnsPreSignedUrls() {
        when(adminCommandPort.editStoreImages(any(), anyList()))
                .thenReturn(new EditStoreImageResponseDTO(
                        store.getId(),
                        List.of(new StorePreSignedUrlImage(0, "https://s3.test/store.jpg"))));

        AdminEditStoreServiceRequest request = new AdminEditStoreServiceRequest(
                "수정매장명", "서울 강서구 테스트 1", 37.5665, 127.0,
                "대표이름", "01012345678", "123456789", Bank.KB국민, "123456",
                "설명", "주차가능",
                List.of(new StoreImagesRegister(0, "store-img-key")),
                LocalDateTime.of(2025, 1, 1, 9, 0), LocalDateTime.of(2025, 1, 1, 18, 0),
                15000, 13500, 10, 10, SaleStatus.OFF,
                null
        );

        AdminEditStoreResponse result = adminService.editStore(store.getId(), request);

        assertThat(result.getStorePreSignedUrlImages()).hasSize(1);
    }

    @Test
    @DisplayName("goodsImages가 있으면 상품 이미지를 수정하고 goodsName을 이미지에서 가져온다")
    void editStore_withGoodsImages_updatesGoodsNameFromImage() {
        when(adminCommandPort.editGoodsImages(any(), anyList()))
                .thenReturn(new EditGoodsImageResponseDTO(
                        store.getId(),
                        List.of(new GoodsPreSignedUrlImage(0, "https://s3.test/goods.jpg"))));

        AdminEditStoreServiceRequest request = new AdminEditStoreServiceRequest(
                "수정매장명", "서울 강서구 테스트 1", 37.5665, 127.0,
                "대표이름", "01012345678", "123456789", Bank.KB국민, "123456",
                "설명", "주차가능",
                null,
                LocalDateTime.of(2025, 1, 1, 9, 0), LocalDateTime.of(2025, 1, 1, 18, 0),
                15000, 13500, 10, 10, SaleStatus.OFF,
                List.of(new GoodsImagesRegister(0, "goods-key", "https://s3.test/goods.jpg", "새상품명"))
        );

        AdminEditStoreResponse result = adminService.editStore(store.getId(), request);

        assertThat(result.getGoodsPreSignedUrlImages()).hasSize(1);
    }

    @Test
    @DisplayName("goodsImages가 빈 리스트이면 상품 이미지 수정을 건너뛴다")
    void editStore_withEmptyGoodsImages_skipsGoodsImageUpdate() {
        AdminEditStoreServiceRequest request = new AdminEditStoreServiceRequest(
                "수정매장명", "서울 강서구 테스트 1", 37.5665, 127.0,
                "대표이름", "01012345678", "123456789", Bank.KB국민, "123456",
                "설명", "주차가능",
                null,
                LocalDateTime.of(2025, 1, 1, 9, 0), LocalDateTime.of(2025, 1, 1, 18, 0),
                15000, 13500, 10, 10, SaleStatus.OFF,
                List.of()
        );

        AdminEditStoreResponse result = adminService.editStore(store.getId(), request);

        assertThat(result.getGoodsPreSignedUrlImages()).isEmpty();
    }

    @Test
    @DisplayName("goodsImages가 있지만 goodsName이 없으면 기존 상품명을 유지한다")
    void editStore_goodsImagesWithoutGoodsName_keepsExistingName() {
        when(adminCommandPort.editGoodsImages(any(), anyList()))
                .thenReturn(new EditGoodsImageResponseDTO(store.getId(), List.of()));

        AdminEditStoreServiceRequest request = new AdminEditStoreServiceRequest(
                "수정매장명", "서울 강서구 테스트 1", 37.5665, 127.0,
                "대표이름", "01012345678", "123456789", Bank.KB국민, "123456",
                "설명", "주차가능",
                null,
                LocalDateTime.of(2025, 1, 1, 9, 0), LocalDateTime.of(2025, 1, 1, 18, 0),
                15000, 13500, 10, 10, SaleStatus.OFF,
                List.of(new GoodsImagesRegister(0, "goods-key", "https://s3.test/goods.jpg", ""))
        );

        AdminEditStoreResponse result = adminService.editStore(store.getId(), request);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("승인된 매장 목록을 조회한다")
    void getAllApprovedStores_returnsApprovedStores() {
        StoreAdminListResponse result = adminService.getAllApprovedStores();
        assertThat(result.storeAdminListDTOs()).hasSize(1);
    }
}
