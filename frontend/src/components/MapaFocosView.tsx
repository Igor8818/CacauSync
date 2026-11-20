import React, { useMemo, useState } from 'react';
import { LeituraColeta } from '../types';

interface MapaFocosViewProps {
  leituras: LeituraColeta[];
  onAbrirNaConferencia: (leituraId: number) => void;
}

type CategoriaPonto = 'SADIO' | 'VASSOURA' | 'PODRIDAO' | 'NAO_COLETAVEL';

function categoriaDominante(l: LeituraColeta): CategoriaPonto {
  if (!l.coletavel) return 'NAO_COLETAVEL';
  const pod = l.qtdPodridao ?? 0;
  const vas = l.qtdVassoura ?? 0;
  if (pod > 0 && pod >= vas) return 'PODRIDAO';
  if (vas > 0) return 'VASSOURA';
  return 'SADIO';
}

const COR_PONTO: Record<CategoriaPonto, string> = {
  SADIO: '#2E7D32',
  VASSOURA: '#8E24AA',
  PODRIDAO: '#D32F2F',
  NAO_COLETAVEL: '#64748B',
};

export const MapaFocosView: React.FC<MapaFocosViewProps> = ({
  leituras,
  onAbrirNaConferencia,
}) => {
  const [hovered, setHovered] = useState<LeituraColeta | null>(null);

  // SPEC-003 & RN-BACK-03 §2: exclui REJEITADA e deduplica para a leitura mais recente por planta
  const leiturasValidasUnicas = useMemo(() => {
    const ordenadas = [...leituras]
      .filter((l) => l.statusValidacao !== 'REJEITADA')
      .sort((a, b) => {
        const cmpTime = (b.importadoEm || '').localeCompare(a.importadoEm || '');
        return cmpTime !== 0 ? cmpTime : b.id - a.id;
      });
    const vistas = new Set<string>();
    const unicas: LeituraColeta[] = [];
    for (const l of ordenadas) {
      const chave = (l.plantCode || String(l.id)).toLowerCase();
      if (!vistas.has(chave)) {
        vistas.add(chave);
        unicas.push(l);
      }
    }
    return unicas;
  }, [leituras]);

  const comGps = useMemo(
    () =>
      leiturasValidasUnicas.filter(
        (l) =>
          !l.alertaGps &&
          Number.isFinite(l.latitude) &&
          Number.isFinite(l.longitude) &&
          Math.abs(l.latitude) > 0.0001 &&
          Math.abs(l.longitude) > 0.0001
      ),
    [leiturasValidasUnicas]
  );

  const semGps = useMemo(
    () =>
      leiturasValidasUnicas.filter(
        (l) =>
          l.alertaGps ||
          !Number.isFinite(l.latitude) ||
          !Number.isFinite(l.longitude) ||
          (Math.abs(l.latitude) <= 0.0001 && Math.abs(l.longitude) <= 0.0001)
      ),
    [leiturasValidasUnicas]
  );

  const bounds = useMemo(() => {
    if (comGps.length === 0) {
      return { minLat: -14.8, maxLat: -14.7, minLng: -39.3, maxLng: -39.2 };
    }
    let minLat = Infinity;
    let maxLat = -Infinity;
    let minLng = Infinity;
    let maxLng = -Infinity;
    comGps.forEach((l) => {
      if (l.latitude < minLat) minLat = l.latitude;
      if (l.latitude > maxLat) maxLat = l.latitude;
      if (l.longitude < minLng) minLng = l.longitude;
      if (l.longitude > maxLng) maxLng = l.longitude;
    });
    const padLat = Math.max(0.0002, (maxLat - minLat) * 0.12);
    const padLng = Math.max(0.0002, (maxLng - minLng) * 0.12);
    return {
      minLat: minLat - padLat,
      maxLat: maxLat + padLat,
      minLng: minLng - padLng,
      maxLng: maxLng + padLng,
    };
  }, [comGps]);

  const W = 720;
  const H = 460;

  const project = (lat: number, lng: number) => {
    const x =
      ((lng - bounds.minLng) / Math.max(1e-7, bounds.maxLng - bounds.minLng)) * (W - 60) + 30;
    const y =
      H -
      30 -
      ((lat - bounds.minLat) / Math.max(1e-7, bounds.maxLat - bounds.minLat)) * (H - 60);
    return { x, y };
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
      <div className="lg:col-span-8 bg-white border border-emerald-100 rounded-xl p-4 shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
          <div>
            <h3 className="font-extrabold text-emerald-950 text-base">
              Mapa Espacial de Focos Fitossanitários (Offline GPS)
            </h3>
            <p className="text-xs text-gray-500">
              {comGps.length} planta(s) única(s) georreferenciada(s). Clique em um ponto para abrir a foto na Conferência.
            </p>
          </div>
          <div className="flex flex-wrap gap-3 text-xs font-semibold">
            <span className="flex items-center gap-1">
              <span className="w-3 h-3 rounded-full bg-[#2E7D32]" /> Sadio
            </span>
            <span className="flex items-center gap-1">
              <span className="w-3 h-3 rounded-full bg-[#8E24AA]" /> Vassoura
            </span>
            <span className="flex items-center gap-1">
              <span className="w-3 h-3 rounded-full bg-[#D32F2F]" /> Podridão
            </span>
            <span className="flex items-center gap-1">
              <span className="w-3 h-3 rounded-full bg-[#64748B]" /> Não Coletável
            </span>
          </div>
        </div>

        <div className="relative bg-emerald-950/5 rounded-xl border border-emerald-100 overflow-hidden">
          {comGps.length === 0 ? (
            <div className="py-24 text-center text-sm text-gray-500">
              Nenhuma coordenada GPS válida registrada nas leituras ativas deste ensaio.
            </div>
          ) : (
            <svg viewBox={`0 0 ${W} ${H}`} className="w-full h-auto block">
              {[0.25, 0.5, 0.75].map((p) => (
                <g key={p}>
                  <line
                    x1={0}
                    y1={H * p}
                    x2={W}
                    y2={H * p}
                    stroke="#D1FAE5"
                    strokeDasharray="4 4"
                  />
                  <line
                    x1={W * p}
                    y1={0}
                    x2={W * p}
                    y2={H}
                    stroke="#D1FAE5"
                    strokeDasharray="4 4"
                  />
                </g>
              ))}
              {comGps.map((l) => {
                const { x, y } = project(l.latitude, l.longitude);
                const cat = categoriaDominante(l);
                const cor = COR_PONTO[cat];
                const isHover = hovered?.id === l.id;
                return (
                  <circle
                    key={l.id}
                    cx={x}
                    cy={y}
                    r={isHover ? 9 : 6}
                    fill={cor}
                    stroke="#FFFFFF"
                    strokeWidth={2}
                    className="cursor-pointer transition-all"
                    onMouseEnter={() => setHovered(l)}
                    onClick={() => onAbrirNaConferencia(l.id)}
                  />
                );
              })}
            </svg>
          )}

          {hovered && (
            <div className="px-3 py-2 bg-white/95 border-t border-emerald-100 text-xs flex flex-wrap justify-between gap-2">
              <span className="font-bold text-emerald-950">
                {hovered.plantCode} • Clone {hovered.codigoClone}
              </span>
              <span>
                Lat {hovered.latitude.toFixed(5)}, Lng {hovered.longitude.toFixed(5)} •{' '}
                {hovered.qtdEfetiva ?? hovered.qtdFrutos} fruto(s)
              </span>
            </div>
          )}
        </div>
      </div>

      <div className="lg:col-span-4 bg-white border border-emerald-100 rounded-xl p-4 shadow-sm flex flex-col max-h-[540px]">
        <h4 className="font-bold text-sm text-amber-800 mb-1">
          Plantas Sem GPS / Coordenada 0,0 ({semGps.length})
        </h4>
        <p className="text-xs text-gray-500 mb-3">
          Coletas feitas sem fix de satélite aparecem aqui para auditoria.
        </p>
        <div className="overflow-y-auto space-y-1.5 flex-1 pr-1">
          {semGps.map((l) => (
            <button
              key={l.id}
              onClick={() => onAbrirNaConferencia(l.id)}
              className="w-full text-left p-2 rounded-lg border border-gray-200 hover:bg-amber-50 text-xs flex justify-between items-center"
            >
              <div>
                <div className="font-bold text-gray-900">{l.plantCode}</div>
                <div className="text-gray-500">
                  {l.qtdEfetiva ?? l.qtdFrutos} frutos • {l.statusValidacao}
                </div>
              </div>
              <span className="text-emerald-700 font-bold">Abrir →</span>
            </button>
          ))}
          {semGps.length === 0 && (
            <div className="text-center text-xs text-emerald-700 py-8 font-semibold">
              100% das coletas ativas possuem GPS válido!
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
