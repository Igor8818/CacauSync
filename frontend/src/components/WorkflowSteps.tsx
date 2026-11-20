import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  buscarStatusUsbApi,
  carregarRotaTc1801Api,
  enviarRotaUsbApi,
  importarPlanilhaRotaApi,
  sincronizarCsvManualApi,
  sincronizarViaUsbApi,
} from '../api';
import { UsbStatusResponse } from '../types';

interface WorkflowStepsProps {
  ensaio: string;
  totalPlantasRota: number;
  onAtualizacaoNecessaria: () => void;
  onToast: (msg: string, tipo?: 'ok' | 'erro') => void;
}

export const WorkflowSteps: React.FC<WorkflowStepsProps> = ({
  ensaio,
  totalPlantasRota,
  onAtualizacaoNecessaria,
  onToast,
}) => {
  const [carregandoRota, setCarregandoRota] = useState(false);
  const [sincronizandoUsb, setSincronizandoUsb] = useState(false);
  const [sincronizandoManual, setSincronizandoManual] = useState(false);
  const [statusUsb, setStatusUsb] = useState<UsbStatusResponse | null>(null);
  const [checandoUsb, setChecandoUsb] = useState(false);

  const [csvFile, setCsvFile] = useState<File | null>(null);
  const [fotosFiles, setFotosFiles] = useState<FileList | null>(null);

  const inputRotaRef = useRef<HTMLInputElement | null>(null);

  const atualizarStatusUsb = useCallback(async () => {
    setChecandoUsb(true);
    try {
      const st = await buscarStatusUsbApi();
      setStatusUsb(st);
    } catch {
      setStatusUsb(null);
    } finally {
      setChecandoUsb(false);
    }
  }, []);

  useEffect(() => {
    atualizarStatusUsb();
  }, [atualizarStatusUsb]);

  const handleImportarPlanilha = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setCarregandoRota(true);
    try {
      const res = await importarPlanilhaRotaApi(ensaio, file, true);
      onToast(res.mensagem || `Rota ${res.totalPlantasRota} plantas importada!`, 'ok');
      onAtualizacaoNecessaria();
      atualizarStatusUsb();
    } catch (err: any) {
      onToast(err.message || 'Erro ao importar planilha.', 'erro');
    } finally {
      setCarregandoRota(false);
      e.target.value = '';
    }
  };

  const handleEnviarRotaUsb = async () => {
    setCarregandoRota(true);
    try {
      const res = await enviarRotaUsbApi(ensaio);
      onToast(res.mensagem || 'Rota enviada para o celular!', 'ok');
      atualizarStatusUsb();
    } catch (err: any) {
      onToast(err.message || 'Falha ao enviar rota via USB.', 'erro');
    } finally {
      setCarregandoRota(false);
    }
  };

  const handleCarregarTc1801 = async () => {
    setCarregandoRota(true);
    try {
      const res = await carregarRotaTc1801Api(ensaio);
      onToast(res.mensagem || 'Rota TC-1801 carregada!', 'ok');
      onAtualizacaoNecessaria();
      atualizarStatusUsb();
    } catch (err: any) {
      onToast(err.message || 'Falha ao carregar TC-1801.', 'erro');
    } finally {
      setCarregandoRota(false);
    }
  };

  const handleSincronizarUsb = async () => {
    setSincronizandoUsb(true);
    try {
      const res = await sincronizarViaUsbApi(ensaio);
      onToast(
        `Sincronizado! ${res.leiturasNovasInseridas ?? 0} nova(s), ${res.fotosAnexadas ?? 0} foto(s).`,
        'ok'
      );
      onAtualizacaoNecessaria();
      atualizarStatusUsb();
    } catch (err: any) {
      onToast(err.message || 'Erro ao sincronizar via cabo USB.', 'erro');
    } finally {
      setSincronizandoUsb(false);
    }
  };

  const handleImportarManual = async () => {
    setSincronizandoManual(true);
    try {
      const res = await sincronizarCsvManualApi(ensaio, csvFile, fotosFiles);
      onToast(
        `Importação manual concluída: ${res.leiturasNovasInseridas ?? 0} leitura(s), ${res.fotosAnexadas ?? 0} foto(s).`,
        'ok'
      );
      setCsvFile(null);
      setFotosFiles(null);
      onAtualizacaoNecessaria();
    } catch (err: any) {
      onToast(err.message || 'Erro ao importar CSV/fotos.', 'erro');
    } finally {
      setSincronizandoManual(false);
    }
  };

  return (
    <div className="space-y-3 mb-5">
      {/* Banner Live Status USB ADB */}
      <div className="bg-white border border-emerald-100 rounded-xl px-4 py-2.5 shadow-sm flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2.5 text-xs">
          <span
            className={`inline-block w-2.5 h-2.5 rounded-full ${
              statusUsb?.dispositivoConectado
                ? 'bg-emerald-500 animate-pulse'
                : statusUsb?.adbDisponivel
                ? 'bg-amber-500'
                : 'bg-gray-400'
            }`}
          />
          <span className="font-bold text-gray-800">Cabo USB ADB:</span>
          <span className="text-gray-600">
            {statusUsb
              ? statusUsb.mensagem
              : checandoUsb
              ? 'Verificando dispositivo...'
              : 'Status indisponível'}
          </span>
          {statusUsb?.dispositivoConectado && statusUsb.appInstalado && (
            <span className="px-2 py-0.5 rounded bg-emerald-100 text-emerald-900 font-bold">
              {statusUsb.registrosNoCelular} registro(s) no celular
            </span>
          )}
        </div>
        <button
          onClick={atualizarStatusUsb}
          disabled={checandoUsb}
          className="px-3 py-1 text-xs font-semibold rounded-lg border border-gray-200 hover:bg-gray-50 disabled:opacity-50"
        >
          {checandoUsb ? 'Verificando...' : '↻ Atualizar USB'}
        </button>
      </div>

      {/* 3 Passos do Fluxo */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
        {/* Passo 1 */}
        <div className="bg-white rounded-xl border border-emerald-100 p-4 shadow-sm flex flex-col justify-between gap-3">
          <div>
            <div className="text-xs font-bold text-emerald-700 uppercase">1. Rota & Planilha</div>
            <h4 className="font-extrabold text-gray-900 text-sm mt-0.5">
              Ensaio {ensaio} ({totalPlantasRota} plantas)
            </h4>
            <p className="text-xs text-gray-500 mt-1">
              Importe Excel (.xlsx/.csv) e envie a sequência ao CacauApp pelo USB.
            </p>
          </div>
          <div className="flex flex-wrap gap-1.5">
            <input
              ref={inputRotaRef}
              type="file"
              accept=".xlsx,.csv,.tsv"
              onChange={handleImportarPlanilha}
              className="hidden"
            />
            <button
              onClick={() => inputRotaRef.current?.click()}
              disabled={carregandoRota}
              className="px-2.5 py-1.5 text-xs font-bold bg-emerald-800 text-white rounded-lg hover:bg-emerald-700 disabled:opacity-50"
            >
              Importar .xlsx/.csv
            </button>
            <button
              onClick={handleEnviarRotaUsb}
              disabled={carregandoRota}
              className="px-2.5 py-1.5 text-xs font-bold border border-emerald-700 text-emerald-800 rounded-lg hover:bg-emerald-50 disabled:opacity-50"
            >
              Enviar Rota USB
            </button>
            {ensaio.toUpperCase() === 'TC-1801' && (
              <button
                onClick={handleCarregarTc1801}
                disabled={carregandoRota}
                className="px-2.5 py-1.5 text-xs font-semibold border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50"
              >
                Padrão TC-1801
              </button>
            )}
          </div>
        </div>

        {/* Passo 2 */}
        <div className="bg-white rounded-xl border border-emerald-100 p-4 shadow-sm flex flex-col justify-between gap-3">
          <div>
            <div className="text-xs font-bold text-emerald-700 uppercase">2. Puxar Coletas USB</div>
            <h4 className="font-extrabold text-gray-900 text-sm mt-0.5">
              1 Clique do Celular p/ PC
            </h4>
            <p className="text-xs text-gray-500 mt-1">
              Lê JSON, bounding boxes auditadas e fotos JPEG direto de files/fotos_sync no celular.
            </p>
          </div>
          <button
            onClick={handleSincronizarUsb}
            disabled={sincronizandoUsb}
            className="w-full py-2 text-xs font-extrabold bg-amber-600 text-white rounded-lg hover:bg-amber-500 disabled:opacity-50"
          >
            {sincronizandoUsb ? 'Sincronizando USB...' : '⚡ Puxar Coletas & Fotos via USB'}
          </button>
        </div>

        {/* Passo 3 */}
        <div className="bg-white rounded-xl border border-emerald-100 p-4 shadow-sm flex flex-col justify-between gap-2">
          <div>
            <div className="text-xs font-bold text-emerald-700 uppercase">3. Importação Manual</div>
            <h4 className="font-extrabold text-gray-900 text-sm mt-0.5">CSV + Fotos (Offline)</h4>
          </div>
          <div className="space-y-1.5 text-xs">
            <label className="block">
              <span className="text-gray-600 font-semibold">CSV do app:</span>
              <input
                type="file"
                accept=".csv"
                onChange={(e) => setCsvFile(e.target.files?.[0] || null)}
                className="block w-full text-[11px] text-gray-600 mt-0.5"
              />
            </label>
            <label className="block">
              <span className="text-gray-600 font-semibold">Fotos (CEPEC_Analise):</span>
              <input
                type="file"
                accept="image/*"
                multiple
                onChange={(e) => setFotosFiles(e.target.files)}
                className="block w-full text-[11px] text-gray-600 mt-0.5"
              />
            </label>
          </div>
          <button
            onClick={handleImportarManual}
            disabled={sincronizandoManual || !csvFile}
            className="w-full py-1.5 text-xs font-bold border border-emerald-800 text-emerald-900 rounded-lg hover:bg-emerald-50 disabled:opacity-40"
          >
            {sincronizandoManual ? 'Enviando...' : 'Importar CSV + Fotos'}
          </button>
        </div>
      </div>
    </div>
  );
};
