package com.eqdom.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.eqdom.document.client.AuditClient;
import com.eqdom.document.client.CreditApplicationClient;
import com.eqdom.document.client.CreditApplicationDto;
import com.eqdom.document.client.NotificationClient;
import com.eqdom.document.dto.ChangeDocumentStatusRequest;
import com.eqdom.document.entity.Document;
import com.eqdom.document.entity.DocumentStatus;
import com.eqdom.document.entity.DocumentType;
import com.eqdom.document.exception.InvalidRequestException;
import com.eqdom.document.mapper.DocumentMapper;
import com.eqdom.document.repository.DocumentRepository;
import com.eqdom.document.security.JwtPrincipal;

class DocumentServiceTest {

    private DocumentRepository documentRepository;
    private CreditApplicationClient creditApplicationClient;
    private FileStorageService fileStorageService;
    private DocumentService documentService;
    private Authentication clientAuth;

    @BeforeEach
    void setUp() {
        documentRepository = mock(DocumentRepository.class);
        creditApplicationClient = mock(CreditApplicationClient.class);
        fileStorageService = mock(FileStorageService.class);
        AuditClient auditClient = mock(AuditClient.class);
        NotificationClient notificationClient = mock(NotificationClient.class);
        DocumentMapper mapper = new DocumentMapper();

        documentService = new DocumentService(documentRepository, creditApplicationClient, auditClient,
                notificationClient, fileStorageService, mapper);

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_CLIENT"));
        clientAuth = mock(Authentication.class);
        when(clientAuth.getPrincipal()).thenReturn(new JwtPrincipal(4L, "client1"));
        doReturn(authorities).when(clientAuth).getAuthorities();
    }

    @Test
    void uploadRejectsEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "cin.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> documentService.upload(1L, DocumentType.CIN, emptyFile, clientAuth))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void uploadRejectsWhenApplicationIsInLockedStatus() {
        when(creditApplicationClient.getById(1L)).thenReturn(new CreditApplicationDto(1L, "CR-1", 5L, "ACCEPTEE"));
        MockMultipartFile file = new MockMultipartFile("file", "cin.pdf", "application/pdf", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> documentService.upload(1L, DocumentType.CIN, file, clientAuth))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("ACCEPTEE");
    }

    @Test
    void changeStatusRejectsRejectionWithoutComment() {
        Document document = pendingDocument();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(creditApplicationClient.getById(any())).thenReturn(new CreditApplicationDto(1L, "CR-1", 5L, "EN_ETUDE"));

        ChangeDocumentStatusRequest request = ChangeDocumentStatusRequest.builder()
                .statut(DocumentStatus.REJETE)
                .build();

        assertThatThrownBy(() -> documentService.changeStatus(1L, request, clientAuth))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("comment is required");
    }

    @Test
    void changeStatusRejectsAlreadyDecidedDocument() {
        Document document = pendingDocument();
        document.setStatut(DocumentStatus.VALIDE);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(creditApplicationClient.getById(any())).thenReturn(new CreditApplicationDto(1L, "CR-1", 5L, "EN_ETUDE"));

        ChangeDocumentStatusRequest request = ChangeDocumentStatusRequest.builder()
                .statut(DocumentStatus.REJETE)
                .commentaire("Illegible")
                .build();

        assertThatThrownBy(() -> documentService.changeStatus(1L, request, clientAuth))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Only pending documents");
    }

    @Test
    void deleteRejectsNonPendingDocument() {
        Document document = pendingDocument();
        document.setStatut(DocumentStatus.VALIDE);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(creditApplicationClient.getById(any())).thenReturn(new CreditApplicationDto(1L, "CR-1", 5L, "EN_ETUDE"));

        assertThatThrownBy(() -> documentService.delete(1L, clientAuth))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Only pending documents can be deleted");
    }

    @Test
    void changeStatusAcceptsValidationWithoutComment() {
        Document document = pendingDocument();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(creditApplicationClient.getById(any())).thenReturn(new CreditApplicationDto(1L, "CR-1", 5L, "EN_ETUDE"));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ChangeDocumentStatusRequest request = ChangeDocumentStatusRequest.builder()
                .statut(DocumentStatus.VALIDE)
                .build();

        var response = documentService.changeStatus(1L, request, clientAuth);

        assertThat(response.getStatut()).isEqualTo(DocumentStatus.VALIDE);
    }

    private Document pendingDocument() {
        return Document.builder()
                .id(1L)
                .creditApplicationId(1L)
                .type(DocumentType.CIN)
                .nomFichier("cin.pdf")
                .cheminFichier("1/abc.pdf")
                .statut(DocumentStatus.EN_ATTENTE)
                .uploadedByUserId(4L)
                .build();
    }
}
