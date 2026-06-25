package com.magambell.server.store.app.port.out.response;

import com.magambell.server.goods.adapter.in.web.GoodsImagesRegister;
import com.magambell.server.goods.domain.enums.SaleStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record OwnerStoreDetailDTO(
        Long storeId,
        String storeName,
        String address,
        Set<String> storeImageUrls,
        List<GoodsInfo> goodsList,
        List<GoodsImagesRegister> goodsImageList,
        String description,
        String parkingDescription
) {
    public OwnerStoreDetailDTO {
        storeImageUrls = storeImageUrls == null ? Set.of() : storeImageUrls;
        goodsList = goodsList == null ? List.of() : goodsList;
        goodsImageList = goodsImageList == null ? List.of() : goodsImageList;
    }
    public record GoodsInfo(
            Long goodsId,
            String goodsName,
            Integer originPrice,
            Integer discount,
            Integer salePrice,
            LocalDateTime startTime,
            LocalDateTime endTime,
            SaleStatus saleStatus,
            Integer stockQuantity
    ) {
        public String getGoodsId() {
            return String.valueOf(goodsId);
        }
    }

    public String getStoreId() {
        return String.valueOf(storeId);
    }
}
