import React, { useState, useEffect } from 'react';
import { Header } from './components/Header';
import { KpiBar } from './components/KpiBar';
import { WorkflowSteps } from './components/WorkflowSteps';
import { ConferenciaSplitView } from './components/ConferenciaSplitView';
import { LoginView } from './components/LoginView';
import {
  buscarRelatorioApi,
  getAuthToken,
  limparCacheFotos,
  listarEnsaiosApi,
  listarLeiturasApi,
  listarRotaJsonApi,
  setAuthToken,
  setOnUnauthorized,
} from './api';
import {
  EnsaioResumo,
  ItemRota,
  LeituraColeta,
  RelatorioProdutividade,
  UsuarioToken,
} from './types';

export const App: React.FC = () => {
  const [usuario, setUsuario] = useState<UsuarioToken | null>(() => {
    const salvo = localStorage.getItem('cacausync_user');
    return salvo ? JSON.parse(salvo) : null;
  });

  const [ensaios, setEnsaios] = useState<EnsaioResumo[]>([]);
  const [ensaioAtivo, setEnsaioAtivo] = useState<string>(() => {
    return localStorage.getItem('cacausync_ensaio') || 'TC-1801';
  });

  const [relatorio, setRelatorio] = useState<RelatorioProdutividade | null>(null);
  const [itensRota, setItensRota] = useState<ItemRota[]>([]);
  const [leituras, setLeituras] = useState<LeituraColeta[]>([]);
  const [carregandoDados, setCarregandoDados] = useState(false);

  // Sistema de Toast
  const [toastMsg, setToastMsg] = useState<{ texto: string; erro: boolean } | null>(null);

  const dispararToast = (texto: string, erro = false) => {
    setToastMsg({ texto, erro });
    setTimeout(() => {
      setToastMsg((cur) => (cur?.texto === texto ? null : cur));
    }, 4000);
  };

  // Logout automático em 401
  useEffect(() => {
    setOnUnauthorized(() => {
      fazerLogout();
      dispararToast('Sua sessão expirou. Entre novamente.', true);
    });
  }, []);

  const handleLoginSuccess = (user: UsuarioToken) => {
    setUsuario(user);
    setAuthToken(user.token);
    localStorage.setItem('cacausync_user', JSON.stringify(user));
  };

  const fazerLogout = () => {
    setUsuario(null);
    setAuthToken('');
    limparCacheFotos();
    localStorage.removeItem('cacausync_user');
  };

  // Carregar lista de ensaios após login
  useEffect(() => {
    if (!usuario) return;
    listarEnsaiosApi()
      .then((lista) => {
        setEnsaios(lista);
        if (lista.length > 0 && !lista.some((e) => e.codigo === ensaioAtivo)) {
          setEnsaioAtivo(lista[0].codigo);
        }
      })
      .catch((err) => {
        dispararToast(err.message, true);
      });
  }, [usuario]);

  // Carregar dados do ensaio ativo
  const carregarDadosDoEnsaio = async () => {
    if (!usuario || !ensaioAtivo) return;
    setCarregandoDados(true);
    try {
      const [rel, rota, leitsPage] = await Promise.all([
        buscarRelatorioApi(ensaioAtivo).catch(() => null),
        listarRotaJsonApi(ensaioAtivo).catch(() => []),
        listarLeiturasApi(ensaioAtivo, 0, 500).catch(() => ({ content: [] as LeituraColeta[] })),
      ]);

      setRelatorio(rel);
      setItensRota(rota);
      setLeituras(leitsPage.content || []);
    } catch (err: any) {
      dispararToast(`Erro ao carregar dados do ensaio: ${err.message}`, true);
    } finally {
      setCarregandoDados(false);
    }
  };

  useEffect(() => {
    if (usuario) {
      localStorage.setItem('cacausync_ensaio', ensaioAtivo);
      carregarDadosDoEnsaio();
    }
  }, [usuario, ensaioAtivo]);

  // Se não estiver logado, exibe a tela de login moderna com hero institucional
  if (!usuario) {
    return <LoginView onLoginSuccess={handleLoginSuccess} />;
  }

  return (
    <div className="min-h-screen flex flex-col bg-stone-100 text-stone-900">
      {/* Topo Institucional */}
      <Header
        usuario={usuario}
        ensaios={ensaios}
        ensaioAtivo={ensaioAtivo}
        onTrocarEnsaio={(cod) => setEnsaioAtivo(cod)}
        onLogout={fazerLogout}
      />

      {/* Conteúdo Principal */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-6">
        {/* Barra de Progresso e Métricas Consolidadas */}
        <KpiBar
          relatorio={relatorio}
          totalPlantasRota={itensRota.length}
        />

        {/* Passos 1 e 2: Logística de Rota e Recepção de Fotos */}
        <WorkflowSteps
          ensaio={ensaioAtivo}
          totalPlantasRota={itensRota.length}
          onAtualizacaoNecessaria={carregarDadosDoEnsaio}
          onToast={dispararToast}
        />

        {/* Passo 3: Conferência Fitossanitária com Tela Dividida */}
        <ConferenciaSplitView
          leituras={leituras}
          itensRota={itensRota}
          ensaio={ensaioAtivo}
          onAtualizar={carregarDadosDoEnsaio}
          onToast={dispararToast}
        />
      </main>

      {/* Rodapé Informativo */}
      <footer className="bg-white border-t border-stone-200 py-4 text-center text-xs text-stone-500">
        <div>
          CEPLAC • CEPEC — Centro de Pesquisas do Cacau • Rodovia Jorge Amado, km 22, Ilhéus/Itabuna - BA
        </div>
        <div className="text-[11px] text-stone-400 mt-1">
          CacauSync &amp; CacauApp — Visão Computacional Embarcada (Edge AI / YOLOv8) &amp; Melhoramento Genético
        </div>
      </footer>

      {/* Notificação Toast Flutuante */}
      {toastMsg && (
        <div
          className={`fixed bottom-5 right-5 z-50 px-4 py-3 rounded-2xl shadow-xl text-xs font-bold transition-all transform duration-300 ${
            toastMsg.erro
              ? 'bg-rose-900 text-rose-100 border border-rose-700'
              : 'bg-stone-900 text-white border border-stone-700'
          }`}
        >
          {toastMsg.texto}
        </div>
      )}
    </div>
  );
};
