package com.magambell.server.goods.domain.entity;

import static com.magambell.server.goods.domain.enums.SaleStatus.OFF;
import static com.magambell.server.goods.domain.enums.SaleStatus.ON;

import com.magambell.server.common.BaseTimeEntity;
import com.magambell.server.common.enums.ErrorCode;
import com.magambell.server.common.exception.InvalidRequestException;
import com.magambell.server.goods.app.port.in.dto.EditGoodsDTO;
import com.magambell.server.goods.app.port.in.dto.RegisterGoodsDTO;
import com.magambell.server.goods.domain.enums.SaleStatus;
import com.magambell.server.stock.domain.enums.StockType;
import com.magambell.server.stock.domain.entity.Stock;
import com.magambell.server.stock.domain.entity.StockHistory;
import com.magambell.server.store.domain.entity.Store;
import com.magambell.server.user.domain.entity.User;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Goods extends BaseTimeEntity {

    @Column(name = "goods_id")
    @Tsid
    @Id
    private Long id;
    private String name;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer originalPrice;
    private Integer discount;
    private Integer salePrice;
    private String description;

    @Enumerated(EnumType.STRING)
    private SaleStatus saleStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @OneToOne(mappedBy = "goods", cascade = CascadeType.ALL)
    private Stock stock;

    @OneToMany(mappedBy = "goods", cascade = CascadeType.ALL)
    private List<StockHistory> stockHistory = new ArrayList<>();

    @Builder(access = AccessLevel.PRIVATE)
    private Goods(final String name, final LocalDateTime startTime, final LocalDateTime endTime,
                  final Integer originalPrice,
                  final Integer discount,
                  final Integer salePrice, final String description, final SaleStatus saleStatus) {
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.originalPrice = originalPrice;
        this.discount = discount;
        this.salePrice = salePrice;
        this.description = description;
        this.saleStatus = saleStatus;
    }

    public static Goods create(RegisterGoodsDTO dto) {
        Goods goods = Goods.builder()
                .name(dto.name())
                .startTime(dto.startTime())
                .endTime(dto.endTime())
                .originalPrice(dto.originalPrice())
                .discount(dto.discount())
                .salePrice(dto.salePrice())
                .saleStatus(OFF)
                .build();

        goods.validateTime(dto.startTime(), dto.endTime());

        Stock stock = Stock.create(dto.quantity());
        StockHistory stockHistory = dto.toStock();
        goods.addStock(stock);
        goods.addStockHistory(stockHistory);
        return goods;
    }

    public void addStore(final Store store) {
        this.store = store;
    }

    public void addStock(final Stock stock) {
        this.stock = stock;
        stock.addGoods(this);
    }

    public void decreaseStock(int amount) {
        this.stock.decrease(amount);
    }

    public int getStockQuantity() {
        return this.stock.getQuantity();
    }

    public void addStockHistory(final StockHistory stockHistory) {
        this.stockHistory.add(stockHistory);
        stockHistory.addGoods(this);
    }

    public void changeStatus(final User user, final SaleStatus saleStatus, final LocalDateTime today) {
        if (!this.store.isOwnedBy(user)) {
            throw new InvalidRequestException(ErrorCode.INVALID_GOODS_OWNER);
        }
        if (saleStatus == ON) {
            checkStock();
            adjustDatesToTodayIfNeeded(today);
        }
        this.saleStatus = saleStatus;
    }

    public void edit(final EditGoodsDTO dto) {
        validateTime(dto.startTime(), dto.endTime());

        this.name = dto.name();
        this.startTime = dto.startTime();
        this.endTime = dto.endTime();
        this.originalPrice = dto.originalPrice();
        this.discount = dto.discount();
        this.salePrice = dto.salePrice();

        StockHistory stockHistory = StockHistory.create(StockType.MANUAL, this.stock.getQuantity(), dto.quantity(),
                dto.quantity());
        this.stock.editQuantity(dto.quantity());
        this.addStockHistory(stockHistory);
    }

    public int editQuantity(final Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new InvalidRequestException(ErrorCode.STOCK_INVALID_QUANTITY);
        }

        int beforeQuantity = this.stock.getQuantity();
        if (!quantity.equals(beforeQuantity)) {
            StockHistory stockHistory = StockHistory.create(StockType.MANUAL, beforeQuantity, quantity, quantity);
            this.stock.editQuantity(quantity);
            this.addStockHistory(stockHistory);
        }

        return beforeQuantity;
    }

    public void changeSaleStatusToOffBySystem() {
        this.saleStatus = OFF;
    }

    public void editByAdmin(final String name, final LocalDateTime startTime, final LocalDateTime endTime,
                           final Integer originalPrice, final Integer discount, final Integer salePrice,
                           final Integer quantity, final SaleStatus saleStatus) {
        validateTime(startTime, endTime);

        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.originalPrice = originalPrice;
        this.discount = discount;
        this.salePrice = salePrice;
        this.saleStatus = saleStatus;

        if (quantity != null && !quantity.equals(this.stock.getQuantity())) {
            StockHistory stockHistory = StockHistory.create(StockType.MANUAL, this.stock.getQuantity(), quantity, quantity);
            this.stock.editQuantity(quantity);
            this.addStockHistory(stockHistory);
        }
    }

    private void validateTime(final LocalDateTime start, final LocalDateTime end) {
        if (!start.isBefore(end)) {
            throw new InvalidRequestException(ErrorCode.TIME_VALID);
        }
    }

    private void checkStock() {
        if (this.stock.getQuantity() == 0) {
            throw new InvalidRequestException(ErrorCode.STOCK_NOT_ENOUGH);
        }
    }

    private void adjustDatesToTodayIfNeeded(final LocalDateTime today) {
        LocalTime currentTime = today.toLocalTime();
        LocalDate baseDate = today.toLocalDate();

        LocalTime start = this.startTime.toLocalTime();
        LocalTime end = this.endTime.toLocalTime();

        if (currentTime.isAfter(end)) {
            baseDate = today.toLocalDate().plusDays(1);
        }

        this.startTime = LocalDateTime.of(baseDate, start);
        this.endTime = LocalDateTime.of(baseDate, end);
    }
}
