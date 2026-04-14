package com.magambell.server.stock.domain.entity;

import static com.magambell.server.stock.domain.enums.StockType.CANCEL;
import static com.magambell.server.stock.domain.enums.StockType.ORDER;

import com.magambell.server.common.BaseTimeEntity;
import com.magambell.server.common.enums.ErrorCode;
import com.magambell.server.common.exception.InvalidRequestException;
import com.magambell.server.goods.domain.entity.Goods;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Stock extends BaseTimeEntity {

    @Column(name = "stock_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    private Integer quantity;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goods_id")
    private Goods goods;

    private Stock(final Integer quantity) {
        this.quantity = quantity;
    }

    public static Stock create(Integer quantity) {
        return new Stock(quantity);
    }

    public void addGoods(final Goods goods) {
        this.goods = goods;
    }

    public void decrease(int amount) {
        if (amount <= 0) {
            throw new InvalidRequestException(ErrorCode.STOCK_INVALID_QUANTITY);
        }
        if (this.quantity < amount) {
            throw new InvalidRequestException(ErrorCode.STOCK_NOT_ENOUGH);
        }
        this.quantity -= amount;
    }

    public void increase(int amount) {
        if (amount <= 0) {
            throw new InvalidRequestException(ErrorCode.STOCK_INVALID_QUANTITY);
        }
        this.quantity += amount;
    }

    public StockHistory recordDecrease(final Goods goods, final Integer amount) {
        int beforeQuantity = this.quantity;
        decrease(amount);
        int afterQuantity = this.quantity;

        if (afterQuantity == 0) {
            goods.changeSaleStatusToOffBySystem();
        }

        StockHistory stockHistory = StockHistory.create(ORDER, beforeQuantity, amount, afterQuantity);
        goods.addStockHistory(stockHistory);

        return stockHistory;
    }

    public StockHistory restoreCancel(final Goods goods, final Integer quantity) {
        int beforeQuantity = this.quantity;
        increase(quantity);
        int afterQuantity = this.quantity;

        StockHistory stockHistory = StockHistory.create(CANCEL, beforeQuantity, quantity, afterQuantity);
        goods.addStockHistory(stockHistory);

        return stockHistory;
    }

    public void editQuantity(final Integer quantity) {
        this.quantity = quantity;
    }
}
