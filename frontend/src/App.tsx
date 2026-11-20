import React, { useCallback, useEffect, useState } from 'react';
import {
  buscarRelatorioApi,
  getAuthToken,
  limparCacheFotos,
  listarEnsaiosApi,
  listarRotaJsonApi,
  listarTodasLeiturasApi,
  setAuthToken,
  setOnUnauthorized,
} from './api';
import { ConferenciaSplitView } from './components/ConferenciaSplitView';
import { Header } from './components/Header';
import { KpiBar } from './components/KpiBar';
import { LoginView } from './components/LoginView';
import { MapaFocosView } from './components/MapaFocosView';
import { RankingClonesView } from './components/RankingClonesView';
import { WorkflowSteps } from './components/WorkflowSteps';
import {
  EnsaioResumo,
  ItemRota,
  LeituraColeta,
  RelatorioProdutividade,
  UsuarioToken,
} from './types';

function carregarUsuarioStorage(): UsuarioToken | null {
  try {
    const raw = localStorage.getItem('cacausync_user');
    return raw ? (JSON.parse(raw) as UsuarioToken) : null;
  } catch {
    return null;
  }
}

type AbaPrincipal = 'CONFERENCIA' | 'RANKING' | 'MAPA';

export const App: React.FC = () => {
  const [token, setToken] = useState<string>(() => getAuthToken());
  const [usuario, setUsuario] = useState<UsuarioToken | null>(() => carregarUsuarioStorage());

  const [ensaios, setEnsaios] = useState<EnsaioResumo[]>([]);
  const [ensaioAtivo, setEnsaioAtivo] = useState<string>('TC-1801');
  const [relatorio, setRelatorio] = useState<RelatorioProdutividade | null>(null);
  const [itensRota, setItensRota] = useState<ItemRota[]>([]);
  const [leituras, setLeituras] = useState<LeituraColeta[]>([]);

  const [indiceFruto, setIndiceFruto] = useState<number>(22);
  const [plantasPorHa, setPlantasPorHa] = useState<number>(1111);
  const [campanha, setCampanha] = useState<string>('');
  const [aba, setAba] = useState<AbaPrincipal>('CONFERENCIA');
  const [leituraFocadaId, setLeituraFocadaId] = useState<number | null>(null);

  const [toast, setToast] = useState<{ msg: string; tipo: 'ok' | 'erro' } | null>(null);

  const exibirToast = useCallback((msg: string, tipo: 'ok' | 'erro' = 'ok') => {
    setToast({ msg, tipo });
    window.setTimeout(() => {
      setToast((cur) => (cur?.msg === msg ? null : cur));
    }, 4500);
  }, []);

  const handleLogout = useCallback(() => {
    setAuthToken('');
    localStorage.removeItem('cacausync_user');
    limparCacheFotos();
    setToken('');
    setUsuario(null);
  }, []);

  useEffect(() => {
    setOnUnauthorized(() => {
      handleLogout();
      exibirToast('Sessão expirada. Faça login novamente.', 'erro');
    });
  }, [handleLogout, exibirToast]);

  const carregarDados = useCallback(
    async (codigoEnsaioAlvo: string) => {
      if (!token) return;
      try {
        const [listaEnsaios, rel, rota, todasLeituras] = await Promise.all([
          listarEnsaiosApi(),
          buscarRelatorioApi(codigoEnsaioAlvo, {
            indiceFruto,
            plantasPorHa,
            campanha: campanha || undefined,
          }).catch(() => null),
          listarRotaJsonApi(codigoEnsaioAlvo).catch(() => []),
          listarTodasLeiturasApi(
            codigoEnsaioAlvo,
            undefined,
            campanha || undefined
          ).catch(() => []),
        ]);
        setEnsaios(listaEnsaios);
        setRelatorio(rel);
        setItensRota(rota);
        setLeituras(todasLeituras);
      } catch (err: any) {
        exibirToast(err.message || 'Erro ao carregar dados do ensaio.', 'erro');
      }
    },
    [token, indiceFruto, plantasPorHa, campanha, exibirToast]
  );

  useEffect(() => {
    if (token) {
      carregarDados(ensaioAtivo);
    }
  }, [token, ensaioAtivo, carregarDados]);

  if (!token) {
    return (
      <LoginView
        onLoginSuccess={(u) => {
          setUsuario(u);
          setToken(u.token);
        }}
      />
    );
  }

  return (
    <div className="min-h-screen bg-[#F5F7F5] text-gray-900">
      <Header
        usuario={
          usuario || {
            token,
            tipo: 'Bearer',
            nomePesquisador: 'Pesquisador CEPEC',
            email: '',
            role: 'ROLE_PESQUISADOR',
            expiraEmSegundos: 0,
          }
        }
        ensaios={ensaios}
        ensaioAtivo={ensaioAtivo}
        onTrocarEnsaio={(novo) => {
          limparCacheFotos();
          setEnsaioAtivo(novo);
        }}
        onLogout={handleLogout}
        onToast={exibirToast}
      />

      {toast && (
        <div
          className={`fixed bottom-5 right-5 z-50 px-4 py-3 rounded-xl shadow-lg text-sm font-bold text-white transition-all ${
            toast.tipo === 'erro' ? 'bg-red-700' : 'bg-emerald-800'
          }`}
        >
          {toast.msg}
        </div>
      )}

      <main className="max-w-7xl mx-auto px-4 py-5">
        <KpiBar
          relatorio={relatorio}
          totalPlantasRota={itensRota.length}
          leituras={leituras}
        />

        {/* Abas principais */}
        <div className="flex flex-wrap gap-2 mb-4 border-b border-emerald-200 pb-3">
          {(
            [
              ['CONFERENCIA', '1. Logística & Conferência'],
              ['RANKING', '2. Ranking Clones & @/ha'],
              ['MAPA', '3. Mapa de Focos GPS'],
            ] as const
          ).map(([id, titulo]) => (
            <button
              key={id}
              onClick={() => setAba(id)}
              className={`px-4 py-2 rounded-xl text-sm font-extrabold transition ${
                aba === id
                  ? 'bg-emerald-800 text-white shadow-sm'
                  : 'bg-white text-emerald-950 border border-emerald-100 hover:bg-emerald-50'
              }`}
            >
              {titulo}
            </button>
          ))}
        </div>

        {aba === 'CONFERENCIA' && (
          <>
            <WorkflowSteps
              ensaio={ensaioAtivo}
              totalPlantasRota={itensRota.length}
              onAtualizacaoNecessaria={() => carregarDados(ensaioAtivo)}
              onToast={exibirToast}
            />
            <ConferenciaSplitView
              leituras={leituras}
              itensRota={itensRota}
              ensaio={ensaioAtivo}
              indiceFruto={indiceFruto}
              leituraFocadaId={leituraFocadaId}
              onAtualizar={() => carregarDados(ensaioAtivo)}
              onToast={exibirToast}
            />
          </>
        )}

        {aba === 'RANKING' && (
          <RankingClonesView
            ensaio={ensaioAtivo}
            relatorio={relatorio}
            indiceFruto={indiceFruto}
            plantasPorHa={plantasPorHa}
            campanha={campanha}
            onAlterarParametros={(idx, plHa, camp) => {
              setIndiceFruto(idx);
              setPlantasPorHa(plHa);
              setCampanha(camp);
            }}
            onToast={exibirToast}
          />
        )}

        {aba === 'MAPA' && (
          <MapaFocosView
            leituras={leituras}
            onAbrirNaConferencia={(id) => {
              setLeituraFocadaId(id);
              setAba('CONFERENCIA');
            }}
          />
        )}
      </main>
    </div>
  );
};

export default App;
