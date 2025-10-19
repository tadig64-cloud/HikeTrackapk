
package com.hikemvp.group

import kotlin.random.Random

object GroupPin {
    fun generate4(): String = (Random.nextInt(0, 10000)).toString().padStart(4, '0')
}
