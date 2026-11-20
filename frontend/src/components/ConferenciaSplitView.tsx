import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  anexarFotoLeituraApi,
  carregarFotoUrlApi,
  excluirLeituraApi,
  validarLeituraApi,
} from '../api';
import { DeteccaoBox, ItemRota, LeituraColeta, StatusValidacao } from '../types';

interface ConferenciaSplitViewProps {
  leituras: LeituraColeta[];
  itensRota: ItemRota[];
  ensaio: string;
  indiceFruto?: number;
  leituraFocadaId?: number | null;
  onAtualizar: () => void;
  onToast: (msg: string, tipo?: 'ok' | 'erro') => void;
}

type ClasseCacau = 'Sadio' | 'Vassoura de Bruxa' | 'Podridão' | 'Não Avaliável';
type ModoOverlay = 'COMPLETO' | 'SUTIL' | 'LIMPO';
type HandleCanto = 'TL' | 'TR' | 'BL' | 'BR';

const CLASSES_ORDEM: ClasseCacau[] = [
  'Sadio',
  'Vassoura de Bruxa',
  'Podridão',
  'Não Avaliável',
];

const COR_CLASSE: Record<ClasseCacau, string> = {
  Sadio: '#2E7D32',
  'Vassoura de Bruxa': '#8E24AA',
  Podridão: '#D32F2F',
  'Não Avaliável': '#EF6C00',
};

function normalizarClasseDto(raw?: string | null): ClasseCacau {
  const s = (raw || '').trim().toLowerCase();
  if (s.includes('vassoura')) return 'Vassoura de Bruxa';
  if (s.includes('podrid') || s.includes('monili')) return 'Podridão';
  if (s.includes('avali') || s.includes('nao ') || s.includes('não ')) return 'Não Avaliável';
  return 'Sadio';
}

function corDaClasse(raw?: string | null): string {
  return COR_CLASSE[normalizarClasseDto(raw)];
}

function proxClasse(raw?: string | null): ClasseCacau {
  const cur = normalizarClasseDto(raw);
  const idx = CLASSES_ORDEM.indexOf(cur);
  return CLASSES_ORDEM[(idx + 1) % CLASSES_ORDEM.length];
}

function clamp01(v: number): number {
  return Math.max(0, Math.min(1, v));
}

export const ConferenciaSplitView: React.FC<ConferenciaSplitViewProps> = ({
  leituras,
  itensRota,
  ensaio,
  indiceFruto = 22,
  leituraFocadaId,
  onAtualizar,
  onToast,
}) => {
  const [filtro, setFiltro] = useState<'TODAS' | 'PENDENTES' | 'COM_FOTO' | 'VALIDADAS'>('TODAS');
  const [busca, setBusca] = useState('');
  const [selecionadaId, setSelecionadaId] = useState<number | null>(null);

  const [caixas, setCaixas] = useState<DeteccaoBox[]>([]);
  const [caixaSelIdx, setCaixaSelIdx] = useState<number | null>(null);
  const [undoStack, setUndoStack] = useState<Array<{ caixas: DeteccaoBox[]; selIdx: number | null }>>([]);
  const [modoOverlay, setModoOverlay] = useState<ModoOverlay>('COMPLETO');
  const [observacao, setObservacao] = useState('');
  const [caixasEditadasManual, setCaixasEditadasManual] = useState(false);

  const [fotoUrl, setFotoUrl] = useState<string | null>(null);
  const [carregandoFoto, setCarregandoFoto] = useState(false);
  const [salvando, setSalvando] = useState(false);
  const [excluindo, setExcluindo] = useState(false);
  const [confirmarExclusao, setConfirmarExclusao] = useState(false);

  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const imgRef = useRef<HTMLImageElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const dragStateRef = useRef<{
    ativo: boolean;
    tipo: 'MOVE' | 'HANDLE' | null;
    handle: HandleCanto | null;
    idx: number;
    startNormX: number;
    startNormY: number;
    origBox: DeteccaoBox;
    arrastou: boolean;
  }>({
    ativo: false,
    tipo: null,
    handle: null,
    idx: -1,
    startNormX: 0,
    startNormY: 0,
    origBox: { classe: 'Sadio', confianca: 1, x: 0, y: 0, largura: 0.1, altura: 0.1 },
    arrastou: false,
  });

  const mapaOrdemRota = useMemo(() => {
    const m = new Map<string, number>();
    itensRota.forEach((it) => {
      if (it.plantId) m.set(it.plantId.toLowerCase(), it.ordem);
    });
    return m;
  }, [itensRota]);

  const leiturasFiltradas = useMemo(() => {
    const q = busca.trim().toLowerCase();
    const lista = leituras.filter((l) => {
      if (filtro === 'PENDENTES' && l.statusValidacao !== 'PENDENTE') return false;
      if (filtro === 'COM_FOTO' && !l.temFoto) return false;
      if (
        filtro === 'VALIDADAS' &&
        l.statusValidacao !== 'VALIDADA' &&
        l.statusValidacao !== 'AJUSTADA'
      ) {
        return false;
      }
      if (!q) return true;
      return (
        (l.plantCode || '').toLowerCase().includes(q) ||
        (l.codigoClone || '').toLowerCase().includes(q) ||
        (l.numeroParcela || '').toLowerCase().includes(q) ||
        String(l.numeroPlanta ?? '').includes(q)
      );
    });

    return [...lista].sort((a, b) => {
      const oa = a.ordemRota ?? mapaOrdemRota.get((a.plantCode || '').toLowerCase()) ?? Number.MAX_SAFE_INTEGER;
      const ob = b.ordemRota ?? mapaOrdemRota.get((b.plantCode || '').toLowerCase()) ?? Number.MAX_SAFE_INTEGER;
      if (oa !== ob) return oa - ob;
      return a.id - b.id;
    });
  }, [leituras, filtro, busca, mapaOrdemRota]);

  useEffect(() => {
    if (leituraFocadaId && leituras.some((l) => l.id === leituraFocadaId)) {
      setFiltro('TODAS');
      setSelecionadaId(leituraFocadaId);
    }
  }, [leituraFocadaId, leituras]);

  useEffect(() => {
    if (leiturasFiltradas.length === 0) {
      setSelecionadaId(null);
      return;
    }
    if (!selecionadaId || !leiturasFiltradas.some((l) => l.id === selecionadaId)) {
      setSelecionadaId(leiturasFiltradas[0].id);
    }
  }, [leiturasFiltradas, selecionadaId]);

  const leituraAtual = useMemo(
    () => leiturasFiltradas.find((l) => l.id === selecionadaId) || null,
    [leiturasFiltradas, selecionadaId]
  );

  useEffect(() => {
    setConfirmarExclusao(false);
    setUndoStack([]);
    setCaixasEditadasManual(false);
    if (!leituraAtual) {
      setCaixas([]);
      setCaixaSelIdx(null);
      setObservacao('');
      setFotoUrl(null);
      return;
    }

    const iniciais = (leituraAtual.deteccoes || []).map((d) => ({
      ...d,
      classe: normalizarClasseDto(d.classe),
    }));
    setCaixas(iniciais);
    setCaixaSelIdx(null);
    setObservacao(leituraAtual.observacaoValidacao || '');

    let cancelado = false;
    if (leituraAtual.temFoto) {
      setCarregandoFoto(true);
      carregarFotoUrlApi(leituraAtual.id)
        .then((u) => {
          if (!cancelado) setFotoUrl(u);
        })
        .finally(() => {
          if (!cancelado) setCarregandoFoto(false);
        });
    } else {
      setFotoUrl(null);
    }
    return () => {
      cancelado = true;
    };
  }, [
    leituraAtual?.id,
    leituraAtual?.temFoto,
    leituraAtual?.statusValidacao,
    leituraAtual?.validadoEm,
    leituraAtual?.qtdEfetiva,
  ]);

  const pushUndo = useCallback(
    (snapshotCaixas: DeteccaoBox[], selIdx: number | null) => {
      setUndoStack((prev) => {
        const next = [...prev, { caixas: snapshotCaixas.map((b) => ({ ...b })), selIdx }];
        return next.length > 25 ? next.slice(next.length - 25) : next;
      });
    },
    []
  );

  const desfazer = useCallback(() => {
    setUndoStack((prev) => {
      if (prev.length === 0) return prev;
      const last = prev[prev.length - 1];
      setCaixas(last.caixas);
      setCaixaSelIdx(last.selIdx);
      setCaixasEditadasManual(true);
      return prev.slice(0, prev.length - 1);
    });
  }, []);

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement | null;
      if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA')) return;

      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'z') {
        e.preventDefault();
        desfazer();
      } else if (e.key.toLowerCase() === 'h') {
        e.preventDefault();
        setModoOverlay((m) => (m === 'COMPLETO' ? 'SUTIL' : m === 'SUTIL' ? 'LIMPO' : 'COMPLETO'));
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [desfazer]);

  const desenharCanvas = useCallback(() => {
    const canvas = canvasRef.current;
    const img = imgRef.current;
    if (!canvas || !img) return;

    const w = img.clientWidth;
    const h = img.clientHeight;
    if (w <= 0 || h <= 0) return;

    if (canvas.width !== w || canvas.height !== h) {
      canvas.width = w;
      canvas.height = h;
    }

    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.clearRect(0, 0, w, h);

    caixas.forEach((box, idx) => {
      const x = box.x * w;
      const y = box.y * h;
      const bw = box.largura * w;
      const bh = box.altura * h;
      const selecionado = idx === caixaSelIdx;
      const cor = corDaClasse(box.classe);

      ctx.save();
      if (selecionado) {
        ctx.fillStyle = `${cor}33`;
        ctx.fillRect(x, y, bw, bh);
        ctx.lineWidth = 4;
        ctx.strokeStyle = '#FFFFFF';
        ctx.strokeRect(x, y, bw, bh);
        ctx.lineWidth = 2.5;
        ctx.strokeStyle = cor;
        ctx.strokeRect(x, y, bw, bh);
      } else {
        ctx.lineWidth = modoOverlay === 'COMPLETO' ? 2.5 : 1.8;
        ctx.strokeStyle = cor;
        ctx.strokeRect(x, y, bw, bh);
      }

      if (selecionado || modoOverlay === 'COMPLETO') {
        const texto = `${normalizarClasseDto(box.classe)} ${Math.round((box.confianca || 0) * 100)}%`;
        ctx.font = 'bold 11px sans-serif';
        const tw = ctx.measureText(texto).width;
        const by = Math.max(0, y - 18);
        ctx.fillStyle = cor;
        ctx.fillRect(x, by, tw + 10, 18);
        ctx.fillStyle = '#FFFFFF';
        ctx.fillText(texto, x + 5, by + 13);
      }

      if (selecionado) {
        const cantos: Array<[number, number]> = [
          [x, y],
          [x + bw, y],
          [x, y + bh],
          [x + bw, y + bh],
        ];
        cantos.forEach(([cx, cy]) => {
          ctx.beginPath();
          ctx.arc(cx, cy, 7, 0, Math.PI * 2);
          ctx.fillStyle = '#FFFFFF';
          ctx.fill();
          ctx.lineWidth = 2.5;
          ctx.strokeStyle = cor;
          ctx.stroke();
        });
      }
      ctx.restore();
    });
  }, [caixas, caixaSelIdx, modoOverlay]);

  useEffect(() => {
    desenharCanvas();
  }, [desenharCanvas, fotoUrl]);

  const obterCoords = (e: React.MouseEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    if (!canvas) return { px: 0, py: 0, nx: 0, ny: 0, w: 1, h: 1 };
    const rect = canvas.getBoundingClientRect();
    const px = e.clientX - rect.left;
    const py = e.clientY - rect.top;
    const w = Math.max(1, rect.width);
    const h = Math.max(1, rect.height);
    return {
      px,
      py,
      nx: clamp01(px / w),
      ny: clamp01(py / h),
      w,
      h,
    };
  };

  const handleMouseDown = (e: React.MouseEvent<HTMLCanvasElement>) => {
    const { px, py, nx, ny, w, h } = obterCoords(e);

    if (caixaSelIdx !== null && caixaSelIdx >= 0 && caixaSelIdx < caixas.length) {
      const sel = caixas[caixaSelIdx];
      const l = sel.x * w;
      const t = sel.y * h;
      const r = (sel.x + sel.largura) * w;
      const b = (sel.y + sel.altura) * h;

      const cantos: Array<[HandleCanto, number, number]> = [
        ['TL', l, t],
        ['TR', r, t],
        ['BL', l, b],
        ['BR', r, b],
      ];
      const handleHit = cantos.find(([, cx, cy]) => Math.hypot(px - cx, py - cy) <= 13);
      if (handleHit) {
        dragStateRef.current = {
          ativo: true,
          tipo: 'HANDLE',
          handle: handleHit[0],
          idx: caixaSelIdx,
          startNormX: nx,
          startNormY: ny,
          origBox: { ...sel },
          arrastou: false,
        };
        return;
      }

      if (nx >= sel.x && nx <= sel.x + sel.largura && ny >= sel.y && ny <= sel.y + sel.altura) {
        dragStateRef.current = {
          ativo: true,
          tipo: 'MOVE',
          handle: null,
          idx: caixaSelIdx,
          startNormX: nx,
          startNormY: ny,
          origBox: { ...sel },
          arrastou: false,
        };
        return;
      }
    }

    dragStateRef.current = {
      ativo: false,
      tipo: null,
      handle: null,
      idx: -1,
      startNormX: nx,
      startNormY: ny,
      origBox: { classe: 'Sadio', confianca: 1, x: 0, y: 0, largura: 0.1, altura: 0.1 },
      arrastou: false,
    };
  };

  const handleMouseMove = (e: React.MouseEvent<HTMLCanvasElement>) => {
    const st = dragStateRef.current;
    if (!st.ativo || st.idx < 0 || st.idx >= caixas.length) return;

    const { nx, ny } = obterCoords(e);
    const dx = nx - st.startNormX;
    const dy = ny - st.startNormY;
    if (!st.arrastou && Math.hypot(dx, dy) < 0.008) return;

    if (!st.arrastou) {
      pushUndo(caixas, caixaSelIdx);
      st.arrastou = true;
      setCaixasEditadasManual(true);
    }

    const orig = st.origBox;
    const nextBox = { ...orig };

    if (st.tipo === 'MOVE') {
      nextBox.x = clamp01(Math.min(1 - orig.largura, orig.x + dx));
      nextBox.y = clamp01(Math.min(1 - orig.altura, orig.y + dy));
    } else if (st.tipo === 'HANDLE' && st.handle) {
      const minW = 0.025;
      const minH = 0.025;
      let l = orig.x;
      let t = orig.y;
      let r = orig.x + orig.largura;
      let b = orig.y + orig.altura;

      if (st.handle === 'TL') {
        l = Math.max(0, Math.min(r - minW, nx));
        t = Math.max(0, Math.min(b - minH, ny));
      } else if (st.handle === 'TR') {
        r = Math.min(1, Math.max(l + minW, nx));
        t = Math.max(0, Math.min(b - minH, ny));
      } else if (st.handle === 'BL') {
        l = Math.max(0, Math.min(r - minW, nx));
        b = Math.min(1, Math.max(t + minH, ny));
      } else if (st.handle === 'BR') {
        r = Math.min(1, Math.max(l + minW, nx));
        b = Math.min(1, Math.max(t + minH, ny));
      }
      nextBox.x = l;
      nextBox.y = t;
      nextBox.largura = r - l;
      nextBox.altura = b - t;
    }

    setCaixas((prev) => prev.map((b, i) => (i === st.idx ? nextBox : b)));
  };

  const handleMouseUp = (e: React.MouseEvent<HTMLCanvasElement>) => {
    const st = dragStateRef.current;
    const arrastou = st.arrastou;
    dragStateRef.current.ativo = false;
    if (arrastou) return;

    const { nx, ny } = obterCoords(e);
    let hitIdx = -1;
    let bestDist = Infinity;
    caixas.forEach((b, idx) => {
      if (nx >= b.x && nx <= b.x + b.largura && ny >= b.y && ny <= b.y + b.altura) {
        const cx = b.x + b.largura / 2;
        const cy = b.y + b.altura / 2;
        const d = Math.hypot(nx - cx, ny - cy);
        if (d < bestDist) {
          bestDist = d;
          hitIdx = idx;
        }
      }
    });

    if (hitIdx === -1) {
      pushUndo(caixas, caixaSelIdx);
      const wBox = 0.12;
      const hBox = 0.15;
      const x = clamp01(Math.min(1 - wBox, Math.max(0, nx - wBox / 2)));
      const y = clamp01(Math.min(1 - hBox, Math.max(0, ny - hBox / 2)));
      const nova: DeteccaoBox = {
        classe: 'Sadio',
        confianca: 1.0,
        x,
        y,
        largura: wBox,
        altura: hBox,
      };
      setCaixas((prev) => {
        const next = [...prev, nova];
        setCaixaSelIdx(next.length - 1);
        return next;
      });
      setCaixasEditadasManual(true);
    } else if (hitIdx !== caixaSelIdx) {
      setCaixaSelIdx(hitIdx);
    } else {
      pushUndo(caixas, caixaSelIdx);
      setCaixas((prev) =>
        prev.map((b, i) => (i === hitIdx ? { ...b, classe: proxClasse(b.classe) } : b))
      );
      setCaixasEditadasManual(true);
    }
  };

  // Permite zerar falsos positivos (totalAtual = 0) quando todas as caixas são removidas
  const usarCaixas =
    caixas.length > 0 ||
    caixasEditadasManual ||
    (leituraAtual?.deteccoes?.length ?? 0) > 0;

  const sadios = usarCaixas
    ? caixas.filter((c) => normalizarClasseDto(c.classe) === 'Sadio').length
    : leituraAtual?.qtdSadios ?? 0;
  const vassoura = usarCaixas
    ? caixas.filter((c) => normalizarClasseDto(c.classe) === 'Vassoura de Bruxa').length
    : leituraAtual?.qtdVassoura ?? 0;
  const podridao = usarCaixas
    ? caixas.filter((c) => normalizarClasseDto(c.classe) === 'Podridão').length
    : leituraAtual?.qtdPodridao ?? 0;
  const naoAv = usarCaixas
    ? caixas.filter((c) => normalizarClasseDto(c.classe) === 'Não Avaliável').length
    : leituraAtual?.qtdNaoAvaliavel ?? 0;
  const totalAtual = usarCaixas ? caixas.length : leituraAtual?.qtdEfetiva ?? leituraAtual?.qtdFrutos ?? 0;

  const indiceSeguro = Math.max(1, indiceFruto);
  const estimativaKg = sadios / indiceSeguro;
  const estimativaArrobas = estimativaKg / 15;

  const enviarValidacao = async (statusSolicitado: StatusValidacao) => {
    if (!leituraAtual || salvando) return;
    setSalvando(true);
    try {
      const mudouContagemOuClasse =
        totalAtual !== leituraAtual.qtdFrutos ||
        sadios !== (leituraAtual.qtdSadios ?? 0) ||
        vassoura !== (leituraAtual.qtdVassoura ?? 0) ||
        podridao !== (leituraAtual.qtdPodridao ?? 0) ||
        naoAv !== (leituraAtual.qtdNaoAvaliavel ?? 0) ||
        caixasEditadasManual;

      const statusEfetivo: StatusValidacao =
        statusSolicitado === 'VALIDADA' && mudouContagemOuClasse ? 'AJUSTADA' : statusSolicitado;

      const detsPayload = caixas.map((b) => ({
        classe: normalizarClasseDto(b.classe),
        confianca: Number((b.confianca ?? 1).toFixed(4)),
        x: clamp01(b.x),
        y: clamp01(b.y),
        largura: clamp01(Math.min(1 - clamp01(b.x), b.largura)),
        altura: clamp01(Math.min(1 - clamp01(b.y), b.altura)),
      }));

      const payload: Parameters<typeof validarLeituraApi>[1] =
        statusEfetivo === 'VALIDADA' || statusEfetivo === 'AJUSTADA'
          ? {
              status: statusEfetivo,
              qtdFrutosValidada: statusEfetivo === 'AJUSTADA' ? totalAtual : null,
              observacao: observacao.trim() ? observacao.trim() : null,
              qtdSadios: sadios,
              qtdVassoura: vassoura,
              qtdPodridao: podridao,
              qtdNaoAvaliavel: naoAv,
              deteccoes: detsPayload,
            }
          : {
              status: statusEfetivo,
              qtdFrutosValidada: null,
              observacao: observacao.trim() ? observacao.trim() : null,
            };

      await validarLeituraApi(leituraAtual.id, payload);

      setCaixasEditadasManual(false);
      onToast(
        `Leitura ${leituraAtual.plantCode} salva como ${statusEfetivo} (${totalAtual} frutos).`,
        'ok'
      );
      onAtualizar();
    } catch (err: any) {
      onToast(err.message || 'Falha ao salvar auditoria.', 'erro');
    } finally {
      setSalvando(false);
    }
  };

  const removerCaixaSelecionada = () => {
    if (caixaSelIdx === null || caixaSelIdx < 0 || caixaSelIdx >= caixas.length) return;
    pushUndo(caixas, caixaSelIdx);
    setCaixas((prev) => prev.filter((_, idx) => idx !== caixaSelIdx));
    setCaixaSelIdx(null);
    setCaixasEditadasManual(true);
  };

  const handleExcluirLeitura = async () => {
    if (!leituraAtual || excluindo) return;
    setExcluindo(true);
    try {
      await excluirLeituraApi(leituraAtual.id);
      setConfirmarExclusao(false);
      onToast(`Leitura ${leituraAtual.plantCode} excluída do ensaio ${ensaio}.`, 'ok');
      onAtualizar();
    } catch (err: any) {
      onToast(err.message || 'Erro ao excluir leitura.', 'erro');
    } finally {
      setExcluindo(false);
    }
  };

  const handleUploadFoto = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || !leituraAtual) return;
    try {
      await anexarFotoLeituraApi(leituraAtual.id, file);
      const novaUrl = await carregarFotoUrlApi(leituraAtual.id, true);
      setFotoUrl(novaUrl);
      onToast('Foto anexada à leitura!', 'ok');
      onAtualizar();
    } catch (err: any) {
      onToast(err.message || 'Erro ao anexar foto.', 'erro');
    } finally {
      e.target.value = '';
    }
  };

  const idxAtualLista = leituraAtual
    ? leiturasFiltradas.findIndex((l) => l.id === leituraAtual.id)
    : -1;

  return (
    <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
      <div className="lg:col-span-4 bg-white border border-emerald-100 rounded-xl shadow-sm p-3 flex flex-col max-h-[760px]">
        <div className="flex flex-wrap gap-1.5 mb-2">
          {(
            [
              ['TODAS', 'Todas'],
              ['PENDENTES', 'Pendentes'],
              ['COM_FOTO', 'Com Foto'],
              ['VALIDADAS', 'Validadas'],
            ] as const
          ).map(([id, lbl]) => (
            <button
              key={id}
              onClick={() => setFiltro(id)}
              className={`px-2.5 py-1 rounded-lg text-xs font-semibold transition ${
                filtro === id
                  ? 'bg-emerald-800 text-white'
                  : 'bg-emerald-50 text-emerald-900 hover:bg-emerald-100'
              }`}
            >
              {lbl}
            </button>
          ))}
        </div>

        <input
          type="text"
          value={busca}
          onChange={(e) => setBusca(e.target.value)}
          placeholder="Buscar Plant_ID, clone, parcela..."
          className="w-full px-3 py-2 text-sm border border-gray-200 rounded-lg mb-2 focus:outline-none focus:ring-2 focus:ring-emerald-600"
        />

        <div className="text-xs text-gray-500 mb-2">
          Exibindo <strong>{leiturasFiltradas.length}</strong> de {leituras.length} leitura(s)
        </div>

        <div className="overflow-y-auto space-y-1.5 pr-1 flex-1">
          {leiturasFiltradas.map((l) => {
            const sel = l.id === selecionadaId;
            return (
              <button
                key={l.id}
                onClick={() => setSelecionadaId(l.id)}
                className={`w-full text-left p-2.5 rounded-lg border transition flex items-center justify-between ${
                  sel
                    ? 'bg-emerald-50 border-emerald-700 ring-1 ring-emerald-700'
                    : 'bg-white border-gray-200 hover:bg-gray-50'
                }`}
              >
                <div className="min-w-0 pr-2">
                  <div className="font-bold text-sm text-gray-900 truncate">{l.plantCode}</div>
                  <div className="text-xs text-gray-600">
                    {l.qtdEfetiva ?? l.qtdFrutos} frutos • {l.campanha || 'Campo'}
                    {l.alertaGps && <span className="ml-1 text-amber-700 font-semibold">• GPS 0,0</span>}
                  </div>
                </div>
                <span
                  className={`text-[10px] font-bold px-2 py-0.5 rounded-full shrink-0 ${
                    l.statusValidacao === 'PENDENTE'
                      ? 'bg-amber-100 text-amber-800'
                      : l.statusValidacao === 'REJEITADA'
                      ? 'bg-red-100 text-red-800'
                      : 'bg-emerald-100 text-emerald-800'
                  }`}
                >
                  {l.statusValidacao}
                </span>
              </button>
            );
          })}
          {leiturasFiltradas.length === 0 && (
            <div className="text-center text-sm text-gray-500 py-8">
              Nenhuma leitura encontrada para este filtro.
            </div>
          )}
        </div>
      </div>

      <div className="lg:col-span-8 bg-white border border-emerald-100 rounded-xl shadow-sm p-4 flex flex-col gap-3">
        {!leituraAtual ? (
          <div className="py-20 text-center text-gray-500">
            Selecione uma planta à esquerda para auditar foto e detecções.
          </div>
        ) : (
          <>
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-gray-100 pb-3">
              <div>
                <div className="flex items-center gap-2">
                  <h3 className="text-lg font-extrabold text-emerald-950">{leituraAtual.plantCode}</h3>
                  <span className="text-xs px-2 py-0.5 rounded bg-amber-100 text-amber-900 font-bold">
                    {estimativaArrobas.toFixed(3)} @ ({estimativaKg.toFixed(2)} kg)
                  </span>
                </div>
                <p className="text-xs text-gray-500">
                  Clone {leituraAtual.codigoClone} • Parcela {leituraAtual.numeroParcela} • Planta{' '}
                  {leituraAtual.numeroPlanta} • IA orig.: {leituraAtual.qtdFrutos} fruto(s)
                </p>
              </div>

              <div className="flex flex-wrap items-center gap-1.5">
                <button
                  onClick={desfazer}
                  disabled={undoStack.length === 0}
                  title="Desfazer (Ctrl+Z)"
                  className="px-2.5 py-1.5 text-xs font-semibold rounded-lg border border-gray-200 bg-white hover:bg-gray-50 disabled:opacity-40"
                >
                  ↶ Desfazer ({undoStack.length})
                </button>
                <button
                  onClick={() =>
                    setModoOverlay((m) => (m === 'COMPLETO' ? 'SUTIL' : m === 'SUTIL' ? 'LIMPO' : 'COMPLETO'))
                  }
                  title="Alternar rótulos (H)"
                  className="px-2.5 py-1.5 text-xs font-semibold rounded-lg border border-gray-200 bg-white hover:bg-gray-50"
                >
                  Visual: {modoOverlay}
                </button>
                <button
                  disabled={idxAtualLista <= 0}
                  onClick={() =>
                    idxAtualLista > 0 && setSelecionadaId(leiturasFiltradas[idxAtualLista - 1].id)
                  }
                  className="px-2.5 py-1.5 text-xs font-semibold rounded-lg border border-gray-200 bg-white hover:bg-gray-50 disabled:opacity-40"
                >
                  ← Ant.
                </button>
                <button
                  disabled={idxAtualLista < 0 || idxAtualLista >= leiturasFiltradas.length - 1}
                  onClick={() =>
                    idxAtualLista < leiturasFiltradas.length - 1 &&
                    setSelecionadaId(leiturasFiltradas[idxAtualLista + 1].id)
                  }
                  className="px-2.5 py-1.5 text-xs font-semibold rounded-lg border border-gray-200 bg-white hover:bg-gray-50 disabled:opacity-40"
                >
                  Próx. →
                </button>
              </div>
            </div>

            <div className="relative bg-gray-950 rounded-xl overflow-hidden flex items-center justify-center min-h-[340px]">
              {carregandoFoto ? (
                <div className="text-emerald-200 text-sm py-20">Carregando imagem em alta resolução...</div>
              ) : fotoUrl ? (
                <div className="relative inline-block max-w-full">
                  <img
                    ref={imgRef}
                    src={fotoUrl}
                    alt={leituraAtual.plantCode}
                    onLoad={desenharCanvas}
                    className="max-h-[480px] w-auto block select-none"
                  />
                  <canvas
                    ref={canvasRef}
                    onMouseDown={handleMouseDown}
                    onMouseMove={handleMouseMove}
                    onMouseUp={handleMouseUp}
                    className="absolute inset-0 cursor-crosshair"
                  />
                </div>
              ) : (
                <div className="text-center py-16 px-4 text-gray-300 space-y-3">
                  <p className="text-sm">Sem foto anexada para esta leitura.</p>
                  <button
                    onClick={() => fileInputRef.current?.click()}
                    className="px-4 py-2 bg-emerald-700 text-white rounded-lg text-xs font-bold hover:bg-emerald-600"
                  >
                    Anexar Foto Manualmente
                  </button>
                </div>
              )}
              <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                onChange={handleUploadFoto}
                className="hidden"
              />
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-5 gap-2">
              <div className="bg-emerald-50 border border-emerald-200 rounded-lg p-2 text-center">
                <div className="text-lg font-extrabold text-emerald-950">{totalAtual}</div>
                <div className="text-[11px] font-semibold text-emerald-800">Total Frutos</div>
              </div>
              <div className="bg-green-50 border border-green-200 rounded-lg p-2 text-center">
                <div className="text-lg font-extrabold text-green-800">{sadios}</div>
                <div className="text-[11px] font-semibold text-green-800">Sadios</div>
              </div>
              <div className="bg-purple-50 border border-purple-200 rounded-lg p-2 text-center">
                <div className="text-lg font-extrabold text-purple-800">{vassoura}</div>
                <div className="text-[11px] font-semibold text-purple-800">Vassoura</div>
              </div>
              <div className="bg-red-50 border border-red-200 rounded-lg p-2 text-center">
                <div className="text-lg font-extrabold text-red-700">{podridao}</div>
                <div className="text-[11px] font-semibold text-red-800">Podridão</div>
              </div>
              <div className="bg-orange-50 border border-orange-200 rounded-lg p-2 text-center">
                <div className="text-lg font-extrabold text-orange-700">{naoAv}</div>
                <div className="text-[11px] font-semibold text-orange-800">Não Avaliável</div>
              </div>
            </div>

            {caixaSelIdx !== null && caixas[caixaSelIdx] ? (
              <div className="bg-emerald-50 border border-emerald-200 rounded-lg px-3 py-2 flex flex-wrap items-center justify-between gap-2">
                <div className="flex flex-wrap items-center gap-1.5 text-xs">
                  <span className="font-bold text-emerald-950 mr-1">
                    Fruto #{caixaSelIdx + 1}:
                  </span>
                  {CLASSES_ORDEM.map((cls) => {
                    const ativa = normalizarClasseDto(caixas[caixaSelIdx].classe) === cls;
                    return (
                      <button
                        key={cls}
                        onClick={() => {
                          pushUndo(caixas, caixaSelIdx);
                          setCaixas((prev) =>
                            prev.map((b, i) => (i === caixaSelIdx ? { ...b, classe: cls } : b))
                          );
                          setCaixasEditadasManual(true);
                        }}
                        style={{
                          backgroundColor: ativa ? COR_CLASSE[cls] : '#FFFFFF',
                          color: ativa ? '#FFFFFF' : COR_CLASSE[cls],
                          borderColor: COR_CLASSE[cls],
                        }}
                        className="px-2 py-0.5 text-[11px] font-bold rounded border transition"
                      >
                        {cls}
                      </button>
                    );
                  })}
                </div>
                <button
                  onClick={removerCaixaSelecionada}
                  className="px-2.5 py-1 text-xs font-bold bg-red-600 text-white rounded-md hover:bg-red-500"
                >
                  Remover Fruto
                </button>
              </div>
            ) : (
              <div className="text-xs text-gray-500">
                Clique em uma caixa para selecionar (arraste os cantos ou o corpo; clique novamente para trocar a doença; clique fora para adicionar fruto).
              </div>
            )}

            <div className="flex flex-col sm:flex-row gap-2">
              <input
                type="text"
                value={observacao}
                onChange={(e) => setObservacao(e.target.value)}
                placeholder="Observação científica / Nota de campo (ex.: planta tombada, enxertia, foco)..."
                className="flex-1 px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-emerald-600"
              />
            </div>

            <div className="flex flex-wrap items-center justify-between gap-2 pt-1">
              <div className="flex flex-wrap gap-2">
                <button
                  onClick={() => enviarValidacao('REJEITADA')}
                  disabled={salvando}
                  className="px-3 py-2 text-xs font-bold rounded-lg border border-red-300 text-red-700 bg-white hover:bg-red-50 disabled:opacity-50"
                >
                  Rejeitar Foto
                </button>
                {leituraAtual.statusValidacao !== 'PENDENTE' && (
                  <button
                    onClick={() => enviarValidacao('PENDENTE')}
                    disabled={salvando}
                    className="px-3 py-2 text-xs font-bold rounded-lg border border-gray-300 text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-50"
                  >
                    Voltar p/ Pendente
                  </button>
                )}
                {!confirmarExclusao ? (
                  <button
                    onClick={() => setConfirmarExclusao(true)}
                    className="px-3 py-2 text-xs font-bold rounded-lg border border-red-200 text-red-600 hover:bg-red-50"
                  >
                    Excluir Leitura
                  </button>
                ) : (
                  <div className="flex items-center gap-1.5 bg-red-50 border border-red-200 rounded-lg px-2 py-1">
                    <span className="text-xs text-red-800 font-semibold">Confirmar exclusão?</span>
                    <button
                      onClick={handleExcluirLeitura}
                      disabled={excluindo}
                      className="px-2 py-1 text-xs font-bold bg-red-600 text-white rounded"
                    >
                      {excluindo ? '...' : 'Sim'}
                    </button>
                    <button
                      onClick={() => setConfirmarExclusao(false)}
                      className="px-2 py-1 text-xs font-semibold text-gray-700"
                    >
                      Não
                    </button>
                  </div>
                )}
              </div>

              <button
                onClick={() => enviarValidacao('VALIDADA')}
                disabled={salvando}
                className="px-5 py-2.5 text-sm font-extrabold rounded-lg bg-emerald-800 text-white hover:bg-emerald-700 shadow-sm disabled:opacity-50"
              >
                {salvando
                  ? 'Salvando...'
                  : caixasEditadasManual || totalAtual !== leituraAtual.qtdFrutos
                  ? `Salvar Ajustes (${totalAtual} frutos)`
                  : `Confirmar Leitura (${totalAtual} frutos)`}
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
};
