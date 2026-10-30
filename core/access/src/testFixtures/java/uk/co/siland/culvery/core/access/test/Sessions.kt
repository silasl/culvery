package uk.co.siland.culvery.core.access.test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role

/** Alex, signed in with [role]. */
fun signedIn(role: Role) = Identified(Person(PersonId("alex"), "Alex", 0), role)

/** Waits for every job launched in this scope, so a check that nothing happened can't pass by being early. */
fun CoroutineScope.settled() = runBlocking { coroutineContext.job.children.toList().forEach { it.join() } }
