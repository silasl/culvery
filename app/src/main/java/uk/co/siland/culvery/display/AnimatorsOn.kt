package uk.co.siland.culvery.display

import android.animation.ValueAnimator
import javax.inject.Inject

/** Whether Android's "Remove animations" is off, so animations may run. */
fun interface AnimatorsOn {
    fun now(): Boolean
}

class AndroidAnimatorsOn @Inject constructor() : AnimatorsOn {
    override fun now(): Boolean = ValueAnimator.areAnimatorsEnabled()
}
