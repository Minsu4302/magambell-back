package com.magambell.server.store.app.service;

import com.magambell.server.common.enums.ErrorCode;
import com.magambell.server.common.exception.DuplicateException;
import com.magambell.server.common.exception.InvalidRequestException;
import com.magambell.server.common.s3.S3InputPort;
import com.magambell.server.region.app.port.out.RegionQueryPort;
import com.magambell.server.region.domain.entity.Region;
import com.magambell.server.review.adapter.out.persistence.ReviewListResponse;
import com.magambell.server.notification.app.port.in.NotificationUseCase;
import com.magambell.server.store.adapter.in.web.StoreImagesRegister;
import com.magambell.server.store.adapter.out.persistence.*;
import com.magambell.server.store.app.port.in.StoreUseCase;
import com.magambell.server.store.app.port.in.request.*;
import com.magambell.server.store.app.port.out.StoreCommandPort;
import com.magambell.server.store.app.port.out.StoreQueryPort;
import com.magambell.server.store.app.port.out.response.*;
import com.magambell.server.store.domain.enums.Approved;
import com.magambell.server.store.domain.entity.Store;
import com.magambell.server.user.app.port.out.UserQueryPort;
import com.magambell.server.user.domain.enums.UserRole;
import com.magambell.server.user.domain.entity.User;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class StoreService implements StoreUseCase {

    private final StoreCommandPort storeCommandPort;
    private final StoreQueryPort storeQueryPort;
    private final UserQueryPort userQueryPort;
    private final RegionQueryPort regionQueryPort;
    private final S3InputPort s3InputPort;
    private final NotificationUseCase notificationUseCase;

    @Transactional
    @Override
    public StoreImagesResponse registerStore(final RegisterStoreServiceRequest request, final Long userId) {
        User user = userQueryPort.findById(userId);
        checkDuplicateStore(user);
        StoreRegisterResponseDTO storeRegisterResponseDTO = storeCommandPort.registerStore(
                request.toStoreDTO(Approved.WAITING, user));

        return new StoreImagesResponse(String.valueOf(storeRegisterResponseDTO.id()),
                storeRegisterResponseDTO.storePreSignedUrlImages());
    }

    @Override
    public StoreListResponse getStoreList(final SearchStoreListServiceRequest request) {
        return new StoreListResponse(
                storeQueryPort.getStoreList(request, PageRequest.of(request.page() - 1, request.size())));
    }

    @Transactional
    @Override
    public void storeApprove(final StoreApproveServiceRequest request) {
        Store store = storeQueryPort.findById(request.id());
        storeCommandPort.storeApprove(request.id());
        notificationUseCase.notifyStoreApproved(store.getUser());
    }

    @Override
    public StoreDetailResponse getStoreDetail(final Long storeId) {
        return storeQueryPort.getStoreDetail(storeId);
    }

    @Override
    public OwnerStoreDetailDTO getOwnerStoreInfo(final Long userId) {
        User user = userQueryPort.findById(userId);
        return convertOwnerStoreImageUrls(storeQueryPort.getOwnerStoreInfo(user));
    }

    @Override
    public StoreListResponse getCloseStoreList(final CloseStoreListServiceRequest request) {
        return new StoreListResponse(storeQueryPort.getCloseStoreList(request));
    }

    @Override
    public StoreAdminListResponse getWaitingStoreList(final WaitingStoreListServiceRequest request) {
        List<StoreAdminListDTO> waitingStoreList = storeQueryPort.getWaitingStoreList(
            PageRequest.of(request.page() - 1, request.size()));

        return new StoreAdminListResponse(
            waitingStoreList.stream().map(this::convertAdminStoreImageUrls).toList());
    }

    @Override
    public StoreImagesResponse getStoreImageList(final Long userId, final Long storeId) {
        User user = userQueryPort.findById(userId);
        validateUserRoleAndStore(user, storeId);

        List<StorePreSignedUrlImage> images =
                Optional.ofNullable(storeQueryPort.getStoreImageList(storeId))
                        .orElseGet(List::of).stream()
                        .filter(Objects::nonNull)
                .map(img -> new StorePreSignedUrlImage(img.getOrder(), s3InputPort.getReadUrl(img.getName())))
                        .toList();

        return new StoreImagesResponse(String.valueOf(storeId), images);
    }

    @Override
    @Transactional
    public StoreImagesResponse editStoreImage(final EditStoreImageServiceRequest request) {
        User user = userQueryPort.findById(request.userId());
        Store store = storeQueryPort.getStoreAndStoreImages(request.storeId());

        validateUserRoleAndStore(user, store.getId());

        EditStoreImageResponseDTO editStoreImageResponseDTO = changeStoreImage(store, request.storeImagesRegisters());
        return new StoreImagesResponse(String.valueOf(editStoreImageResponseDTO.id()),
                editStoreImageResponseDTO.storePreSignedUrlImages());
    }

    @Override
    @Transactional
    public void registerOpenRegion(final RegisterOpenRegionServiceRequest request, final Long userId) {
        User user = userQueryPort.findById(userId);
        Region region = regionQueryPort.findById(request.regionId());
        storeCommandPort.registerOpenRegion(request.toOpenRegionDTO(region, user));
    }

    @Override
    public List<OpenRegionListDTO> getOpenRegionList(final OpenRegionListServiceRequest request) {
        return storeQueryPort.getOpenRegionList(request, PageRequest.of(request.page() - 1, request.size()));
    }

    @Override
    public StoreSearchResponse searchStores(final StoreSearchServiceRequest request) {
        List<StoreSearchItemDTO> stores = storeQueryPort.searchStores(request);
        
        // limit + 1 개 조회했으므로, limit 개수 초과 여부로 다음 페이지 존재 확인
        boolean hasNext = stores.size() > request.limit();
        
        // 실제 반환할 데이터는 limit 개수만큼
        List<StoreSearchItemDTO> resultStores = hasNext 
            ? stores.subList(0, request.limit()) 
            : stores;
        
        // 다음 커서 생성
        String nextCursor = null;
        if (hasNext && !resultStores.isEmpty()) {
            StoreSearchItemDTO lastStore = resultStores.get(resultStores.size() - 1);
            nextCursor = encodeCursor(lastStore.createdAt(), lastStore.storeId());
        }
        
        return new StoreSearchResponse(resultStores, nextCursor, hasNext);
    }

    private void checkDuplicateStore(final User user) {
        if (storeQueryPort.existsByUser(user)) {
            throw new DuplicateException(ErrorCode.DUPLICATE_STORE);
        }
    }

    private void validateUserRoleAndStore(final User user, final Long storeId) {
        if (user.getUserRole() == UserRole.ADMIN) {
            return;
        }

        Long ownerId = storeQueryPort.findOwnerIdByStoreId(storeId);

        if (!ownerId.equals(user.getId())) {
            throw new InvalidRequestException(ErrorCode.INVALID_STORE_OWNER);
        }
    }

    private EditStoreImageResponseDTO changeStoreImage(final Store store,
                                                       final List<StoreImagesRegister> storeImagesRegisters) {
        return storeCommandPort.editStoreImage(store, storeImagesRegisters);
    }

    private String encodeCursor(LocalDateTime createdAt, Long storeId) {
        String cursorString = createdAt.toString() + "_" + storeId;
        return Base64.getEncoder().encodeToString(cursorString.getBytes());
    }

        private OwnerStoreDetailDTO convertOwnerStoreImageUrls(final OwnerStoreDetailDTO ownerStoreDetailDTO) {
        LinkedHashSet<String> storeImageUrls = ownerStoreDetailDTO.storeImageUrls().stream()
            .filter(Objects::nonNull)
            .map(s3InputPort::getReadUrl)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        List<com.magambell.server.goods.adapter.in.web.GoodsImagesRegister> goodsImageList = ownerStoreDetailDTO.goodsImageList().stream()
            .map(img -> new com.magambell.server.goods.adapter.in.web.GoodsImagesRegister(
                img.id(),
                img.key(),
                s3InputPort.getReadUrl(img.imageUrl()),
                img.goodsName()
            ))
            .toList();

        return new OwnerStoreDetailDTO(
            ownerStoreDetailDTO.storeId(),
            ownerStoreDetailDTO.storeName(),
            ownerStoreDetailDTO.address(),
            storeImageUrls,
            ownerStoreDetailDTO.goodsList(),
            goodsImageList,
            ownerStoreDetailDTO.description(),
            ownerStoreDetailDTO.parkingDescription()
        );
        }

        private StoreAdminListDTO convertAdminStoreImageUrls(final StoreAdminListDTO storeAdminListDTO) {
        LinkedHashSet<String> storeImageUrls = storeAdminListDTO.ImageUrl().stream()
            .filter(Objects::nonNull)
            .map(s3InputPort::getReadUrl)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        LinkedHashSet<StoreAdminListDTO.GoodsImageInfo> goodsImageList = storeAdminListDTO.goodsImageList().stream()
            .filter(Objects::nonNull)
            .map(img -> new StoreAdminListDTO.GoodsImageInfo(
                img.goodsImageId(),
                s3InputPort.getReadUrl(img.imageUrl()),
                img.key(),
                img.goodsName()
            ))
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        return new StoreAdminListDTO(
            storeAdminListDTO.storeId(),
            storeAdminListDTO.storeName(),
            storeImageUrls,
            storeAdminListDTO.latitude(),
            storeAdminListDTO.longitude(),
            storeAdminListDTO.address(),
            storeAdminListDTO.description(),
            storeAdminListDTO.parkingDescription(),
            storeAdminListDTO.goodsName(),
            storeAdminListDTO.startTime(),
            storeAdminListDTO.endTime(),
            storeAdminListDTO.originPrice(),
            storeAdminListDTO.discount(),
            storeAdminListDTO.salePrice(),
            storeAdminListDTO.quantity(),
            storeAdminListDTO.distance(),
            storeAdminListDTO.saleStatus(),
            storeAdminListDTO.nickName(),
            storeAdminListDTO.ownerName(),
            storeAdminListDTO.ownerPhone(),
            storeAdminListDTO.businessNumber(),
            storeAdminListDTO.bankName(),
            storeAdminListDTO.bankAccount(),
            goodsImageList
        );
        }
}
