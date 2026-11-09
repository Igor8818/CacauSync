package br.gov.ceplac.cepec.cacausync.service;

import br.gov.ceplac.cepec.cacausync.dto.DeteccaoDto;
import br.gov.ceplac.cepec.cacausync.dto.ItemRotaDto;
import br.gov.ceplac.cepec.cacausync.dto.LeituraSincronizadaDto;
import br.gov.ceplac.cepec.cacausync.dto.ResultadoImportacaoCsvResponse;
import br.gov.ceplac.cepec.cacausync.dto.SyncJsonRequest;
import br.gov.ceplac.cepec.cacausync.exception.RegraNegocioException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Serviço de Sincronização Automática em 1 Clique via Cabo USB (ADB).
 * Permite ao pesquisador puxar todas as coletas, caixas auditadas do YOLO e fotos
 * diretamente do celular conectado na USB sem precisar escolher arquivos CSV ou fotos manualmente.
 */
@Service
public class UsbAdbSyncService {

    private static final String APP_PACKAGE = "com.example.myapplication";

    private final ColetaSyncService coletaSyncService;
    private final LeituraService leituraService;
    private final ObjectMapper objectMapper;

    public UsbAdbSyncService(
            ColetaSyncService coletaSyncService,
            LeituraService leituraService,
            ObjectMapper objectMapper
    ) {
        this.coletaSyncService = coletaSyncService;
        this.leituraService = leituraService;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void inicializarTunelUsbAssincrono() {
        Thread t = new Thread(() -> {
            try {
                String adbPath = localizarAdbExecutavel();
                executarComandoTexto(List.of(adbPath, "start-server"), 5);
                Thread.sleep(1500);
                executarComandoTexto(List.of(adbPath, "reverse", "tcp:8080", "tcp:8080"), 5);
            } catch (Exception ignored) {
            }
        }, "usb-adb-init");
        t.setDaemon(true);
        t.start();
    }

    public ResultadoImportacaoCsvResponse sincronizarViaUsb(String codigoEnsaioPadrao, String emailPesquisador) {
        String adbPath = localizarAdbExecutavel();
        String serial = detectarDispositivoConectado(adbPath);

        // Ativa automaticamente o túnel reverso USB (permite que o celular também acesse o PC em 127.0.0.1:8080)
        executarComandoTexto(List.of(adbPath, "-s", serial, "reverse", "tcp:8080", "tcp:8080"), 5);

        String pacoteApp = detectarPacoteInstalado(adbPath, serial);

        // Lê o JSON local persistido pelo CacauApp
        byte[] jsonBytes = executarComandoBinario(
                List.of(adbPath, "-s", serial, "exec-out", "run-as", pacoteApp, "cat", "files/registros_cacau_local.json"),
                10
        );

        String jsonTexto = new String(jsonBytes, StandardCharsets.UTF_8).trim();
        if (jsonTexto.isEmpty() || !jsonTexto.startsWith("[")) {
            throw new RegraNegocioException(
                    "Nenhuma coleta salva foi encontrada no celular conectado (" + serial + "). "
                    + "Registre ao menos uma planta no CacauApp antes de puxar os dados."
            );
        }

        JsonNode rootArray;
        try {
            rootArray = objectMapper.readTree(jsonTexto);
        } catch (Exception e) {
            throw new RegraNegocioException("Erro ao interpretar os registros do celular: " + e.getMessage());
        }

        if (!rootArray.isArray() || rootArray.isEmpty()) {
            throw new RegraNegocioException("A lista de coletas do celular está vazia.");
        }

        String ensaioEfetivo = (codigoEnsaioPadrao != null && !codigoEnsaioPadrao.isBlank())
                ? codigoEnsaioPadrao.trim().toUpperCase(Locale.ROOT)
                : "TC-1801";

        List<SyncJsonRequest.ItemSync> itensSync = new ArrayList<>();
        Map<String, String> uriPorPlanta = new HashMap<>();

        for (JsonNode node : rootArray) {
            String plantId = texto(node, "plantId", "").trim();
            if (plantId.isEmpty()) continue;

            String ensaioItem = texto(node, "ensaioManual", ensaioEfetivo).trim();
            if (ensaioItem.isEmpty()) ensaioItem = ensaioEfetivo;

            String diagnostico = texto(node, "diagnostico", "");
            boolean naoColetavel = isNaoColetavel(diagnostico);

            String[] partes = plantId.split("_");
            String clone = (partes.length > 0 && !partes[0].isBlank()) ? partes[0] : "CCN-51";
            String parcela = texto(node, "parcelaManual", "1");
            if (parcela.isBlank()) parcela = "1";
            int numPlanta = node.path("numPlanta").asInt(1);
            if (numPlanta <= 0) numPlanta = 1;

            int totalFrutos = node.path("totalFrutos").asInt(0);
            String resultado = naoColetavel ? "Não Coletável" : String.valueOf(Math.max(0, totalFrutos));
            String data = texto(node, "data", "01/01/2026 12:00:00");
            double lat = node.path("lat").asDouble(0.0);
            double lng = node.path("lng").asDouble(0.0);

            int sadios = naoColetavel ? 0 : node.path("sadios").asInt(0);
            int vassoura = naoColetavel ? 0 : node.path("vassoura").asInt(0);
            int podridao = naoColetavel ? 0 : node.path("podridao").asInt(0);
            int naoAvaliavel = naoColetavel ? 0 : node.path("naoAvaliavel").asInt(0);

            List<DeteccaoDto> deteccoes = new ArrayList<>();
            JsonNode caixasNode = node.path("caixas");
            if (!naoColetavel && caixasNode.isArray()) {
                for (JsonNode cx : caixasNode) {
                    String classe = normalizarClasse(texto(cx, "classe", "Sadio"));
                    double conf = clamp01(cx.path("confianca").asDouble(0.9));
                    double x = clamp01(cx.path("x").asDouble(0.0));
                    double y = clamp01(cx.path("y").asDouble(0.0));
                    double w = clamp01(cx.path("largura").asDouble(0.1));
                    double h = clamp01(cx.path("altura").asDouble(0.1));
                    if (x + w > 1.0) w = Math.max(0.0, 1.0 - x);
                    if (y + h > 1.0) h = Math.max(0.0, 1.0 - y);
                    deteccoes.add(new DeteccaoDto(classe, conf, x, y, w, h));
                }
            }

            itensSync.add(new SyncJsonRequest.ItemSync(
                    ensaioItem,
                    plantId,
                    clone,
                    parcela,
                    numPlanta,
                    resultado,
                    data,
                    lat,
                    lng,
                    sadios,
                    vassoura,
                    podridao,
                    naoAvaliavel,
                    deteccoes
            ));

            uriPorPlanta.put(plantId, texto(node, "uri", ""));
        }

        if (itensSync.isEmpty()) {
            throw new RegraNegocioException("Nenhum registro válido encontrado no celular.");
        }

        SyncJsonRequest syncRequest = new SyncJsonRequest(ensaioEfetivo, itensSync);
        ResultadoImportacaoCsvResponse baseResp = coletaSyncService.sincronizarJson(syncRequest, emailPesquisador);

        // Lista arquivos em /sdcard/Pictures/CEPEC_Analise/ como fallback para fotos tiradas pela câmera
        List<String> arquivosCepecAnalise = listarArquivosCepecAnalise(adbPath, serial);

        int fotosAnexadas = 0;
        List<String> avisos = new ArrayList<>(baseResp.avisos() != null ? baseResp.avisos() : List.of());

        if (baseResp.leituras() != null) {
            for (LeituraSincronizadaDto item : baseResp.leituras()) {
                if (item.leituraId() == null || item.leituraId() <= 0) continue;
                String plantId = item.plantId();
                String uriStr = uriPorPlanta.getOrDefault(plantId, "");

                byte[] fotoBytes = extrairFotoDoCelular(adbPath, serial, pacoteApp, plantId, uriStr, arquivosCepecAnalise);
                if (isImagemValida(fotoBytes)) {
                    try {
                        leituraService.anexarFoto(item.leituraId(), fotoBytes);
                        fotosAnexadas++;
                    } catch (Exception e) {
                        avisos.add("Aviso ao salvar foto da planta " + plantId + ": " + e.getMessage());
                    }
                }
            }
        }

        return new ResultadoImportacaoCsvResponse(
                baseResp.codigoEnsaio(),
                "Cabo USB (" + serial + ")",
                baseResp.totalLinhasProcessadas(),
                baseResp.leiturasNovasInseridas(),
                baseResp.leiturasDuplicadasIgnoradas(),
                baseResp.plantasMarcadasNaoColetaveis(),
                baseResp.alertasGpsZerado(),
                baseResp.totalFrutosImportadosLote(),
                fotosAnexadas,
                Math.max(0, baseResp.totalLinhasProcessadas() - fotosAnexadas),
                baseResp.processadoEm(),
                avisos,
                baseResp.leituras()
        );
    }

    /**
     * Envia a planilha de rota diretamente para a aba "Modo Rota" do CacauApp no celular conectado via USB,
     * sem que o trabalhador precise selecionar arquivos CSV nem mapear colunas manualmente.
     */
    public String enviarRotaParaCelularViaUsb(String codigoEnsaio, List<ItemRotaDto> itensRota) {
        if (itensRota == null || itensRota.isEmpty()) {
            throw new RegraNegocioException("A rota do ensaio " + codigoEnsaio + " está vazia.");
        }

        String adbPath = localizarAdbExecutavel();
        String serial = detectarDispositivoConectado(adbPath);

        // Ativa túnel reverso USB por garantia
        executarComandoTexto(List.of(adbPath, "-s", serial, "reverse", "tcp:8080", "tcp:8080"), 5);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("codigoEnsaio", codigoEnsaio);
        payload.put("atualizadoEm", System.currentTimeMillis());
        payload.put("itens", itensRota);

        File tempFile = null;
        try {
            byte[] jsonBytes = objectMapper.writeValueAsBytes(payload);
            tempFile = File.createTempFile("cacau_rota_", ".json");
            Files.write(tempFile.toPath(), jsonBytes);

            String remoteTmp = "/data/local/tmp/rota_precarregada.json";
            executarComandoTexto(List.of(adbPath, "-s", serial, "push", tempFile.getAbsolutePath(), remoteTmp), 10);
            executarComandoTexto(List.of(adbPath, "-s", serial, "shell", "run-as", APP_PACKAGE, "mkdir", "-p", "files"), 5);
            executarComandoTexto(List.of(adbPath, "-s", serial, "shell", "run-as", APP_PACKAGE, "cp", remoteTmp, "files/rota_precarregada.json"), 8);

            // Verifica se o arquivo chegou com sucesso no armazenamento interno do CacauApp
            byte[] verificado = executarComandoBinario(
                    List.of(adbPath, "-s", serial, "exec-out", "run-as", APP_PACKAGE, "cat", "files/rota_precarregada.json"),
                    5
            );
            String textoVerificado = new String(verificado, StandardCharsets.UTF_8).trim();
            if (!textoVerificado.startsWith("{") && !textoVerificado.startsWith("[")) {
                throw new RegraNegocioException("Não foi possível gravar a rota no CacauApp instalado no aparelho (" + serial + "). Verifique se o CacauApp está instalado.");
            }
            return serial;
        } catch (RegraNegocioException e) {
            throw e;
        } catch (Exception e) {
            throw new RegraNegocioException("Falha ao enviar rota via cabo USB: " + e.getMessage());
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    private String detectarPacoteInstalado(String adbPath, String serial) {
        List<String> candidatos = List.of(APP_PACKAGE, "br.gov.ceplac.cepec.cacauapp");
        for (String c : candidatos) {
            byte[] b = executarComandoBinario(
                    List.of(adbPath, "-s", serial, "exec-out", "run-as", c, "cat", "files/registros_cacau_local.json"),
                    5
            );
            String s = new String(b, StandardCharsets.UTF_8).trim();
            if (s.startsWith("[")) {
                return c;
            }
        }
        return APP_PACKAGE;
    }

    private byte[] extrairFotoDoCelular(
            String adbPath,
            String serial,
            String pacote,
            String plantId,
            String uriStr,
            List<String> arquivosCepecAnalise
    ) {
        String safeName = plantId.replaceAll("[^a-zA-Z0-9._-]", "_") + ".jpg";

        // 1. Tenta o cache interno otimizado (files/fotos_sync/<safeName>)
        byte[] bytesCache = executarComandoBinario(
                List.of(adbPath, "-s", serial, "exec-out", "run-as", pacote, "cat", "files/fotos_sync/" + safeName),
                8
        );
        if (isImagemValida(bytesCache)) {
            return bytesCache;
        }

        // 2. Se veio da galeria (file:///storage/emulated/0/...), lê direto pelo caminho do arquivo
        if (uriStr != null && uriStr.startsWith("file://")) {
            String filePath = uriStr.substring("file://".length());
            byte[] bytesFile = executarComandoBinario(
                    List.of(adbPath, "-s", serial, "exec-out", "cat", filePath),
                    8
            );
            if (isImagemValida(bytesFile)) {
                return bytesFile;
            }
        }

        // 3. Procura em /sdcard/Pictures/CEPEC_Analise/<plantId>_*.jpg
        String prefixo1 = plantId + "_";
        String prefixo2 = plantId.replaceAll("[^a-zA-Z0-9._-]", "_") + "_";
        for (String nomeArq : arquivosCepecAnalise) {
            if (nomeArq.startsWith(prefixo1) || nomeArq.startsWith(prefixo2) || nomeArq.equalsIgnoreCase(plantId + ".jpg")) {
                byte[] bytesMedia = executarComandoBinario(
                        List.of(adbPath, "-s", serial, "exec-out", "cat", "/sdcard/Pictures/CEPEC_Analise/" + nomeArq),
                        8
                );
                if (isImagemValida(bytesMedia)) {
                    return bytesMedia;
                }
            }
        }

        return new byte[0];
    }

    private List<String> listarArquivosCepecAnalise(String adbPath, String serial) {
        String saida = executarComandoTexto(
                List.of(adbPath, "-s", serial, "shell", "ls", "-1t", "/sdcard/Pictures/CEPEC_Analise"),
                5
        );
        List<String> lista = new ArrayList<>();
        if (saida == null || saida.isBlank()) return lista;
        for (String linha : saida.split("\\r?\\n")) {
            String l = linha.trim();
            if (!l.isEmpty() && !l.contains("No such file")) {
                lista.add(l);
            }
        }
        return lista;
    }

    private boolean isImagemValida(byte[] bytes) {
        if (bytes == null || bytes.length < 64) return false;
        // JPEG (FF D8) ou PNG (89 50 4E 47)
        boolean isJpeg = (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8;
        boolean isPng = (bytes[0] & 0xFF) == 0x89 && (bytes[1] & 0xFF) == 0x50;
        return isJpeg || isPng;
    }

    private String localizarAdbExecutavel() {
        List<String> candidatos = new ArrayList<>();
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            candidatos.add(localAppData + "\\Android\\Sdk\\platform-tools\\adb.exe");
        }
        String androidHome = System.getenv("ANDROID_HOME");
        if (androidHome != null && !androidHome.isBlank()) {
            candidatos.add(androidHome + File.separator + "platform-tools" + File.separator + "adb.exe");
            candidatos.add(androidHome + File.separator + "platform-tools" + File.separator + "adb");
        }
        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isBlank()) {
            candidatos.add(userHome + "\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe");
        }

        for (String caminho : candidatos) {
            File f = new File(caminho);
            if (f.exists() && f.isFile()) {
                return f.getAbsolutePath();
            }
        }
        return "adb";
    }

    private String detectarDispositivoConectado(String adbPath) {
        executarComandoTexto(List.of(adbPath, "start-server"), 5);
        for (int tentativa = 0; tentativa < 3; tentativa++) {
            String saida = executarComandoTexto(List.of(adbPath, "devices"), 5);
            if (saida == null || saida.isBlank()) {
                throw new RegraNegocioException(
                        "Não foi possível executar o ADB no computador. Verifique se o Android SDK Platform-Tools está instalado."
                );
            }
            for (String linha : saida.split("\\r?\\n")) {
                String l = linha.trim();
                if (l.isEmpty() || l.startsWith("List of devices") || l.startsWith("*")) continue;
                if (l.endsWith("\tdevice") || l.matches("^\\S+\\s+device$")) {
                    return l.split("\\s+")[0];
                }
                if (l.contains("unauthorized")) {
                    throw new RegraNegocioException(
                            "O celular está conectado no USB, mas aguarda confirmação na tela ('Permitir Depuração USB'). "
                            + "Aceite no celular e clique novamente."
                    );
                }
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new RegraNegocioException(
                "Nenhum celular detectado no cabo USB. Conecte o celular ao PC com a Depuração USB ativa, "
                + "ou toque no botão verde 'ENVIAR PARA O PC AGORA' dentro do CacauApp pelo Wi-Fi."
        );
    }

    private String executarComandoTexto(List<String> comando, int timeoutSegundos) {
        byte[] bytes = executarComandoBinario(comando, timeoutSegundos);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private byte[] executarComandoBinario(List<String> comando, int timeoutSegundos) {
        try {
            ProcessBuilder pb = new ProcessBuilder(comando);
            pb.redirectErrorStream(false);
            Process process = pb.start();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream is = process.getInputStream()) {
                byte[] buffer = new byte[8192];
                int lidos;
                while ((lidos = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, lidos);
                }
            }
            process.waitFor(timeoutSegundos, TimeUnit.SECONDS);
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            return baos.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private static String texto(JsonNode node, String campo, String padrao) {
        JsonNode v = node.get(campo);
        if (v == null || v.isNull()) return padrao;
        String s = v.asText();
        return (s != null) ? s : padrao;
    }

    private static boolean isNaoColetavel(String diag) {
        if (diag == null || diag.isBlank()) return false;
        String d = diag.trim().toLowerCase(Locale.ROOT);
        return d.contains("não coletável") || d.contains("nao coletavel")
                || d.contains("ausente") || d.contains("morta") || d.contains("seca");
    }

    private static String normalizarClasse(String classe) {
        if (classe == null) return "Sadio";
        String c = classe.trim().toLowerCase(Locale.ROOT);
        if (c.contains("vassoura")) return "Vassoura de Bruxa";
        if (c.contains("podrid")) return "Podridão";
        if (c.contains("avali") || c.contains("nao ") || c.contains("não ")) return "Não Avaliável";
        return "Sadio";
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        return Math.max(0.0, Math.min(1.0, v));
    }
}
