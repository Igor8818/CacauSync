package br.gov.ceplac.cepec.cacausync.dto;

/** Foto recebida no upload: nome original (usado para casar com o CSV) e os bytes. */
public record ArquivoFoto(String nomeOriginal, byte[] conteudo) {}
