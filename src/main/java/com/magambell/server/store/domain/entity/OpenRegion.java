package com.magambell.server.store.domain.entity;

import com.magambell.server.common.BaseTimeEntity;
import com.magambell.server.favorite.domain.entity.Favorite;
import com.magambell.server.goods.domain.entity.Goods;
import com.magambell.server.notification.domain.entity.FcmToken;
import com.magambell.server.region.domain.entity.Region;
import com.magambell.server.review.app.port.in.dto.ReportReviewDTO;
import com.magambell.server.review.domain.entity.Review;
import com.magambell.server.review.domain.entity.ReviewReport;
import com.magambell.server.store.app.port.in.dto.OpenRegionDTO;
import com.magambell.server.store.app.port.in.dto.RegisterStoreDTO;
import com.magambell.server.store.domain.enums.Approved;
import com.magambell.server.store.domain.enums.Bank;
import com.magambell.server.user.domain.entity.User;
import com.magambell.server.user.domain.enums.UserRole;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class OpenRegion extends BaseTimeEntity {

    @Column(name = "open_region_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @Deprecated // 기존 데이터 호환성을 위해 유지
    private String region;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id")
    private Region regionEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder(access = AccessLevel.PRIVATE)
    public OpenRegion(Region regionEntity, User user) {
        this.regionEntity = regionEntity;
        this.user = user;
    }

    public static OpenRegion create(final OpenRegionDTO dto) {
        return OpenRegion.builder()
                .regionEntity(dto.region())
                .user(dto.user())
                .build();
    }

}
