package com.eqdom.document.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.eqdom.document.dto.ChangeDocumentStatusRequest;
import com.eqdom.document.dto.DocumentResponse;
import com.eqdom.document.entity.DocumentStatus;
import com.eqdom.document.entity.DocumentType;
import com.eqdom.document.service.DocumentService;
import com.eqdom.document.service.FileDownload;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Credit application document upload, validation and download")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','AGENT','CLIENT')")
    public ResponseEntity<DocumentResponse> upload(@RequestParam("creditApplicationId") Long creditApplicationId,
                                                    @RequestParam("type") DocumentType type,
                                                    @RequestParam("file") MultipartFile file,
                                                    Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.upload(creditApplicationId, type, file, authentication));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> get(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(documentService.get(id, authentication));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id, Authentication authentication) {
        FileDownload download = documentService.download(id, authentication);
        MediaType mediaType = download.contentType() != null
                ? MediaType.parseMediaType(download.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.filename() + "\"")
                .body(download.data());
    }

    @GetMapping
    public ResponseEntity<Page<DocumentResponse>> search(@RequestParam(required = false) Long creditApplicationId,
                                                          @RequestParam(required = false) DocumentType type,
                                                          @RequestParam(required = false) DocumentStatus statut,
                                                          Pageable pageable,
                                                          Authentication authentication) {
        return ResponseEntity.ok(documentService.search(creditApplicationId, type, statut, pageable, authentication));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        documentService.delete(id, authentication);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT','RESPONSABLE')")
    public ResponseEntity<DocumentResponse> changeStatus(@PathVariable Long id,
                                                          @Valid @RequestBody ChangeDocumentStatusRequest request,
                                                          Authentication authentication) {
        return ResponseEntity.ok(documentService.changeStatus(id, request, authentication));
    }
}
