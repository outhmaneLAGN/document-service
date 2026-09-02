package com.eqdom.document.dto;

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
public class DocumentSummaryResponse {

    private Long creditApplicationId;
    private boolean hasValidCin;
    private boolean hasValidIncomeProof;
    private boolean hasRejectedDocuments;
    private boolean documentsComplete;
}
