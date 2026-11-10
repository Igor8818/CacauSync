import React from 'react';
import { CheckCircle2, Clock, Image as ImageIcon, AlertTriangle, ShieldCheck } from 'lucide-react';
import { RelatorioProdutividade } from '../types';

interface KpiBarProps {
  relatorio: RelatorioProdutividade | null;
  totalPlantasRota: number;
}

export const KpiBar: React.FC<KpiBarProps> = ({ relatorio, totalPlantasRota }) => {
  const totalPlantas = totalPlantasRota || relatorio?.totalPlantasEnsaio || 0;
  const coletadas = relatorio?.totalLeiturasRegistradas || 0;
  const pendentes = relatorio?.leiturasPendentesValidacao || 0;
  const validadas = relatorio?.leiturasValidadas || 0;

  const percColetadas = totalPlantas > 0 ? Math.min(100, Math.round((coletadas / totalPlantas) * 100)) : 0;
  const percValidadas = coletadas > 0 ? Math.min(100, Math.round((validadas / coletadas) * 100)) : 0;

  const sadios = relatorio?.totalSadios || 0;
  const doentes = (relatorio?.totalVassoura || 0) + (relatorio?.totalPodridao || 0);
  const totalFrutos = relatorio?.totalFrutosContabilizados || (sadios + doentes);

  return (
    <div className="bg-white rounded-2xl border border-stone-200/90 p-5 shadow-sm space-y-4">
      {/* Barra de Progresso Principal da Rota de Campo */}
      <div>
        <div className="flex flex-wrap items-center justify-between gap-2 mb-2">
          <div className="flex items-center gap-2">
            <span className="text-xs font-bold uppercase tracking-wider text-stone-600">
              Progresso Geral da Rota de Campo
            </span>
            <span className="text-xs px-2 py-0.5 rounded-full font-bold bg-emerald-100 text-emerald-800">
              {percColetadas}% Coletado
            </span>
          </div>
          <div className="text-xs font-semibold text-stone-500">
            <strong className="text-stone-900 font-bold">{coletadas}</strong> de{' '}
            <strong className="text-stone-900 font-bold">{totalPlantas}</strong> plantas com foto ou registro
          </div>
        </div>

        {/* Barra Visual Colorida */}
        <div className="w-full h-3 bg-stone-100 rounded-full overflow-hidden flex border border-stone-200">
          <div
            className="h-full bg-emerald-600 transition-all duration-500"
            style={{ width: `${totalPlantas > 0 ? (validadas / totalPlantas) * 100 : 0}%` }}
            title={`Validadas pelo pesquisador: ${validadas}`}
          />
          <div
            className="h-full bg-amber-500 transition-all duration-500"
            style={{ width: `${totalPlantas > 0 ? (pendentes / totalPlantas) * 100 : 0}%` }}
            title={`Aguardando conferência: ${pendentes}`}
          />
        </div>

        <div className="flex items-center gap-4 mt-2 text-[11px] text-stone-500">
          <span className="flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 rounded-full bg-emerald-600 inline-block" />
            Conferidas pelo Pesquisador: <strong className="text-stone-800">{validadas}</strong>
          </span>
          <span className="flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 rounded-full bg-amber-500 inline-block" />
            Aguardando Conferência: <strong className="text-stone-800">{pendentes}</strong>
          </span>
          <span className="flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 rounded-full bg-stone-300 inline-block" />
            A Coletar em Campo: <strong className="text-stone-800">{Math.max(0, totalPlantas - coletadas)}</strong>
          </span>
        </div>
      </div>

      {/* Grid de Métricas Principais */}
      <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-5 gap-3 pt-3 border-t border-stone-100">
        <div className="p-3 rounded-xl bg-stone-50 border border-stone-200/60">
          <div className="flex items-center gap-1.5 text-xs text-stone-500 font-medium">
            <ImageIcon className="w-4 h-4 text-emerald-600" />
            <span>Fotos Recebidas</span>
          </div>
          <div className="text-2xl font-black text-stone-900 mt-1">{coletadas}</div>
        </div>

        <div className="p-3 rounded-xl bg-amber-50/50 border border-amber-200/70">
          <div className="flex items-center gap-1.5 text-xs text-amber-800 font-semibold">
            <Clock className="w-4 h-4 text-amber-600" />
            <span>Pendente Conferência</span>
          </div>
          <div className="text-2xl font-black text-amber-900 mt-1">{pendentes}</div>
        </div>

        <div className="p-3 rounded-xl bg-emerald-50/50 border border-emerald-200/70">
          <div className="flex items-center gap-1.5 text-xs text-emerald-800 font-semibold">
            <CheckCircle2 className="w-4 h-4 text-emerald-600" />
            <span>Conferidas / OK</span>
          </div>
          <div className="text-2xl font-black text-emerald-900 mt-1">{validadas}</div>
        </div>

        <div className="p-3 rounded-xl bg-stone-50 border border-stone-200/60">
          <div className="flex items-center gap-1.5 text-xs text-stone-600 font-medium">
            <span className="w-2 h-2 rounded-full bg-emerald-500" />
            <span>Frutos Sadios</span>
          </div>
          <div className="text-2xl font-black text-emerald-700 mt-1">{sadios}</div>
        </div>

        <div className="p-3 rounded-xl bg-rose-50/50 border border-rose-200/70 col-span-2 sm:col-span-1">
          <div className="flex items-center gap-1.5 text-xs text-rose-800 font-semibold">
            <AlertTriangle className="w-4 h-4 text-rose-600" />
            <span>Frutos com Doença</span>
          </div>
          <div className="text-2xl font-black text-rose-700 mt-1">{doentes}</div>
          <div className="text-[10px] text-rose-600/90 font-medium">Vassoura / Podridão</div>
        </div>
      </div>
    </div>
  );
};
