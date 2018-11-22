package com.wix.detox.espresso

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.Timing
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.assertj.core.api.Assertions.assertThat
import org.joor.Reflect
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import java.util.*
import kotlin.Comparator

fun now() = System.nanoTime() / 1000000L

fun aTimer(interval: Int, isRepeating: Boolean) = aTimer(now() + interval + 10, interval, isRepeating)

fun aTimer(targetTime: Long, interval: Int, isRepeating: Boolean): Any {
    val timerClass = Class.forName("com.facebook.react.modules.core.Timing\$Timer")
    return Reflect.on(timerClass).create(-1, targetTime, interval, isRepeating).get()
}

class ReactNativeTimersIdlingResourceTest {

    private lateinit var reactAppContext: ReactApplicationContext
    private lateinit var pendingTimers: PriorityQueue<Any>

    @Before fun setUp() {
        pendingTimers = PriorityQueue(2, {_, _ -> 0})

        val timersNativeModule: Timing = mock()
        Reflect.on(timersNativeModule).set("mTimers", pendingTimers)

        reactAppContext = mock {
            on { hasNativeModule<Timing>(ArgumentMatchers.any()) }.doReturn(true)
            on { getNativeModule<Timing>(ArgumentMatchers.any()) }.doReturn(timersNativeModule)
        }
    }

    @Test fun `should be idle if there are no timers in queue`() {
        assertThat(uut().isIdleNow).isTrue()
    }

    @Test fun `should be busy if there's a pending timer`() {
        givenOneShotTimer(1500)
        assertThat(uut().isIdleNow).isFalse()
    }

    @Test fun `should be idle if pending timer is far away`() {
        givenOneShotTimer(1501)
        assertThat(uut().isIdleNow).isTrue()
    }

    @Test fun `should be idle if the only timer is a repeating one`() {
        givenRepeatingTimer(1500)
        assertThat(uut().isIdleNow).isTrue()
    }

    @Test fun `should be busy if a pending timer lies beyond a repeating one`() {
        givenRepeatingTimer(100)
        givenOneShotTimer(1499)
        assertThat(uut().isIdleNow).isFalse()
    }

    @Test fun `should be idle if the only timer is overdue`() {
        givenOverdueTimer()
        assertThat(uut().isIdleNow).isTrue()
    }

    @Test fun `should be busy if a pending timer lies beyond an overdue timer`() {
        givenOverdueTimer()
        givenOneShotTimer(123)
        assertThat(uut().isIdleNow).isFalse()
    }

    private fun uut() = ReactNativeTimersIdlingResourceKT(reactAppContext)

    private fun givenOneShotTimer(interval: Int) = givenTimer(interval, false)

    private fun givenRepeatingTimer(interval: Int) = givenTimer(interval, true)

    private fun givenTimer(interval: Int, repeating: Boolean) {
        pendingTimers.add(aTimer(interval, repeating))
    }

    private fun givenOverdueTimer() {
        val timer = aTimer(now() - 100, 123, false)
        pendingTimers.add(timer)
    }
}
