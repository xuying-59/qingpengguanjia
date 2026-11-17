package com.example.qimo413;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CropProfileTest {
    @Test public void allRequestedCropsHaveUsableTemperatureProfiles() {
        assertEquals(6, CropProfile.all().size());
        for (CropProfile profile : CropProfile.all()) {
            assertTrue(profile.temperatureMin < profile.temperatureMax);
            assertTrue(profile.temperatureMin >= 0f);
            assertTrue(profile.temperatureMax <= 50f);
        }
    }

    @Test public void legacyTomatoNameResolvesToCurrentProfile() {
        assertEquals("西红柿", CropProfile.forName("番茄").name);
    }
}
