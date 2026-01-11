package study.min.product.service.redis

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import study.min.product.ProductApplication
import java.time.Duration
import kotlin.collections.get

// 테스트용 data class
// Jackson 역직렬화를 위한 @JsonCreator, @JsonProperty 추가
data class User @JsonCreator constructor(
    @JsonProperty("id") val id: Long,
    @JsonProperty("name") val name: String,
    @JsonProperty("email") val email: String
)

@SpringBootTest(classes = [ProductApplication::class])
class RedisServiceTest {

    @Autowired
    private lateinit var redisService: RedisService

    @DisplayName("String 타입 저장/조회 테스트")
    @Test
    fun putString() {
        // given
        val key = "test:string"
        val value = "hello redis"

        // when
        redisService.put(key, value)
        val result = redisService.get<String>(key)

        // then
        assertThat(result).isEqualTo(value)
    }

    @DisplayName("Int 타입 저장/조회 테스트")
    @Test
    fun putInt() {
        // given
        val key = "test:int"
        val value = 12345

        // when
        redisService.put(key, value)
        val result = redisService.get<Int>(key)

        // then
        assertThat(result).isEqualTo(value)
    }

    @DisplayName("Long 타입 저장/조회 테스트")
    @Test
    fun putLong() {
        // given
        val key = "test:long"
        val value = 9876543210L

        // when
        redisService.put(key, value)
        val result = redisService.get<Long>(key)

        // then
        assertThat(result).isEqualTo(value)
    }

    @DisplayName("Double 타입 저장/조회 테스트")
    @Test
    fun putDouble() {
        // given
        val key = "test:double"
        val value = 123.456

        // when
        redisService.put(key, value)
        val result = redisService.get<Double>(key)

        // then
        assertThat(result).isEqualTo(value)
    }

    @DisplayName("Boolean 타입 저장/조회 테스트")
    @Test
    fun putBoolean() {
        // given
        val key = "test:boolean"
        val value = true

        // when
        redisService.put(key, value)
        val result = redisService.get<Boolean>(key)

        // then
        assertThat(result).isEqualTo(value)
    }

    @DisplayName("List 타입 저장/조회 테스트")
    @Test
    fun putList() {
        // given
        val key = "test:list"
        val value = listOf("apple", "banana", "cherry")

        // when
        redisService.put(key, value)
        val result = redisService.get<List<*>>(key)

        // then
        assertThat(result).isEqualTo(value)
    }

    @DisplayName("Map 타입 저장/조회 테스트")
    @Test
    fun putMap() {
        // given
        val key = "test:map"
        val value = mapOf(
            "name" to "kim",
            "age" to 25,
            "city" to "seoul"
        )

        // when
        redisService.put(key, value)
        val result = redisService.get<Map<*, *>>(key)

        // then
        assertThat(result).isEqualTo(value)
    }

    @DisplayName("객체 저장/조회 테스트")
    @Test
    fun putObjectImproved() {
        // given
        val key = "test:user"
        val value = User(
            id = 1L,
            name = "lee",
            email = "lee@example.com"
        )

        // when
        redisService.put(key, value)
        val result = redisService.get<User>(key)

        // then
        assertThat(result).isNotNull
        assertThat(result!!.id).isEqualTo(1L)      // !!: null이 아님을 단언
        assertThat(result.name).isEqualTo("lee")   // 이후로는 !!없이 사용 가능
        assertThat(result.email).isEqualTo("lee@example.com")
    }

    @DisplayName("TTL과 함께 저장 테스트")
    @Test
    fun putWithTTL() {
        // given
        val key = "test:ttl"
        val value = "expire soon"
        val ttl = Duration.ofSeconds(10)

        // when
        redisService.put(key, value, ttl)
        val result = redisService.get<String>(key)

        // then
        assertThat(result).isEqualTo(value)
        assertThat(redisService.hasKey(key)).isTrue()
    }

    @DisplayName("null 값 조회 테스트")
    @Test
    fun getNonExistentKey() {
        // given
        val key = "test:non-existent"

        // when
        val result = redisService.get<String>(key)

        // then
        assertThat(result).isNull()
    }

    @DisplayName("데이터 삭제 테스트")
    @Test
    fun deleteKey() {
        // given
        val key = "test:delete"
        val value = "to be deleted"
        redisService.put(key, value)

        // when
        val deleted = redisService.delete(key)
        val result = redisService.get<String>(key)

        // then
        assertThat(deleted).isTrue()
        assertThat(result).isNull()
    }

    @DisplayName("Hash 데이터 저장/조회 테스트")
    @Test
    fun putAndGetHash() {
        // given
        val key = "test:user:hash"
        val hashKey1 = "name"
        val hashKey2 = "age"
        val hashKey3 = "city"

        // when
        redisService.putHash(key, hashKey1, "kim")
        redisService.putHash(key, hashKey2, 25)
        redisService.putHash(key, hashKey3, "seoul")

        val name = redisService.getHash(key, hashKey1)
        val age = redisService.getHash(key, hashKey2)
        val city = redisService.getHash(key, hashKey3)

        // then
        assertThat(name).isEqualTo("kim")
        assertThat(age).isEqualTo(25)
        assertThat(city).isEqualTo("seoul")
    }

    @DisplayName("Hash 전체 조회 테스트")
    @Test
    fun getAllHash() {
        // given
        val key = "test:product:hash"
        val hashData = mapOf(
            "id" to 1,
            "name" to "MacBook",
            "price" to 2000000,
            "stock" to 10
        )

        // when
        hashData.forEach { (hashKey, value) ->
            redisService.putHash(key, hashKey, value)
        }
        val result = redisService.getAllHash(key)

        // then
        assertThat(result).hasSize(4)
        assertThat(result["id"]).isEqualTo(1)
        assertThat(result["name"]).isEqualTo("MacBook")
        assertThat(result["price"]).isEqualTo(2000000)
        assertThat(result["stock"]).isEqualTo(10)
    }

    @DisplayName("Hash 필드 삭제 테스트")
    @Test
    fun deleteHash() {
        // given
        val key = "test:user:delete:hash"
        redisService.putHash(key, "name", "kim")
        redisService.putHash(key, "age", 25)
        redisService.putHash(key, "city", "seoul")

        // when
        val deletedCount = redisService.deleteHash(key, "age")
        val allHash = redisService.getAllHash(key)
        val deletedField = redisService.getHash(key, "age")

        // then
        assertThat(deletedCount).isEqualTo(1L)
        assertThat(allHash).hasSize(2)
        assertThat(allHash).containsKeys("name", "city")
        assertThat(allHash).doesNotContainKey("age")
        assertThat(deletedField).isNull()
    }

    @DisplayName("Hash 여러 필드 동시 삭제 테스트")
    @Test
    fun deleteMultipleHashFields() {
        // given
        val key = "test:user:multi:delete"
        redisService.putHash(key, "field1", "value1")
        redisService.putHash(key, "field2", "value2")
        redisService.putHash(key, "field3", "value3")
        redisService.putHash(key, "field4", "value4")

        // when
        redisService.deleteHash(key, "field1")
        redisService.deleteHash(key, "field3")
        val remainingHash = redisService.getAllHash(key)

        // then
        assertThat(remainingHash).hasSize(2)
        assertThat(remainingHash).containsKeys("field2", "field4")
        assertThat(remainingHash["field2"]).isEqualTo("value2")
        assertThat(remainingHash["field4"]).isEqualTo("value4")
    }

    @DisplayName("Hash에 객체 저장/조회 테스트")
    @Test
    fun putObjectInHash() {
        // given
        val key = "test:users:hash"
        val user1 = User(1L, "kim", "kim@example.com")
        val user2 = User(2L, "lee", "lee@example.com")

        // when
        redisService.putHash(key, "user:1", user1)
        redisService.putHash(key, "user:2", user2)

        val allUsers = redisService.getAllHash(key)
        val retrievedUser1 = redisService.getHash(key, "user:1")

        // then
        assertThat(allUsers).hasSize(2)
        assertThat(retrievedUser1).isNotNull()

        // RedisSerializer.json()은 객체를 LinkedHashMap으로 역직렬화
        val user1Map = retrievedUser1 as? Map<*, *>
        assertThat(user1Map).isNotNull
        assertThat(user1Map!!["id"]).isEqualTo(1)
        assertThat(user1Map["name"]).isEqualTo("kim")
        assertThat(user1Map["email"]).isEqualTo("kim@example.com")
    }
}