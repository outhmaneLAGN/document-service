package com.eqdom.document.repository;

import org.springframework.data.jpa.domain.Specification;

import com.eqdom.document.entity.Document;
import com.eqdom.document.entity.DocumentStatus;
import com.eqdom.document.entity.DocumentType;

public final class DocumentSpecifications {

    private DocumentSpecifications() {
    }

    public static Specification<Document> withFilters(Long creditApplicationId, DocumentType type, DocumentStatus statut) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (creditApplicationId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("creditApplicationId"), creditApplicationId));
            }
            if (type != null) {
                predicate = cb.and(predicate, cb.equal(root.get("type"), type));
            }
            if (statut != null) {
                predicate = cb.and(predicate, cb.equal(root.get("statut"), statut));
            }
            return predicate;
        };
    }
}
