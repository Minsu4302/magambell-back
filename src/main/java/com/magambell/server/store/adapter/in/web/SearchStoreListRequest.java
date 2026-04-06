package com.magambell.server.store.adapter.in.web;

import com.magambell.server.store.app.port.in.request.SearchStoreListServiceRequest;
import com.magambell.server.store.domain.enums.SearchSortType;
import jakarta.validation.constraints.Positive;

public record SearchStoreListRequest(
        Double latitude,
        Double longitude,
        String keyword,
        SearchSortType sortType,
    String sort,
        Boolean onlyAvailable,

        @Positive(message = "페이지를 선택해 주세요.")
        Integer page,

        @Positive(message = "화면에 개수를 주세요.")
        Integer size
) {
    public SearchStoreListServiceRequest toService() {
        return new SearchStoreListServiceRequest(
                latitude,
                longitude,
                keyword,
                resolveSortType(),
                onlyAvailable,
                page,
                size
        );
    }

    private SearchSortType resolveSortType() {
        if (sortType != null) {
            return sortType;
        }
        if (sort == null || sort.isBlank()) {
            return null;
        }

        String normalized = sort.trim().toUpperCase();
        return switch (normalized) {
            case "RECENT_DESC", "RECENT", "LATEST", "LATEST_DESC" -> SearchSortType.RECENT_DESC;
            case "PRICE_ASC", "LOW_PRICE", "PRICE_LOW" -> SearchSortType.PRICE_ASC;
            case "DISTANCE_ASC", "DISTANCE", "NEAR" -> SearchSortType.DISTANCE_ASC;
            case "RATING_DESC", "RATING", "RATE" -> SearchSortType.RATING_DESC;
            case "POPULAR_DESC", "POPULAR", "REVIEW", "REVIEW_DESC", "MOST_REVIEWED" -> SearchSortType.POPULAR_DESC;
            default -> null;
        };
    }
}
