package uk.co.siland.culvery.shell

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShellToastsTest {
    private val toasts = ShellToasts()

    @Test
    fun aNewToastReplacesTheCurrentOne() {
        toasts.show("First")
        toasts.show("Second", "delete")
        assertThat(toasts.current.value?.let { it.message to it.icon }).isEqualTo("Second" to "delete")
    }

    @Test
    fun theDefaultIconIsInfo() {
        toasts.show("Event deleted")
        assertThat(toasts.current.value?.icon).isEqualTo("info")
    }

    @Test
    fun hidingAnOlderToastKeepsTheNewerOne() {
        toasts.show("First")
        val first = toasts.current.value!!.id
        toasts.show("Second")
        toasts.hide(first)
        assertThat(toasts.current.value?.message).isEqualTo("Second")
        toasts.hide(toasts.current.value!!.id)
        assertThat(toasts.current.value).isNull()
    }
}
