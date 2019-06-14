package ca.umanitoba.dam.islandora.fc3indexer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ISODateFormatterTests {

    @Test
    public void testDate() throws Exception {
        final String input = "1923/03/4";
        final String output = "1923-03-04";
        assertEquals("Date only did not match", output, ISODateFormatter.parseDate(input));
    }

    @Test
    public void testDateTime() throws Exception {
        final String input = "1934/06/12 17:48:28";
        final String output = "1934-06-12T17:48:28";
        assertEquals("Date time did not match", output, ISODateFormatter.parseDate(input));
    }

    @Test
    public void testDate2() throws Exception {
        final String input = "1923-03-4";
        final String output = "1923-03-04";
        assertEquals("Format did not match expected", output, ISODateFormatter.parseDate(input));
    }

    @Test
    public void testDateTime2() throws Exception {
        final String input = "1934-6-12 17:48:28";
        final String output = "1934-06-12T17:48:28";
        assertEquals("Format did not match expected", output, ISODateFormatter.parseDate(input));
    }

    @Test
    public void testDate3() throws Exception {
        final String input = "1919";
        final String output = null;
        assertEquals("Format did not match expected", output, ISODateFormatter.parseDate(input));
    }
}
