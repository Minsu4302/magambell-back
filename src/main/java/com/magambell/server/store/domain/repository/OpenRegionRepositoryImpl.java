package com.magambell.server.store.domain.repository;

import com.magambell.server.review.domain.enums.ReviewStatus;
import com.magambell.server.store.adapter.out.persistence.StoreDetailResponse;
import com.magambell.server.store.app.port.in.dto.OpenRegionDTO;
import com.magambell.server.store.app.port.in.request.OpenRegionListServiceRequest;
import com.magambell.server.store.app.port.in.request.SearchStoreListServiceRequest;
import com.magambell.server.store.app.port.out.dto.StoreDetailDTO;
import com.magambell.server.store.app.port.out.response.OpenRegionListDTO;
import com.magambell.server.store.app.port.out.response.OwnerStoreDetailDTO;
import com.magambell.server.store.app.port.out.response.StoreAdminListDTO;
import com.magambell.server.store.app.port.out.response.StoreListDTOResponse;
import com.magambell.server.store.domain.entity.Store;
import com.magambell.server.store.domain.entity.StoreImage;
import com.magambell.server.store.domain.enums.Approved;
import com.magambell.server.store.domain.enums.SearchSortType;
import com.magambell.server.user.domain.enums.UserStatus;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.magambell.server.goods.domain.entity.QGoods.goods;
import static com.magambell.server.order.domain.entity.QOrder.order;
import static com.magambell.server.order.domain.entity.QOrderGoods.orderGoods;
import static com.magambell.server.review.domain.entity.QReview.review;
import static com.magambell.server.stock.domain.entity.QStock.stock;
import static com.magambell.server.store.domain.entity.QOpenRegion.openRegion;
import static com.magambell.server.store.domain.entity.QStore.store;
import static com.magambell.server.store.domain.entity.QStoreImage.storeImage;
import static com.magambell.server.store.domain.enums.Approved.APPROVED;
import static com.magambell.server.store.domain.enums.Approved.WAITING;
import static com.magambell.server.user.domain.entity.QUser.user;
import static com.querydsl.core.group.GroupBy.*;
import static com.querydsl.core.types.ExpressionUtils.count;

@RequiredArgsConstructor
public class OpenRegionRepositoryImpl implements OpenRegionRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    @Override
    public List<OpenRegionListDTO> getOpenRegionList(OpenRegionListServiceRequest request, Pageable pageable) {
        return queryFactory
                .select(
                        Projections.constructor(
                                OpenRegionListDTO.class,
                                openRegion.region,
                                openRegion.user.id,
                                user.nickName
                        )
                )
                .from(openRegion)
                .innerJoin(user).on(user.id.eq(openRegion.user.id))
                .orderBy(openRegion.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
    }

}
