package com.api.supermarket.service.Impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.api.supermarket.dto.request.CategoryRequest;
import com.api.supermarket.dto.response.CategoryResponse;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.entity.Category;
import com.api.supermarket.repository.CategoryRepository;
import com.api.supermarket.repository.ProductRepository;

class CategoryServiceImplTest {

    private CategoryRepository categoryRepository;
    private ProductRepository productRepository;
    private com.api.supermarket.integration.DataSyncPublisher dataSyncPublisher;
    private CategoryServiceImpl categoryService;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        productRepository = mock(ProductRepository.class);
        dataSyncPublisher = mock(com.api.supermarket.integration.DataSyncPublisher.class);
        categoryService = new CategoryServiceImpl(
            categoryRepository,
            productRepository,
            dataSyncPublisher
        );

        when(categoryRepository.save(any(Category.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void getAllCategoriesShouldReturnMappedResponses() {
        Category category = createCategory(1L, "Đồ uống", true);
        when(categoryRepository.findAll()).thenReturn(List.of(category));

        List<CategoryResponse> responses = categoryService.getAllCategories();

        assertEquals(1, responses.size());
        assertCategoryResponse(responses.get(0), category);
    }

    @Test
    void getCategoryByIdShouldReturnMappedResponse() {
        Category category = createCategory(2L, "Thực phẩm", true);
        when(categoryRepository.findById(2L))
            .thenReturn(Optional.of(category));

        CategoryResponse response = categoryService.getCategoryById(2L);

        assertCategoryResponse(response, category);
    }

    @Test
    void createCategoryShouldReturnMappedResponse() {
        CategoryRequest request = createRequest("Gia dụng", null);
        when(categoryRepository.existsByCategoryName("Gia dụng"))
            .thenReturn(false);

        CategoryResponse response = categoryService.createCategory(request);

        assertEquals("Gia dụng", response.getCategoryName());
        assertTrue(response.getIsActive());
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void updateCategoryShouldReturnMappedResponse() {
        Category category = createCategory(3L, "Tên cũ", false);
        CategoryRequest request = createRequest("Tên mới", true);

        when(categoryRepository.findById(3L))
            .thenReturn(Optional.of(category));
        when(categoryRepository.existsByCategoryName("Tên mới"))
            .thenReturn(false);

        CategoryResponse response = categoryService.updateCategory(3L, request);

        assertEquals("Tên mới", response.getCategoryName());
        assertTrue(response.getIsActive());
        verify(categoryRepository).save(category);
    }

    @Test
    void searchCategoriesShouldReturnMappedResponses() {
        Category category = createCategory(4L, "Nước ngọt", true);
        when(categoryRepository.findByCategoryNameContaining("Nước"))
            .thenReturn(List.of(category));

        List<CategoryResponse> responses =
            categoryService.getCategoriesByNameContaining("Nước");

        assertEquals(1, responses.size());
        assertCategoryResponse(responses.get(0), category);
    }

    @Test
    void getActiveCategoriesShouldReturnMappedResponses() {
        Category category = createCategory(5L, "Đang bán", true);
        when(categoryRepository.findByIsActiveTrue())
            .thenReturn(List.of(category));

        List<CategoryResponse> responses =
            categoryService.getCategoriesByIsActiveTrue();

        assertEquals(1, responses.size());
        assertTrue(responses.get(0).getIsActive());
    }

    @Test
    void filterCategoriesShouldKeepPaginationMetadataAndMapContent() {
        List<Category> content = List.of(
            createCategory(6L, "Sữa", true),
            createCategory(7L, "Bánh", true)
        );
        Page<Category> categoryPage = new PageImpl<>(
            content,
            PageRequest.of(1, 2),
            5
        );

        when(categoryRepository.filterCategories(
            eq("a"),
            eq("mô tả"),
            eq(true),
            any(Pageable.class)
        )).thenReturn(categoryPage);

        PageResponse<CategoryResponse> response =
            categoryService.filterCategories(
                1,
                2,
                "categoryName",
                "desc",
                "a",
                "mô tả",
                true
            );

        assertEquals(2, response.getContent().size());
        assertEquals(1, response.getPage());
        assertEquals(2, response.getSize());
        assertEquals(5, response.getTotalElements());
        assertEquals(3, response.getTotalPages());
        assertFalse(response.isLast());
        assertEquals("Sữa", response.getContent().get(0).getCategoryName());
    }

    private Category createCategory(Long id, String name, Boolean active) {
        Category category = new Category();
        category.setCategoryId(id);
        category.setCategoryName(name);
        category.setDescription("Mô tả " + name);
        category.setIsActive(active);
        category.setCreateAt(LocalDateTime.of(2026, 7, 24, 10, 0));
        return category;
    }

    private CategoryRequest createRequest(String name, Boolean active) {
        CategoryRequest request = new CategoryRequest();
        request.setCategoryName(name);
        request.setDescription("Mô tả " + name);
        request.setIsActive(active);
        return request;
    }

    private void assertCategoryResponse(
        CategoryResponse response,
        Category category
    ) {
        assertEquals(category.getCategoryId(), response.getCategoryId());
        assertEquals(category.getCategoryName(), response.getCategoryName());
        assertEquals(category.getDescription(), response.getDescription());
        assertEquals(category.getIsActive(), response.getIsActive());
        assertEquals(category.getCreateAt(), response.getCreateAt());
    }
}
