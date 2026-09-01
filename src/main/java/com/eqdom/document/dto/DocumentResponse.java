package com.eqdom.document.dto;

import java.time.LocalDateTime;

import com.eqdom.document.entity.DocumentStatus;
import com.eqdom.document.entity.DocumentType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentResponse {

    private Long id;
    private Long creditApplicationId;
    private DocumentType type;
    private String nomFichier;
    private String typeMime;
    private Long tailleFichier;
    private DocumentStatus statut;
    private Long uploadedByUserId;
    private Long validatedByUserId;
    private String commentaireRejet;
    private LocalDateTime dateValidation;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
