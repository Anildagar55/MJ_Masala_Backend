package in.mjmasala.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI at /swagger-ui.html, with a Bearer field so protected routes can be tried. */
@Configuration
public class OpenApiConfig {

    private static final String SCHEME = "bearerAuth";

    @Bean
    public OpenAPI mjMasalaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MJ MASALA API")
                        .version("1.0.0")
                        .description("""
                                Backend for the MJ MASALA storefront: catalogue, reviews, server-side
                                cart and wishlist, coupons, orders, enquiries and JWT auth.

                                Sign in with POST /auth/login, copy the accessToken, then use the
                                Authorize button with the value `Bearer <token>`.
                                """))
                .components(new Components().addSecuritySchemes(SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME));
    }
}
