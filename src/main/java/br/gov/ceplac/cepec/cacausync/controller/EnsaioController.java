package br.gov.ceplac.cepec.cacausync.controller;

import br.gov.ceplac.cepec.cacausync.domain.entity.StatusValidacao;
import br.gov.ceplac.cepec.cacausync.dto.CriarEnsaioRequest;
import br.gov.ceplac.cepec.cacausync.dto.EnsaioResumoResponse;
import br.gov.ceplac.cepec.cacausync.dto.ItemRotaDto;
import br.gov.ceplac.cepec.cacausync.dto.LeituraColetaResponse;
import br.gov.ceplac.cepec.cacausync.dto.RelatorioProdutividadeEnsaioResponse;
import br.gov.ceplac.cepec.cacausync.dto.ResultadoRotaResponse;
import br.gov.ceplac.cepec.cacausync.service.ColetaSyncService;
import br.gov.ceplac.cepec.cacausync.service.EnsaioService;
import br.gov.ceplac.cepec.cacausync.service.RotaPlanejamentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ensaios")
@Tag(name = "3. Ensaios Genéticos, Rotas e Relatórios", description = "Planejamento de rotas para o FieldBook Android e relatórios de produtividade por clone")
@SecurityRequirement(name = "bearerAuth")
public class EnsaioController {

    private final EnsaioService ensaioService;
    private final ColetaSyncService coletaSyncService;
    private final RotaPlanejamentoService rotaPlanejamentoService;

    public EnsaioController(
            EnsaioService ensaioService,
            ColetaSyncService coletaSyncService,
            RotaPlanejamentoService rotaPlanejamentoService
    ) {
        this.ensaioService = ensaioService;
        this.coletaSyncService = coletaSyncService;
        this.rotaPlanejamentoService = rotaPlanejamentoService;
    }

    @GetMapping
    @Operation(summary = "Listar todos os ensaios genéticos cadastrados no CEPEC")
    public ResponseEntity<List<EnsaioResumoResponse>> listarEnsaios() {
        return ResponseEntity.ok(ensaioService.listarEnsaios());
    }

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cadastrar novo ensaio genético (Exige ROLE_COORDENADOR)")
    public ResponseEntity<EnsaioResumoResponse> criarEnsaio(@Valid @RequestBody CriarEnsaioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ensaioService.criarEnsaio(request));
    }

    @GetMapping("/{codigo}/relatorio-clones")
    @Operation(
            summary = "Gerar relatório consolidado de produtividade e sobrevivência por clone",
            description = "Cada planta conta uma vez (leitura válida mais recente). Média por planta viva avaliada, cobertura da coleta, "
                    + "frutos sadios x doentes e estimativa indicativa em kg/ha e @/ha (SPEC-003). "
                    + "Parâmetros opcionais: indiceFruto (frutos sadios por kg de amêndoa seca, padrão 22), "
                    + "plantasPorHa (padrão 1111) e campanha (filtra as leituras)."
    )
    public ResponseEntity<RelatorioProdutividadeEnsaioResponse> relatorioProdutividade(
            @PathVariable String codigo,
            @RequestParam(value = "indiceFruto", required = false) Double indiceFruto,
            @RequestParam(value = "plantasPorHa", required = false) Integer plantasPorHa,
            @RequestParam(value = "campanha", required = false) String campanha
    ) {
        return ResponseEntity.ok(ensaioService.gerarRelatorioProdutividade(codigo, indiceFruto, plantasPorHa, campanha));
    }

    @GetMapping("/{codigo}/leituras")
    @Operation(
            summary = "Listar leituras de coleta do ensaio na ordem da planilha para auditoria",
            description = "Ordenado pela sequência da planilha de rota (planta.ordemRota ASC). Use ?status=PENDENTE para ver só o que ainda falta conferir."
    )
    public ResponseEntity<Page<LeituraColetaResponse>> listarLeituras(
            @PathVariable String codigo,
            @RequestParam(value = "status", required = false) StatusValidacao status,
            @PageableDefault(size = 200, sort = {"planta.ordemRota", "id"}, direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ResponseEntity.ok(coletaSyncService.listarLeiturasPorEnsaio(codigo, status, pageable));
    }

    @GetMapping("/{codigo}/rota-json")
    @Operation(
            summary = "Listar a rota completa do ensaio em JSON na sequência da planilha",
            description = "Usado pelo botão 'Puxar Rota do PC' do CacauApp e pelo painel de conferência do CacauSync."
    )
    public ResponseEntity<List<ItemRotaDto>> listarRotaJson(@PathVariable String codigo) {
        return ResponseEntity.ok(rotaPlanejamentoService.listarRotaDoEnsaio(codigo));
    }

    @PostMapping(value = "/{codigo}/importar-rota", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Importar planilha de rota (.csv ou .xlsx) e enviar opcionalmente para o celular via USB",
            description = "Lê qualquer planilha CSV ou Excel (.xlsx) com Clone, Parcela, Planta (e Ordem), cadastra a sequência no ensaio e já envia para o CacauApp se o celular estiver no USB."
    )
    public ResponseEntity<ResultadoRotaResponse> importarPlanilhaRota(
            @PathVariable String codigo,
            @RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam(value = "enviarParaUsb", defaultValue = "true") boolean enviarParaUsb
    ) throws IOException {
        return ResponseEntity.ok(rotaPlanejamentoService.importarPlanilhaRota(
                codigo,
                arquivo.getInputStream(),
                arquivo.getOriginalFilename(),
                enviarParaUsb
        ));
    }

    @PostMapping("/{codigo}/enviar-rota-usb")
    @Operation(
            summary = "Enviar a rota atual do ensaio diretamente para o celular conectado via Cabo USB (1 Clique)",
            description = "Grava files/rota_precarregada.json diretamente no CacauApp via ADB para que o trabalhador veja a rota pronta na aba Modo Rota."
    )
    public ResponseEntity<ResultadoRotaResponse> enviarRotaParaUsb(@PathVariable String codigo) {
        return ResponseEntity.ok(rotaPlanejamentoService.enviarRotaAtualParaUsb(codigo));
    }

    @PostMapping("/{codigo}/carregar-rota-tc1801")
    @Operation(
            summary = "Carregar a planilha completa de 940 plantas do Ensaio TC-1801 e enviar opcionalmente ao celular via USB"
    )
    public ResponseEntity<ResultadoRotaResponse> carregarRotaPadraoTc1801(
            @PathVariable String codigo,
            @RequestParam(value = "enviarParaUsb", defaultValue = "true") boolean enviarParaUsb
    ) {
        return ResponseEntity.ok(rotaPlanejamentoService.carregarRotaCompletaTc1801(codigo, enviarParaUsb));
    }

    @GetMapping(value = "/{codigo}/rota-csv", produces = "text/csv")
    @Operation(
            summary = "Baixar planilha CSV de Rota Pré-Campo para o Modo Rota (FieldBook) do CacauApp",
            description = "Exporta o CSV no formato exato de dados_cacau.csv para carregamento offline no Android antes de ir a campo."
    )
    public ResponseEntity<byte[]> exportarRotaCsv(@PathVariable String codigo) {
        String csv = ensaioService.exportarRotaCsvParaFieldBook(codigo);
        String filename = "dados_cacau_" + codigo.toLowerCase() + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping(value = "/{codigo}/analise-csv", produces = "text/csv")
    @Operation(
            summary = "Baixar planilha analítica consolidada (pós-auditoria) com doenças e validações",
            description = "Exporta todas as leituras do ensaio com contagens da IA, contagens validadas, Sadios, Vassoura, Podridão, Não Avaliável, GPS e auditor."
    )
    public ResponseEntity<byte[]> exportarAnaliseCsv(@PathVariable String codigo) {
        String csv = ensaioService.exportarAnaliseCompletaCsv(codigo);
        String filename = "analise_auditada_" + codigo.toLowerCase() + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
