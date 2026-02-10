package study.min.order.persistence

import jakarta.persistence.*

/**
 * 주문 상품 엔티티
 */
@Entity
@Table(name = "order_item")
class OrderItem : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = ForeignKey(ConstraintMode.NO_CONSTRAINT))
    var order: Order? = null

    @Column(name = "product_id", nullable = false)
    var productId: Long = 0

    @Column(name = "quantity", nullable = false)
    var quantity: Int = 0

    @Column(name = "price", nullable = false)
    var price: Long = 0

    /**
     * 상품별 총 금액 계산
     */
    fun getTotalPrice(): Long {
        return price * quantity
    }
}
