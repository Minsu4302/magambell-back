package com.magambell.server.store.adapter.in.web;

import com.magambell.server.store.app.port.in.request.SearchStoreListServiceRequest;
import com.magambell.server.store.domain.enums.SearchSortType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SearchStoreListRequest(
        Double latitude,
        Double longitude,
        String keyword,
    String sortType,
    String sort,
    String sortBy,
    String orderBy,
    String sortOption,
        Boolean onlyAvailable,

        @NotNull(message = "페이지를 선택해 주세요.")
        @Positive(message = "페이지를 선택해 주세요.")
        Integer page,

        @NotNull(message = "화면에 개수를 주세요.")
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
        for (String candidate : java.util.Arrays.asList(sortType, sort, sortBy, orderBy, sortOption)) {
            SearchSortType parsed = parseSort(candidate);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private SearchSortType parseSort(String rawSort) {
        if (rawSort == null || rawSort.isBlank()) {
            return null;
        }

        String normalized = rawSort.trim();
        String upper = normalized.toUpperCase();
        String compact = upper
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "")
                .replace("+", "");

        // 숫자/한글/영문 혼합 클라이언트 파라미터를 모두 허용
        if ("기본순".equals(normalized) || "최신순".equals(normalized)) {
            return SearchSortType.RECENT_DESC;
        }
        if ("가격낮은순".equals(normalized)) {
            return SearchSortType.PRICE_ASC;
        }
        if ("가까운순".equals(normalized) || "거리순".equals(normalized)) {
            return SearchSortType.DISTANCE_ASC;
        }
        if ("리뷰많은순".equals(normalized) || "리뷰순".equals(normalized)) {
            return SearchSortType.POPULAR_DESC;
        }

        // 구분자/접두어가 섞인 문자열도 폭넓게 처리
        if (normalized.contains("리뷰") || upper.contains("REVIEW") || upper.contains("POPULAR")
                || compact.contains("REVIEW") || compact.contains("POPULAR")) {
            return SearchSortType.POPULAR_DESC;
        }

        if ("1".equals(normalized)) {
            return SearchSortType.RECENT_DESC;
        }
        if ("2".equals(normalized)) {
            return SearchSortType.PRICE_ASC;
        }
        if ("3".equals(normalized)) {
            return SearchSortType.POPULAR_DESC;
        }

        return switch (upper) {
            case "RECENT_DESC", "RECENT", "LATEST", "LATEST_DESC" -> SearchSortType.RECENT_DESC;
            case "PRICE_ASC", "LOW_PRICE", "PRICE_LOW" -> SearchSortType.PRICE_ASC;
            case "DISTANCE_ASC", "DISTANCE", "NEAR" -> SearchSortType.DISTANCE_ASC;
            case "RATING_DESC", "RATING", "RATE" -> SearchSortType.RATING_DESC;
            case "POPULAR_DESC", "POPULAR", "REVIEW", "REVIEW_DESC", "MOST_REVIEWED" -> SearchSortType.POPULAR_DESC;
            default -> null;
        };
    }
}
