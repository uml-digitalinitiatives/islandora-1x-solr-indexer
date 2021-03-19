package ca.umanitoba.dam.islandora.fc3indexer.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.regex.Pattern;

public class ISODateFormatter {

    final static String frac = "([.,][0-9]+)";

    final static String sec_el = "(\\:[0-9]{2}" + frac + "?)";

    final static String min_el = "(\\:[0-9]{2}(" + frac + "|" + sec_el + "?))";

    final static String time_el = "([0-9]{2}(" + frac + "|" + min_el + "))";

    final static String time_offset = "(Z|[+-]" + time_el + ")";

    final static String time_pattern = "(T" + time_el + time_offset + "?)";

    final static String day_el = "(-[0-9]{2})";

    final static String month_el = "(-[0-9]{2}" + day_el + ")";

    final static String date_el = "([0-9]{4}" + month_el + ")";

    final static String iso_date_format = "(" + date_el + time_pattern + "?)";

    final static ZoneId zone = ZoneId.systemDefault();

    public static String parseDate(final String dateValue) {
        final TemporalAccessor date;
        if (Pattern.matches("([0-9]{1,2}/)([0-9]{1,2}/)([0-9]{4})", dateValue)) {
            date =
                DateTimeFormatter.ofPattern("M/d/y").withZone(zone).parse(dateValue);
            return LocalDate.from(date).format(DateTimeFormatter.ISO_DATE);
        } else if (Pattern.matches("([0-9]{4}/)([0-9]{1,2}/)([0-9]{1,2})", dateValue)) {
            date =
                DateTimeFormatter.ofPattern("y/M/d").withZone(zone).parse(dateValue);
            return LocalDate.from(date).format(DateTimeFormatter.ISO_DATE);
        } else if (Pattern.matches("([0-9]{4}-)([0-9]{1,2}-)([0-9]{1,2})", dateValue)) {
            date =
                DateTimeFormatter.ofPattern("y-M-d").withZone(zone).parse(dateValue);
            return LocalDate.from(date).format(DateTimeFormatter.ISO_DATE);
        } else if (Pattern.matches("[0-9]{4}/[0-9]{1,2}/[0-9]{1,2} [0-9]{1,2}:[0-9]{2}:([0-9]{2})?", dateValue)) {
            date = DateTimeFormatter.ofPattern("y/M/d H:m:s").withZone(zone)
                .parse(dateValue);
            return LocalDateTime.from(date).format(DateTimeFormatter.ISO_DATE_TIME);
        } else if (Pattern.matches("([0-9]{1,2}/)([0-9]{1,2}/)([0-9]{4}) ([0-9]{1,2}:[0-9]{2})", dateValue)) {
            date = DateTimeFormatter.ofPattern("M/d/y H:m").withZone(zone)
                .parse(dateValue);
            return LocalDateTime.from(date).format(DateTimeFormatter.ISO_DATE_TIME);
        } else if (Pattern.matches("([0-9]{4})(-[0-9]{1,2})(-[0-9]{1,2}) [0-9]{1,2}:[0-9]{2}(:[0-9]{2})?",
            dateValue)) {
            date = DateTimeFormatter.ofPattern("y-M-d H:m:s").withZone(zone)
                .parse(dateValue);
            return LocalDateTime.from(date).format(DateTimeFormatter.ISO_DATE_TIME);
        } else if (Pattern.matches(iso_date_format, dateValue)) {
            date = DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse(dateValue);
            return LocalDateTime.from(date).format(DateTimeFormatter.ISO_DATE_TIME);
        } else {
            return null;
        }

    }
}
