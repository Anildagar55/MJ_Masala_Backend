package in.mjmasala.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * MJ MASALA API.
 *
 * Storefront backend: catalogue, reviews, server-side cart and wishlist, coupons,
 * orders, wholesale enquiries and JWT auth. See README.md for the endpoint map and
 * how the React frontend is switched over to it.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableJpaAuditing
public class MjMasalaApplication {

    public static void main(String[] args) {
        SpringApplication.run(MjMasalaApplication.class, args);
    }
}
