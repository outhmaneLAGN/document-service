package com.eqdom.document.mapper;

import org.springframework.stereotype.Component;

import com.eqdom.document.dto.DocumentResponse;
import com.eqdom.document.entity.Document;

@Component
public class DocumentMapper {

    public DocumentResponse toResponse(Document document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .creditApplicationId(document.getCreditApplicationId())
                .type(document.getType())
                .nomFichier(document.getNomFichier())
                .typeMime(document.getTypeMime())
                .tailleFichier(document.getTailleFichier())
                .statut(document.getStatut())
                .uploadedByUserId(document.getUploadedByUserId())
                .validatedByUserId(document.getValidatedByUserId())
                .commentaireRejet(document.getCommentaireRejet())
                .dateValidation(document.getDateValidation())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }
}
