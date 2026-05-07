package com.magambell.server.store.app.port.in.request;

public record MapStoreListServiceRequest(
        Double swLatitude,
        Double swLongitude,
        Double neLatitude,
        Double neLongitude
) {
}
