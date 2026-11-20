import React, { useMemo, useState } from 'react';
import { baixarArquivoCsv } from '../api';
import { ProdutividadeClone, RelatorioProdutividade } from '../types';

interface RankingClonesViewProps {
  ensaio: string;
  relatorio: RelatorioProdutividade | null;
  indiceFruto: number;
  plantasPorHa: number;
  campanha: string;
  onAlterarParametros: (novoIndice: number, novasPlantasHa: number, novaCampanha: string) => void;
  onToast: (msg: string, tipo?: 'ok' | 'erro') => void;
}

export const RankingClonesView: React.FC<RankingClonesViewProps> = ({
  ensaio,
  relatorio,
  indiceFruto,
  plantasPorHa,
  campanha,
  onAlterarParametros,
  onToast,
}) => {
  const [busca, setBusca] = useState('');
  const [ordenacao, setOrdenacao] = useState<'MEDIA_SADIOS' | 'ARROBAS_HA' | 'MENOR_INFECCAO'>('MEDIA_SADIOS');

  const clonesFiltrados = useMemo(() => {
    const lista: ProdutividadeClone[] = relatorio?.rankingClones || [];
    const q = busca.trim().toLowerCase();
    const filt = q
      ? lista.filter(
          (c) =>
            c.codigoClone.toLowerCase().includes(q) ||
            (c.descricaoClone || '').toLowerCase().includes(q)
        )
      : lista;

    return [...filt].sort((a, b) => {
      if (ordenacao === 'ARROBAS_HA') {
        return (
          (b.estimativa?.produtividadeRealArrobasHa ?? 0) -
          (a.estimativa?.produtividadeRealArrobasHa ?? 0)
        );
      }
      if (ordenacao === 'MENOR_INFECCAO') {
        return a.taxaInfeccaoPercentual - b.taxaInfeccaoPercentual;
      }
      const diffMedia =
        (b.estimativa?.mediaSadiosPorPlantaAvaliada ?? 0) -
        (a.estimativa?.mediaSadiosPorPlantaAvaliada ?? 0);
      if (Math.abs(diffMedia) > 1e-6) return diffMedia;
      return b.totalFrutosSadios - a.totalFrutosSadios;
    });
  }, [relatorio, busca, ordenacao]);

  const copiarResumo = async () => {
    if (!relatorio) return;
    const linhas = [
      `CEPEC/CEPLAC • Ensaio ${relatorio.codigoEnsaio} (IF ${indiceFruto} • ${plantasPorHa} pl/ha)`,
      `Plantas avaliadas: ${relatorio.totalPlantasAvaliadas}/${relatorio.totalPlantasVivas} vivas (${relatorio.coberturaPercentual.toFixed(1)}%)`,
      `Produtividade real estimada: ${relatorio.estimativa.produtividadeRealArrobasHa.toFixed(1)} @/ha (${relatorio.estimativa.produtividadeRealKgHa.toFixed(0)} kg/ha)`,
      `Perda por doenças: -${relatorio.estimativa.perdaDoencasArrobasHa.toFixed(1)} @/ha (${relatorio.taxaInfeccaoEnsaioPercentual.toFixed(1)}% infecção)`,
      '',
      'Top Clones (média sadios/planta avaliada):',
      ...clonesFiltrados.slice(0, 10).map(
        (c, i) =>
          `${i + 1}. ${c.codigoClone}: ${(c.estimativa?.mediaSadiosPorPlantaAvaliada ?? 0).toFixed(1)} sad/pl | ${(c.estimativa?.produtividadeRealArrobasHa ?? 0).toFixed(1)} @/ha | Infec. ${c.taxaInfeccaoPercentual.toFixed(1)}%`
      ),
    ];
    try {
      await navigator.clipboard.writeText(linhas.join('\n'));
      onToast('Resumo agronômico copiado para a área de transferência!', 'ok');
    } catch {
      onToast('Não foi possível copiar automaticamente.', 'erro');
    }
  };

  return (
    <div className="space-y-4">
      <div className="bg-white border border-emerald-100 rounded-xl p-4 shadow-sm flex flex-wrap items-end justify-between gap-4">
        <div className="flex flex-wrap items-end gap-3">
          <div>
            <label className="block text-xs font-bold text-gray-600 mb-1">
              Índice de Fruto (frutos p/ 1 kg)
            </label>
            <div className="flex items-center gap-1.5">
              <input
                type="number"
                min={1}
                max={200}
                value={indiceFruto}
                onChange={(e) => {
                  const val = Math.max(1, Math.min(200, Number(e.target.value) || 22));
                  onAlterarParametros(val, plantasPorHa, campanha);
                }}
                className="w-20 px-2.5 py-1.5 text-sm font-bold border border-gray-300 rounded-lg"
              />
              {[20, 22, 25].map((preset) => (
                <button
                  key={preset}
                  onClick={() => onAlterarParametros(preset, plantasPorHa, campanha)}
                  className={`px-2 py-1 text-xs font-bold rounded ${
                    indiceFruto === preset
                      ? 'bg-emerald-800 text-white'
                      : 'bg-emerald-50 text-emerald-900 hover:bg-emerald-100'
                  }`}
                >
                  IF {preset}
                </button>
              ))}
            </div>
          </div>

          <div>
            <label className="block text-xs font-bold text-gray-600 mb-1">
              Densidade (plantas/ha)
            </label>
            <input
              type="number"
              min={1}
              max={10000}
              value={plantasPorHa}
              onChange={(e) => {
                const val = Math.max(1, Math.min(10000, Number(e.target.value) || 1111));
                onAlterarParametros(indiceFruto, val, campanha);
              }}
              className="w-28 px-2.5 py-1.5 text-sm font-bold border border-gray-300 rounded-lg"
            />
          </div>

          <div>
            <label className="block text-xs font-bold text-gray-600 mb-1">Campanha</label>
            <select
              value={campanha}
              onChange={(e) => onAlterarParametros(indiceFruto, plantasPorHa, e.target.value)}
              className="px-3 py-1.5 text-sm border border-gray-300 rounded-lg bg-white"
            >
              <option value="">Todas as campanhas</option>
              {(relatorio?.campanhasDisponiveis || []).map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="flex flex-wrap gap-2">
          <button
            onClick={async () => {
              try {
                await baixarArquivoCsv(
                  `/api/v1/ensaios/${encodeURIComponent(ensaio)}/analise-csv?indiceFruto=${indiceFruto}&plantasPorHa=${plantasPorHa}`,
                  `analise_${ensaio}.csv`
                );
                onToast('Análise CSV exportada!', 'ok');
              } catch (err: any) {
                onToast(err?.message || 'Erro ao exportar análise CSV.', 'erro');
              }
            }}
            className="px-3 py-1.5 text-xs font-bold bg-emerald-800 text-white rounded-lg hover:bg-emerald-700"
          >
            ⬇ Exportar Análise CSV
          </button>
          <button
            onClick={async () => {
              try {
                await baixarArquivoCsv(
                  `/api/v1/ensaios/${encodeURIComponent(ensaio)}/rota-csv`,
                  `rota_${ensaio}.csv`
                );
                onToast('Rota CSV exportada!', 'ok');
              } catch (err: any) {
                onToast(err?.message || 'Erro ao exportar rota CSV.', 'erro');
              }
            }}
            className="px-3 py-1.5 text-xs font-bold border border-emerald-700 text-emerald-800 rounded-lg hover:bg-emerald-50"
          >
            ⬇ Exportar Rota CSV
          </button>
          <button
            onClick={copiarResumo}
            className="px-3 py-1.5 text-xs font-bold border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50"
          >
            Copiar Resumo
          </button>
        </div>
      </div>

      <div className="bg-white border border-emerald-100 rounded-xl p-4 shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
          <input
            type="text"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
            placeholder="Filtrar código de clone..."
            className="w-64 px-3 py-1.5 text-sm border border-gray-300 rounded-lg"
          />
          <div className="flex gap-1.5">
            {(
              [
                ['MEDIA_SADIOS', 'Média Sadios/Pl (CEPEC)'],
                ['ARROBAS_HA', 'Maior @/ha'],
                ['MENOR_INFECCAO', 'Menor Infecção'],
              ] as const
            ).map(([k, lbl]) => (
              <button
                key={k}
                onClick={() => setOrdenacao(k)}
                className={`px-3 py-1 text-xs font-bold rounded-lg ${
                  ordenacao === k
                    ? 'bg-emerald-800 text-white'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
              >
                {lbl}
              </button>
            ))}
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left border-collapse">
            <thead>
              <tr className="border-b border-gray-200 text-xs text-gray-500 uppercase bg-emerald-50/50">
                <th className="py-2.5 px-3">#</th>
                <th className="py-2.5 px-3">Clone</th>
                <th className="py-2.5 px-3">Avaliadas (Vivas)</th>
                <th className="py-2.5 px-3">Sobrev.%</th>
                <th className="py-2.5 px-3">Sadios</th>
                <th className="py-2.5 px-3">Vassoura</th>
                <th className="py-2.5 px-3">Podridão</th>
                <th className="py-2.5 px-3">Infec.%</th>
                <th className="py-2.5 px-3">Média Sad/Pl</th>
                <th className="py-2.5 px-3">kg/ha</th>
                <th className="py-2.5 px-3">@/ha</th>
                <th className="py-2.5 px-3">Perda @/ha</th>
              </tr>
            </thead>
            <tbody>
              {clonesFiltrados.map((c, idx) => (
                <tr key={c.codigoClone} className="border-b border-gray-100 hover:bg-emerald-50/30">
                  <td className="py-2.5 px-3 font-extrabold text-amber-700">{idx + 1}</td>
                  <td className="py-2.5 px-3 font-bold text-gray-900">{c.codigoClone}</td>
                  <td
                    className="py-2.5 px-3"
                    title={`${c.totalPlantasCadastradas} plantas cadastradas no ensaio`}
                  >
                    {c.plantasAvaliadas}/{c.plantasVivasColetaveis}
                  </td>
                  <td className="py-2.5 px-3">{c.taxaSobrevivenciaPercentual.toFixed(1)}%</td>
                  <td className="py-2.5 px-3 font-semibold text-green-700">
                    {c.totalFrutosSadios}
                  </td>
                  <td className="py-2.5 px-3 text-purple-700">{c.totalFrutosVassoura}</td>
                  <td className="py-2.5 px-3 text-red-700">{c.totalFrutosPodridao}</td>
                  <td
                    className={`py-2.5 px-3 font-semibold ${
                      c.taxaInfeccaoPercentual >= 20 ? 'text-red-700' : 'text-gray-700'
                    }`}
                  >
                    {c.taxaInfeccaoPercentual.toFixed(1)}%
                  </td>
                  <td className="py-2.5 px-3 font-bold text-emerald-950">
                    {(c.estimativa?.mediaSadiosPorPlantaAvaliada ?? 0).toFixed(1)}
                  </td>
                  <td className="py-2.5 px-3 font-semibold">
                    {(c.estimativa?.produtividadeRealKgHa ?? 0).toFixed(0)}
                  </td>
                  <td className="py-2.5 px-3 font-extrabold text-emerald-900">
                    {(c.estimativa?.produtividadeRealArrobasHa ?? 0).toFixed(1)}
                  </td>
                  <td className="py-2.5 px-3 text-red-600 font-semibold">
                    -{(c.estimativa?.perdaDoencasArrobasHa ?? 0).toFixed(1)}
                  </td>
                </tr>
              ))}
              {clonesFiltrados.length === 0 && (
                <tr>
                  <td colSpan={12} className="py-10 text-center text-gray-500">
                    Nenhum dado de clone encontrado neste ensaio.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
