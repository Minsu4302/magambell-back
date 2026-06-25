package com.magambell.server.order.app.port.out.response;

import com.magambell.server.order.domain.enums.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record OrderListDTO(
        Long orderId,
        OrderStatus orderStatus,
        LocalDateTime createdAt,
        String memo,
        Long storeId,
        String storeName,
        Set<String> imageUrls,
        List<OrderGoodsInfo> goodsList,
        Set<Long> reviewIds,
        String payType,
        String easyPayProvider,
        Double latitude,
        Double longitude,
        LocalDateTime pickupTime,
        String address,
        Integer discount,
        Boolean isReviewWritten
) {
    public OrderListDTO {
        imageUrls = imageUrls == null ? Set.of() : imageUrls;
        goodsList = goodsList == null ? List.of() : goodsList;
        reviewIds = reviewIds == null ? Set.of() : reviewIds;
    }
    public record OrderGoodsInfo(
            Long orderGoodsId,
            String goodsName,
            Integer quantity,
            Integer salePrice
    ) {
        public String getOrderGoodsId() {
            return String.valueOf(orderGoodsId);
        }
    }

    public String getOrderId() {
        return String.valueOf(orderId);
    }

    public String getStoreId() {
        return String.valueOf(storeId);
    }

    public List<String> getReviewIds() {
        return reviewIds.stream()
                .map(String::valueOf)
                .toList();
    }
}
