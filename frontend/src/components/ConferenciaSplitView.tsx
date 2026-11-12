import React, { useState, useEffect, useRef } from 'react';
import {
  ChevronLeft,
  ChevronRight,
  Check,
  Trash2,
  Plus,
  Minus,
  Search,
  Maximize2,
  Minimize2,
  ZoomIn,
  ZoomOut,
  RotateCcw,
  PanelLeftClose,
  PanelLeft,
  Image as ImageIcon,
  CheckCircle2,
  Clock,
  Sparkles,
  X,
} from 'lucide-react';
import { DeteccaoBox, ItemRota, LeituraColeta, StatusValidacao } from '../types';
import { carregarFotoUrlApi, excluirLeituraApi, validarLeituraApi } from '../api';

interface ConferenciaSplitViewProps {
  leituras: LeituraColeta[];
  itensRota: ItemRota[];
  ensaio: string;
  onAtualizar: () => void;
  onToast: (msg: string, erro?: boolean) => void;
}

const CORES_CLASSES: Record<string, string> = {
  sadio: '#10b981', // emerald-500
  vassoura: '#f59e0b', // amber-500
  podridao: '#ef4444', // red-500
  nao_avaliavel: '#94a3b8', // slate-400
};

function getCorClasse(classe: string): string {
  const c = (classe || '').toLowerCase().trim();
  if (c.includes('vassoura')) return CORES_CLASSES.vassoura;
  if (c.includes('podri') || c.includes('monil')) return CORES_CLASSES.podridao;
  if (c.includes('nao') || c.includes('não')) return CORES_CLASSES.nao_avaliavel;
  return CORES_CLASSES.sadio;
}

export const ConferenciaSplitView: React.FC<ConferenciaSplitViewProps> = ({
  leituras,
  itensRota,
  ensaio,
  onAtualizar,
  onToast,
}) => {
  // Filtros da lista
  const [filtro, setFiltro] = useState<'TODAS' | 'PENDENTES' | 'COM_FOTO' | 'VALIDADAS'>('COM_FOTO');
  const [busca, setBusca] = useState('');

  // Item selecionado atualmente para conferência
  const [selecionadoId, setSelecionadoId] = useState<number | null>(null);

  // Estados de Ergonomia Visual
  const [modoTelaCheia, setModoTelaCheia] = useState(false);
  const [listaLateralVisivel, setListaLateralVisivel] = useState(true);
  const [zoomNivel, setZoomNivel] = useState(1);

  // Detalhes da leitura selecionada
  const [fotoUrl, setFotoUrl] = useState<string | null>(null);
  const [carregandoFoto, setCarregandoFoto] = useState(false);
  const [caixas, setCaixas] = useState<DeteccaoBox[]>([]);
  const [caixaAtivaIndex, setCaixaAtivaIndex] = useState<number | null>(null);
  const [observacao, setObservacao] = useState('');
  const [salvando, setSalvando] = useState(false);

  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const canvasFullscreenRef = useRef<HTMLCanvasElement | null>(null);
  const imgRef = useRef<HTMLImageElement | null>(null);

  // Lista ordenada por ordemRota
  const leiturasOrdenadas = [...leituras].sort((a, b) => {
    const oA = a.ordemRota ?? 999999;
    const oB = b.ordemRota ?? 999999;
    return oA - oB;
  });

  // Filtragem da lista
  const leiturasFiltradas = leiturasOrdenadas.filter((l) => {
    if (filtro === 'PENDENTES' && l.statusValidacao !== 'PENDENTE') return false;
    if (filtro === 'VALIDADAS' && l.statusValidacao !== 'VALIDADA' && l.statusValidacao !== 'AJUSTADA') return false;
    if (filtro === 'COM_FOTO' && !l.temFoto) return false;

    if (busca.trim()) {
      const b = busca.toLowerCase();
      const matchClone = (l.codigoClone || '').toLowerCase().includes(b);
      const matchPlant = (l.plantCode || '').toLowerCase().includes(b);
      const matchParcela = (l.numeroParcela || '').toLowerCase().includes(b);
      const matchOrdem = String(l.ordemRota || '').includes(b);
      return matchClone || matchPlant || matchParcela || matchOrdem;
    }
    return true;
  });

  // Selecionar o primeiro automaticamente ao carregar
  useEffect(() => {
    if (selecionadoId === null && leiturasFiltradas.length > 0) {
      const primeiro = leiturasFiltradas.find((l) => l.temFoto) || leiturasFiltradas[0];
      if (primeiro) {
        setSelecionadoId(primeiro.id);
      }
    }
  }, [leiturasFiltradas, selecionadoId]);

  const leituraAtual = leituras.find((l) => l.id === selecionadoId) || null;

  // Carregar foto e caixas da leitura selecionada
  useEffect(() => {
    if (!leituraAtual) {
      setFotoUrl(null);
      setCaixas([]);
      return;
    }

    setCaixas(leituraAtual.deteccoes ? [...leituraAtual.deteccoes] : []);
    setObservacao(leituraAtual.observacaoValidacao || '');
    setCaixaAtivaIndex(null);
    setZoomNivel(1);

    if (leituraAtual.temFoto) {
      setCarregandoFoto(true);
      carregarFotoUrlApi(leituraAtual.id)
        .then((url) => {
          setFotoUrl(url);
          setCarregandoFoto(false);
        })
        .catch(() => setCarregandoFoto(false));
    } else {
      setFotoUrl(null);
      setCarregandoFoto(false);
    }
  }, [selecionadoId]);

  // Função para desenhar caixas no Canvas
  const desenharNoCanvas = (canvas: HTMLCanvasElement) => {
    if (!fotoUrl) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const img = new Image();
    img.src = fotoUrl;
    img.onload = () => {
      imgRef.current = img;
      canvas.width = img.naturalWidth || 800;
      canvas.height = img.naturalHeight || 600;

      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height);

      // Desenhar cada caixa YOLO
      caixas.forEach((box, idx) => {
        const x = box.x * canvas.width;
        const y = box.y * canvas.height;
        const w = box.largura * canvas.width;
        const h = box.altura * canvas.height;

        const isSelected = idx === caixaAtivaIndex;
        const cor = getCorClasse(box.classe);

        // Bounding Box
        ctx.strokeStyle = cor;
        ctx.lineWidth = isSelected ? 4 : 2.5;
        ctx.strokeRect(x, y, w, h);

        // Sombra de seleção semitransparente
        if (isSelected) {
          ctx.fillStyle = `${cor}30`;
          ctx.fillRect(x, y, w, h);
        }

        // Tag superior com nome da classe
        const label = `#${idx + 1} ${box.classe}`;
        ctx.font = 'bold 15px Inter, sans-serif';
        const txtWidth = ctx.measureText(label).width;

        ctx.fillStyle = cor;
        ctx.fillRect(x, Math.max(0, y - 24), txtWidth + 12, 24);

        ctx.fillStyle = '#ffffff';
        ctx.fillText(label, x + 6, Math.max(17, y - 7));
      });
    };
  };

  // Redesenho nos dois Canvas (normal e fullscreen)
  useEffect(() => {
    if (canvasRef.current) desenharNoCanvas(canvasRef.current);
    if (canvasFullscreenRef.current) desenharNoCanvas(canvasFullscreenRef.current);
  }, [fotoUrl, caixas, caixaAtivaIndex, modoTelaCheia]);

  // Manipulação de clique no Canvas
  const processarCliqueCanvas = (e: React.MouseEvent<HTMLCanvasElement>, canvas: HTMLCanvasElement) => {
    const rect = canvas.getBoundingClientRect();
    const scaleX = canvas.width / rect.width;
    const scaleY = canvas.height / rect.height;

    const clickX = (e.clientX - rect.left) * scaleX;
    const clickY = (e.clientY - rect.top) * scaleY;

    // Verificar se clicou em uma caixa existente
    let encontrada: number | null = null;
    caixas.forEach((box, idx) => {
      const bx = box.x * canvas.width;
      const by = box.y * canvas.height;
      const bw = box.largura * canvas.width;
      const bh = box.altura * canvas.height;

      if (clickX >= bx && clickX <= bx + bw && clickY >= by && clickY <= by + bh) {
        encontrada = idx;
      }
    });

    if (encontrada !== null) {
      setCaixaAtivaIndex(encontrada);
    } else {
      // Adicionar fruto manual na posição clicada
      const novaLargura = 0.14;
      const novaAltura = 0.22;
      const novoX = Math.max(0, Math.min(1 - novaLargura, (clickX / canvas.width) - (novaLargura / 2)));
      const novoY = Math.max(0, Math.min(1 - novaAltura, (clickY / canvas.height) - (novaAltura / 2)));

      const novaBox: DeteccaoBox = {
        classe: 'Sadio',
        confianca: 1.0,
        x: novoX,
        y: novoY,
        largura: novaLargura,
        altura: novaAltura,
      };
      setCaixas([...caixas, novaBox]);
      setCaixaAtivaIndex(caixas.length);
      onToast('Fruto adicionado manualmente!');
    }
  };

  // Alterar classe da caixa ativa
  const mudarClasseAtiva = (novaClasse: string) => {
    if (caixaAtivaIndex === null || !caixas[caixaAtivaIndex]) return;
    const novas = [...caixas];
    novas[caixaAtivaIndex] = { ...novas[caixaAtivaIndex], classe: novaClasse };
    setCaixas(novas);
  };

  // Redimensionar caixa ativa
  const redimensionarAtiva = (fator: number) => {
    if (caixaAtivaIndex === null || !caixas[caixaAtivaIndex]) return;
    const b = caixas[caixaAtivaIndex];
    const nw = Math.max(0.03, Math.min(0.85, b.largura * fator));
    const nh = Math.max(0.03, Math.min(0.85, b.altura * fator));
    const nx = Math.max(0, Math.min(1 - nw, b.x + (b.largura - nw) / 2));
    const ny = Math.max(0, Math.min(1 - nh, b.y + (b.altura - nh) / 2));

    const novas = [...caixas];
    novas[caixaAtivaIndex] = { ...b, largura: nw, altura: nh, x: nx, y: ny };
    setCaixas(novas);
  };

  // Excluir caixa ativa
  const excluirAtiva = () => {
    if (caixaAtivaIndex === null) return;
    const novas = caixas.filter((_, idx) => idx !== caixaAtivaIndex);
    setCaixas(novas);
    setCaixaAtivaIndex(null);
    onToast('Marcação removida.');
  };

  // Navegar na ordem da planilha
  const indiceAtual = leiturasFiltradas.findIndex((l) => l.id === selecionadoId);

  const irParaAnterior = () => {
    if (indiceAtual > 0) {
      setSelecionadoId(leiturasFiltradas[indiceAtual - 1].id);
    }
  };

  const irParaProxima = () => {
    if (indiceAtual < leiturasFiltradas.length - 1) {
      setSelecionadoId(leiturasFiltradas[indiceAtual + 1].id);
    }
  };

  // Salvar Validação
  const salvarValidacao = async (status: StatusValidacao, avancarApos = true) => {
    if (!leituraAtual) return;
    setSalvando(true);
    try {
      let sadios = 0, vassoura = 0, podridao = 0, naoAv = 0;
      caixas.forEach((b) => {
        const c = b.classe.toLowerCase();
        if (c.includes('vassoura')) vassoura++;
        else if (c.includes('podri') || c.includes('monil')) podridao++;
        else if (c.includes('nao') || c.includes('não')) naoAv++;
        else sadios++;
      });

      const total = sadios + vassoura + podridao + naoAv;
      const statusEfetivo: StatusValidacao =
        status === 'VALIDADA' && total !== leituraAtual.qtdFrutos ? 'AJUSTADA' : status;

      await validarLeituraApi(leituraAtual.id, {
        status: statusEfetivo,
        qtdFrutosValidada: total,
        observacao: observacao.trim() || null,
        qtdSadios: sadios,
        qtdVassoura: vassoura,
        qtdPodridao: podridao,
        qtdNaoAvaliavel: naoAv,
        deteccoes: caixas,
      });

      onToast(`Planta #${leituraAtual.ordemRota || ''} ${statusEfetivo.toLowerCase()} com sucesso!`);
      onAtualizar();

      if (avancarApos && indiceAtual < leiturasFiltradas.length - 1) {
        setSelecionadoId(leiturasFiltradas[indiceAtual + 1].id);
      }
    } catch (err: any) {
      onToast(`Erro ao salvar: ${err.message}`, true);
    } finally {
      setSalvando(false);
    }
  };

  // Excluir Coleta / Leitura inteira
  const handleExcluirLeitura = async (leitura: LeituraColeta, e?: React.MouseEvent) => {
    if (e) e.stopPropagation();
    const confirmar = window.confirm(
      `Deseja realmente excluir esta coleta da planta ${leitura.codigoClone || ''} (Parcela ${leitura.numeroParcela}, Planta ${leitura.numeroPlanta})?`
    );
    if (!confirmar) return;

    setSalvando(true);
    try {
      const idx = leiturasFiltradas.findIndex((item) => item.id === leitura.id);
      let proximoId: number | null = null;
      if (leiturasFiltradas.length > 1) {
        proximoId =
          idx < leiturasFiltradas.length - 1
            ? leiturasFiltradas[idx + 1].id
            : leiturasFiltradas[idx - 1].id;
      }

      await excluirLeituraApi(leitura.id);
      onToast(`Coleta excluída com sucesso!`);
      if (selecionadoId === leitura.id) {
        setSelecionadoId(proximoId);
      }
      onAtualizar();
    } catch (err: any) {
      onToast(`Erro ao excluir coleta: ${err.message}`, true);
    } finally {
      setSalvando(false);
    }
  };

  // Atalhos de teclado
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (['INPUT', 'TEXTAREA'].includes((e.target as HTMLElement)?.tagName)) return;

      if (e.key === 'Escape') {
        if (modoTelaCheia) {
          e.preventDefault();
          setModoTelaCheia(false);
        }
      } else if (e.key === 'f' || e.key === 'F') {
        e.preventDefault();
        setModoTelaCheia((prev) => !prev);
      } else if (e.key === 'ArrowLeft') {
        e.preventDefault();
        irParaAnterior();
      } else if (e.key === 'ArrowRight') {
        e.preventDefault();
        irParaProxima();
      } else if (e.key === '1') {
        mudarClasseAtiva('Sadio');
      } else if (e.key === '2') {
        mudarClasseAtiva('Vassoura de Bruxa');
      } else if (e.key === '3') {
        mudarClasseAtiva('Podridão Parda');
      } else if (e.key === '4') {
        mudarClasseAtiva('Não Avaliável');
      } else if (e.key === 'Delete' || e.key === 'Backspace') {
        excluirAtiva();
      } else if (e.key === '+' || e.key === '=') {
        redimensionarAtiva(1.1);
      } else if (e.key === '-' || e.key === '_') {
        redimensionarAtiva(0.9);
      } else if (e.key === 'Enter') {
        e.preventDefault();
        salvarValidacao('VALIDADA', true);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  });

  // Barra de Ferramentas Fitossanitárias Reutilizável
  const renderControlesFitossanitarios = () => (
    <div className="flex flex-wrap items-center justify-between gap-3 text-xs">
      <div className="flex flex-wrap items-center gap-1.5">
        <span className="text-[11px] font-bold text-stone-400 uppercase mr-1">
          {caixaAtivaIndex !== null ? `Fruto #${caixaAtivaIndex + 1}:` : 'Toque no fruto:'}
        </span>

        <button
          type="button"
          onClick={() => mudarClasseAtiva('Sadio')}
          disabled={caixaAtivaIndex === null}
          className="px-3 py-1.5 rounded-lg text-xs font-bold bg-emerald-700 hover:bg-emerald-600 text-white disabled:opacity-40 transition shadow-sm"
          title="Atalho: tecla 1"
        >
          🟢 Sadio (1)
        </button>
        <button
          type="button"
          onClick={() => mudarClasseAtiva('Vassoura de Bruxa')}
          disabled={caixaAtivaIndex === null}
          className="px-3 py-1.5 rounded-lg text-xs font-bold bg-amber-600 hover:bg-amber-500 text-white disabled:opacity-40 transition shadow-sm"
          title="Atalho: tecla 2"
        >
          🟠 Vassoura (2)
        </button>
        <button
          type="button"
          onClick={() => mudarClasseAtiva('Podridão Parda')}
          disabled={caixaAtivaIndex === null}
          className="px-3 py-1.5 rounded-lg text-xs font-bold bg-rose-700 hover:bg-rose-600 text-white disabled:opacity-40 transition shadow-sm"
          title="Atalho: tecla 3"
        >
          🔴 Podridão (3)
        </button>
        <button
          type="button"
          onClick={() => mudarClasseAtiva('Não Avaliável')}
          disabled={caixaAtivaIndex === null}
          className="px-3 py-1.5 rounded-lg text-xs font-bold bg-stone-700 hover:bg-stone-600 text-stone-200 disabled:opacity-40 transition shadow-sm"
          title="Atalho: tecla 4"
        >
          ⚪ Não Avaliável (4)
        </button>

        {/* Ajustes de Tamanho e Exclusão */}
        <div className="flex items-center gap-1 ml-2 pl-2 border-l border-stone-700">
          <button
            type="button"
            onClick={() => redimensionarAtiva(0.9)}
            disabled={caixaAtivaIndex === null}
            className="p-1.5 rounded-lg bg-stone-800 hover:bg-stone-700 text-stone-200 disabled:opacity-40 transition"
            title="Diminuir caixa (-)"
          >
            <Minus className="w-3.5 h-3.5" />
          </button>
          <button
            type="button"
            onClick={() => redimensionarAtiva(1.1)}
            disabled={caixaAtivaIndex === null}
            className="p-1.5 rounded-lg bg-stone-800 hover:bg-stone-700 text-stone-200 disabled:opacity-40 transition"
            title="Aumentar caixa (+)"
          >
            <Plus className="w-3.5 h-3.5" />
          </button>
          <button
            type="button"
            onClick={excluirAtiva}
            disabled={caixaAtivaIndex === null}
            className="p-1.5 rounded-lg bg-rose-900/80 hover:bg-rose-800 text-rose-200 disabled:opacity-40 transition"
            title="Excluir marcação (Del)"
          >
            <Trash2 className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* Controles de Zoom da Foto */}
      <div className="flex items-center gap-1.5 text-stone-300">
        <button
          type="button"
          onClick={() => setZoomNivel((z) => Math.max(0.7, z - 0.2))}
          className="p-1.5 rounded-lg bg-stone-800 hover:bg-stone-700 text-stone-300"
          title="Reduzir zoom da foto"
        >
          <ZoomOut className="w-3.5 h-3.5" />
        </button>
        <span className="text-[11px] font-bold text-stone-400 w-10 text-center">
          {Math.round(zoomNivel * 100)}%
        </span>
        <button
          type="button"
          onClick={() => setZoomNivel((z) => Math.min(2.5, z + 0.2))}
          className="p-1.5 rounded-lg bg-stone-800 hover:bg-stone-700 text-stone-300"
          title="Ampliar zoom da foto"
        >
          <ZoomIn className="w-3.5 h-3.5" />
        </button>
        {zoomNivel !== 1 && (
          <button
            type="button"
            onClick={() => setZoomNivel(1)}
            className="p-1.5 rounded-lg bg-stone-800 hover:bg-stone-700 text-stone-300"
            title="Resetar zoom para 100%"
          >
            <RotateCcw className="w-3.5 h-3.5" />
          </button>
        )}
      </div>
    </div>
  );

  return (
    <div className="bg-white rounded-2xl border border-stone-200/90 shadow-sm overflow-hidden flex flex-col">
      {/* Topo da Seção de Conferência */}
      <div className="p-4 sm:p-5 border-b border-stone-100 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2.5">
          <span className="w-7 h-7 rounded-lg bg-emerald-100 text-emerald-800 font-extrabold text-xs flex items-center justify-center">
            3
          </span>
          <div>
            <h2 className="text-sm font-extrabold text-stone-900">
              Conferência Fitossanitária de Fotos na Ordem da Planilha
            </h2>
            <p className="text-xs text-stone-500">
              Clique em "⛶ Tela Cheia" ou pressione "F" para inspeção ampla de alta definição
            </p>
          </div>
        </div>

        {/* Filtros Rápidos */}
        <div className="flex flex-wrap items-center gap-2">
          <div className="inline-flex rounded-xl bg-stone-100 p-1 text-xs font-semibold text-stone-600">
            <button
              onClick={() => setFiltro('COM_FOTO')}
              className={`px-3 py-1 rounded-lg transition ${
                filtro === 'COM_FOTO' ? 'bg-white text-stone-900 shadow-sm font-bold' : 'hover:text-stone-900'
              }`}
            >
              📷 Com Foto ({leituras.filter((l) => l.temFoto).length})
            </button>
            <button
              onClick={() => setFiltro('PENDENTES')}
              className={`px-3 py-1 rounded-lg transition ${
                filtro === 'PENDENTES' ? 'bg-white text-stone-900 shadow-sm font-bold' : 'hover:text-stone-900'
              }`}
            >
              ⏳ Pendentes ({leituras.filter((l) => l.statusValidacao === 'PENDENTE').length})
            </button>
            <button
              onClick={() => setFiltro('VALIDADAS')}
              className={`px-3 py-1 rounded-lg transition ${
                filtro === 'VALIDADAS' ? 'bg-white text-stone-900 shadow-sm font-bold' : 'hover:text-stone-900'
              }`}
            >
              ✅ Validadas ({leituras.filter((l) => l.statusValidacao !== 'PENDENTE').length})
            </button>
            <button
              onClick={() => setFiltro('TODAS')}
              className={`px-3 py-1 rounded-lg transition ${
                filtro === 'TODAS' ? 'bg-white text-stone-900 shadow-sm font-bold' : 'hover:text-stone-900'
              }`}
            >
              Todas ({leituras.length})
            </button>
          </div>

          {/* Campo de Busca */}
          <div className="relative">
            <Search className="w-3.5 h-3.5 text-stone-400 absolute left-2.5 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="Buscar clone, parcela..."
              value={busca}
              onChange={(e) => setBusca(e.target.value)}
              className="pl-8 pr-3 py-1 text-xs rounded-xl border border-stone-200 bg-stone-50 focus:bg-white focus:outline-none focus:ring-1 focus:ring-emerald-600 w-44"
            />
          </div>
        </div>
      </div>

      {/* ÁREA SPLIT VIEW (TELA DIVIDIDA) */}
      <div className="grid grid-cols-1 lg:grid-cols-12 min-h-[640px]">
        {/* LADO ESQUERDO: LISTA SEQUENCIAL DA PLANILHA */}
        {listaLateralVisivel && (
          <div className="lg:col-span-4 border-r border-stone-100 flex flex-col h-[680px] overflow-hidden transition-all">
            <div className="p-3 bg-stone-50 border-b border-stone-200/60 text-xs font-bold text-stone-600 flex items-center justify-between">
              <span>Ordem da Planilha ({leiturasFiltradas.length} plantas)</span>
              <button
                type="button"
                onClick={() => setListaLateralVisivel(false)}
                className="p-1 rounded text-stone-400 hover:text-stone-700 hover:bg-stone-200 transition"
                title="Ocultar lista e dar mais espaço para a foto"
              >
                <PanelLeftClose className="w-4 h-4" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto divide-y divide-stone-100">
              {leiturasFiltradas.length === 0 ? (
                <div className="p-8 text-center text-xs text-stone-400">
                  Nenhuma leitura encontrada com os filtros selecionados.
                </div>
              ) : (
                leiturasFiltradas.map((l) => {
                  const ativo = l.id === selecionadoId;
                  const isPendente = l.statusValidacao === 'PENDENTE';

                  return (
                    <div
                      key={l.id}
                      onClick={() => setSelecionadoId(l.id)}
                      className={`p-3.5 flex items-center gap-3 cursor-pointer transition select-none ${
                        ativo
                          ? 'bg-emerald-50/90 border-l-4 border-emerald-600'
                          : 'hover:bg-stone-50/80 border-l-4 border-transparent'
                      }`}
                    >
                      {/* Número de Ordem */}
                      <div
                        className={`w-9 h-9 rounded-xl flex items-center justify-center font-extrabold text-xs shadow-sm ${
                          ativo
                            ? 'bg-emerald-700 text-white'
                            : 'bg-stone-100 text-stone-700 border border-stone-200'
                        }`}
                      >
                        #{l.ordemRota || l.numeroPlanta}
                      </div>

                      {/* Dados Básicos */}
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="font-extrabold text-xs text-stone-900 truncate">
                            {l.codigoClone || 'Desconhecido'}
                          </span>
                          <span className="text-[11px] px-2 py-0.5 rounded-full font-semibold bg-stone-100 text-stone-600">
                            P{l.numeroParcela} • Pl{l.numeroPlanta}
                          </span>
                        </div>
                        <div className="text-[11px] text-stone-500 mt-0.5 flex items-center gap-2">
                          <span>{l.dataColeta || 'Sem data'}</span>
                          {l.alertaGps && (
                            <span className="text-amber-600 font-bold" title="Sem sinal GPS">
                              ⚠ GPS
                            </span>
                          )}
                        </div>
                      </div>

                      {/* Contagem, Status e Exclusão */}
                      <div className="flex items-center gap-2">
                        <div className="text-right flex flex-col items-end gap-1">
                          <span className="text-xs font-bold text-stone-800">
                            {l.qtdEfetiva ?? l.qtdFrutos} frutos
                          </span>
                          <span
                            className={`text-[10px] font-bold px-2 py-0.5 rounded-full uppercase tracking-wider ${
                              isPendente
                                ? 'bg-amber-100 text-amber-800'
                                : 'bg-emerald-100 text-emerald-800'
                            }`}
                          >
                            {l.statusValidacao}
                          </span>
                        </div>
                        <button
                          type="button"
                          onClick={(e) => handleExcluirLeitura(l, e)}
                          disabled={salvando}
                          className="p-1.5 rounded-lg text-stone-400 hover:text-rose-600 hover:bg-rose-50 transition"
                          title="Excluir esta coleta/foto"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </div>
        )}

        {/* LADO DIREITO: AUDITOR DA FOTO E CAIXAS YOLO */}
        <div
          className={`${
            listaLateralVisivel ? 'lg:col-span-8' : 'lg:col-span-12'
          } bg-stone-900 flex flex-col h-[680px] overflow-hidden text-white transition-all`}
        >
          {leituraAtual ? (
            <>
              {/* Barra Superior do Auditor */}
              <div className="p-3 bg-stone-950/90 border-b border-stone-800 flex items-center justify-between text-xs">
                <div className="flex items-center gap-2">
                  {!listaLateralVisivel && (
                    <button
                      type="button"
                      onClick={() => setListaLateralVisivel(true)}
                      className="p-1.5 rounded-lg bg-stone-800 hover:bg-stone-700 text-stone-200 mr-1 transition"
                      title="Exibir lista da rota"
                    >
                      <PanelLeft className="w-4 h-4" />
                    </button>
                  )}
                  <span className="font-black text-emerald-400 text-sm">
                    Planta #{leituraAtual.ordemRota || leituraAtual.numeroPlanta}
                  </span>
                  <span className="text-stone-500">•</span>
                  <span className="font-extrabold text-white text-sm">{leituraAtual.codigoClone}</span>
                  <span className="text-stone-500">•</span>
                  <span className="text-stone-300">
                    Parcela {leituraAtual.numeroParcela}, Planta {leituraAtual.numeroPlanta}
                  </span>
                </div>

                {/* Navegador e Botão de TELA CHEIA */}
                <div className="flex items-center gap-2">
                  <div className="flex items-center gap-1 bg-stone-900 px-2 py-1 rounded-xl border border-stone-800">
                    <button
                      onClick={irParaAnterior}
                      disabled={indiceAtual <= 0}
                      className="p-1 rounded-lg bg-stone-800 hover:bg-stone-700 disabled:opacity-30 transition text-stone-200"
                      title="Planta anterior (Seta para a Esquerda)"
                    >
                      <ChevronLeft className="w-4 h-4" />
                    </button>
                    <span className="text-[11px] text-stone-300 font-semibold px-2">
                      {indiceAtual + 1} de {leiturasFiltradas.length}
                    </span>
                    <button
                      onClick={irParaProxima}
                      disabled={indiceAtual >= leiturasFiltradas.length - 1}
                      className="p-1 rounded-lg bg-stone-800 hover:bg-stone-700 disabled:opacity-30 transition text-stone-200"
                      title="Próxima planta (Seta para a Direita)"
                    >
                      <ChevronRight className="w-4 h-4" />
                    </button>
                  </div>

                  {/* BOTÃO EXPANDIR PARA TELA CHEIA */}
                  <button
                    type="button"
                    onClick={() => setModoTelaCheia(true)}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-emerald-700 hover:bg-emerald-600 text-white font-extrabold text-xs shadow-md shadow-emerald-950/30 transition"
                    title="Abrir em Tela Cheia Imersiva (Atalho: tecla F)"
                  >
                    <Maximize2 className="w-4 h-4" />
                    <span className="hidden sm:inline">Tela Cheia</span>
                  </button>
                </div>
              </div>

              {/* Área do Canvas com Scroll/Pan e Zoom */}
              <div className="flex-1 relative flex items-center justify-center p-2 bg-stone-950/70 overflow-auto">
                {carregandoFoto ? (
                  <div className="text-center text-xs text-stone-400">
                    Carregando foto em alta resolução...
                  </div>
                ) : fotoUrl ? (
                  <div
                    className="relative transition-transform duration-150"
                    style={{ transform: `scale(${zoomNivel})`, transformOrigin: 'center center' }}
                  >
                    <canvas
                      ref={canvasRef}
                      onClick={(e) => canvasRef.current && processarCliqueCanvas(e, canvasRef.current)}
                      className="max-h-[500px] max-w-full rounded-xl shadow-2xl cursor-crosshair border border-stone-800"
                    />
                  </div>
                ) : (
                  <div className="text-center p-8 text-stone-400 text-xs flex flex-col items-center gap-2">
                    <ImageIcon className="w-10 h-10 text-stone-600" />
                    <span>Esta leitura não possui foto associada.</span>
                  </div>
                )}
              </div>

              {/* Barra Inferior com Classificação e Ações */}
              <div className="p-3.5 bg-stone-950 border-t border-stone-800 space-y-3">
                {renderControlesFitossanitarios()}

                {/* Linha de Aprovação e Exclusão */}
                <div className="flex flex-wrap items-center justify-between gap-3 pt-2 border-t border-stone-800/80">
                  <div className="text-xs text-stone-400">
                    Total: <strong className="text-white">{caixas.length} frutos</strong>{' '}
                    (🟢 {caixas.filter((c) => !c.classe.toLowerCase().includes('vassoura') && !c.classe.toLowerCase().includes('podri') && !c.classe.toLowerCase().includes('nao')).length} sadios,{' '}
                    🟠 {caixas.filter((c) => c.classe.toLowerCase().includes('vassoura')).length} vassoura,{' '}
                    🔴 {caixas.filter((c) => c.classe.toLowerCase().includes('podri')).length} podridão)
                  </div>

                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => handleExcluirLeitura(leituraAtual)}
                      disabled={salvando}
                      className="px-3.5 py-2.5 rounded-xl bg-rose-950/90 hover:bg-rose-800 text-rose-200 hover:text-white font-bold text-xs flex items-center gap-1.5 border border-rose-800/70 transition cursor-pointer"
                      title="Excluir esta foto/coleta do sistema"
                    >
                      <Trash2 className="w-4 h-4" />
                      <span>Excluir Foto</span>
                    </button>

                    <button
                      type="button"
                      onClick={() => salvarValidacao('VALIDADA', true)}
                      disabled={salvando}
                      className="px-5 py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-500 active:bg-emerald-700 text-white font-extrabold text-xs flex items-center gap-2 shadow-lg shadow-emerald-950/40 transition cursor-pointer"
                    >
                      <Check className="w-4 h-4" />
                      <span>{salvando ? 'Salvando...' : 'Aprovar Foto e Ir p/ Próxima (Enter)'}</span>
                    </button>
                  </div>
                </div>
              </div>
            </>
          ) : (
            <div className="flex-1 flex items-center justify-center text-xs text-stone-500">
              Selecione uma planta na lista ao lado para conferir.
            </div>
          )}
        </div>
      </div>

      {/* =====================================================================
          MODO TELA CHEIA IMERSIVO (100% DA JANELA COM MÁXIMA RESOLUÇÃO)
          ===================================================================== */}
      {modoTelaCheia && leituraAtual && (
        <div className="fixed inset-0 z-50 bg-stone-950/98 backdrop-blur-md flex flex-col text-white animate-fadeIn">
          {/* Topo Flutuante da Tela Cheia */}
          <div className="p-4 bg-stone-950/90 border-b border-stone-800 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <span className="w-8 h-8 rounded-xl bg-emerald-600/30 border border-emerald-500/50 text-emerald-400 font-black text-sm flex items-center justify-center">
                #{leituraAtual.ordemRota || leituraAtual.numeroPlanta}
              </span>
              <div>
                <div className="flex items-center gap-2">
                  <h3 className="text-base font-black text-white">{leituraAtual.codigoClone}</h3>
                  <span className="text-xs px-2.5 py-0.5 rounded-full font-bold bg-stone-800 text-stone-300">
                    Parcela {leituraAtual.numeroParcela} • Planta {leituraAtual.numeroPlanta}
                  </span>
                </div>
                <p className="text-[11px] text-stone-400">
                  Data: {leituraAtual.dataColeta || 'Sem data'} • Ensaio: {ensaio}
                </p>
              </div>
            </div>

            {/* Navegador Central Flutuante */}
            <div className="flex items-center gap-2 bg-stone-900/90 px-3 py-1.5 rounded-2xl border border-stone-800 shadow-lg">
              <button
                type="button"
                onClick={irParaAnterior}
                disabled={indiceAtual <= 0}
                className="px-2.5 py-1.5 rounded-xl bg-stone-800 hover:bg-stone-700 disabled:opacity-30 transition flex items-center gap-1 text-xs font-bold"
              >
                <ChevronLeft className="w-4 h-4" />
                <span className="hidden sm:inline">Anterior</span>
              </button>
              <span className="text-xs text-stone-300 font-bold px-3">
                {indiceAtual + 1} de {leiturasFiltradas.length}
              </span>
              <button
                type="button"
                onClick={irParaProxima}
                disabled={indiceAtual >= leiturasFiltradas.length - 1}
                className="px-2.5 py-1.5 rounded-xl bg-stone-800 hover:bg-stone-700 disabled:opacity-30 transition flex items-center gap-1 text-xs font-bold"
              >
                <span className="hidden sm:inline">Próxima</span>
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>

            {/* Fechar Tela Cheia */}
            <button
              type="button"
              onClick={() => setModoTelaCheia(false)}
              className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-stone-800 hover:bg-rose-900 text-stone-200 hover:text-white transition font-bold text-xs"
              title="Sair da tela cheia (Esc ou F)"
            >
              <Minimize2 className="w-4 h-4" />
              <span className="hidden sm:inline">Fechar Tela Cheia (Esc)</span>
            </button>
          </div>

          {/* Área Central Expandida: Foto ocupa 100% do espaço útil */}
          <div className="flex-1 relative flex items-center justify-center p-3 overflow-auto bg-black/40">
            {carregandoFoto ? (
              <div className="text-center text-sm text-stone-400">
                Carregando foto em altíssima resolução...
              </div>
            ) : fotoUrl ? (
              <div
                className="transition-transform duration-150"
                style={{ transform: `scale(${zoomNivel})`, transformOrigin: 'center center' }}
              >
                <canvas
                  ref={canvasFullscreenRef}
                  onClick={(e) =>
                    canvasFullscreenRef.current &&
                    processarCliqueCanvas(e, canvasFullscreenRef.current)
                  }
                  className="max-h-[75vh] max-w-[95vw] rounded-2xl shadow-2xl cursor-crosshair border border-stone-800 object-contain"
                />
              </div>
            ) : (
              <div className="text-center p-8 text-stone-400 text-sm">
                Esta leitura não possui foto associada.
              </div>
            )}
          </div>

          {/* Barra Inferior Fixa da Tela Cheia */}
          <div className="p-4 bg-stone-950 border-t border-stone-800 space-y-3 shadow-2xl">
            {renderControlesFitossanitarios()}

            <div className="flex items-center justify-between gap-4 pt-2 border-t border-stone-800">
              <div className="text-xs text-stone-400">
                Total de Frutos: <strong className="text-white">{caixas.length}</strong> (🟢{' '}
                {
                  caixas.filter(
                    (c) =>
                      !c.classe.toLowerCase().includes('vassoura') &&
                      !c.classe.toLowerCase().includes('podri') &&
                      !c.classe.toLowerCase().includes('nao')
                  ).length
                }{' '}
                sadios, 🟠{' '}
                {caixas.filter((c) => c.classe.toLowerCase().includes('vassoura')).length} vassoura, 🔴{' '}
                {caixas.filter((c) => c.classe.toLowerCase().includes('podri')).length} podridão)
              </div>

              <div className="flex items-center gap-3">
                <button
                  type="button"
                  onClick={() => handleExcluirLeitura(leituraAtual)}
                  disabled={salvando}
                  className="px-4 py-2.5 rounded-xl bg-rose-950/90 hover:bg-rose-800 text-rose-200 hover:text-white font-bold text-xs flex items-center gap-1.5 border border-rose-800/70 transition cursor-pointer"
                  title="Excluir esta foto/coleta do sistema"
                >
                  <Trash2 className="w-4 h-4" />
                  <span>Excluir Foto</span>
                </button>

                <button
                  type="button"
                  onClick={() => salvarValidacao('VALIDADA', true)}
                  disabled={salvando}
                  className="px-6 py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-500 active:bg-emerald-700 text-white font-extrabold text-xs flex items-center gap-2 shadow-xl shadow-emerald-950/60 transition cursor-pointer"
                >
                  <Check className="w-4 h-4" />
                  <span>{salvando ? 'Salvando...' : 'Aprovar Foto e Ir p/ Próxima (Enter)'}</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
