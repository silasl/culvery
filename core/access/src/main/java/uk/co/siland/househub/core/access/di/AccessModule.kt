package uk.co.siland.househub.core.access.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import uk.co.siland.househub.core.access.AccessControl
import uk.co.siland.househub.core.access.CorePermissionSource
import uk.co.siland.househub.core.access.DefaultAccessControl
import uk.co.siland.househub.core.access.PermissionSource

@Module
@InstallIn(SingletonComponent::class)
abstract class AccessModule {
    @Binds
    abstract fun accessControl(impl: DefaultAccessControl): AccessControl

    @Multibinds
    abstract fun permissionSources(): Set<PermissionSource>

    @Binds
    @IntoSet
    abstract fun corePermissions(impl: CorePermissionSource): PermissionSource
}
