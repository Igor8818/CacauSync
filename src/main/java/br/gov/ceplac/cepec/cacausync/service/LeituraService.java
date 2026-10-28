package br.gov.ceplac.cepec.cacausync.service;

import br.gov.ceplac.cepec.cacausync.domain.entity.LeituraColeta;
import br.gov.ceplac.cepec.cacausync.domain.entity.Planta;
import br.gov.ceplac.cepec.cacausync.domain.entity.SituacaoPlanta;
import br.gov.ceplac.cepec.cacausync.domain.entity.StatusValidacao;
import br.gov.ceplac.cepec.cacausync.dto.LeituraColetaResponse;
import br.gov.ceplac.cepec.cacausync.dto.ValidarLeituraRequest;
import br.gov.ceplac.cepec.cacausync.exception.RecursoNaoEncontradoException;
import br.gov.ceplac.cepec.cacausync.exception.RegraNegocioException;
import br.gov.ceplac.cepec.cacausync.repository.LeituraColetaRepository;
import br.gov.ceplac.cepec.cacausync.repository.PlantaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Operacoes sobre UMA leitura: ver, anexar/substituir foto e validar (revisao do pesquisador). */
@Service
public class LeituraService {

    private final LeituraColetaRepository leituraRepository;
    private final PlantaRepository plantaRepository;
    private final FotoStorageService fotoStorageService;
    private final LeituraMapper leituraMapper;

    public LeituraService(
            LeituraColetaRepository leituraRepository,
            PlantaRepository plantaRepository,
            FotoStorageService fotoStorageService,
            LeituraMapper leituraMapper
    ) {
        this.leituraRepository = leituraRepository;
        this.plantaRepository = plantaRepository;
        this.fotoStorageService = fotoStorageService;
        this.leituraMapper = leituraMapper;
    }

    public record FotoCarregada(byte[] conteudo, String contentType) {}

    @Transactional(readOnly = true)
    public LeituraColetaResponse buscar(Long id) {
        return leituraMapper.paraResponse(obter(id));
    }

    @Transactional
    public LeituraColetaResponse validar(Long id, ValidarLeituraRequest req, String emailPesquisador) {
        LeituraColeta leitura = obter(id);
        StatusValidacao status = req.status();

        if (status == StatusValidacao.AJUSTADA && req.qtdFrutosValidada() == null) {
            throw new RegraNegocioException("Para ajustar, informe a contagem correta de frutos (qtdFrutosValidada).");
        }

        leitura.setStatusValidacao(status);
        leitura.setObservacaoValidacao(textoOuNulo(req.observacao()));

        if (status == StatusValidacao.PENDENTE) {
            // "Desfazer": volta ao estado de recem-chegada.
            leitura.setQtdFrutosValidada(null);
            leitura.setValidadoPor(null);
            leitura.setValidadoEm(null);
        } else {
            leitura.setQtdFrutosValidada(status == StatusValidacao.AJUSTADA ? req.qtdFrutosValidada() : null);
            leitura.setValidadoPor(emailPesquisador);
            leitura.setValidadoEm(LocalDateTime.now());
        }

        // O pesquisador olhou a foto e viu frutos onde o app marcou "Não Coletável": a planta esta viva.
        if (status == StatusValidacao.AJUSTADA && !Boolean.TRUE.equals(leitura.getColetavel())
                && req.qtdFrutosValidada() > 0) {
            leitura.setColetavel(true);
            Planta planta = leitura.getPlanta();
            planta.setSituacao(SituacaoPlanta.NORMAL);
            plantaRepository.save(planta);
        }

        return leituraMapper.paraResponse(leituraRepository.save(leitura));
    }

    @Transactional
    public LeituraColetaResponse anexarFoto(Long id, byte[] conteudo) {
        LeituraColeta leitura = obter(id);
        String chaveAntiga = leitura.getFotoChave();

        FotoStorageService.FotoSalva salva = fotoStorageService.salvar(conteudo);
        leitura.setFotoChave(salva.chave());
        leitura.setFotoContentType(salva.contentType());
        LeituraColeta salvo = leituraRepository.save(leitura);

        fotoStorageService.remover(chaveAntiga);
        return leituraMapper.paraResponse(salvo);
    }

    @Transactional(readOnly = true)
    public FotoCarregada carregarFoto(Long id) {
        LeituraColeta leitura = obter(id);
        if (!leitura.temFoto()) {
            throw new RecursoNaoEncontradoException("Esta leitura ainda não tem foto anexada.");
        }
        return new FotoCarregada(fotoStorageService.carregar(leitura.getFotoChave()), leitura.getFotoContentType());
    }

    private LeituraColeta obter(Long id) {
        return leituraRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Leitura de coleta não encontrada: " + id));
    }

    private String textoOuNulo(String texto) {
        return (texto == null || texto.isBlank()) ? null : texto.trim();
    }
}
