package uk.co.siland.culvery.core.setup

import uk.co.siland.culvery.core.household.Role

// 4a design §4. Ruling 11 and 18 name the strings the spec doesn't give.

// Wizard frame (§4.1, §3.4)
internal const val BACK = "Back"
internal const val SKIP_FOR_NOW = "Skip for now"
internal const val ENTER_PIN = "Enter PIN"

// Welcome, You, Household, Done (§4.2)
internal const val START = "Start"
internal const val WELCOME = "Welcome to Culvery"
internal const val WELCOME_LINE = "Your home's location, the people who live here, and your calendars."
internal const val USE_SAMPLE_HOUSEHOLD = "Use a sample household"
internal const val WHOS_SETTING_UP = "Who's setting this up?"
internal const val SET_YOUR_PIN = "Set your PIN"
internal const val WHO_ELSE = "Who else lives here?"
internal const val CULVERY_IS_READY = "Culvery is ready"
internal const val OPEN_CULVERY = "Open Culvery"

// Home location (§4.3)
internal const val WHERES_HOME = "Where's home?"
internal const val TOWN_OR_CITY = "Town or city"
internal const val USED_FOR = "Used for the time zone, and for weather."
internal const val COULD_NOT_SEARCH = "Couldn't search for towns — check the tablet's Wi-Fi and try again."

internal fun noTownsMatch(query: String): String = "No towns match \"$query\"."

// People (§4.4)
internal const val ADD_PERSON = "Add person"
internal const val PIN_SET = "PIN set"
internal const val NO_PIN = "No PIN"
internal const val NAME = "Name"
internal const val COLOUR = "Colour"
internal const val ROLE = "Role"
internal const val PIN = "PIN"
internal const val SET_PIN = "Set PIN"
internal const val CHANGE_PIN = "Change PIN"
internal const val REMOVE_PIN = "Remove PIN"
internal const val SAVE_PERSON = "Save person"
internal const val SAVE_CHANGES = "Save changes"
internal const val REMOVE_PERSON = "Remove person"
internal const val KEEP = "Keep"
internal const val COLOUR_TAKEN = "That colour is taken."
internal const val PIN_TAKEN = "That PIN is taken — choose another."
internal const val ADMIN_NEEDS_PIN = "An Admin needs a PIN."
internal const val NEEDS_AN_ADMIN = "Culvery needs at least one Admin with a PIN."

internal fun someoneCalled(name: String): String = "Someone is already called $name."

internal fun removeQuestion(name: String): String = "Remove $name? $name's events and calendars show as Family."

internal fun removed(name: String): String = "$name removed"

internal fun roleName(role: Role): String = when (role) {
    Role.ADMIN -> "Admin"
    Role.ADULT -> "Adult"
    Role.CHILD -> "Child"
}

internal fun roleLine(role: Role): String = when (role) {
    Role.ADMIN -> "Admin — can change settings and people"
    Role.ADULT -> "Adult — can add and change any event"
    Role.CHILD -> "Child — can add their own events"
}

// Settings (§4.6, §4.7)
internal const val SETTINGS = "Settings"
internal const val CLOSE = "Close"
internal const val HOME_LOCATION = "Home location"
internal const val PEOPLE = "People"
internal const val KIOSK = "Kiosk"
internal const val EXIT_KIOSK = "Exit kiosk"
internal const val KIOSK_LINE = "Culvery keeps the tablet on this app. Exit to use other apps; it locks again next time Culvery opens."
