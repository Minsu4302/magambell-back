package com.magambell.server.store.app.port.in.dto;

import com.magambell.server.region.domain.entity.Region;
import com.magambell.server.user.domain.entity.User;

// JPA 엔티티를 그대로 전달하는 내부 커맨드 객체이므로 방어 복사 대신 경고 억제
@SuppressWarnings({"EI_EXPOSE_REP2", "EI_EXPOSE_REP"})
public record OpenRegionDTO(
        Region region,
        User user
) {

}
