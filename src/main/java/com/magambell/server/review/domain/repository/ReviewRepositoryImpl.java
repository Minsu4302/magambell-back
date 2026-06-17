package com.magambell.server.review.domain.repository;

import com.magambell.server.order.domain.enums.OrderStatus;
import com.magambell.server.review.app.port.in.request.ReviewListServiceRequest;
import com.magambell.server.review.app.port.in.request.ReviewRatingAllServiceRequest;
import com.magambell.server.review.app.port.out.response.ReviewListDTO;
import com.magambell.server.review.app.port.out.response.ReviewRatingSummaryDTO;
import com.magambell.server.review.domain.enums.ReviewStatus;
import com.magambell.server.user.domain.enums.UserStatus;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static com.magambell.server.goods.domain.entity.QGoods.goods;
import static com.magambell.server.order.domain.entity.QOrder.order;
import static com.magambell.server.order.domain.entity.QOrderGoods.orderGoods;
import static com.magambell.server.review.domain.entity.QReview.review;
import static com.magambell.server.review.domain.entity.QReviewImage.reviewImage;
import static com.magambell.server.review.domain.entity.QReviewReport.reviewReport;
import static com.magambell.server.store.domain.entity.QStore.store;
import static com.magambell.server.user.domain.entity.QUser.user;
import static com.querydsl.core.group.GroupBy.groupBy;
import static com.querydsl.core.group.GroupBy.set;

@RequiredArgsConstructor
public class ReviewRepositoryImpl implements ReviewRepositoryCustom {

    private final JPAQueryFactory queryFactory;


    @Override
    public List<ReviewListDTO> getReviewList(final ReviewListServiceRequest request, final Pageable pageable) {
        BooleanBuilder conditions = new BooleanBuilder();
        conditions.and(goods.id.eq(request.goodsId()));
        conditions.and(user.userStatus.eq(UserStatus.ACTIVE));
        conditions.and(order.orderStatus.eq(OrderStatus.COMPLETED));
        conditions.and(review.reviewStatus.eq(ReviewStatus.ACTIVE));
        if (request.imageCheck()) {
            conditions.and(reviewImage.isNotNull());
        }

        return getReviewListDTOS(pageable, conditions, request.userId());
    }

    @Override
    public ReviewRatingSummaryDTO getReviewRatingAll(final ReviewRatingAllServiceRequest request) {
        BooleanBuilder conditions = new BooleanBuilder();
        conditions.and(goods.id.eq(request.goodsId()));
        conditions.and(user.userStatus.eq(UserStatus.ACTIVE));
        conditions.and(order.orderStatus.eq(OrderStatus.COMPLETED));
        conditions.and(review.reviewStatus.eq(ReviewStatus.ACTIVE));
        if (request.imageCheck()) {
            conditions.and(reviewImage.isNotNull());
        }

        NumberPath<Long> ratingCount = Expressions.numberPath(Long.class, "ratingCount");

        List<Tuple> results = queryFactory
                .select(review.rating, review.id.countDistinct().as(ratingCount))
                .from(review)
                .leftJoin(reviewImage).on(reviewImage.review.id.eq(review.id))
                .innerJoin(orderGoods).on(orderGoods.id.eq(review.orderGoods.id))
                .innerJoin(order).on(order.id.eq(orderGoods.order.id))
                .innerJoin(goods).on(goods.id.eq(orderGoods.goods.id))
                .innerJoin(store).on(store.id.eq(goods.store.id))
                .innerJoin(user).on(user.id.eq(review.user.id))
                .where(conditions)
                .groupBy(review.rating)
                .fetch();

        long totalCount = 0;
        long ratingSum = 0;
        long rating1 = 0, rating2 = 0, rating3 = 0;

        for (Tuple tuple : results) {
            Integer rating = tuple.get(review.rating);
            Long count = tuple.get(ratingCount);

            if (rating != null && count != null) {
                totalCount += count;
                ratingSum += rating * count;

                switch (rating) {
                    case 1 -> rating1 = count;
                    case 2 -> rating2 = count;
                    case 3 -> rating3 = count;
                    default -> { /* 3점 평점 시스템: 1~3 외 값은 무시 */ }
                }
            }
        }

        double averageRating = totalCount > 0 ? Math.round(((double) ratingSum / totalCount) * 10.0) / 10.0 : 0.0;

        return new ReviewRatingSummaryDTO(
                averageRating,
                totalCount,
                rating1,
                rating2,
                rating3
        );
    }

    @Override
    public List<ReviewListDTO> getReviewListByUser(final Long userId, final Pageable pageable) {
        BooleanBuilder conditions = new BooleanBuilder();
        conditions.and(review.user.id.eq(userId));
        conditions.and(review.reviewStatus.eq(ReviewStatus.ACTIVE));
        return getReviewListDTOS(pageable, conditions, null);
    }

    private List<ReviewListDTO> getReviewListDTOS(final Pageable pageable, final BooleanBuilder conditions, final Long userId) {
        // userId가 존재한다면, 해당 유저가 신고한 reviewId 목록 제외
        if (userId != null) {
            List<Long> reportedReviewIds = queryFactory
                    .select(reviewReport.review.id)
                    .from(reviewReport)
                    .where(reviewReport.user.id.eq(userId))
                    .fetch();

            if (!reportedReviewIds.isEmpty()) {
                conditions.and(review.id.notIn(reportedReviewIds));
            }
        }

        List<Long> reviewIds = queryFactory
                .select(review.id)
                .from(review)
                .leftJoin(reviewImage).on(reviewImage.review.id.eq(review.id))
                .innerJoin(orderGoods).on(orderGoods.id.eq(review.orderGoods.id))
                .innerJoin(order).on(order.id.eq(orderGoods.order.id))
                .innerJoin(goods).on(goods.id.eq(orderGoods.goods.id))
                .innerJoin(store).on(store.id.eq(goods.store.id))
                .innerJoin(user).on(user.id.eq(review.user.id))
                .where(conditions)
                .orderBy(review.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return queryFactory
                .select(review, reviewImage, order, orderGoods, goods, store, user)
                .from(review)
                .leftJoin(reviewImage).on(reviewImage.review.id.eq(review.id))
                .innerJoin(orderGoods).on(orderGoods.id.eq(review.orderGoods.id))
                .innerJoin(order).on(order.id.eq(orderGoods.order.id))
                .innerJoin(goods).on(goods.id.eq(orderGoods.goods.id))
                .innerJoin(store).on(store.id.eq(goods.store.id))
                .innerJoin(user).on(user.id.eq(review.user.id))
                .where(review.id.in(reviewIds))
                .orderBy(review.createdAt.desc())
                .transform(
                        groupBy(review.id)
                                .list(
                                        Projections.constructor(
                                                ReviewListDTO.class,
                                                review.id,
                                                review.rating,
                                                review.description,
                                                review.createdAt,
                                                set(reviewImage.name),
                                                user.nickName,
                                                goods.id,
                                                store.id,
                                                store.name
                                        )
                                )
                );
    }
}
