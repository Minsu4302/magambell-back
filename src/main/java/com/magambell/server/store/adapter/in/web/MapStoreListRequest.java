package com.magambell.server.store.adapter.in.web;

import com.magambell.server.store.app.port.in.request.MapStoreListServiceRequest;
import jakarta.validation.constraints.NotNull;

public record MapStoreListRequest(
        @NotNull(message = "남서쪽 위도를 입력해 주세요.")
        Double swLatitude,

        @NotNull(message = "남서쪽 경도를 입력해 주세요.")
        Double swLongitude,

        @NotNull(message = "북동쪽 위도를 입력해 주세요.")
        Double neLatitude,

        @NotNull(message = "북동쪽 경도를 입력해 주세요.")
        Double neLongitude,

        Boolean onlyAvailable
) {
    public MapStoreListServiceRequest toService() {
        return new MapStoreListServiceRequest(swLatitude, swLongitude, neLatitude, neLongitude, onlyAvailable);
    }
}
