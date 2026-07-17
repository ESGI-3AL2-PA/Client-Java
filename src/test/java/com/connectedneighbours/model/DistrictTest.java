package com.connectedneighbours.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DistrictTest {

    @Test
    void defaultConstructor_createsEmptyDistrict() {
        District district = new District();
        assertNull(district.getId());
        assertNull(district.getName());
    }

    @Test
    void parameterizedConstructor_setsIdAndName() {
        District district = new District("d1", "Centre-ville");

        assertEquals("d1", district.getId());
        assertEquals("Centre-ville", district.getName());
    }

    @Test
    void settersAndGetters_workCorrectly() {
        District district = new District();

        district.setId("d2");
        district.setName("Quartier Nord");

        assertEquals("d2", district.getId());
        assertEquals("Quartier Nord", district.getName());
    }

    @Test
    void toString_containsIdAndName() {
        District district = new District("d1", "Centre-ville");
        String str = district.toString();
        assertTrue(str.contains("d1"));
        assertTrue(str.contains("Centre-ville"));
    }
}
