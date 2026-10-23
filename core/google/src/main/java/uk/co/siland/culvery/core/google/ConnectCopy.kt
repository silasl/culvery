package uk.co.siland.culvery.core.google

/** A service that couldn’t be reached says check the Wi-Fi; any other failure, try again. */
fun couldNotConnect(service: String, unreachable: Boolean): String =
    if (unreachable) "Couldn’t reach $service — check the tablet’s Wi-Fi, then try again." else "Couldn’t connect to $service — try again."
