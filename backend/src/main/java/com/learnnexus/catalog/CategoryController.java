package com.learnnexus.catalog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Categories", description = "Course taxonomy for the current tenant.")
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CourseService courseService;

    @Operation(summary = "List categories with course counts")
    @GetMapping
    public List<CatalogDtos.CategoryResponse> list() {
        return courseService.categories();
    }

    @Operation(summary = "Create a category")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN','AUTHOR')")
    public CatalogDtos.CategoryResponse create(@Valid @RequestBody CatalogDtos.CategoryRequest request) {
        return courseService.createCategory(request);
    }

    @Operation(summary = "Update a category")
    @PutMapping("/{categoryId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN','AUTHOR')")
    public CatalogDtos.CategoryResponse update(@PathVariable UUID categoryId,
                                               @Valid @RequestBody CatalogDtos.CategoryRequest request) {
        return courseService.updateCategory(categoryId, request);
    }

    @Operation(summary = "Delete an unused category")
    @DeleteMapping("/{categoryId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','PLATFORM_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID categoryId) {
        courseService.deleteCategory(categoryId);
        return ResponseEntity.noContent().build();
    }
}
