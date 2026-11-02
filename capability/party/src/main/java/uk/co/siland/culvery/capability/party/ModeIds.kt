package uk.co.siland.culvery.capability.party

import kotlin.random.Random
import uk.co.siland.culvery.core.house.HouseModes
import uk.co.siland.culvery.core.ui.Icons

/** Party mode's icon since 13, kept by the move (15b §4.4). */
const val PARTY_ICON = Icons.CELEBRATION

private const val ID_LETTERS = 6
private val RESERVED = setOf(HouseModes.HOLIDAY, HouseModes.PARTY, HouseModes.STORED)

/** A new stored mode's id (15b Ruling 7): "mode" and six letters a–z, drawn again while it clashes with [taken] or a reserved id. */
fun newModeId(taken: Set<String>, random: Random = Random.Default): String {
    while (true) {
        val id = "mode" + String(CharArray(ID_LETTERS) { 'a' + random.nextInt(26) })
        if (id !in taken && id !in RESERVED) return id
    }
}
