package com.liftley.sync360.domain.model

import kotlin.random.Random

object FileReceiveCode {
    const val DIGIT_COUNT = 4

    fun generate(): String {
        return Random.nextInt(
            from = 1_000,
            until = 10_000
        ).toString()
    }

    fun isValid(code: String): Boolean {
        return code.length == DIGIT_COUNT && code.all { character ->
            character in '0'..'9'
        }
    }
}
