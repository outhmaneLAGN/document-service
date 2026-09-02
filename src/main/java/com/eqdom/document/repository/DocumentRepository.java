package com.eqdom.document.repository;

import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.eqdom.document.entity.Document;
import com.eqdom.document.entity.DocumentStatus;
import com.eqdom.document.entity.DocumentType;

public interface DocumentRepository extends JpaRepository<Document, Long>, JpaSpecificationExecutor<Document> {

    boolean existsByCreditApplicationIdAndTypeAndStatut(Long creditApplicationId, DocumentType type, DocumentStatus statut);

    boolean existsByCreditApplicationIdAndStatut(Long creditApplicationId, DocumentStatus statut);

    boolean existsByCreditApplicationIdAndTypeAndStatutIn(Long creditApplicationId, DocumentType type,
                                                            Collection<DocumentStatus> statuts);
}
