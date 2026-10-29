package in.mjmasala.api.service;

import in.mjmasala.api.dto.shipping.PincodeDto;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Pin-code serviceability.
 *
 * IMPORTANT: this is a stand-in. There is no courier contract behind it — the rule below
 * is a plausible shape (all Indian pins served, ETA by zone, cash on delivery everywhere
 * except a few far-flung prefixes) so the storefront has something real to render. Replace
 * {@link #check} with a call to the courier's pincode API before launch; nothing else in
 * the codebase needs to change, because the response shape is already what the UI expects.
 */
@Service
public class ShippingService {

    /** Prefixes where we would not offer cash on delivery. */
    private static final Set<Character> NO_COD_PREFIXES = Set.of('1', '7');

    /** First digit → zone, which is how the ETA is estimated. */
    private static final Set<Character> METRO_PREFIXES = Set.of('1', '4', '5', '6');

    public PincodeDto check(String pin) {
        if (pin == null || !pin.matches("^[1-9][0-9]{5}$")) {
            return new PincodeDto(pin, false, 0, false,
                    "Enter a 6-digit Indian pin code");
        }
        char zone = pin.charAt(0);
        int etaDays = METRO_PREFIXES.contains(zone) ? 2 : 4;
        boolean cod = !NO_COD_PREFIXES.contains(zone);

        return new PincodeDto(pin, true, etaDays, cod,
                "Delivery in " + etaDays + "–" + (etaDays + 2) + " working days"
                        + (cod ? " · Cash on delivery available" : " · Prepaid only"));
    }
}
