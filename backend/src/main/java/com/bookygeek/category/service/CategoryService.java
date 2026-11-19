package com.bookygeek.category.service;

import com.bookygeek.category.dto.CategoryNode;
import com.bookygeek.category.dto.CategoryRef;
import com.bookygeek.category.entity.Category;
import com.bookygeek.category.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Owns the category tree. Catalogue calls {@link #subtreeIds} and {@link #refsByIds}: that is the one approved
 * dependency between the two modules (Catalogue to Category), and no repository or entity is shared.
 */
@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categories;

    public CategoryService(CategoryRepository categories) {
        this.categories = categories;
    }

    /** The category tree: top-level categories in display order, each with its children. */
    public List<CategoryNode> tree() {
        List<Category> all = categories.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, List<Category>> childrenOf = all.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId, LinkedHashMap::new, Collectors.toList()));
        return all.stream()
                .filter(c -> c.getParentId() == null)
                .map(root -> node(root, childrenOf))
                .toList();
    }

    /**
     * The id of a category and of all its descendants, so that a parent category lists the books of its children.
     * An unknown id gives an empty set, which matches no book.
     */
    public Set<Long> subtreeIds(long categoryId) {
        List<Category> all = categories.findAllByOrderBySortOrderAscIdAsc();
        Set<Long> result = new HashSet<>();
        if (all.stream().noneMatch(c -> c.getId() == categoryId)) {
            return result;
        }
        Deque<Long> pending = new ArrayDeque<>(List.of(categoryId));
        while (!pending.isEmpty()) {
            long current = pending.pop();
            if (result.add(current)) {
                all.stream().filter(c -> Long.valueOf(current).equals(c.getParentId()))
                        .forEach(child -> pending.push(child.getId()));
            }
        }
        return result;
    }

    /** Names for the given category ids, keyed by id (ids that do not exist are left out). */
    public Map<Long, CategoryRef> refsByIds(Collection<Long> ids) {
        Set<Long> wanted = Set.copyOf(ids);
        return categories.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(c -> wanted.contains(c.getId()))
                .collect(Collectors.toMap(Category::getId, c -> new CategoryRef(c.getId(), c.getName()),
                        (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * The path from the top-level category down to the given category, for example Non-Fiction then Self Help (the
     * breadcrumb of a book page). An unknown id gives an empty path.
     */
    public List<CategoryRef> pathTo(long categoryId) {
        Map<Long, Category> byId = categories.findAllByOrderBySortOrderAscIdAsc().stream()
                .collect(Collectors.toMap(Category::getId, c -> c));
        Deque<CategoryRef> path = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        Category current = byId.get(categoryId);
        while (current != null && visited.add(current.getId())) {
            path.addFirst(new CategoryRef(current.getId(), current.getName()));
            current = current.getParentId() == null ? null : byId.get(current.getParentId());
        }
        return List.copyOf(path);
    }

    private static CategoryNode node(Category category, Map<Long, List<Category>> childrenOf) {
        List<CategoryNode> children = childrenOf.getOrDefault(category.getId(), List.of()).stream()
                .map(child -> node(child, childrenOf))
                .toList();
        return new CategoryNode(category.getId(), category.getName(), children);
    }
}
