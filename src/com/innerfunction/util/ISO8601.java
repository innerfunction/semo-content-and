package com.innerfunction.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

/**
 * Helper class for handling ISO 8601 strings of the following format:
 * "2008-03-01T13:00:00+01:00". It also supports parsing the "Z" timezone.
 */
public final class ISO8601 {
    
    /** Transform Calendar to ISO 8601 string. */
    public static String fromCalendar(final Calendar calendar) {
        Date date = calendar.getTime();
        String formatted = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault() ).format(date);
        return formatted.substring(0, 22) + ":" + formatted.substring(22);
    }

    /** Get current date and time formatted as ISO 8601 string. */
    public static String now() {
        return fromCalendar(GregorianCalendar.getInstance());
    }

    /**
     * Transform ISO 8601 string to Date.
     */
    public static Date toDate(final String iso8601string) throws ParseException {
        // Replace Z or UTC at the end of the string with the time offset for UTC.
        String s = iso8601string.replaceFirst("(Z|UTC)$", "+00:00");
        // Replace a space after the date with T.
        s = s.replaceFirst("^(\\d\\d\\d\\d-\\d\\d-\\d\\d) ","$1T");
        // Remove a space after the time.
        s = s.replaceFirst("(T\\d\\d:\\d\\d:\\d\\d) ","$1");
        try {
            s = s.substring(0, 22) + s.substring(23);
        }
        catch (IndexOutOfBoundsException e) {
            throw new org.apache.http.ParseException("Invalid length");
        }
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault() ).parse(s);
    }

    /** Transform ISO 8601 string to Calendar. 
     * @throws java.text.ParseException */
    public static Calendar toCalendar(final String iso8601string) throws ParseException {
        Calendar calendar = GregorianCalendar.getInstance();
        Date date = ISO8601.toDate( iso8601string );
        calendar.setTime(date);
        return calendar;
    }
}