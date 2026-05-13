package com.dynamis.sep_api.onboarding.application.dto;

import com.dynamis.sep_api.onboarding.domain.vo.TipoDocumento;

/** Comando de upload de um documento cadastral — usado por {@code EnviarDocumentoUseCase}. */
public record DocumentoUploadCommand(TipoDocumento tipo, String mimeType, String nomeOriginal, byte[] conteudo) {}
