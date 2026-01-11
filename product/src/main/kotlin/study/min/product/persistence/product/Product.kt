package study.min.product.persistence.product

import jakarta.persistence.*

@Entity
@Table(name = "product")
open class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    open var id: Long? = null

    @Column(name = "name", nullable = false)
    open var name: String? = null

    @Column(name = "price", nullable = false)
    open var price: Int? = null

}