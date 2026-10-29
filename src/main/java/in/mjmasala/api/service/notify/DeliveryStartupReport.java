package in.mjmasala.api.service.notify;

import in.mjmasala.api.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Says at startup whether one-time codes are actually being delivered.
 *
 * Silence here is the dangerous case: an operator who believes codes are going out when
 * they are only reaching a log file will not find out from a user until the user gives up.
 */
@Component
@Order(1)
public class DeliveryStartupReport implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DeliveryStartupReport.class);

    private final RoutingNotificationSender router;
    private final AppProperties properties;

    public DeliveryStartupReport(RoutingNotificationSender router, AppProperties properties) {
        this.router = router;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("One-time code delivery: {}", router.describeDelivery());

        if (properties.otp().exposeCode() && (router.describeDelivery().contains("on"))) {
            log.warn("""
                    OTP_EXPOSE_CODE is on while a real provider is configured. The code is being
                    returned in API responses as well as sent. Set OTP_EXPOSE_CODE=false.""");
        }
    }
}
