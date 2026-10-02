package uk.co.siland.culvery.provider.calendar_google

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** 4c design §5.3: what the tablet says when Play services can't run Google's sign-in. */
const val UPDATE_PLAY_SERVICES = "Update Google Play services on this tablet, then try again."

/** Whether Play services can run Google's sign-in now (4c design §5.3, M4), behind a seam for tests. */
fun interface PlayServicesCheck {
    fun usable(): Boolean
}

class GooglePlayServicesCheck @Inject constructor(@ApplicationContext private val context: Context) : PlayServicesCheck {
    override fun usable(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
}
