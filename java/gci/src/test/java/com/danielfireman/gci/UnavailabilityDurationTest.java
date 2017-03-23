package com.danielfireman.gci;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Clock;
import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

/**
 * @author danielfireman
 */
public class UnavailabilityDurationTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    Clock clock;


    @Test
    public void testDurationIsAlwaysNonNegative() {
        when(clock.millis())
                .thenReturn(1L)    // Start.
                .thenReturn(2L)    // End.
                .thenReturn(3L);   // Estimate.

        UnavailabilityDuration duration = new UnavailabilityDuration(clock);
        duration.begin();
        duration.end();
        assertEquals(Duration.ZERO, duration.estimate());

        verify(clock, times(2)).millis();
    }

    @Test
    public void testDuration() {
        when(clock.millis())
                .thenReturn(0L)
                .thenReturn(10L)
                .thenReturn(15L)
                .thenReturn(20L)
                .thenReturn(21L);

        UnavailabilityDuration duration = new UnavailabilityDuration(clock);
        duration.begin();
        duration.end();
        duration.begin();
        duration.end();
        assertEquals(21, duration.estimate().toMillis());

        verify(clock, times(4)).millis();
    }
}
