package com.magambell.server.review.app.port.out.response;

import java.time.LocalDateTime;
import java.util.Set;

public record ReviewListDTO(
        Long reviewId,
        Integer rating,
        String description,
        LocalDateTime createdAt,
        Set<String> imageUrls,
        String nickName,
        Long goodsId,
        Long storeId,
        String storeName
) {
    public ReviewListDTO {
        imageUrls = imageUrls == null ? Set.of() : imageUrls;
    }
    public String getReviewId() {
        return String.valueOf(reviewId);
    }

    public String getGoodsId() {
        return String.valueOf(goodsId);
    }

    public String getStoreId() {
        return String.valueOf(storeId);
    }
}
