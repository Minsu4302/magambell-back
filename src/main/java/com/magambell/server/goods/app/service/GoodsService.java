package com.magambell.server.goods.app.service;

import com.magambell.server.common.enums.ErrorCode;
import com.magambell.server.common.exception.DuplicateException;
import com.magambell.server.common.exception.NotFoundException;
import com.magambell.server.goods.adapter.in.web.GoodsImagesRegister;
import com.magambell.server.goods.adapter.out.persistence.GoodsImagesResponse;
import com.magambell.server.goods.app.port.in.GoodsUseCase;
import com.magambell.server.goods.app.port.in.request.ChangeGoodsStatusServiceRequest;
import com.magambell.server.goods.app.port.in.request.EditGoodsImagesServiceRequest;
import com.magambell.server.goods.app.port.in.request.EditGoodsServiceRequest;
import com.magambell.server.goods.app.port.in.request.RegisterGoodsServiceRequest;
import com.magambell.server.goods.app.port.out.GoodsCommandPort;
import com.magambell.server.goods.app.port.out.GoodsQueryPort;
import com.magambell.server.goods.app.port.out.response.EditGoodsImageResponseDTO;
import com.magambell.server.goods.domain.entity.Goods;
import com.magambell.server.goods.domain.enums.SaleStatus;
import com.magambell.server.notification.app.port.in.NotificationUseCase;
import com.magambell.server.notification.app.port.in.request.NotifyStoreOpenRequest;
import com.magambell.server.store.app.port.out.StoreQueryPort;
import com.magambell.server.store.domain.entity.Store;
import com.magambell.server.user.app.port.out.UserQueryPort;
import com.magambell.server.user.domain.entity.User;
import com.magambell.server.user.domain.enums.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class GoodsService implements GoodsUseCase {

    private final UserQueryPort userQueryPort;
    private final StoreQueryPort storeQueryPort;
    private final GoodsCommandPort goodsCommandPort;
    private final GoodsQueryPort goodsQueryPort;
    private final NotificationUseCase notificationUseCase;

    @Transactional
    @Override
    public GoodsImagesResponse registerGoods(final RegisterGoodsServiceRequest request, final Long userId) {
        User user = userQueryPort.findById(userId);
        Store store = storeQueryPort.getStoreByUserWithLock(user)
                .orElseThrow(() -> new NotFoundException(ErrorCode.STORE_NOT_FOUND));
        existGoods(store);
        GoodsImagesResponse response = new GoodsImagesResponse(
            goodsCommandPort.registerGoods(request.toDTO(store)).goodsPreSignedUrlImages()
        );
        notificationUseCase.notifyNewSignupStoreReview(UserRole.OWNER);

        return response;
    }

    @Transactional
    @Override
    public void changeGoodsStatus(final ChangeGoodsStatusServiceRequest request, final LocalDateTime today) {
        User user = userQueryPort.findById(request.userId());
        Goods goods = goodsQueryPort.findWithStoreAndUserById(request.goodsId());
        
        // 이미 ON 상태인지 확인 (중복 알림 방지)
        boolean wasOff = goods.getSaleStatus() == SaleStatus.OFF;
        
        goods.changeStatus(user, request.saleStatus(), today);

        // OFF -> ON 상태 변경일 때만 알림 전송
        if (wasOff && request.saleStatus() == SaleStatus.ON) {
            log.info("매장 오픈 알림 전송 시작 - storeId: {}, storeName: {}", goods.getStore().getId(), goods.getStore().getName());
            try {
                notificationUseCase.notifyStoreOpen(new NotifyStoreOpenRequest(goods.getStore()));
                log.info("매장 오픈 알림 전송 완료 - storeId: {}", goods.getStore().getId());
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                // FCM 토큰 삭제 동시성 에러는 무시 (이미 다른 스레드에서 삭제됨)
                log.warn("알림 전송 중 동시성 에러 발생 (무시) - storeId: {}", goods.getStore().getId());
            }
        } else if (!wasOff && request.saleStatus() == SaleStatus.ON) {
            log.info("매장이 이미 오픈 상태이므로 알림 전송 생략 - storeId: {}, storeName: {}", goods.getStore().getId(), goods.getStore().getName());
        }
    }

    @Transactional
    @Override
    public GoodsImagesResponse editGoods(final EditGoodsServiceRequest request) {
        Goods goods = goodsQueryPort.findOwnedGoodsWithRelations(request.goodsId(), request.userId());
        goods.edit(request.toDTO());

        EditGoodsImageResponseDTO editGoodsImageResponseDTO = changeGoodsImage(goods, request.goodsImagesRegisters());
        return new GoodsImagesResponse(editGoodsImageResponseDTO.goodsPreSignedUrlImages());


    }

    @Transactional
    @Override
    public GoodsImagesResponse editGoodsImages(final EditGoodsImagesServiceRequest request) {
        Goods goods = goodsQueryPort.findOwnedGoodsWithRelations(request.goodsId(), request.userId());
        
        EditGoodsImageResponseDTO editGoodsImageResponseDTO = changeGoodsImage(goods, request.goodsImagesRegisters());
        return new GoodsImagesResponse(editGoodsImageResponseDTO.goodsPreSignedUrlImages());
    }

    @Transactional
    @Override
    public void changeSaleStatusToOff(final LocalDateTime now) {
        List<Goods> expiredGoods = goodsQueryPort.findExpiredGoods(now);
        expiredGoods.forEach(Goods::changeSaleStatusToOffBySystem);
    }

    private Store getStore(final User user) {
        return storeQueryPort.getStoreByUser(user)
                .orElseThrow(() -> new NotFoundException(ErrorCode.STORE_NOT_FOUND));
    }

    private void existGoods(final Store store) {
        if (goodsQueryPort.existsByStoreId(store.getId())) {
            throw new DuplicateException(ErrorCode.DUPLICATE_GOODS);
        }
    }

    private EditGoodsImageResponseDTO changeGoodsImage(final Goods goods,
                                                       final List<GoodsImagesRegister> goodsImagesRegisters) {
        return goodsCommandPort.editGoodsImage(goods, goodsImagesRegisters);
    }
}
