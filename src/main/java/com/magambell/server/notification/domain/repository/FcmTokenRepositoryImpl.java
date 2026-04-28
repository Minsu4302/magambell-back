package com.magambell.server.notification.domain.repository;

import static com.magambell.server.notification.domain.entity.QFcmToken.fcmToken;
import static com.magambell.server.store.domain.entity.QStore.store;
import static com.magambell.server.user.domain.entity.QUser.user;

import com.magambell.server.notification.app.port.out.dto.FcmTokenDTO;
import com.magambell.server.user.domain.enums.UserStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FcmTokenRepositoryImpl implements FcmTokenRepositoryCustom {

    private final JPAQueryFactory queryFactory;


    @Override
    public List<FcmTokenDTO> findWithAllByStoreId(final Long storeId) {
        return queryFactory
                .select(
                        Projections.constructor(FcmTokenDTO.class,
                                fcmToken.id,
                                fcmToken.token,
                                user.id,
                                user.nickName,
                                store.name
                        )
                )
                .from(fcmToken)
                .innerJoin(user).on(user.id.eq(fcmToken.user.id))
                .innerJoin(store).on(store.id.eq(fcmToken.store.id))
                .where(store.id.eq(storeId))
                .fetch();
    }

    @Override
    public long countByStoreId(final Long storeId) {
        Long count = queryFactory
                .select(fcmToken.id.count())
                .from(fcmToken)
                .where(fcmToken.store.id.eq(storeId))
                .fetchOne();

        return count == null ? 0L : count;
    }

    @Override
    public FcmTokenDTO findWithAllByUserIdAndStoreIsNull(final Long userId) {
        return queryFactory
                .select(
                        Projections.constructor(FcmTokenDTO.class,
                                fcmToken.id,
                                fcmToken.token,
                                user.id,
                                user.nickName,
                                store.name
                        )
                )
                .from(fcmToken)
                .innerJoin(user).on(user.id.eq(fcmToken.user.id))
                .leftJoin(store).on(store.id.eq(fcmToken.store.id))
                .where(user.id.eq(userId)
                        .and(user.userStatus.eq(UserStatus.ACTIVE))
                        .and(fcmToken.store.isNull()))
                .fetchOne();
    }

    @Override
    public List<FcmTokenDTO> findWithAllByUsersIdsAndStoreIsNull(final List<Long> userList) {
        return queryFactory
                .select(
                        Projections.constructor(FcmTokenDTO.class,
                                fcmToken.id,
                                fcmToken.token,
                                user.id,
                                user.nickName,
                                store.name
                        )
                )
                .from(fcmToken)
                .innerJoin(user).on(user.id.eq(fcmToken.user.id))
                .leftJoin(store).on(store.id.eq(fcmToken.store.id))
                .where(user.id.in(userList)
                        .and(user.userStatus.eq(UserStatus.ACTIVE))
                        .and(fcmToken.store.isNull()))
                .fetch();
    }
}
