import java.math.BigDecimal;
import java.time.Instant;

public class NanoTimer {
    static String getTime() {
        Instant instant = Instant.now();
        int nanoSuffix = (instant.getNano() / 1000) % 1000;
        BigDecimal currTime = new BigDecimal(System.currentTimeMillis() / 1e3 + nanoSuffix / 1e6);
        return String.format("%.7f", currTime);
    }
}
