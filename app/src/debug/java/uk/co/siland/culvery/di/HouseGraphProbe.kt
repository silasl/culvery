package uk.co.siland.culvery.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.Optional
import uk.co.siland.culvery.capability.holiday.HolidayHouse
import uk.co.siland.culvery.capability.lights.Bridges
import uk.co.siland.culvery.capability.music.Speakers
import uk.co.siland.culvery.capability.party.PartyHouse
import uk.co.siland.culvery.core.house.HouseHeating
import uk.co.siland.culvery.core.house.HouseHoover
import uk.co.siland.culvery.core.house.HouseLights
import uk.co.siland.culvery.core.house.HouseMode
import uk.co.siland.culvery.core.house.HouseModeInfo
import uk.co.siland.culvery.core.house.HouseModeSwitch
import uk.co.siland.culvery.core.house.HouseMusic
import uk.co.siland.culvery.core.house.HousePlugs
import uk.co.siland.culvery.core.house.MultiDayEvents
import uk.co.siland.culvery.core.plugin.Startable
import uk.co.siland.culvery.core.ui.ShowingTheme
import uk.co.siland.culvery.display.SeasonalThemes

/** Debug builds only: lets the unit test read what the real graph gives Holiday mode and Party mode (11 §4.2, 13 §6a). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface HouseGraphProbe {
    fun holidayHouse(): HolidayHouse
    fun partyHouse(): PartyHouse
    fun houseMode(): HouseMode
    fun lights(): Optional<HouseLights>
    fun heating(): Optional<HouseHeating>
    fun hoover(): Optional<HouseHoover>
    fun plugs(): Optional<HousePlugs>
    fun modes(): Set<@JvmSuppressWildcards HouseModeInfo>
    fun switches(): Set<@JvmSuppressWildcards HouseModeSwitch>
    fun music(): Optional<HouseMusic>
    fun speakers(): Speakers
    fun bridges(): Bridges
    fun multiDayEvents(): Optional<MultiDayEvents>
    fun startables(): Set<@JvmSuppressWildcards Startable>
    fun showingTheme(): ShowingTheme
    fun seasonalThemes(): SeasonalThemes
}
