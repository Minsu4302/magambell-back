package com.magambell.server.store.adapter.out.persistence;

import com.magambell.server.goods.adapter.in.web.GoodsImagesRegister;
import com.magambell.server.goods.domain.enums.SaleStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record StoreDetailResponse(
        String storeId,
        String goodsId,
        String storeName,
        String address,
        String description,
        Set<String> images,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer originalPrice,
        Integer salePrice,
        Integer discount,
        Integer quantity,
        SaleStatus saleStatus,
        Double latitude,
        Double longitude,
        String parkingDescription,
        Long reviewCount,
        Double averageRating,
        List<GoodsImagesRegister> goodsImages

) {
    public StoreDetailResponse {
        images = images == null ? new java.util.LinkedHashSet<>() : new java.util.LinkedHashSet<>(images);
        goodsImages = List.copyOf(goodsImages);
    }
}
