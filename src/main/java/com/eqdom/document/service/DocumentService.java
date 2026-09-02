package com.eqdom.document.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.eqdom.document.client.AuditClient;
import com.eqdom.document.client.AuditEventRequest;
import com.eqdom.document.client.CreateNotificationRequest;
import com.eqdom.document.client.CreditApplicationClient;
import com.eqdom.document.client.CreditApplicationDto;
import com.eqdom.document.client.CustomerClient;
import com.eqdom.document.client.CustomerDto;
import com.eqdom.document.client.NotificationClient;
import com.eqdom.document.dto.ChangeDocumentStatusRequest;
import com.eqdom.document.dto.DocumentResponse;
import com.eqdom.document.dto.DocumentSummaryResponse;
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
    private static final Set<DocumentStatus> ACTIVE_DOCUMENT_STATUSES =
            Set.of(DocumentStatus.EN_ATTENTE, DocumentStatus.VALIDE);

    // Only these file types are accepted; the extension AND the resulting content type are both
    // derived from this whitelist rather than trusted from the client, closing off arbitrary file
    // upload (executables, HTML/SVG that could be used for stored XSS, etc.).
    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "pdf", "application/pdf",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png"
    );

    private final DocumentRepository documentRepository;
    private final CreditApplicationClient creditApplicationClient;
    private final CustomerClient customerClient;
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

        String originalName = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file");
        String canonicalContentType = resolveAllowedContentType(originalName);

        var creditApplication = creditApplicationClient.getById(creditApplicationId);
        enforceOwnershipIfClient(creditApplication, authentication);
        if (LOCKED_STATUSES.contains(creditApplication.statut())) {
            throw new InvalidRequestException("Documents cannot be added to an application in status "
                    + creditApplication.statut());
        }

        if (documentRepository.existsByCreditApplicationIdAndTypeAndStatutIn(creditApplicationId, type, ACTIVE_DOCUMENT_STATUSES)) {
            throw new InvalidRequestException("A " + type + " document is already on file for this application "
                    + "(pending review or already validated). Delete it before uploading a replacement.");
        }

        String relativePath = fileStorageService.store(creditApplicationId, file);
        Document document;
        try {
            document = Document.builder()
                    .creditApplicationId(creditApplicationId)
                    .type(type)
                    .nomFichier(originalName)
                    .cheminFichier(relativePath)
                    .typeMime(canonicalContentType)
                    .tailleFichier(file.getSize())
                    .statut(DocumentStatus.EN_ATTENTE)
                    .uploadedByUserId(currentUserId(authentication))
                    .build();
            document = documentRepository.save(document);
        } catch (RuntimeException ex) {
            // The row was never persisted (or the transaction will roll back): don't leave an
            // orphaned file behind on disk with nothing in the DB pointing at it.
            fileStorageService.delete(relativePath);
            throw ex;
        }

        audit("DOCUMENT_UPLOAD", document.getId(), null, document.getType().name());

        return mapper.toResponse(document);
    }

    private String resolveAllowedContentType(String originalName) {
        int dotIndex = originalName.lastIndexOf('.');
        String extension = dotIndex >= 0 ? originalName.substring(dotIndex + 1).toLowerCase() : "";
        String contentType = ALLOWED_CONTENT_TYPES.get(extension);
        if (contentType == null) {
            throw new InvalidRequestException(
                    "Unsupported file type. Allowed document formats: PDF, JPG, JPEG, PNG");
        }
        return contentType;
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(Long id, Authentication authentication) {
        Document document = findOrThrow(id);
        var creditApplication = creditApplicationClient.getById(document.getCreditApplicationId());
        enforceOwnershipIfClient(creditApplication, authentication);
        return mapper.toResponse(document);
    }

    @Transactional(readOnly = true)
    public FileDownload download(Long id, Authentication authentication) {
        Document document = findOrThrow(id);
        var creditApplication = creditApplicationClient.getById(document.getCreditApplicationId());
        enforceOwnershipIfClient(creditApplication, authentication);
        byte[] data = fileStorageService.load(document.getCheminFichier());
        return new FileDownload(data, document.getNomFichier(), document.getTypeMime());
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> search(Long creditApplicationId, DocumentType type, DocumentStatus statut,
                                          Pageable pageable, Authentication authentication) {
        if (creditApplicationId != null) {
            var creditApplication = creditApplicationClient.getById(creditApplicationId);
            enforceOwnershipIfClient(creditApplication, authentication);
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
        var creditApplication = creditApplicationClient.getById(document.getCreditApplicationId());
        enforceOwnershipIfClient(creditApplication, authentication);

        if (LOCKED_STATUSES.contains(creditApplication.statut())) {
            throw new InvalidRequestException("Documents cannot be modified once the application is "
                    + creditApplication.statut());
        }
        if (document.getStatut() != DocumentStatus.EN_ATTENTE) {
            throw new InvalidRequestException("Only pending documents can be deleted");
        }

        if (!isStaff(authentication)) {
            Long callerUserId = currentUserId(authentication);
            if (document.getUploadedByUserId() == null || !document.getUploadedByUserId().equals(callerUserId)) {
                throw new AccessDeniedException("You may only delete documents you uploaded");
            }
        }

        documentRepository.delete(document);
        audit("DOCUMENT_DELETE", document.getId(), document.getStatut().name(), null);
        deleteFileAfterCommit(document.getCheminFichier());
    }

    /**
     * Only removes the physical file once the surrounding transaction has actually committed, so a
     * failed delete transaction never leaves the DB row pointing at a file that's already gone.
     */
    private void deleteFileAfterCommit(String relativePath) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fileStorageService.delete(relativePath);
                }
            });
        } else {
            fileStorageService.delete(relativePath);
        }
    }

    @Transactional
    public DocumentResponse changeStatus(Long id, ChangeDocumentStatusRequest request, Authentication authentication) {
        Document document = findOrThrow(id);
        var creditApplication = creditApplicationClient.getById(document.getCreditApplicationId());

        if (LOCKED_STATUSES.contains(creditApplication.statut())) {
            throw new InvalidRequestException("Documents cannot be modified once the application is "
                    + creditApplication.statut());
        }
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
        notifyStatusChange(document, creditApplication);

        return mapper.toResponse(document);
    }

    /**
     * Document-completeness signal consumed by credit-service before letting a dossier move to a
     * decision stage: at least one VALIDE CIN and one VALIDE proof of income, and no document
     * currently sitting in REJETE (which must be resolved - re-uploaded or removed - first).
     */
    @Transactional(readOnly = true)
    public DocumentSummaryResponse getSummary(Long creditApplicationId) {
        boolean hasValidCin = documentRepository.existsByCreditApplicationIdAndTypeAndStatut(
                creditApplicationId, DocumentType.CIN, DocumentStatus.VALIDE);
        boolean hasValidIncomeProof = documentRepository.existsByCreditApplicationIdAndTypeAndStatut(
                creditApplicationId, DocumentType.JUSTIFICATIF_SALAIRE, DocumentStatus.VALIDE);
        boolean hasRejectedDocuments = documentRepository.existsByCreditApplicationIdAndStatut(
                creditApplicationId, DocumentStatus.REJETE);

        return DocumentSummaryResponse.builder()
                .creditApplicationId(creditApplicationId)
                .hasValidCin(hasValidCin)
                .hasValidIncomeProof(hasValidIncomeProof)
                .hasRejectedDocuments(hasRejectedDocuments)
                .documentsComplete(hasValidCin && hasValidIncomeProof && !hasRejectedDocuments)
                .build();
    }

    private void audit(String action, Long entiteId, String ancienneValeur, String nouvelleValeur) {
        try {
            auditClient.record(new AuditEventRequest(action, "DOCUMENT", entiteId, ancienneValeur, nouvelleValeur));
        } catch (Exception ex) {
            log.warn("Failed to record audit event {} for document {}: {}", action, entiteId, ex.getMessage());
        }
    }

    private void notifyStatusChange(Document document, CreditApplicationDto creditApplication) {
        // The recipient is the actual applicant (the customer behind the credit application), not
        // necessarily whoever uploaded the file - an AGENT can upload on a CLIENT's behalf, and it's
        // the client who needs to know their document was validated/rejected.
        Long recipientUserId = resolveApplicantUserId(creditApplication);
        if (recipientUserId == null) {
            return;
        }

        String type = document.getStatut() == DocumentStatus.VALIDE ? "DOCUMENT_VALIDE" : "DOCUMENT_REJETE";
        String titre = document.getStatut() == DocumentStatus.VALIDE ? "Document validé" : "Document rejeté";
        String message = "Votre document " + document.getType() + " (" + document.getNomFichier() + ") a été "
                + (document.getStatut() == DocumentStatus.VALIDE ? "validé." : "rejeté: " + document.getCommentaireRejet());

        try {
            notificationClient.create(new CreateNotificationRequest(recipientUserId, null, type, titre,
                    message, "DOCUMENT", document.getId()));
        } catch (Exception ex) {
            log.warn("Failed to send notification {} for document {}: {}", type, document.getId(), ex.getMessage());
        }
    }

    private Long resolveApplicantUserId(CreditApplicationDto creditApplication) {
        try {
            CustomerDto customer = customerClient.getCustomer(creditApplication.customerId());
            if (customer != null && customer.userId() != null) {
                return customer.userId();
            }
        } catch (Exception ex) {
            log.warn("Failed to resolve customer for application {}: {}", creditApplication.id(), ex.getMessage());
        }
        // Fall back to whoever created the application (best-effort - still better than silently
        // dropping the notification if customer-service is unreachable).
        return creditApplication.createdByUserId();
    }

    private Document findOrThrow(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + id));
    }

    private void enforceOwnershipIfClient(CreditApplicationDto creditApplication, Authentication authentication) {
        if (isStaff(authentication)) {
            return;
        }
        Long callerUserId = currentUserId(authentication);
        if (creditApplication.createdByUserId() == null || !creditApplication.createdByUserId().equals(callerUserId)) {
            throw new AccessDeniedException("You may only access documents on your own credit applications");
        }
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
