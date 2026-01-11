package study.min.product.persistence.product

import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction

@Entity
@Table(name = "product_stock")
open class ProductStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    open var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "product_id", nullable = false)
    open var product: Product? = null

    @ColumnDefault("0")
    @Column(name = "quantity", nullable = false)
    open var quantity: Int? = null

    fun decrease(quantity: Int) {
        require(quantity > 0) { "quantity must be greater than 0" }
        val currentQuantity = this.quantity ?: 0
        require(currentQuantity >= quantity) { "재고가 부족합니다. 현재 재고: $currentQuantity, 요청 수량: $quantity" }
        this.quantity = currentQuantity - quantity
    }

}