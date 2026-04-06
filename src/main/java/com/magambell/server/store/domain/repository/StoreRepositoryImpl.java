package com.magambell.server.store.domain.repository;

import com.magambell.server.goods.adapter.in.web.GoodsImagesRegister;
import com.magambell.server.goods.domain.enums.SaleStatus;
import com.magambell.server.review.domain.enums.ReviewStatus;
import com.magambell.server.store.adapter.out.persistence.StoreDetailResponse;
import com.magambell.server.store.app.port.in.request.CloseStoreListServiceRequest;
import com.magambell.server.store.app.port.in.request.SearchStoreListServiceRequest;
import com.magambell.server.store.app.port.in.request.StoreSearchServiceRequest;
import com.magambell.server.store.app.port.out.dto.StoreDetailDTO;
import com.magambell.server.store.app.port.out.response.OwnerStoreDetailDTO;
import com.magambell.server.store.app.port.out.response.StoreAdminListDTO;
import com.magambell.server.store.app.port.out.response.StoreListDTOResponse;
import com.magambell.server.store.app.port.out.response.StoreSearchItemDTO;
import com.magambell.server.store.domain.entity.Store;
import com.magambell.server.store.domain.entity.StoreImage;
import com.magambell.server.store.domain.enums.Approved;
import com.magambell.server.store.domain.enums.SearchSortType;
import com.magambell.server.user.domain.enums.UserStatus;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.magambell.server.goods.domain.entity.QGoods.goods;
import static com.magambell.server.goods.domain.entity.QGoodsImage.goodsImage;
import static com.magambell.server.order.domain.entity.QOrderGoods.orderGoods;
import static com.magambell.server.review.domain.entity.QReview.review;
import static com.magambell.server.stock.domain.entity.QStock.stock;
import static com.magambell.server.store.domain.entity.QStore.store;
import static com.magambell.server.store.domain.entity.QStoreImage.storeImage;
import static com.magambell.server.store.domain.enums.Approved.APPROVED;
import static com.magambell.server.store.domain.enums.Approved.WAITING;
import static com.magambell.server.user.domain.entity.QUser.user;
import static com.querydsl.core.group.GroupBy.*;
import static com.querydsl.core.types.ExpressionUtils.count;

@RequiredArgsConstructor
public class StoreRepositoryImpl implements StoreRepositoryCustom {

    private static final Integer LIMIT_KM = 6;
    private final JPAQueryFactory queryFactory;

    @Override
    public List<StoreListDTOResponse> getStoreList(final SearchStoreListServiceRequest request,
                                                   final Pageable pageable) {
        NumberExpression<Double> distance = null;

        if (request.latitude() != null && request.longitude() != null
                && request.latitude() != 0.0 && request.longitude() != 0.0) {
            distance = Expressions.numberTemplate(
                    Double.class,
                    "6371 * acos(cos(radians({0})) * cos(radians({1})) * cos(radians({2}) - radians({3})) + sin(radians({0})) * sin(radians({1})))",
                    request.latitude(),
                    store.latitude,
                    request.longitude(),
                    store.longitude
            );
        }

        BooleanBuilder conditions = new BooleanBuilder();
        Optional.ofNullable(radiusCondition(distance)).ifPresent(conditions::and);
        Optional.ofNullable(availableNowCondition(request)).ifPresent(conditions::and);
        Optional.ofNullable(keywordCondition(request.keyword())).ifPresent(conditions::and);
        conditions.and(store.approved.eq(APPROVED));
        conditions.and(user.userStatus.eq(UserStatus.ACTIVE));

        List<Long> storeIds = queryFactory
                .select(store.id)
                .from(store)
                .leftJoin(goods).on(goods.store.id.eq(store.id))
                .innerJoin(stock).on(stock.goods.id.eq(goods.id))
                .innerJoin(user).on(user.id.eq(store.user.id))
                .leftJoin(orderGoods).on(orderGoods.goods.id.eq(goods.id))
            .leftJoin(review).on(review.orderGoods.id.eq(orderGoods.id)
                .and(review.reviewStatus.eq(ReviewStatus.ACTIVE)))
                .where(conditions)
                .groupBy(store.id)
                .orderBy(sortCondition(request.sortType(), distance))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        if (storeIds.isEmpty()) {
            return List.of();
        }

        Map<Long, StoreListDTOResponse> storeMap = queryFactory.select(store, storeImage, goods, stock)
                .from(store)
                .leftJoin(storeImage).on(storeImage.store.id.eq(store.id))
                .leftJoin(goods).on(goods.store.id.eq(store.id))
                .innerJoin(stock).on(stock.goods.id.eq(goods.id))
                .leftJoin(orderGoods).on(orderGoods.goods.id.eq(goods.id))
            .leftJoin(review).on(review.orderGoods.id.eq(orderGoods.id)
                .and(review.reviewStatus.eq(ReviewStatus.ACTIVE)))
                .where(store.id.in(storeIds))
                .transform(
                groupBy(store.id)
                    .as(Projections.constructor(StoreListDTOResponse.class,
                                        store.id,
                                        store.name,
                                        set(storeImage.name),
                                        store.latitude,
                                        store.longitude,
                                        store.address,
                                        goods.name,
                                        goods.startTime,
                                        goods.endTime,
                                        goods.originalPrice,
                                        goods.discount,
                                        goods.salePrice,
                                        stock.quantity,
                                        distance != null ? distance : Expressions.nullExpression(Double.class),
                                        goods.saleStatus
                                ))
                );

                        return storeIds.stream()
                            .map(storeMap::get)
                            .filter(Objects::nonNull)
                            .toList();
    }

    @Override
    public Optional<StoreDetailResponse> getStoreDetail(final Long storeId) {
        // 먼저 기본 store 정보 조회 (goodsImages 제외)
        StoreDetailDTO storeDetail = queryFactory
                .select(Projections.constructor(StoreDetailDTO.class,
                        store.id,
                        goods.id,
                        store.name,
                        store.address,
                    store.description,
                        Expressions.constant(Collections.emptySet()), // images - 나중에 set으로 변경
                        goods.startTime,
                        goods.endTime,
                        goods.originalPrice,
                        goods.salePrice,
                        goods.discount,
                        stock.quantity,
                        goods.saleStatus,
                        store.latitude,
                        store.longitude,
                        store.parkingDescription,
                        Expressions.constant(Collections.emptyList()) // goodsImages - 나중에 교체
                ))
                .from(store)
                .leftJoin(goods).on(goods.store.id.eq(store.id))
                .leftJoin(stock).on(stock.goods.id.eq(goods.id))
                .innerJoin(user).on(user.id.eq(store.user.id))
                .where(
                        store.id.eq(storeId)
                                .and(store.approved.eq(APPROVED))
                                .and(user.userStatus.eq(UserStatus.ACTIVE))
                )
                .fetchFirst();

        if (storeDetail == null) {
            return Optional.empty();
        }

        // storeImages 조회
        List<String> storeImages = queryFactory
                .select(storeImage.name)
                .from(storeImage)
                .where(storeImage.store.id.eq(storeId))
                .fetch();
        
        // goodsImages 조회 (goods가 있는 경우만)
        List<GoodsImagesRegister> goodsImages = Collections.emptyList();
        if (storeDetail.goodsId() != null) {
            goodsImages = queryFactory
                    .select(Projections.constructor(GoodsImagesRegister.class,
                            goodsImage.id.intValue(),
                            goodsImage.imageUrl,
                            goodsImage.imageUrl,
                            goodsImage.goodsName
                    ))
                    .from(goodsImage)
                    .where(goodsImage.goods.id.eq(storeDetail.goodsId()))
                    .fetch()
                    .stream()
                    .map(img -> new GoodsImagesRegister(
                            img.id(),
                            extractKeyFromUrl(img.key()),
                            img.imageUrl(),
                            img.goodsName()
                    ))
                    .toList();
        }

        // DTO 업데이트
        StoreDetailDTO updatedDTO = new StoreDetailDTO(
                storeDetail.storeId(),
                storeDetail.goodsId(),
                storeDetail.storeName(),
                storeDetail.address(),
            storeDetail.description(),
                storeImages.isEmpty() ? Collections.emptySet() : Set.copyOf(storeImages),
                storeDetail.startTime(),
                storeDetail.endTime(),
                storeDetail.originalPrice(),
                storeDetail.salePrice(),
                storeDetail.discount(),
                storeDetail.quantity(),
                storeDetail.saleStatus(),
                storeDetail.latitude(),
                storeDetail.longitude(),
                storeDetail.parkingDescription(),
                goodsImages
        );

        // Review 정보 조회
        Tuple aggregation = queryFactory
                .select(
                        count(review.id),
                        review.rating.avg()
                )
                .from(review)
                .leftJoin(orderGoods).on(orderGoods.id.eq(review.orderGoods.id))
                .leftJoin(goods).on(goods.id.eq(orderGoods.goods.id))
                .leftJoin(store).on(store.id.eq(goods.store.id))
                .innerJoin(user).on(user.id.eq(store.user.id))
                .where(
                        store.id.eq(storeId)
                                .and(store.approved.eq(APPROVED))
                                .and(user.userStatus.eq(UserStatus.ACTIVE))
                                .and(review.reviewStatus.eq(ReviewStatus.ACTIVE))
                )
                .fetchOne();

        return Optional.of(
                updatedDTO.toResponse(aggregation.get(0, Long.class), aggregation.get(1, Double.class)));
    }

    @Override
    public Optional<OwnerStoreDetailDTO> getOwnerStoreInfo(final Long userId) {
        Map<Long, OwnerStoreDetailDTO> result = queryFactory
                .from(store)
                .innerJoin(user).on(user.id.eq(store.user.id))
                .leftJoin(storeImage).on(storeImage.store.id.eq(store.id))
                .leftJoin(goods).on(goods.store.id.eq(store.id))
                .leftJoin(stock).on(stock.goods.id.eq(goods.id))
                .leftJoin(goodsImage).on(goodsImage.goods.id.eq(goods.id))
                .where(
                        store.user.id.eq(userId),
                        store.approved.eq(Approved.APPROVED),
                        user.userStatus.eq(UserStatus.ACTIVE)
                )
                .transform(
                        groupBy(store.id).as(
                                Projections.constructor(OwnerStoreDetailDTO.class,
                                        store.id,
                                        store.name,
                                        store.address,
                                        set(storeImage.name),
                                        list(Projections.constructor(OwnerStoreDetailDTO.GoodsInfo.class,
                                                goods.id,
                                                goods.name,
                                                goods.originalPrice,
                                                goods.discount,
                                                goods.salePrice,
                                                goods.startTime,
                                                goods.endTime,
                                                goods.saleStatus,
                                                stock.quantity
                                        )),
                                        list(Projections.constructor(GoodsImagesRegister.class,
                                                goodsImage.id.intValue(),
                                                goodsImage.imageUrl,
                                                goodsImage.imageUrl,
                                                goodsImage.goodsName
                                        )),
                                        store.description,
                                        store.parkingDescription
                                )
                        )
                );

        // 중복 제거 및 imageUrl에서 key 추출
        return result.values().stream()
                .map(dto -> new OwnerStoreDetailDTO(
                        dto.storeId(),
                        dto.storeName(),
                        dto.address(),
                        dto.storeImageUrls(),
                        dto.goodsList().stream().distinct().toList(),
                        dto.goodsImageList().stream()
                                .map(img -> new GoodsImagesRegister(
                                        img.id(),
                                        extractKeyFromUrl(img.key()),
                                        img.imageUrl(),
                                        img.goodsName()
                                ))
                                .distinct()
                                .toList(),
                        dto.description(),
                        dto.parkingDescription()
                ))
                .findFirst();
    }

    @Override
    public List<StoreListDTOResponse> getCloseStoreList(final CloseStoreListServiceRequest request) {
        NumberExpression<Double> distance = Expressions.numberTemplate(
                Double.class,
                "6371 * acos(cos(radians({0})) * cos(radians({1})) * cos(radians({2}) - radians({3})) + sin(radians({0})) * sin(radians({1})))",
                request.latitude(),
                store.latitude,
                request.longitude(),
                store.longitude
        );

        BooleanBuilder conditions = new BooleanBuilder();
        conditions.and(store.approved.eq(APPROVED));
        conditions.and(user.userStatus.eq(UserStatus.ACTIVE));

        List<Long> storeIds = queryFactory
                .select(store.id)
                .from(store)
                .leftJoin(goods).on(goods.store.id.eq(store.id))
                .innerJoin(stock).on(stock.goods.id.eq(goods.id))
                .innerJoin(user).on(user.id.eq(store.user.id))
                .where(conditions)
                .groupBy(store.id)
                .orderBy(distance.asc())
                .fetch();

        if (storeIds.isEmpty()) {
            return List.of();
        }

        return queryFactory.select(store, storeImage, goods, stock)
                .from(store)
                .leftJoin(storeImage).on(storeImage.store.id.eq(store.id))
                .leftJoin(goods).on(goods.store.id.eq(store.id))
                .innerJoin(stock).on(stock.goods.id.eq(goods.id))
                .where(store.id.in(storeIds))
                .orderBy(distance.asc())
                .transform(
                        groupBy(store.id)
                                .list(Projections.constructor(StoreListDTOResponse.class,
                                        store.id,
                                        store.name,
                                        set(storeImage.name),
                                        store.latitude,
                                        store.longitude,
                                        store.address,
                                        goods.name,
                                        goods.startTime,
                                        goods.endTime,
                                        goods.originalPrice,
                                        goods.discount,
                                        goods.salePrice,
                                        stock.quantity,
                                        distance != null ? distance : Expressions.nullExpression(Double.class),
                                        goods.saleStatus
                                ))
                );
    }

    @Override
    public List<StoreAdminListDTO> getWaitingStoreList(final Pageable pageable) {
        BooleanBuilder conditions = new BooleanBuilder();
        conditions.and(store.approved.eq(WAITING));
        conditions.and(user.userStatus.eq(UserStatus.ACTIVE));

        List<Long> storeIds = queryFactory
                .select(store.id)
                .from(store)
                .leftJoin(goods).on(goods.store.id.eq(store.id))
                .innerJoin(stock).on(stock.goods.id.eq(goods.id))
                .innerJoin(user).on(user.id.eq(store.user.id))
                .where(conditions)
                .groupBy(store.id)
                .orderBy(store.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        if (storeIds.isEmpty()) {
            return List.of();
        }

        return queryFactory.select(store, storeImage, goods, stock)
                .from(store)
                .leftJoin(storeImage).on(storeImage.store.id.eq(store.id))
                .leftJoin(goods).on(goods.store.id.eq(store.id))
                .innerJoin(stock).on(stock.goods.id.eq(goods.id))
            .leftJoin(goodsImage).on(goodsImage.goods.id.eq(goods.id))
                .innerJoin(user).on(user.id.eq(store.user.id))
                .where(store.id.in(storeIds))
                .orderBy(store.createdAt.desc())
                .transform(
                        groupBy(store.id)
                                .list(Projections.constructor(StoreAdminListDTO.class,
                                        store.id,
                                        store.name,
                                        set(storeImage.name),
                                        store.latitude,
                                        store.longitude,
                                        store.address,
                                        store.description,
                                        store.parkingDescription,
                                        goods.name,
                                        goods.startTime,
                                        goods.endTime,
                                        goods.originalPrice,
                                        goods.discount,
                                        goods.salePrice,
                                        stock.quantity,
                                        Expressions.nullExpression(Double.class),
                                        goods.saleStatus,
                                        user.nickName,
                                        store.ownerName,
                                        store.ownerPhone,
                                        store.businessNumber,
                                        store.bankName,
                                        store.bankAccount,
                                        set(Projections.constructor(StoreAdminListDTO.GoodsImageInfo.class,
                                            goodsImage.id,
                                            goodsImage.imageUrl,
                                            goodsImage.goodsName
                                        ))
                                ))
                );
    }

    @Override
    public List<StoreAdminListDTO> getAllApprovedStores() {
        BooleanBuilder conditions = new BooleanBuilder();
        conditions.and(store.approved.eq(APPROVED));
        conditions.and(user.userStatus.eq(UserStatus.ACTIVE));

        List<Long> storeIds = queryFactory
                .select(store.id)
                .from(store)
                .leftJoin(goods).on(goods.store.id.eq(store.id))
                .leftJoin(stock).on(stock.goods.id.eq(goods.id))
                .innerJoin(user).on(user.id.eq(store.user.id))
                .where(conditions)
                .groupBy(store.id)
                .orderBy(store.createdAt.desc())
                .fetch();

        if (storeIds.isEmpty()) {
            return List.of();
        }

        return queryFactory.select(store, storeImage, goods, stock)
                .from(store)
                .leftJoin(storeImage).on(storeImage.store.id.eq(store.id))
                .leftJoin(goods).on(goods.store.id.eq(store.id))
                .leftJoin(stock).on(stock.goods.id.eq(goods.id))
            .leftJoin(goodsImage).on(goodsImage.goods.id.eq(goods.id))
                .innerJoin(user).on(user.id.eq(store.user.id))
                .where(store.id.in(storeIds))
                .orderBy(store.createdAt.desc())
                .transform(
                        groupBy(store.id)
                                .list(Projections.constructor(StoreAdminListDTO.class,
                                        store.id,
                                        store.name,
                                        set(storeImage.name),
                                        store.latitude,
                                        store.longitude,
                                        store.address,
                                        store.description,
                                        store.parkingDescription,
                                        goods.name,
                                        goods.startTime,
                                        goods.endTime,
                                        goods.originalPrice,
                                        goods.discount,
                                        goods.salePrice,
                                        stock.quantity,
                                        Expressions.nullExpression(Double.class),
                                        goods.saleStatus,
                                        user.nickName,
                                        store.ownerName,
                                        store.ownerPhone,
                                        store.businessNumber,
                                        store.bankName,
                                        store.bankAccount,
                                        set(Projections.constructor(StoreAdminListDTO.GoodsImageInfo.class,
                                            goodsImage.id,
                                            goodsImage.imageUrl,
                                            goodsImage.goodsName
                                        ))
                                ))
                );
    }

    @Override
    public Optional<Long> findOwnerIdByStoreId(final Long storeId) {
        return Optional.ofNullable(
                queryFactory.select(user.id)
                        .from(store)
                        .innerJoin(user).on(user.id.eq(store.user.id))
                        .where(store.id.eq(storeId))
                        .fetchOne()
        );
    }

    @Override
    public List<StoreImage> getStoreImageList(final Long storeId) {
        return queryFactory.selectFrom(storeImage)
                .innerJoin(store).on(store.id.eq(storeImage.store.id)).fetchJoin()
                .innerJoin(user).on(user.id.eq(store.user.id))
                .where(store.id.eq(storeId).and(user.userStatus.eq(UserStatus.ACTIVE)))
                .fetch();
    }

    @Override
    public Store getStoreAndStoreImages(final Long storeId) {
        return queryFactory.selectFrom(store)
                .leftJoin(storeImage).on(storeImage.store.id.eq(store.id)).fetchJoin()
                .innerJoin(user).on(user.id.eq(store.user.id))
                .where(store.id.eq(storeId).and(user.userStatus.eq(UserStatus.ACTIVE)))
                .fetchOne();
    }

    private BooleanExpression keywordCondition(final String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            return store.name.containsIgnoreCase(keyword);
        }
        return null;
    }

    private BooleanExpression radiusCondition(NumberExpression<Double> distance) {
        if (distance != null) {
            return distance.loe(LIMIT_KM);
        }
        return null;
    }

    private BooleanExpression availableNowCondition(SearchStoreListServiceRequest request) {
        if (Boolean.TRUE.equals(request.onlyAvailable())) {
            return stock.quantity.gt(0);
        }
        return null;
    }

    private OrderSpecifier<?>[] sortCondition(SearchSortType sortType, NumberExpression<Double> distance) {
        long dailySeed = LocalDateTime.now(ZoneId.of("Asia/Seoul")).toLocalDate().toEpochDay();
        OrderSpecifier<Integer> openStoreFirst = new CaseBuilder()
                .when(goods.saleStatus.eq(SaleStatus.ON).and(stock.quantity.gt(0))).then(0)
                .otherwise(1)
                .asc();
        OrderSpecifier<Double> randomOrder = Expressions
                .numberTemplate(Double.class, "abs(sin({0} + {1}))", store.id, dailySeed)
                .asc();

        if (sortType == null) {
            return new OrderSpecifier[]{openStoreFirst, randomOrder, store.id.desc()};
        }
        if (sortType == SearchSortType.RECENT_DESC) {
            return new OrderSpecifier[]{openStoreFirst, randomOrder, store.id.desc()};
        }
        if (sortType == SearchSortType.DISTANCE_ASC) {
            if (distance == null) {
                return new OrderSpecifier[]{store.createdAt.desc().nullsLast(), store.id.desc()};
            }
            return new OrderSpecifier[]{distance.asc(), store.id.desc()};
        }
        if (sortType == SearchSortType.PRICE_ASC) {
            return new OrderSpecifier[]{goods.salePrice.asc().nullsLast(), store.id.desc()};
        }
        if (sortType == SearchSortType.RATING_DESC) {
            return new OrderSpecifier[]{review.rating.avg().desc().nullsLast(), store.id.desc()};
        }
        if (sortType == SearchSortType.POPULAR_DESC) {
            return new OrderSpecifier[]{review.id.countDistinct().desc().nullsLast(), store.id.desc()};
        }
        return new OrderSpecifier[]{store.createdAt.desc().nullsLast(), store.id.desc()};
    }

    private String extractKeyFromUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return imageUrl;
        }
        int lastSlashIndex = imageUrl.lastIndexOf('/');
        return lastSlashIndex >= 0 ? imageUrl.substring(lastSlashIndex + 1) : imageUrl;
    }

    @Override
    public List<StoreSearchItemDTO> searchStores(final StoreSearchServiceRequest request) {
        BooleanBuilder conditions = new BooleanBuilder();
        conditions.and(store.approved.eq(APPROVED));
        conditions.and(user.userStatus.eq(UserStatus.ACTIVE));
        
        // 검색어 조건
        if (request.query() != null && !request.query().isBlank()) {
            conditions.and(store.name.containsIgnoreCase(request.query()));
        }
        
        // 커서 조건
        if (request.cursor() != null && !request.cursor().isBlank()) {
            try {
                CursorData cursorData = decodeCursor(request.cursor());
                if (request.sort().startsWith("-")) {
                    // 내림차순: createdAt < cursor의 createdAt OR (createdAt = cursor의 createdAt AND id < cursor의 id)
                    conditions.and(
                        store.createdAt.lt(cursorData.createdAt())
                            .or(store.createdAt.eq(cursorData.createdAt()).and(store.id.lt(cursorData.id())))
                    );
                } else {
                    // 오름차순: createdAt > cursor의 createdAt OR (createdAt = cursor의 createdAt AND id > cursor의 id)
                    conditions.and(
                        store.createdAt.gt(cursorData.createdAt())
                            .or(store.createdAt.eq(cursorData.createdAt()).and(store.id.gt(cursorData.id())))
                    );
                }
            } catch (Exception e) {
                // 잘못된 커서는 무시
            }
        }
        
        // 정렬 조건 결정
        OrderSpecifier<?> orderSpecifier = store.createdAt.desc();
        if (request.sort() != null) {
            if (request.sort().equals("-createdAt")) {
                orderSpecifier = store.createdAt.desc();
            } else if (request.sort().equals("+createdAt") || request.sort().equals("createdAt")) {
                orderSpecifier = store.createdAt.asc();
            }
        }
        
        // limit + 1 개 조회 (다음 페이지 존재 여부 확인용)
        int fetchLimit = request.limit() + 1;
        
        // Store ID 먼저 조회
        List<Long> storeIds = queryFactory
            .select(store.id)
            .from(store)
            .innerJoin(user).on(user.id.eq(store.user.id))
            .where(conditions)
            .orderBy(orderSpecifier, store.id.desc())
            .limit(fetchLimit)
            .fetch();
        
        if (storeIds.isEmpty()) {
            return List.of();
        }
        
        // transform을 사용하여 이미지를 Set으로 그룹화
        return queryFactory
            .from(store)
            .leftJoin(storeImage).on(storeImage.store.id.eq(store.id))
            .where(store.id.in(storeIds))
            .orderBy(orderSpecifier, store.id.desc())
            .transform(
                groupBy(store.id).list(
                    Projections.constructor(StoreSearchItemDTO.class,
                        store.id,
                        store.name,
                        set(storeImage.name),
                        store.address,
                        store.latitude,
                        store.longitude,
                        store.description,
                        store.createdAt
                    )
                )
            );
    }
    
    private CursorData decodeCursor(String cursor) {
        try {
            String decoded = new String(Base64.getDecoder().decode(cursor));
            String[] parts = decoded.split("_");
            if (parts.length == 2) {
                LocalDateTime createdAt = LocalDateTime.parse(parts[0]);
                Long id = Long.parseLong(parts[1]);
                return new CursorData(createdAt, id);
            }
        } catch (Exception e) {
            // 파싱 실패
        }
        throw new IllegalArgumentException("Invalid cursor format");
    }
    
    private record CursorData(LocalDateTime createdAt, Long id) {}
}
