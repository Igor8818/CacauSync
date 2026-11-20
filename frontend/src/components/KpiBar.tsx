import React from 'react';
import { LeituraColeta, RelatorioProdutividade } from '../types';

interface KpiBarProps {
  relatorio: RelatorioProdutividade | null;
  totalPlantasRota: number;
  leituras?: LeituraColeta[];
}

export const KpiBar: React.FC<KpiBarProps> = ({
  relatorio,
  totalPlantasRota,
  leituras = [],
}) => {
  const totalPlantasVivas = relatorio?.totalPlantasVivas || totalPlantasRota || relatorio?.totalPlantasEnsaio || 0;
  const avaliadas = relatorio?.totalPlantasAvaliadas ?? 0;
  const pendentes = relatorio?.leiturasPendentesValidacao ?? leituras.filter((l) => l.statusValidacao === 'PENDENTE').length;
  const validadas = relatorio?.leiturasValidadas ?? 0;

  const pctCobertura =
    relatorio?.coberturaPercentual ??
    (totalPlantasVivas > 0 ? Math.min(100, (avaliadas / totalPlantasVivas) * 100) : 0);

  const arrobasHa = relatorio?.estimativa?.produtividadeRealArrobasHa ?? 0;
  const kgHa = relatorio?.estimativa?.produtividadeRealKgHa ?? 0;
  const perdaArbHa = relatorio?.estimativa?.perdaDoencasArrobasHa ?? 0;
  const indiceFruto = relatorio?.parametros?.indiceFruto ?? 22;
  const plantasHa = relatorio?.parametros?.plantasPorHa ?? 1111;

  const totalFrutos = relatorio?.totalFrutosAcumulados ?? 0;
  const sadios = relatorio?.totalFrutosSadios ?? 0;
  const doentes = (relatorio?.totalFrutosVassoura ?? 0) + (relatorio?.totalFrutosPodridao ?? 0);
  const taxaInfeccao = relatorio?.taxaInfeccaoEnsaioPercentual ?? 0;
  const alertasGps = relatorio?.leiturasComAlertaGps ?? 0;

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 mb-5">
      <div className="bg-white rounded-xl border border-emerald-100 p-4 shadow-sm">
        <div className="flex items-center justify-between text-xs font-semibold text-gray-500">
          <span>Cobertura do Ensaio</span>
          <span className="text-emerald-700 font-bold">{pctCobertura.toFixed(1)}%</span>
        </div>
        <div className="text-2xl font-extrabold text-emerald-950 mt-1">
          {avaliadas}{' '}
          <span className="text-sm font-semibold text-gray-500">/ {totalPlantasVivas || '—'} vivas</span>
        </div>
        <div className="w-full h-2 bg-emerald-100 rounded-full mt-2 overflow-hidden">
          <div
            className="h-full bg-emerald-700 rounded-full transition-all"
            style={{ width: `${Math.min(100, pctCobertura)}%` }}
          />
        </div>
        <div className="text-[11px] text-gray-600 mt-2 flex justify-between">
          <span>Auditadas: {validadas}</span>
          <span className={pendentes > 0 ? 'text-amber-700 font-bold' : ''}>
            Pendentes: {pendentes}
          </span>
        </div>
      </div>

      <div className="bg-white rounded-xl border border-emerald-100 p-4 shadow-sm">
        <div className="flex items-center justify-between text-xs font-semibold text-gray-500">
          <span>Produtividade Real</span>
          <span className="text-amber-800 bg-amber-100 px-2 py-0.5 rounded font-bold">
            IF {indiceFruto} • {plantasHa}/ha
          </span>
        </div>
        <div className="text-2xl font-extrabold text-emerald-900 mt-1">
          {arrobasHa.toFixed(1)} <span className="text-sm font-bold text-emerald-700">@/ha</span>
        </div>
        <div className="text-xs font-semibold text-gray-700 mt-1">
          {kgHa.toFixed(0)} kg/ha de amêndoas secas
        </div>
        <div className="text-[11px] text-gray-500 mt-1">
          Base: frutos sadios × sobrevivência do ensaio
        </div>
      </div>

      <div className="bg-white rounded-xl border border-emerald-100 p-4 shadow-sm">
        <div className="flex items-center justify-between text-xs font-semibold text-gray-500">
          <span>Fitossanidade de Frutos</span>
          <span
            className={`font-bold ${
              taxaInfeccao >= 20 ? 'text-red-700' : 'text-emerald-700'
            }`}
          >
            {taxaInfeccao.toFixed(1)}% infec.
          </span>
        </div>
        <div className="text-2xl font-extrabold text-gray-900 mt-1">
          {totalFrutos}{' '}
          <span className="text-sm font-semibold text-gray-500">frutos ({sadios} sadios)</span>
        </div>
        <div className="text-[11px] text-gray-600 mt-2 flex gap-3">
          <span className="text-purple-700 font-semibold">
            Vassoura: {relatorio?.totalFrutosVassoura ?? 0}
          </span>
          <span className="text-red-700 font-semibold">
            Podridão: {relatorio?.totalFrutosPodridao ?? 0}
          </span>
        </div>
      </div>

      <div className="bg-white rounded-xl border border-emerald-100 p-4 shadow-sm">
        <div className="flex items-center justify-between text-xs font-semibold text-gray-500">
          <span>Perdas &amp; Integridade GPS</span>
          {alertasGps > 0 && (
            <span className="text-amber-700 font-bold">{alertasGps} sem GPS</span>
          )}
        </div>
        <div className="text-2xl font-extrabold text-red-700 mt-1">
          -{perdaArbHa.toFixed(1)}{' '}
          <span className="text-sm font-semibold text-red-600">@/ha</span>
        </div>
        <div className="text-xs text-gray-600 mt-1">
          {doentes} fruto(s) descartado(s) por doença
        </div>
        <div className="text-[11px] text-gray-500 mt-1">
          Rejeitadas na revisão: {relatorio?.leiturasRejeitadas ?? 0}
        </div>
      </div>
    </div>
  );
};
