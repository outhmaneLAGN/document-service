package com.eqdom.document.dto;

import com.eqdom.document.entity.DocumentStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class ChangeDocumentStatusRequest {

    @NotNull
    private DocumentStatus statut;

    @Size(max = 500)
    private String commentaire;
}
