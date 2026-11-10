import React, { useState } from 'react';
import {
  Upload,
  Usb,
  Smartphone,
  Wifi,
  Sparkles,
  RefreshCw,
  FolderSync,
  ChevronDown,
  ChevronUp,
  FileSpreadsheet,
} from 'lucide-react';
import {
  carregarRotaTc1801Api,
  enviarRotaUsbApi,
  importarPlanilhaRotaApi,
  sincronizarCsvManualApi,
  sincronizarViaUsbApi,
} from '../api';

interface WorkflowStepsProps {
  ensaio: string;
  totalPlantasRota: number;
  onAtualizacaoNecessaria: () => void;
  onToast: (msg: string, erro?: boolean) => void;
}

export const WorkflowSteps: React.FC<WorkflowStepsProps> = ({
  ensaio,
  totalPlantasRota,
  onAtualizacaoNecessaria,
  onToast,
}) => {
  const [carregandoRota, setCarregandoRota] = useState(false);
  const [statusRotaMsg, setStatusRotaMsg] = useState<string | null>(null);

  const [carregandoSync, setCarregandoSync] = useState(false);
  const [statusSyncMsg, setStatusSyncMsg] = useState<string | null>(null);

  const [expandirManual, setExpandirManual] = useState(false);
  const [arquivoCsvManual, setArquivoCsvManual] = useState<File | null>(null);
  const [fotosManuais, setFotosManuais] = useState<FileList | null>(null);

  // Passo 1: Ações
  const handleImportarArquivo = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setCarregandoRota(true);
    setStatusRotaMsg(`Lendo "${file.name}" e enviando sequência ao celular via USB...`);
    try {
      const res = await importarPlanilhaRotaApi(ensaio, file, true);
      setStatusRotaMsg(`✅ ${res.mensagem}`);
      onToast(`Planilha carregada com ${res.totalPlantasRota} plantas!`);
      onAtualizacaoNecessaria();
    } catch (err: any) {
      setStatusRotaMsg(`❌ ${err.message}`);
      onToast(err.message, true);
    } finally {
      setCarregandoRota(false);
      e.target.value = '';
    }
  };

  const handleEnviarRotaUsb = async () => {
    setCarregandoRota(true);
    setStatusRotaMsg('Enviando rota ativa para a aba "Modo Rota" do celular via USB...');
    try {
      const res = await enviarRotaUsbApi(ensaio);
      setStatusRotaMsg(`✅ ${res.mensagem}`);
      onToast(`Rota (${res.totalPlantasRota} plantas) enviada para o celular!`);
    } catch (err: any) {
      setStatusRotaMsg(`❌ ${err.message}`);
      onToast(err.message, true);
    } finally {
      setCarregandoRota(false);
    }
  };

  const handleCarregarTc1801 = async () => {
    setCarregandoRota(true);
    setStatusRotaMsg('Carregando planilha oficial do TC-1801 (940 plantas) e injetando no celular...');
    try {
      const res = await carregarRotaTc1801Api(ensaio);
      setStatusRotaMsg(`✅ ${res.mensagem}`);
      onToast(`Planilha do TC-1801 (${res.totalPlantasRota} plantas) carregada!`);
      onAtualizacaoNecessaria();
    } catch (err: any) {
      setStatusRotaMsg(`❌ ${err.message}`);
      onToast(err.message, true);
    } finally {
      setCarregandoRota(false);
    }
  };

  // Passo 2: Ações
  const handlePuxarUsb = async () => {
    setCarregandoSync(true);
    setStatusSyncMsg('Conectando ao celular via USB e extraindo coletas + fotos...');
    try {
      const res = await sincronizarViaUsbApi(ensaio);
      setStatusSyncMsg(`✅ ${res.mensagem || 'Fotos e coletas extraídas com sucesso!'}`);
      onToast('Sincronização via USB concluída!');
      onAtualizacaoNecessaria();
    } catch (err: any) {
      setStatusSyncMsg(`❌ ${err.message}`);
      onToast(err.message, true);
    } finally {
      setCarregandoSync(false);
    }
  };

  const handleUploadManual = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!arquivoCsvManual && (!fotosManuais || fotosManuais.length === 0)) {
      onToast('Selecione um arquivo CSV ou fotos para enviar.', true);
      return;
    }
    setCarregandoSync(true);
    setStatusSyncMsg('Processando arquivos manuais...');
    try {
      const res = await sincronizarCsvManualApi(ensaio, arquivoCsvManual, fotosManuais);
      setStatusSyncMsg(`✅ ${res.msg}`);
      onToast('Dados importados com sucesso!');
      setArquivoCsvManual(null);
      setFotosManuais(null);
      onAtualizacaoNecessaria();
    } catch (err: any) {
      setStatusSyncMsg(`❌ ${err.message}`);
      onToast(err.message, true);
    } finally {
      setCarregandoSync(false);
    }
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
      {/* PASSO 1: PLANEJAMENTO E ENVIO DE ROTA */}
      <div className="bg-white rounded-2xl border border-stone-200/90 p-5 shadow-sm flex flex-col justify-between">
        <div>
          <div className="flex items-center justify-between pb-3 border-b border-stone-100 mb-4">
            <div className="flex items-center gap-2.5">
              <span className="w-7 h-7 rounded-lg bg-emerald-100 text-emerald-800 font-extrabold text-xs flex items-center justify-center">
                1
              </span>
              <div>
                <h2 className="text-sm font-extrabold text-stone-900">
                  Planejar e Enviar Rota para o Celular
                </h2>
                <p className="text-xs text-stone-500">
                  O trabalhador seguirá a ordem da planilha planta a planta
                </p>
              </div>
            </div>
            <span className="text-xs px-2.5 py-0.5 rounded-full font-bold bg-stone-100 text-stone-700 border border-stone-200">
              {totalPlantasRota} plantas na rota
            </span>
          </div>

          <p className="text-xs text-stone-600 mb-4 leading-relaxed">
            Selecione uma planilha <strong>Excel (.xlsx)</strong> ou <strong>CSV</strong> com as
            colunas de Clone, Parcela e Planta. O sistema organiza a ordem de visitação e envia ao
            app via USB ou Wi-Fi.
          </p>

          {/* Botões de Ação do Passo 1 */}
          <div className="space-y-2.5">
            <div className="flex flex-wrap gap-2">
              {/* Upload de Planilha */}
              <label className="flex-1 min-w-[190px] inline-flex items-center justify-center gap-2 py-2.5 px-4 rounded-xl bg-emerald-700 hover:bg-emerald-800 text-white font-bold text-xs shadow-sm cursor-pointer transition">
                <Upload className="w-4 h-4" />
                <span>Importar Planilha (.xlsx / .csv)</span>
                <input
                  type="file"
                  accept=".csv,.xlsx"
                  onChange={handleImportarArquivo}
                  disabled={carregandoRota}
                  className="hidden"
                />
              </label>

              {/* Enviar Atual ao USB */}
              <button
                type="button"
                onClick={handleEnviarRotaUsb}
                disabled={carregandoRota}
                className="inline-flex items-center justify-center gap-2 py-2.5 px-3.5 rounded-xl bg-stone-100 hover:bg-stone-200 text-stone-800 font-bold text-xs border border-stone-300 transition"
                title="Reenviar a rota ativa para o celular conectado no cabo USB"
              >
                <Usb className="w-4 h-4 text-stone-600" />
                <span>Enviar p/ Celular (USB)</span>
              </button>
            </div>

            {/* Rota Padrão TC-1801 */}
            <button
              type="button"
              onClick={handleCarregarTc1801}
              disabled={carregandoRota}
              className="w-full inline-flex items-center justify-center gap-2 py-2 px-3 rounded-xl bg-emerald-50 hover:bg-emerald-100 text-emerald-800 font-semibold text-xs border border-emerald-200 transition"
            >
              <Sparkles className="w-3.5 h-3.5 text-emerald-600" />
              <span>Usar Planilha Completa do Ensaio TC-1801 (940 plantas oficiais)</span>
            </button>
          </div>
        </div>

        {statusRotaMsg && (
          <div className="mt-4 p-2.5 rounded-xl bg-stone-50 border border-stone-200 text-xs text-stone-700 font-medium">
            {statusRotaMsg}
          </div>
        )}
      </div>

      {/* PASSO 2: RECEBER FOTOS E COLETAS DO TRABALHADOR */}
      <div className="bg-white rounded-2xl border border-stone-200/90 p-5 shadow-sm flex flex-col justify-between">
        <div>
          <div className="flex items-center justify-between pb-3 border-b border-stone-100 mb-4">
            <div className="flex items-center gap-2.5">
              <span className="w-7 h-7 rounded-lg bg-emerald-100 text-emerald-800 font-extrabold text-xs flex items-center justify-center">
                2
              </span>
              <div>
                <h2 className="text-sm font-extrabold text-stone-900">
                  Receber Fotos e Coletas do Celular
                </h2>
                <p className="text-xs text-stone-500">
                  Fotos de alta resolução e detecções do YOLOv8
                </p>
              </div>
            </div>
            <span className="text-xs px-2.5 py-0.5 rounded-full font-bold bg-emerald-50 text-emerald-700 border border-emerald-200 flex items-center gap-1">
              <Wifi className="w-3 h-3" />
              USB &amp; Wi-Fi
            </span>
          </div>

          <p className="text-xs text-stone-600 mb-4 leading-relaxed">
            Ao voltar da roça, conecte o celular no USB e clique no botão abaixo. Ou, se estiver no mesmo
            Wi-Fi, o trabalhador pode tocar em <strong>"SINCRONIZAR COM PC"</strong> direto no celular.
          </p>

          {/* Botão Principal 1 Clique USB */}
          <div className="flex gap-2">
            <button
              type="button"
              onClick={handlePuxarUsb}
              disabled={carregandoSync}
              className="flex-1 inline-flex items-center justify-center gap-2 py-3 px-4 rounded-xl bg-emerald-800 hover:bg-emerald-900 text-white font-bold text-xs shadow-md shadow-emerald-950/10 transition"
            >
              <Usb className="w-4 h-4 text-emerald-300" />
              <span>{carregandoSync ? 'Puxando do celular...' : 'Puxar Fotos e Coletas do Celular (1 Clique USB)'}</span>
            </button>

            <button
              type="button"
              onClick={onAtualizacaoNecessaria}
              className="p-3 rounded-xl bg-stone-100 hover:bg-stone-200 text-stone-700 border border-stone-300 transition"
              title="Atualizar lista de fotos"
            >
              <RefreshCw className="w-4 h-4" />
            </button>
          </div>

          {/* Opção Alternativa Manual */}
          <div className="mt-3">
            <button
              type="button"
              onClick={() => setExpandirManual(!expandirManual)}
              className="text-xs text-stone-500 hover:text-stone-800 font-medium inline-flex items-center gap-1 transition"
            >
              {expandirManual ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
              <span>Importar CSV ou pasta de fotos manualmente do PC (contingência)</span>
            </button>

            {expandirManual && (
              <form onSubmit={handleUploadManual} className="mt-3 p-3 rounded-xl bg-stone-50 border border-stone-200 space-y-2.5">
                <div>
                  <label className="block text-[11px] font-bold text-stone-600 mb-1">
                    Arquivo CSV de Coleta:
                  </label>
                  <input
                    type="file"
                    accept=".csv"
                    onChange={(e) => setArquivoCsvManual(e.target.files?.[0] || null)}
                    className="text-xs text-stone-600"
                  />
                </div>
                <div>
                  <label className="block text-[11px] font-bold text-stone-600 mb-1">
                    Fotos JPEG/PNG (múltiplas):
                  </label>
                  <input
                    type="file"
                    accept="image/*"
                    multiple
                    onChange={(e) => setFotosManuais(e.target.files)}
                    className="text-xs text-stone-600"
                  />
                </div>
                <button
                  type="submit"
                  disabled={carregandoSync}
                  className="w-full py-1.5 px-3 rounded-lg bg-stone-800 hover:bg-stone-900 text-white font-bold text-xs transition"
                >
                  Processar Arquivos Manuais
                </button>
              </form>
            )}
          </div>
        </div>

        {statusSyncMsg && (
          <div className="mt-4 p-2.5 rounded-xl bg-stone-50 border border-stone-200 text-xs text-stone-700 font-medium">
            {statusSyncMsg}
          </div>
        )}
      </div>
    </div>
  );
};
