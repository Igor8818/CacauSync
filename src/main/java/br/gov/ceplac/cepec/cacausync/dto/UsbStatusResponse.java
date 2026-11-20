package br.gov.ceplac.cepec.cacausync.dto;

public record UsbStatusResponse(
        boolean adbDisponivel,
        boolean dispositivoConectado,
        String serialDispositivo,
        boolean appInstalado,
        int registrosNoCelular,
        boolean rotaNoCelular,
        String mensagem
) {
}
