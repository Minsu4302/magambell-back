package com.magambell.server.goods.domain.entity;

import static com.magambell.server.goods.domain.enums.SaleStatus.OFF;
import static com.magambell.server.goods.domain.enums.SaleStatus.ON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.magambell.server.common.exception.InvalidRequestException;
import com.magambell.server.goods.adapter.in.web.GoodsImagesRegister;
import com.magambell.server.goods.app.port.in.dto.RegisterGoodsDTO;
import com.magambell.server.store.domain.entity.Store;
import com.magambell.server.user.domain.entity.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoodsTest {

    @Mock private Store store;
    @Mock private User user;

    private static final LocalDateTime START = LocalDateTime.of(2025, 1, 1, 9, 0);
    private static final LocalDateTime END   = LocalDateTime.of(2025, 1, 1, 18, 0);

    private Goods createGoods(int quantity) {
        RegisterGoodsDTO dto = new RegisterGoodsDTO(
                "테스트 상품", START, END,
                quantity, 10000, 10, 9000,
                null,
                List.of(new GoodsImagesRegister(0, "key", "https://img.test/a.jpg", "상품명"))
        );
        Goods goods = dto.toGoods();
        goods.addStore(store);
        return goods;
    }

    // ── validateTime ─────────────────────────────────────────────────────

    @Test
    @DisplayName("startTime이 endTime보다 늦으면 예외가 발생한다")
    void create_invalidTime_throwsException() {
        RegisterGoodsDTO dto = new RegisterGoodsDTO(
                "시간오류 상품",
                END,    // start > end
                START,
                5, 10000, 10, 9000, null, List.of()
        );

        assertThatThrownBy(dto::toGoods)
                .isInstanceOf(InvalidRequestException.class);
    }

    // ── changeStatus ──────────────────────────────────────────────────────

    @Test
    @DisplayName("changeStatus: 매장 소유자가 아니면 예외가 발생한다")
    void changeStatus_notOwner_throwsException() {
        when(store.isOwnedBy(user)).thenReturn(false);
        Goods goods = createGoods(5);

        assertThatThrownBy(() -> goods.changeStatus(user, ON, LocalDateTime.now()))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("changeStatus: ON 설정 시 재고가 0이면 예외가 발생한다")
    void changeStatus_onWithZeroStock_throwsException() {
        when(store.isOwnedBy(user)).thenReturn(true);
        Goods goods = createGoods(0);

        assertThatThrownBy(() -> goods.changeStatus(user, ON, LocalDateTime.now()))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("changeStatus: ON 설정 시 현재 시각이 판매 종료 시간 이후면 다음 날로 조정된다")
    void changeStatus_onAfterEndTime_adjustsDateToNextDay() {
        when(store.isOwnedBy(user)).thenReturn(true);
        Goods goods = createGoods(5);
        LocalDate today = START.toLocalDate();
        LocalDateTime now = today.atTime(20, 0);  // 20:00 > 18:00(endTime)

        goods.changeStatus(user, ON, now);

        assertThat(goods.getStartTime().toLocalDate()).isEqualTo(today.plusDays(1));
        assertThat(goods.getEndTime().toLocalDate()).isEqualTo(today.plusDays(1));
        assertThat(goods.getSaleStatus()).isEqualTo(ON);
    }

    @Test
    @DisplayName("changeStatus: ON 설정 시 현재 시각이 판매 종료 시간 이전이면 날짜가 그대로 유지된다")
    void changeStatus_onBeforeEndTime_keepsSameDate() {
        when(store.isOwnedBy(user)).thenReturn(true);
        Goods goods = createGoods(5);
        LocalDate today = START.toLocalDate();
        LocalDateTime now = today.atTime(10, 0);  // 10:00 < 18:00(endTime)

        goods.changeStatus(user, ON, now);

        assertThat(goods.getStartTime().toLocalDate()).isEqualTo(today);
        assertThat(goods.getSaleStatus()).isEqualTo(ON);
    }

    @Test
    @DisplayName("changeStatus: OFF 설정 시 재고 체크 없이 상태가 변경된다")
    void changeStatus_off_changesStatusWithoutStockCheck() {
        when(store.isOwnedBy(user)).thenReturn(true);
        Goods goods = createGoods(0);

        goods.changeStatus(user, OFF, LocalDateTime.now());

        assertThat(goods.getSaleStatus()).isEqualTo(OFF);
    }

    // ── editQuantity ──────────────────────────────────────────────────────

    @Test
    @DisplayName("editQuantity: null 수량은 예외가 발생한다")
    void editQuantity_nullQuantity_throwsException() {
        Goods goods = createGoods(5);

        assertThatThrownBy(() -> goods.editQuantity(null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("editQuantity: 0 이하 수량은 예외가 발생한다")
    void editQuantity_zeroOrNegative_throwsException() {
        Goods goods = createGoods(5);

        assertThatThrownBy(() -> goods.editQuantity(0))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> goods.editQuantity(-1))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("editQuantity: 기존 수량과 같으면 재고 이력을 생성하지 않는다")
    void editQuantity_sameQuantity_noStockHistory() {
        Goods goods = createGoods(5);
        int initialHistorySize = goods.getStockHistory().size();

        goods.editQuantity(5);

        assertThat(goods.getStockHistory()).hasSize(initialHistorySize);
        assertThat(goods.getStockQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("editQuantity: 다른 수량이면 재고 이력을 생성하고 수량을 변경한다")
    void editQuantity_differentQuantity_createsStockHistory() {
        Goods goods = createGoods(5);
        int initialHistorySize = goods.getStockHistory().size();

        goods.editQuantity(10);

        assertThat(goods.getStockHistory()).hasSize(initialHistorySize + 1);
        assertThat(goods.getStockQuantity()).isEqualTo(10);
    }

    // ── editByAdmin ───────────────────────────────────────────────────────

    @Test
    @DisplayName("editByAdmin: 수량이 기존과 같으면 재고 이력을 생성하지 않는다")
    void editByAdmin_sameQuantity_noStockHistory() {
        Goods goods = createGoods(5);
        int initialHistorySize = goods.getStockHistory().size();

        goods.editByAdmin("수정상품", START, END, 15000, 10, 13500, 5, ON);

        assertThat(goods.getStockHistory()).hasSize(initialHistorySize);
        assertThat(goods.getName()).isEqualTo("수정상품");
    }

    @Test
    @DisplayName("editByAdmin: 수량이 다르면 재고 이력을 생성하고 수량을 변경한다")
    void editByAdmin_differentQuantity_createsStockHistory() {
        Goods goods = createGoods(5);
        int initialHistorySize = goods.getStockHistory().size();

        goods.editByAdmin("수정상품", START, END, 15000, 10, 13500, 20, ON);

        assertThat(goods.getStockHistory()).hasSize(initialHistorySize + 1);
        assertThat(goods.getStockQuantity()).isEqualTo(20);
    }

    @Test
    @DisplayName("editByAdmin: quantity가 null이면 재고를 변경하지 않는다")
    void editByAdmin_nullQuantity_noStockChange() {
        Goods goods = createGoods(5);
        int initialHistorySize = goods.getStockHistory().size();

        goods.editByAdmin("수정상품", START, END, 15000, 10, 13500, null, ON);

        assertThat(goods.getStockHistory()).hasSize(initialHistorySize);
        assertThat(goods.getStockQuantity()).isEqualTo(5);
    }
}
