// Generated code. Do not modify.

package com.yandex.div2

import org.json.JSONObject

class EntityWithPropertyWithDefaultValue(
    @JvmField final val int: Expression<Long> = INT_DEFAULT_VALUE, // constraint: number >= 0; default value: 0
    @JvmField final val nested: Nested? = null,
    @JvmField final val url: Expression<Uri> = URL_DEFAULT_VALUE, // valid schemes: [https]; default value: https://yandex.ru
) : Hashable {

    private var _hash: Int? = null 

    override fun hash(): Int {
        _hash?.let {
            return it
        }
        val hash = 
            int.hashCode() +
            (nested?.hash() ?: 0) +
            url.hashCode()
        _hash = hash
        return hash
    }

    companion object {
        const val TYPE = "entity_with_property_with_default_value"

        private val INT_DEFAULT_VALUE = Expression.constant(0L)
        private val URL_DEFAULT_VALUE = Expression.constant(Uri.parse("https://yandex.ru"))

        private val INT_VALIDATOR = ValueValidator<Long> { it: Long -> it >= 0 }
        private val URL_VALIDATOR = ValueValidator<Uri> { it.hasScheme(listOf("https")) }
    }


    class Nested(
        @JvmField final val int: Expression<Long> = INT_DEFAULT_VALUE, // constraint: number >= 0; default value: 0
        @JvmField final val nonOptional: Expression<String>,
        @JvmField final val url: Expression<Uri> = URL_DEFAULT_VALUE, // valid schemes: [https]; default value: https://yandex.ru
    ) : Hashable {

        private var _hash: Int? = null 

        override fun hash(): Int {
            _hash?.let {
                return it
            }
            val hash = 
                int.hashCode() +
                nonOptional.hashCode() +
                url.hashCode()
            _hash = hash
            return hash
        }

        companion object {
            private val INT_DEFAULT_VALUE = Expression.constant(0L)
            private val URL_DEFAULT_VALUE = Expression.constant(Uri.parse("https://yandex.ru"))

            private val INT_VALIDATOR = ValueValidator<Long> { it: Long -> it >= 0 }
            private val URL_VALIDATOR = ValueValidator<Uri> { it.hasScheme(listOf("https")) }
        }

    }
}
