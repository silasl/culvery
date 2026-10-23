package uk.co.siland.culvery.capability.shopping

import javax.inject.Inject
import uk.co.siland.culvery.core.access.PermissionDef
import uk.co.siland.culvery.core.access.PermissionSource
import uk.co.siland.culvery.core.household.Role

object ShoppingPermissions {
    const val SUGGESTIONS = "shopping.suggestions"
}

/** Forgetting a favourite and clearing suggestions take an Admin's fresh PIN; adding and ticking are free (10 §4.7). */
class ShoppingPermissionSource @Inject constructor() : PermissionSource {
    override val permissions = listOf(
        PermissionDef(ShoppingPermissions.SUGGESTIONS, "Clear suggestions", setOf(Role.ADMIN), freshPin = true),
    )
}
