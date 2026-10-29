package in.mjmasala.api.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.mjmasala.api.domain.Category;
import in.mjmasala.api.domain.Combo;
import in.mjmasala.api.domain.ComboItem;
import in.mjmasala.api.domain.ComboSpec;
import in.mjmasala.api.domain.Coupon;
import in.mjmasala.api.domain.CouponType;
import in.mjmasala.api.domain.Product;
import in.mjmasala.api.domain.ProductKind;
import in.mjmasala.api.domain.ProductPack;
import in.mjmasala.api.domain.ProductSpec;
import in.mjmasala.api.domain.Review;
import in.mjmasala.api.domain.Role;
import in.mjmasala.api.domain.User;
import in.mjmasala.api.repository.CategoryRepository;
import in.mjmasala.api.repository.ComboRepository;
import in.mjmasala.api.repository.CouponRepository;
import in.mjmasala.api.repository.ProductRepository;
import in.mjmasala.api.repository.ReviewRepository;
import in.mjmasala.api.repository.UserRepository;
import in.mjmasala.api.service.ReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads the storefront catalogue into an empty database.
 *
 * The JSON in {@code resources/seed} is exported from the frontend's own data modules
 * rather than retyped, so the API and the UI agree on every price, name and review
 * instead of drifting apart. Runs once: if any product already exists it does nothing.
 *
 * Turn it off with {@code app.seed.enabled=false} once the client's real data is in.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    /** Jar colours, matching the packshots the storefront ships. */
    private static final Map<String, String[]> JAR = Map.ofEntries(
            Map.entry("turmeric-powder", new String[]{"#E0AA1E", "black"}),
            Map.entry("red-chilli-powder", new String[]{"#BC2A1A", "black"}),
            Map.entry("jeera-whole", new String[]{"#98784C", "gold"}),
            Map.entry("coriander-powder", new String[]{"#927A40", "black"}),
            Map.entry("garam-masala", new String[]{"#623E24", "black"}),
            Map.entry("kitchen-king-masala", new String[]{"#822E1A", "black"}),
            Map.entry("chana-masala", new String[]{"#723216", "black"}),
            Map.entry("sambar-masala", new String[]{"#763A1C", "black"}),
            Map.entry("pav-bhaji-masala", new String[]{"#7E2C18", "black"}),
            Map.entry("black-pepper", new String[]{"#343230", "gold"}),
            Map.entry("green-cardamom", new String[]{"#84A052", "gold"}),
            Map.entry("cloves", new String[]{"#62402E", "gold"}),
            Map.entry("cinnamon", new String[]{"#9E602E", "gold"}),
            Map.entry("star-anise", new String[]{"#725230", "gold"}),
            Map.entry("bay-leaf", new String[]{"#5A6C3C", "gold"}),
            Map.entry("mustard-seeds", new String[]{"#7E6E36", "gold"}),
            Map.entry("fenugreek-seeds", new String[]{"#9C7A40", "gold"}));

    private final AppProperties properties;
    private final ObjectMapper json;
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ComboRepository combos;
    private final ReviewRepository reviews;
    private final CouponRepository coupons;
    private final UserRepository users;
    private final ReviewService reviewService;
    private final PasswordEncoder encoder;

    public DataSeeder(AppProperties properties, ObjectMapper json, ProductRepository products,
                      CategoryRepository categories, ComboRepository combos, ReviewRepository reviews,
                      CouponRepository coupons, UserRepository users, ReviewService reviewService,
                      PasswordEncoder encoder) {
        this.properties = properties;
        this.json = json;
        this.products = products;
        this.categories = categories;
        this.combos = combos;
        this.reviews = reviews;
        this.coupons = coupons;
        this.users = users;
        this.reviewService = reviewService;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (properties.seed() == null || !properties.seed().enabled()) {
            log.info("Seeding disabled (app.seed.enabled=false)");
            return;
        }
        if (products.count() > 0) {
            log.info("Catalogue already present ({} products) — skipping seed", products.count());
            return;
        }

        JsonNode catalogue = read("seed/catalogue.json");
        JsonNode reviewData = read("seed/reviews.json");

        Map<String, Category> shelves = seedCategories();
        Map<String, Product> bySlug = seedProducts(catalogue.path("products"), shelves);
        seedCombos(catalogue.path("combos"), bySlug);
        int reviewCount = seedReviews(reviewData.path("reviews"), bySlug);
        seedCoupons();
        seedAdmin();

        reviewService.recalculateAll();

        log.info("Seeded {} products, {} combos, {} reviews",
                products.count(), combos.count(), reviewCount);
    }

    /* ------------------------------------------------------------------ */

    private JsonNode read(String path) throws Exception {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return json.readTree(in);
        }
    }

    private Map<String, Category> seedCategories() {
        Map<String, Category> map = new HashMap<>();
        int order = 0;
        for (String[] def : new String[][]{
                {"masala", "Masala & Blends"},
                {"whole", "Whole Spices"},
                {"combos", "Combos"}}) {
            Category category = new Category();
            category.setSlug(def[0]);
            category.setName(def[1]);
            category.setSortOrder(order++);
            map.put(def[0], categories.save(category));
        }
        return map;
    }

    private Map<String, Product> seedProducts(JsonNode list, Map<String, Category> shelves) {
        Map<String, Product> bySlug = new HashMap<>();
        int order = 0;

        for (JsonNode node : list) {
            String slug = node.path("slug").asText();
            Product product = new Product();
            product.setSlug(slug);
            product.setName(node.path("name").asText());
            product.setHindi(node.path("hindi").asText(null));
            product.setKind(parseKind(node.path("kind").asText("POWDER")));
            product.setCategory(shelves.getOrDefault(
                    node.path("category").asText("Masala").equalsIgnoreCase("Whole") ? "whole" : "masala",
                    shelves.get("masala")));
            product.setOrigin(node.path("origin").asText(null));
            product.setBlurb(node.path("blurb").asText(null));
            product.setDescription(node.path("description").asText(null));
            product.setIngredients(node.path("ingredients").asText(null));
            product.setUsage(node.path("usage").asText(null));
            product.setShelf(node.path("shelf").asText(null));
            product.setBestseller(node.path("bestseller").asBoolean(false));
            product.setActive(true);
            product.setSortOrder(order++);
            product.setImageKey(slug);

            String[] jar = JAR.getOrDefault(slug, new String[]{"#C8A44E", "black"});
            product.setSpiceRgb(jar[0]);
            product.setLid(jar[1]);

            for (JsonNode badge : node.path("badges")) {
                product.getBadges().add(badge.asText());
            }

            int packOrder = 0;
            for (JsonNode packNode : node.path("packs")) {
                ProductPack pack = new ProductPack();
                pack.setProduct(product);
                pack.setLabel(packNode.path("g").asText());
                pack.setWeightGrams(gramsFrom(packNode.path("g").asText()));
                pack.setPrice(BigDecimal.valueOf(packNode.path("p").asDouble()));
                pack.setMrp(BigDecimal.valueOf(packNode.path("mrp").asDouble()));
                pack.setSku(slug + "-" + pack.getWeightGrams() + "g");
                pack.setActive(true);
                pack.setSortOrder(packOrder++);
                product.getPacks().add(pack);
            }

            int specOrder = 0;
            for (JsonNode spec : node.path("specs")) {
                ProductSpec row = new ProductSpec();
                row.setProduct(product);
                row.setLabel(spec.get(0).asText());
                row.setValue(spec.get(1).asText());
                row.setSortOrder(specOrder++);
                product.getSpecs().add(row);
            }

            product.recalculatePackAggregates();
            bySlug.put(slug, products.save(product));
        }
        return bySlug;
    }

    private void seedCombos(JsonNode list, Map<String, Product> bySlug) {
        int order = 0;
        for (JsonNode node : list) {
            Combo combo = new Combo();
            combo.setSlug(node.path("slug").asText());
            combo.setName(node.path("name").asText());
            combo.setHindi(node.path("hindi").asText(null));
            combo.setBlurb(node.path("blurb").asText(null));
            combo.setDescription(node.path("description").asText(null));
            combo.setImageKey(comboImageKey(node.path("slug").asText()));
            combo.setPrice(BigDecimal.valueOf(node.path("price").asDouble()));
            combo.setMrp(BigDecimal.valueOf(node.path("mrp").asDouble()));
            combo.setBestseller(node.path("bestseller").asBoolean(false));
            combo.setActive(true);
            combo.setSortOrder(order++);

            for (JsonNode badge : node.path("badges")) {
                combo.getBadges().add(badge.asText());
            }

            int itemOrder = 0;
            for (JsonNode content : node.path("contents")) {
                String[] parsed = splitContent(content.asText(), bySlug);
                if (parsed == null) {
                    log.warn("Could not resolve combo content '{}' — skipped", content.asText());
                    continue;
                }
                ComboItem item = new ComboItem();
                item.setCombo(combo);
                item.setProduct(bySlug.get(parsed[0]));
                item.setPackLabel(parsed[1]);
                item.setQuantity(1);
                item.setSortOrder(itemOrder++);
                combo.getItems().add(item);
            }

            int specOrder = 0;
            for (JsonNode spec : node.path("specs")) {
                ComboSpec row = new ComboSpec();
                row.setCombo(combo);
                row.setLabel(spec.get(0).asText());
                row.setValue(spec.get(1).asText());
                row.setSortOrder(specOrder++);
                combo.getSpecs().add(row);
            }

            combos.save(combo);
        }
    }

    private int seedReviews(JsonNode list, Map<String, Product> bySlug) {
        List<Review> batch = new ArrayList<>();
        for (JsonNode node : list) {
            Product product = bySlug.get(node.path("productSlug").asText());
            if (product == null) continue;

            Review review = new Review();
            review.setProduct(product);
            review.setUser(null);                 // imported review, no account behind it
            review.setAuthorName(node.path("authorName").asText());
            review.setCity(node.path("city").asText(null));
            review.setRating(node.path("rating").asInt());
            review.setTitle(node.path("title").asText(null));
            review.setBody(node.path("body").asText(null));
            review.setVerified(node.path("verified").asBoolean(true));
            review.setHelpfulCount(node.path("helpfulCount").asInt());
            review.setApproved(true);
            batch.add(review);
        }
        List<Review> saved = reviews.saveAll(batch);
        log.info("Seeded {} reviews", saved.size());
        return saved.size();
    }

    private void seedCoupons() {
        coupon("MJ10", CouponType.PERCENT, 10, 0, null, "10% off everything");
        coupon("MJ15", CouponType.PERCENT, 15, 999, null, "15% off orders above ₹999");
        coupon("COMBO20", CouponType.PERCENT, 20, 1499, 500, "20% off orders above ₹1499");
        coupon("FIRST50", CouponType.FLAT, 50, 499, null, "₹50 off your first order");
    }

    private void coupon(String code, CouponType type, long value, long minSubtotal,
                        Integer maxDiscount, String description) {
        if (coupons.findByCodeIgnoreCase(code).isPresent()) return;
        Coupon coupon = new Coupon();
        coupon.setCode(code);
        coupon.setType(type);
        coupon.setValue(BigDecimal.valueOf(value));
        coupon.setMinSubtotal(BigDecimal.valueOf(minSubtotal));
        coupon.setMaxDiscount(maxDiscount == null ? null : BigDecimal.valueOf(maxDiscount));
        coupon.setActive(true);
        coupon.setDescription(description);
        coupons.save(coupon);
    }

    /**
     * The first admin. Override the password in the environment — the default is here so a
     * fresh clone is usable and is logged loudly so nobody ships it.
     */
    private void seedAdmin() {
        String email = System.getenv().getOrDefault("ADMIN_EMAIL", "admin@mjmasala.in").toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) return;

        String password = System.getenv().getOrDefault("ADMIN_PASSWORD", "ChangeMe@123");
        User admin = new User();
        admin.setName("MJ Masala Admin");
        admin.setEmail(email);
        admin.setPasswordHash(encoder.encode(password));
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);
        users.save(admin);

        if ("ChangeMe@123".equals(password)) {
            log.warn("Seeded admin {} with the DEFAULT password. Set ADMIN_PASSWORD before going live.", email);
        } else {
            log.info("Seeded admin {}", email);
        }
    }

    /* ------------------------------ helpers ------------------------------ */

    private ProductKind parseKind(String raw) {
        try {
            return ProductKind.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ProductKind.POWDER;
        }
    }

    private int gramsFrom(String label) {
        String digits = label.replaceAll("[^0-9]", "");
        int value = digits.isEmpty() ? 100 : Integer.parseInt(digits);
        return label.toLowerCase(Locale.ROOT).contains("kg") ? value * 1000 : value;
    }

    /** "Turmeric Powder 100 g" → ["turmeric-powder", "100 g"] */
    private String[] splitContent(String content, Map<String, Product> bySlug) {
        for (Product product : bySlug.values()) {
            if (content.startsWith(product.getName())) {
                String rest = content.substring(product.getName().length()).trim();
                return new String[]{product.getSlug(), rest.isEmpty() ? "100 g" : rest};
            }
        }
        return null;
    }

    private String comboImageKey(String slug) {
        return switch (slug) {
            case "complete-kitchen-combo" -> "combo_kitchen6";
            case "everyday-masala-combo" -> "combo_masala5";
            case "golden-trio" -> "combo_trio";
            case "whole-spice-starter" -> "combo_whole4";
            case "daily-cooking-combo" -> "combo_everyday4";
            case "signature-collection" -> "lineup_hero";
            default -> "combo_duo";
        };
    }

    /** Kept for the README's "seed at" note. */
    public static Instant seededAt() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
