package uk.co.siland.culvery.capability.music

import uk.co.siland.culvery.core.house.PartyGroup

/**
 * The room that leads Party's group (13 §4.1): the leader holding the most chosen rooms, the layout's first on a tie, so the
 * fewest rooms move; else the first chosen room in the layout; null when no chosen room is in it.
 */
internal fun partyCoordinator(groups: List<SpeakerGroup>, chosen: Set<String>): String? =
    groups.filter { it.coordinatorId in chosen }
        .maxByOrNull { g -> g.rooms.count { it.id in chosen } }
        ?.coordinatorId
        ?: groups.flatMap { it.rooms }.firstOrNull { it.id in chosen }?.id

/** [made] is among [playing] with the same leader and rooms; what it plays doesn't matter (13 §2.11). */
internal fun stillParty(playing: List<SpeakerGroup>, made: PartyGroup): Boolean =
    playing.any { g -> g.coordinatorId == made.coordinatorId && g.rooms.map { it.id }.toSet() == made.roomIds }
