-- =============================================================================
-- V12 — Ampliar tipos documento_cadastral para KYB PJ (Sprint 7 — Task 7.5 fix)
-- =============================================================================
-- Sprint 7 introduz tipos de documento PJ (contrato social, CCMEI, comprovante
-- de endereco). Adiciona-os ao CHECK constraint sem remover tipos PF.
-- =============================================================================

ALTER TABLE documento_cadastral DROP CONSTRAINT chk_documento_cadastral_tipo;

ALTER TABLE documento_cadastral ADD CONSTRAINT chk_documento_cadastral_tipo CHECK (tipo IN (
    'RG', 'CNH', 'PASSAPORTE', 'SELFIE',
    'CONTRATO_SOCIAL', 'CCMEI', 'COMPROVANTE_ENDERECO'
));
