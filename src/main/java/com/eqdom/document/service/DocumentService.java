package com.eqdom.document.service;

import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.eqdom.document.client.AuditClient;
import com.eqdom.document.client.AuditEventRequest;
import com.eqdom.document.client.CreateNotificationRequest;
import com.eqdom.document.client.CreditApplicationClient;
import com.eqdom.document.client.NotificationClient;
import com.eqdom.document.dto.ChangeDocumentStatusRequest;
import com.eqdom.document.dto.DocumentResponse;
import com.eqdom.document.entity.Document;
import com.eqdom.document.entity.DocumentStatus;
import com.eqdom.document.entity.DocumentType;
import com.eqdom.document.exception.InvalidRequestException;
import com.eqdom.document.exception.ResourceNotFoundException;
import com.eqdom.document.mapper.DocumentMapper;
import com.eqdom.document.repository.DocumentRepository;
import com.eqdom.document.repository.DocumentSpecifications;
import com.eqdom.document.security.JwtPrincipal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final Set<String> LOCKED_STATUSES = Set.of("ACCEPTEE", "REFUSEE", "ANNULEE");
    private static final Set<String> STAFF_ROLES = Set.of("ROLE_ADMIN", "ROLE_AGENT", "ROLE_RESPONSABLE");

    private final DocumentRepository documentRepository;
    private final CreditApplicationClient creditApplicationClient;
    private final AuditClient auditClient;
    private final NotificationClient notificationClient;
    private final FileStorageService fileStorageService;
    private final DocumentMapper mapper;

    @Transactional
    public DocumentResponse upload(Long creditApplicationId, DocumentType type, MultipartFile file,
                                    Authentication authentication) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("The uploaded file must not be empty");
        }

        var creditApplication = creditApplicationClient.getById(creditApplicationId);
        if (LOCKED_STATUSES.contains(creditApplication.statut())) {
            throw new InvalidRequestException("Documents cannot be added to an application in status "
                    + creditApplication.statut());
        }

        String relativePath = fileStorageService.store(creditApplicationId, file);

        Document document = Document.builder()
                .creditApplicationId(creditApplicationId)
                .type(type)
                .nomFichier(StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file"))
                .cheminFichier(relativePath)
                .typeMime(file.getContentType())
                .tailleFichier(file.getSize())
                .statut(DocumentStatus.EN_ATTENTE)
                .uploadedByUserId(currentUserId(authentication))
                .build();

        document = documentRepository.save(document);
        audit("DOCUMENT_UPLOAD", document.getId(), null, document.getType().name());

        return mapper.toResponse(document);
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(Long id, Authentication authentication) {
        Document document = findOrThrow(id);
        creditApplicationClient.getById(document.getCreditApplicationId());
        return mapper.toResponse(document);
    }

    @Transactional(readOnly = true)
    public FileDownload download(Long id, Authentication authentication) {
        Document document = findOrThrow(id);
        creditApplicationClient.getById(document.getCreditApplicationId());
        byte[] data = fileStorageService.load(document.getCheminFichier());
        return new FileDownload(data, document.getNomFichier(), document.getTypeMime());
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> search(Long creditApplicationId, DocumentType type, DocumentStatus statut,
                                          Pageable pageable, Authentication authentication) {
        if (creditApplicationId != null) {
            creditApplicationClient.getById(creditApplicationId);
        } else if (!isStaff(authentication)) {
            throw new InvalidRequestException("creditApplicationId is required");
        }

        return documentRepository
                .findAll(DocumentSpecifications.withFilters(creditApplicationId, type, statut), pageable)
                .map(mapper::toResponse);
    }

    @Transactional
    public void delete(Long id, Authentication authentication) {
        Document document = findOrThrow(id);
        creditApplicationClient.getById(document.getCreditApplicationId());

        if (document.getStatut() != DocumentStatus.EN_ATTENTE) {
            throw new InvalidRequestException("Only pending documents can be deleted");
        }

        if (!isStaff(authentication)) {
            Long callerUserId = currentUserId(authentication);
            if (document.getUploadedByUserId() == null || !document.getUploadedByUserId().equals(callerUserId)) {
                throw new AccessDeniedException("You may only delete documents you uploaded");
            }
        }

        fileStorageService.delete(document.getCheminFichier());
        documentRepository.delete(document);
        audit("DOCUMENT_DELETE", document.getId(), document.getStatut().name(), null);
    }

    @Transactional
    public DocumentResponse changeStatus(Long id, ChangeDocumentStatusRequest request, Authentication authentication) {
        Document document = findOrThrow(id);
        creditApplicationClient.getById(document.getCreditApplicationId());

        if (document.getStatut() != DocumentStatus.EN_ATTENTE) {
            throw new InvalidRequestException("Only pending documents can be validated or rejected");
        }
        if (request.getStatut() == DocumentStatus.EN_ATTENTE) {
            throw new InvalidRequestException("A document cannot be reset back to EN_ATTENTE");
        }
        if (request.getStatut() == DocumentStatus.REJETE && !StringUtils.hasText(request.getCommentaire())) {
            throw new InvalidRequestException("A comment is required when rejecting a document");
        }

        DocumentStatus previousStatus = document.getStatut();
        document.setStatut(request.getStatut());
        document.setValidatedByUserId(currentUserId(authentication));
        document.setDateValidation(LocalDateTime.now());
        document.setCommentaireRejet(request.getStatut() == DocumentStatus.REJETE ? request.getCommentaire() : null);

        document = documentRepository.save(document);
        audit("DOCUMENT_VALIDATION", document.getId(), previousStatus.name(), document.getStatut().name());
        notifyStatusChange(document);

        return mapper.toResponse(document);
    }

    private void audit(String action, Long entiteId, String ancienneValeur, String nouvelleValeur) {
        try {
            auditClient.record(new AuditEventRequest(action, "DOCUMENT", entiteId, ancienneValeur, nouvelleValeur));
        } catch (Exception ex) {
            log.warn("Failed to record audit event {} for document {}: {}", action, entiteId, ex.getMessage());
        }
    }

    private void notifyStatusChange(Document document) {
        if (document.getUploadedByUserId() == null) {
            return;
        }
        String type = document.getStatut() == DocumentStatus.VALIDE ? "DOCUMENT_VALIDE" : "DOCUMENT_REJETE";
        String titre = document.getStatut() == DocumentStatus.VALIDE ? "Document validé" : "Document rejeté";
        String message = "Votre document " + document.getType() + " (" + document.getNomFichier() + ") a été "
                + (document.getStatut() == DocumentStatus.VALIDE ? "validé." : "rejeté: " + document.getCommentaireRejet());

        try {
            notificationClient.create(new CreateNotificationRequest(document.getUploadedByUserId(), null, type, titre,
                    message, "DOCUMENT", document.getId()));
        } catch (Exception ex) {
            log.warn("Failed to send notification {} for document {}: {}", type, document.getId(), ex.getMessage());
        }
    }

    private Document findOrThrow(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + id));
    }

    private boolean isStaff(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(STAFF_ROLES::contains);
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication.getPrincipal() instanceof JwtPrincipal principal) {
            return principal.userId();
        }
        return null;
    }
}
