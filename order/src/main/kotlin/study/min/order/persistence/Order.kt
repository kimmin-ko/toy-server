package study.min.order.persistence

import jakarta.persistence.*

/**
 * 주문 엔티티
 */
@Entity
@Table(name = "`order`")
class Order : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null

    @Column(name = "order_number", nullable = false, unique = true, length = 50)
    var orderNumber: String = ""

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0

    @Column(name = "total_price", nullable = false)
    var totalPrice: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: OrderStatus = OrderStatus.PENDING

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var orderItems: MutableList<OrderItem> = mutableListOf()

    /**
     * 주문 상품 추가
     */
    fun addOrderItem(orderItem: OrderItem) {
        orderItems.add(orderItem)
        orderItem.order = this
    }

    /**
     * 주문 상품 제거
     */
    fun removeOrderItem(orderItem: OrderItem) {
        orderItems.remove(orderItem)
        orderItem.order = null
    }

    /**
     * 총 금액 계산
     */
    fun calculateTotalPrice() {
        totalPrice = orderItems.sumOf { it.price * it.quantity }
    }
}

/**
 * 주문 상태
 */
enum class OrderStatus {
    PENDING,    // 대기 중
    CONFIRMED,  // 확정
    CANCELLED   // 취소됨
}
