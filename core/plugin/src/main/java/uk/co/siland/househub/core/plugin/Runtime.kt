package uk.co.siland.househub.core.plugin

import javax.inject.Qualifier

fun interface WallClock {
    fun nowMillis(): Long
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
