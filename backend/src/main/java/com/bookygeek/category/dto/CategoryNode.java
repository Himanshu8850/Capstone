package com.bookygeek.category.dto;

import java.util.List;

/** The contract's {@code Category}: a node of the category tree; {@code children} is empty for a leaf. */
public record CategoryNode(long id, String name, List<CategoryNode> children) {
}
