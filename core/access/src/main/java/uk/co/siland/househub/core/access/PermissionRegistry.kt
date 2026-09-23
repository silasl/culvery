package uk.co.siland.househub.core.access

import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.househub.core.household.Role

@Singleton
class PermissionRegistry @Inject constructor(
    sources: Set<@JvmSuppressWildcards PermissionSource>,
) {
    private val byId: Map<String, PermissionDef> = buildMap {
        sources.flatMap { it.permissions }.forEach { def ->
            require(put(def.id, def) == null) { "Duplicate permission id ${def.id}" }
        }
    }

    fun require(id: String): PermissionDef =
        byId[id] ?: throw IllegalArgumentException("Unknown permission $id")

    fun isGranted(role: Role, id: String): Boolean = role in require(id).defaultRoles

    fun all(): Collection<PermissionDef> = byId.values
}
