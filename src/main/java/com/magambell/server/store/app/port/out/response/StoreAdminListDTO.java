package com.magambell.server.store.app.port.out.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.magambell.server.goods.domain.enums.SaleStatus;
import com.magambell.server.store.domain.enums.Bank;
import java.time.LocalDateTime;
import java.util.Set;

public record StoreAdminListDTO(
        Long storeId,
        String storeName,
    @JsonProperty("storeImages") Set<String> imageUrl,
        Double latitude,
        Double longitude,
        String address,
    String description,
    String parkingDescription,
        String goodsName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer originPrice,
        Integer discount,
        Integer salePrice,
        Integer quantity,
        Double distance,
        SaleStatus saleStatus,
        String nickName,
        String ownerName,
        String ownerPhone,
        String businessNumber,
        Bank bankName,
        String bankAccount,
        Set<GoodsImageInfo> goodsImageList
) {
    public record GoodsImageInfo(
            Long goodsImageId,
            String imageUrl,
            String key,
            String goodsName
    ) {
        public GoodsImageInfo(final Long goodsImageId, final String imageUrl, final String goodsName) {
            this(goodsImageId, imageUrl, extractKey(imageUrl), goodsName);
        }

        private static String extractKey(final String imageUrl) {
            if (imageUrl == null || imageUrl.isBlank()) {
                return "";
            }
            int lastSlashIndex = imageUrl.lastIndexOf('/');
            if (lastSlashIndex >= 0 && lastSlashIndex < imageUrl.length() - 1) {
                return imageUrl.substring(lastSlashIndex + 1);
            }
            return imageUrl;
        }

        public String getGoodsImageId() {
            return String.valueOf(goodsImageId);
        }
    }

    public String getStoreId() {
        return String.valueOf(storeId);
    }
}
