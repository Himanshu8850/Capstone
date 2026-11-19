package com.bookygeek.category.controller;

import com.bookygeek.catalogue.dto.Brand;
import com.bookygeek.catalogue.service.CatalogueService;
import com.bookygeek.category.dto.CategoryNode;
import com.bookygeek.category.service.CategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code listCategories} and {@code listBrands} exactly as in {@code api/openapi.yaml}. Both are public (no login).
 * A brand is a book's publisher, so the brand list is read through {@link CatalogueService#listPublishers()}; the
 * controller composes the two services so that no service depends on the other in a cycle.
 */
@RestController
@RequestMapping("/api/v1")
public class CategoryController {

    private final CategoryService categoryService;
    private final CatalogueService catalogueService;

    public CategoryController(CategoryService categoryService, CatalogueService catalogueService) {
        this.categoryService = categoryService;
        this.catalogueService = catalogueService;
    }

    @GetMapping("/categories")
    public List<CategoryNode> listCategories() {
        return categoryService.tree();
    }

    @GetMapping("/brands")
    public List<Brand> listPublishers() {
        return catalogueService.listPublishers();
    }
}
