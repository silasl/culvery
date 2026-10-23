package uk.co.siland.culvery.core.google.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import uk.co.siland.culvery.core.google.Authorizer
import uk.co.siland.culvery.core.google.GooglePlayServicesCheck
import uk.co.siland.culvery.core.google.PlayServicesAuthorizer
import uk.co.siland.culvery.core.google.PlayServicesCheck
import uk.co.siland.culvery.core.google.PlayServicesTokenSource
import uk.co.siland.culvery.core.google.TokenSource

@Module
@InstallIn(SingletonComponent::class)
abstract class GoogleModule {
    @Binds
    abstract fun tokens(impl: PlayServicesTokenSource): TokenSource

    @Binds
    abstract fun authorizer(impl: PlayServicesAuthorizer): Authorizer

    @Binds
    abstract fun playServices(impl: GooglePlayServicesCheck): PlayServicesCheck
}
