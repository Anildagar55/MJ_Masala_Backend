package in.mjmasala.api.web;

import in.mjmasala.api.dto.catalogue.ComboCardDto;
import in.mjmasala.api.dto.catalogue.ComboDetailDto;
import in.mjmasala.api.service.CatalogueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/combos")
@Tag(name = "Combos", description = "Bundled boxes")
public class ComboController {

    private final CatalogueService catalogue;

    public ComboController(CatalogueService catalogue) {
        this.catalogue = catalogue;
    }

    @GetMapping
    @Operation(summary = "All active combos with their contents")
    public List<ComboCardDto> combos() {
        return catalogue.listCombos();
    }

    @GetMapping("/{slug}")
    @Operation(summary = "One combo")
    public ComboDetailDto combo(@PathVariable String slug) {
        return catalogue.getCombo(slug);
    }
}
