package com.magambell.server.store.app.service;

import static com.magambell.server.goods.domain.enums.SaleStatus.ON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;

import com.magambell.server.auth.domain.ProviderType;
import com.magambell.server.common.s3.S3Client;
import com.magambell.server.common.s3.dto.ImageRegister;
import com.magambell.server.goods.adapter.in.web.GoodsImagesRegister;
import com.magambell.server.goods.app.port.in.dto.RegisterGoodsDTO;
import com.magambell.server.goods.domain.entity.Goods;
import com.magambell.server.goods.domain.repository.GoodsRepository;
import com.magambell.server.stock.domain.entity.Stock;
import com.magambell.server.stock.domain.repository.StockHistoryRepository;
import com.magambell.server.stock.domain.repository.StockRepository;
import com.magambell.server.store.adapter.in.web.StoreImagesRegister;
import com.magambell.server.store.adapter.out.persistence.StoreAdminListResponse;
import com.magambell.server.store.adapter.out.persistence.StoreDetailResponse;
import com.magambell.server.store.adapter.out.persistence.StoreImagesResponse;
import com.magambell.server.store.adapter.out.persistence.StoreListResponse;
import com.magambell.server.store.app.port.in.dto.RegisterStoreDTO;
import com.magambell.server.store.app.port.in.request.CloseStoreListServiceRequest;
import com.magambell.server.store.app.port.in.request.EditStoreImageServiceRequest;
import com.magambell.server.store.app.port.in.request.MapStoreListServiceRequest;
import com.magambell.server.store.app.port.in.request.RegisterStoreServiceRequest;
import com.magambell.server.store.app.port.in.request.SearchStoreListServiceRequest;
import com.magambell.server.store.app.port.in.request.WaitingStoreListServiceRequest;
import com.magambell.server.store.app.port.out.response.OwnerStoreDetailDTO;
import com.magambell.server.store.app.port.out.response.StoreListDTOResponse;
import com.magambell.server.store.domain.enums.Approved;
import com.magambell.server.store.domain.enums.Bank;
import com.magambell.server.store.domain.enums.SearchSortType;
import com.magambell.server.store.domain.entity.Store;
import com.magambell.server.store.domain.entity.StoreImage;
import com.magambell.server.store.domain.repository.StoreImageRepository;
import com.magambell.server.store.domain.repository.StoreRepository;
import com.magambell.server.user.app.port.in.dto.UserSocialAccountDTO;
import com.magambell.server.user.domain.enums.UserRole;
import com.magambell.server.user.domain.entity.User;
import com.magambell.server.user.domain.repository.UserRepository;
import com.magambell.server.user.domain.repository.UserSocialAccountRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import com.magambell.server.common.exception.DuplicateException;
import com.magambell.server.common.exception.InvalidRequestException;
import com.magambell.server.store.app.port.in.request.StoreSearchServiceRequest;
import com.magambell.server.store.app.port.out.response.StoreSearchResponse;

@ActiveProfiles("test")
@SpringBootTest
class StoreServiceTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserSocialAccountRepository userSocialAccountRepository;
    @Autowired
    private StoreService storeService;
    @Autowired
    private StoreRepository storeRepository;
    @Autowired
    private StoreImageRepository storeImageRepository;
    @Autowired
    private GoodsRepository goodsRepository;
    @Autowired
    private StockHistoryRepository stockHistoryRepository;
    @Autowired
    private StockRepository stockRepository;
    @MockBean
    private S3Client s3Client;
    private User user;

    @BeforeEach
    void setUp() {
        UserSocialAccountDTO userSocialAccountDTO = new UserSocialAccountDTO("test@test.com", "테스트이름", "닉네임",
                "01012341234",
                ProviderType.KAKAO,
                "testId", UserRole.OWNER);
        user = userSocialAccountDTO.toUser();
        user.addUserSocialAccount(userSocialAccountDTO.toUserSocialAccount());
        userRepository.save(user);
    }

    @AfterEach
    void tearDown() {
        stockHistoryRepository.deleteAllInBatch();
        stockRepository.deleteAllInBatch();
        goodsRepository.deleteAllInBatch();
        storeImageRepository.deleteAllInBatch();
        storeRepository.deleteAllInBatch();
        userSocialAccountRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @DisplayName("매장을 등록한다.")
    @Test
    void registerStore() {
        // given
        RegisterStoreServiceRequest request = new RegisterStoreServiceRequest(
                "테스트 매장",
                "서울 강서구 테스트 211",
                1238.123213,
                5457.123213,
                "대표이름",
                "01012345678",
                "123491923",
                Bank.KB국민,
                "102391485",
                List.of(new StoreImagesRegister(0, "test")),
                null,
                "주차장"
        );

        // when
        storeService.registerStore(request, user.getId());

        // then
        Store store = storeRepository.findAll().get(0);
        assertThat(store).extracting("name", "address", "ownerPhone")
                .contains(
                        "테스트 매장",
                        "서울 강서구 테스트 211",
                        "01012345678");
    }

    @DisplayName("매장 리스트를 가져온다.")
    @Test
    void getStoreList() {
        // given
        SearchStoreListServiceRequest request = new SearchStoreListServiceRequest(
                37.5665, 37.5665, "", SearchSortType.RECENT_DESC, false, 1, 30
        );

        List<Store> storeList = IntStream.range(1, 31)
                .mapToObj(this::createStore)
                .toList();

        storeRepository.saveAll(storeList);

        // when
        StoreListResponse storeListResponse = storeService.getStoreList(request);

        // then
        assertThat(storeListResponse.storeListDTOResponses()).hasSize(30);
        StoreListDTOResponse store = storeListResponse.storeListDTOResponses().get(0);
        assertThat(store.storeName()).contains("테스트 매장");
        assertThat(store.startTime()).isEqualTo(LocalDateTime.of(2025, 1, 1, 9, 0));
        assertThat(store.endTime()).isEqualTo(LocalDateTime.of(2025, 1, 1, 18, 0));
        assertThat(store.originPrice()).isEqualTo(10000);
        assertThat(store.discount()).isEqualTo(10);
        assertThat(store.salePrice()).isEqualTo(9000);
    }

    @DisplayName("매장 상세 정보를 조회한다")
    @Test
    void getStoreDetail() {
        // given
        Store store = createStore(1);
        storeRepository.save(store);

        // when
        StoreDetailResponse result = storeService.getStoreDetail(store.getId());

        // then
        assertThat(result).isNotNull();
        assertThat(result.storeId()).isEqualTo(String.valueOf(store.getId()));
        assertThat(result.storeName()).isEqualTo("테스트 매장1");
        assertThat(result.salePrice()).isEqualTo(9000);
        assertThat(result.images()).isEmpty();
    }

    @DisplayName("사장님 매장 상세 정보를 조회한다")
    @Test
    void getOwnerStoreInfo() {
        // given
        Store store = createStore(1); // storeId = 1L
        storeRepository.save(store);

        // when
        OwnerStoreDetailDTO ownerStoreInfo = storeService.getOwnerStoreInfo(user.getId());

        // then
        assertThat(ownerStoreInfo).isNotNull();
        assertThat(ownerStoreInfo.storeName()).isEqualTo("테스트 매장1");
        assertThat(ownerStoreInfo.goodsList().get(0).salePrice()).isEqualTo(9000);
    }

    @DisplayName("내 주변 매장 리스트")
    @Test
    void getCloseStoreList() {
        // given
        CloseStoreListServiceRequest request = new CloseStoreListServiceRequest(37.5665, 37.5665);

        List<Store> storeList = IntStream.range(1, 31)
                .mapToObj(this::createStore)
                .toList();

        storeRepository.saveAll(storeList);

        // when
        StoreListResponse closeStoreList = storeService.getCloseStoreList(request);

        // then
        assertThat(closeStoreList.storeListDTOResponses()).hasSize(30);
    }

        @DisplayName("지도 범위 매장 리스트")
        @Test
        void getMapStoreList() {
                // given
                MapStoreListServiceRequest request = new MapStoreListServiceRequest(
                        37.3000, 127.1000,
                        37.3400, 127.1400,
                        true
                );

                List<Store> storeList = IntStream.range(1, 31)
                        .mapToObj(this::createMapAvailableStore)
                                .toList();

                storeList = new java.util.ArrayList<>(storeList);
                storeList.add(createMapUnavailableStore(99));

                storeRepository.saveAll(storeList);

                // when
                StoreListResponse mapStoreList = storeService.getMapStoreList(request);

                // then
                assertThat(mapStoreList.storeListDTOResponses()).hasSize(30);
        }

    @DisplayName("승인 대기중인 매장 리스트를 가져온다.")
    @Test
    void getWaitingStoreList() {
        // given
        WaitingStoreListServiceRequest request = new WaitingStoreListServiceRequest(1, 10);

        List<Store> storeList = IntStream.range(1, 31)
                .mapToObj(this::createStore)
                .toList();

        storeRepository.saveAll(storeList);

        // when
        StoreAdminListResponse storeListResponse = storeService.getWaitingStoreList(request);

        // then
        assertThat(storeListResponse.storeAdminListDTOs()).hasSize(0);
    }

    @DisplayName("사장님 매장 이미지 리스트 가져오기")
    @Test
    void getStoreImageList() {
        // given
        Store store = createStore(1);
        storeRepository.save(store);

        // when
        StoreImagesResponse storeImageList = storeService.getStoreImageList(user.getId(), store.getId());

        // then
        assertThat(storeImageList.storePreSignedUrlImages()).hasSize(0);
    }

    @DisplayName("사장님 매장 이미지 변경하기")
    @Test
    void editStoreImage() {
        // given
        Store store = createStore(1);
        storeRepository.save(store);

        EditStoreImageServiceRequest editStoreImageServiceRequest = new EditStoreImageServiceRequest(user.getId(),
                store.getId(), List.of());

        // when
        doNothing().when(s3Client)
                .deleteObjectS3("");
        StoreImagesResponse storeImageList = storeService.editStoreImage(editStoreImageServiceRequest);

        // then
        assertThat(storeImageList.storePreSignedUrlImages()).hasSize(0);
    }

    @DisplayName("이미 매장이 있는 사용자가 매장을 또 등록하면 예외가 발생한다")
    @Test
    void registerStore_throwsWhenDuplicateStore() {
        Long userId = user.getId();
        RegisterStoreServiceRequest request = new RegisterStoreServiceRequest(
                "테스트 매장",
                "서울 강서구 테스트 211",
                37.5665, 37.5665,
                "대표이름", "01012345678", "123491923",
                Bank.KB국민, "102391485",
                List.of(new StoreImagesRegister(0, "test")),
                null, "주차장"
        );

        storeService.registerStore(request, userId);

        assertThatThrownBy(() -> storeService.registerStore(request, userId))
                .isInstanceOf(DuplicateException.class);
    }

    @DisplayName("ADMIN 권한 사용자는 타인의 매장 이미지 목록을 조회할 수 있다")
    @Test
    void getStoreImageList_adminUserCanAccess() {
        Store store = createStore(1);
        storeRepository.save(store);

        UserSocialAccountDTO adminDto = new UserSocialAccountDTO(
                "admin-img@test.com", "관리자", "adminNick", "01099998888",
                ProviderType.KAKAO, "adminUserId", UserRole.ADMIN);
        User adminUser = adminDto.toUser();
        adminUser.addUserSocialAccount(adminDto.toUserSocialAccount());
        userRepository.save(adminUser);

        StoreImagesResponse result = storeService.getStoreImageList(adminUser.getId(), store.getId());

        assertThat(result).isNotNull();
    }

    @DisplayName("매장 소유자가 아닌 사용자가 매장 이미지 조회 시 예외가 발생한다")
    @Test
    void getStoreImageList_throwsWhenNotOwner() {
        Store store = createStore(1);
        storeRepository.save(store);

        UserSocialAccountDTO otherDto = new UserSocialAccountDTO(
                "other-img@test.com", "다른사용자", "otherNick", "01033334444",
                ProviderType.KAKAO, "otherUserId", UserRole.OWNER);
        User otherUser = otherDto.toUser();
        otherUser.addUserSocialAccount(otherDto.toUserSocialAccount());
        userRepository.save(otherUser);

        assertThatThrownBy(() -> storeService.getStoreImageList(otherUser.getId(), store.getId()))
                .isInstanceOf(InvalidRequestException.class);
    }

    @DisplayName("매장 수가 limit보다 적으면 hasNext=false이고 nextCursor가 없다")
    @Test
    void searchStores_hasNextFalse_noNextCursor() {
        List<Store> storeList = IntStream.range(1, 4)
                .mapToObj(this::createStore)
                .toList();
        storeRepository.saveAll(storeList);

        StoreSearchServiceRequest request = new StoreSearchServiceRequest("", "-createdAt", 5, null);
        StoreSearchResponse result = storeService.searchStores(request);

        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.stores()).hasSize(3);
    }

    @DisplayName("매장 수가 limit를 초과하면 hasNext=true이고 nextCursor가 생성된다")
    @Test
    void searchStores_hasNextTrue_nextCursorGenerated() {
        List<Store> storeList = IntStream.range(1, 7)
                .mapToObj(this::createStore)
                .toList();
        storeRepository.saveAll(storeList);

        StoreSearchServiceRequest request = new StoreSearchServiceRequest("", "-createdAt", 5, null);
        StoreSearchResponse result = storeService.searchStores(request);

        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isNotNull();
        assertThat(result.stores()).hasSize(5);
    }

    private Store createStore(int i) {
        UserSocialAccountDTO userSocialAccountDTO = new UserSocialAccountDTO("test" + i + "@test.com", "테스트이름", "닉네임",
                "01012341234",
                ProviderType.KAKAO,
                "testId" + i, UserRole.OWNER);
        user = userSocialAccountDTO.toUser();
        user.addUserSocialAccount(userSocialAccountDTO.toUserSocialAccount());

        RegisterStoreDTO registerStoreDTO = new RegisterStoreDTO(
                "테스트 매장" + i,
                "서울 강서구 테스트 211",
                37.5665,
                37.5665,
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

        Store store = registerStoreDTO.toEntity();
        List<ImageRegister> images = registerStoreDTO.toImage();
        images.forEach(image -> store.addStoreImage(StoreImage.create(image.key(), image.id())));

        RegisterGoodsDTO registerGoodsDTO = new RegisterGoodsDTO(
                "상품명",
                LocalDateTime.of(2025, 1, 1, 9, 0), LocalDateTime.of(2025, 1, 1, 18, 0),
                i, 10000, 10, 9000, store, List.of(new GoodsImagesRegister(0, "test", "https://test.com/test.jpg", "상품명"))

        );
        user.addStore(store);

        Goods goods = registerGoodsDTO.toGoods();
        store.addGoods(goods);
        Stock stock = Stock.create(registerGoodsDTO.quantity());
        goods.addStock(stock);

        userRepository.save(user);
        goods.changeStatus(user, ON, LocalDateTime.of(2025, 1, 1, 8, 0));
        return store;
    }

        private Store createMapAvailableStore(int i) {
                return createMapStore(i, true, 1);
        }

        private Store createMapUnavailableStore(int i) {
                return createMapStore(i, false, 0);
        }

        private Store createMapStore(int i, boolean available, int quantity) {
                LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
                UserSocialAccountDTO userSocialAccountDTO = new UserSocialAccountDTO("test-off" + i + "@test.com", "테스트이름", "닉네임",
                                "01012341234",
                                ProviderType.KAKAO,
                                "testOffId" + i, UserRole.OWNER);
                user = userSocialAccountDTO.toUser();
                user.addUserSocialAccount(userSocialAccountDTO.toUserSocialAccount());

                RegisterStoreDTO registerStoreDTO = new RegisterStoreDTO(
                                "비활성 매장" + i,
                                "서울 강서구 테스트 211",
                        37.325839945374,
                        127.125354580751,
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

                Store store = registerStoreDTO.toEntity();
                List<ImageRegister> images = registerStoreDTO.toImage();
                images.forEach(image -> store.addStoreImage(StoreImage.create(image.key(), image.id())));

                RegisterGoodsDTO registerGoodsDTO = new RegisterGoodsDTO(
                                "상품명",
                        now.minusHours(1), now.plusHours(1),
                        quantity, 10000, 10, 9000, store, List.of(new GoodsImagesRegister(0, "test", "https://test.com/test.jpg", "상품명"))

                );
                user.addStore(store);

                Goods goods = registerGoodsDTO.toGoods();
                store.addGoods(goods);
                Stock stock = Stock.create(quantity);
                goods.addStock(stock);

                userRepository.save(user);
                if (available) {
                        goods.changeStatus(user, ON, now);
                }
                return store;
        }
}
