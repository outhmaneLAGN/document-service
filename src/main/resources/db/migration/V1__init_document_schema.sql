CREATE TABLE IF NOT EXISTS documents (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    credit_application_id BIGINT        NOT NULL,
    type                  VARCHAR(30)   NOT NULL,
    nom_fichier           VARCHAR(255)  NOT NULL,
    chemin_fichier        VARCHAR(500)  NOT NULL,
    type_mime             VARCHAR(100),
    taille_fichier        BIGINT,
    statut                VARCHAR(30)   NOT NULL,
    uploaded_by_user_id   BIGINT,
    validated_by_user_id  BIGINT,
    commentaire_rejet     VARCHAR(500),
    date_validation       DATETIME,
    created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_documents_credit_application ON documents(credit_application_id);
CREATE INDEX idx_documents_statut ON documents(statut);
CREATE INDEX idx_documents_type ON documents(type);
