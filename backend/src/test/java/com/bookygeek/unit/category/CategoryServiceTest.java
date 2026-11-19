package com.bookygeek.unit.category;

import com.bookygeek.category.dto.CategoryNode;
import com.bookygeek.category.dto.CategoryRef;
import com.bookygeek.category.entity.Category;
import com.bookygeek.category.repository.CategoryRepository;
import com.bookygeek.category.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Category tree, subtree resolution and name lookup (T063). Brand listing is a catalogue query, tested in the IT. */
class CategoryServiceTest {

    private CategoryService service;

    private static Category category(long id, String name, Long parentId, int sortOrder) {
        Category category = BeanUtils.instantiateClass(Category.class);
        ReflectionTestUtils.setField(category, "id", id);
        ReflectionTestUtils.setField(category, "name", name);
        ReflectionTestUtils.setField(category, "parentId", parentId);
        ReflectionTestUtils.setField(category, "sortOrder", sortOrder);
        return category;
    }

    @BeforeEach
    void setUp() {
        CategoryRepository repository = mock(CategoryRepository.class);
        when(repository.findAllByOrderBySortOrderAscIdAsc()).thenReturn(List.of(
                category(1, "Fiction", null, 1),
                category(2, "Non-Fiction", null, 2),
                category(3, "Romance", 1L, 1),
                category(4, "Mystery", 1L, 2),
                category(14, "Self Help", 2L, 2)));
        service = new CategoryService(repository);
    }

    @Test
    void treeHasTwoLevelsInDisplayOrder() {
        List<CategoryNode> tree = service.tree();

        assertThat(tree).extracting(CategoryNode::name).containsExactly("Fiction", "Non-Fiction");
        assertThat(tree.get(0).children()).extracting(CategoryNode::name).containsExactly("Romance", "Mystery");
        assertThat(tree.get(1).children()).extracting(CategoryNode::name).containsExactly("Self Help");
        assertThat(tree.get(0).children().get(0).children()).isEmpty();
    }

    @Test
    void aParentCategoryResolvesToItselfAndItsChildren() {
        assertThat(service.subtreeIds(1)).containsExactlyInAnyOrder(1L, 3L, 4L);
        assertThat(service.subtreeIds(2)).containsExactlyInAnyOrder(2L, 14L);
    }

    @Test
    void aLeafCategoryResolvesToItselfOnly() {
        assertThat(service.subtreeIds(14)).containsExactly(14L);
    }

    @Test
    void anUnknownCategoryResolvesToNothingSoItMatchesNoBook() {
        assertThat(service.subtreeIds(999)).isEmpty();
    }

    @Test
    void namesAreLookedUpByIdAndUnknownIdsAreLeftOut() {
        Map<Long, CategoryRef> refs = service.refsByIds(List.of(14L, 3L, 999L));

        assertThat(refs).containsOnlyKeys(14L, 3L);
        assertThat(refs.get(14L)).isEqualTo(new CategoryRef(14, "Self Help"));
    }

    @Test
    void thePathToACategoryRunsFromTheTopLevelDown() {
        assertThat(service.pathTo(14)).containsExactly(new CategoryRef(2, "Non-Fiction"), new CategoryRef(14, "Self Help"));
        assertThat(service.pathTo(2)).containsExactly(new CategoryRef(2, "Non-Fiction"));
    }

    @Test
    void thePathToAnUnknownCategoryIsEmpty() {
        assertThat(service.pathTo(999)).isEmpty();
    }
}
