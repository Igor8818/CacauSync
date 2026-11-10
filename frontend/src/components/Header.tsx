import React from 'react';
import { Download, LogOut, Sprout, Shield, FileSpreadsheet } from 'lucide-react';
import { EnsaioResumo, UsuarioToken } from '../types';
import { baixarArquivoCsv } from '../api';

interface HeaderProps {
  usuario: UsuarioToken;
  ensaios: EnsaioResumo[];
  ensaioAtivo: string;
  onTrocarEnsaio: (codigo: string) => void;
  onLogout: () => void;
}

export const Header: React.FC<HeaderProps> = ({
  usuario,
  ensaios,
  ensaioAtivo,
  onTrocarEnsaio,
  onLogout,
}) => {
  const exportarAnalise = () => {
    baixarArquivoCsv(
      `/api/v1/ensaios/${encodeURIComponent(ensaioAtivo)}/exportar-csv`,
      `analise_auditada_${ensaioAtivo.toLowerCase()}.csv`
    );
  };

  const exportarRota = () => {
    baixarArquivoCsv(
      `/api/v1/ensaios/${encodeURIComponent(ensaioAtivo)}/rota-csv`,
      `dados_cacau_${ensaioAtivo.toLowerCase()}.csv`
    );
  };

  return (
    <header className="bg-emerald-900 border-b border-emerald-800 text-white shadow-md sticky top-0 z-40">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between gap-4">
        {/* Identidade */}
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-emerald-800/80 border border-emerald-700 flex items-center justify-center text-emerald-300 shadow-inner">
            <Sprout className="w-6 h-6" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="font-extrabold text-base tracking-tight">CacauSync</span>
              <span className="text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded-full bg-emerald-800 text-emerald-200 border border-emerald-700/60">
                CEPEC / CEPLAC
              </span>
            </div>
            <p className="text-xs text-emerald-200/80 hidden sm:block">
              Logística de Rota &amp; Conferência Fitossanitária
            </p>
          </div>
        </div>

        {/* Controles centrais e ações */}
        <div className="flex items-center gap-2 sm:gap-3">
          {/* Seletor de Ensaio */}
          <div className="flex items-center gap-1.5 bg-emerald-950/60 border border-emerald-700/60 rounded-xl px-2.5 py-1 text-xs">
            <span className="text-emerald-300 font-semibold hidden md:inline">Ensaio:</span>
            <select
              value={ensaioAtivo}
              onChange={(e) => onTrocarEnsaio(e.target.value)}
              className="bg-transparent text-white font-bold text-xs focus:outline-none cursor-pointer"
            >
              {ensaios.map((e) => (
                <option key={e.codigo} value={e.codigo} className="bg-stone-900 text-white">
                  {e.codigo}
                </option>
              ))}
            </select>
          </div>

          {/* Exportações */}
          <button
            onClick={exportarAnalise}
            title="Exportar dados consolidados e auditados com fotos e doenças"
            className="hidden sm:inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-emerald-800 hover:bg-emerald-700 text-emerald-100 text-xs font-semibold border border-emerald-700/60 transition shadow-sm"
          >
            <FileSpreadsheet className="w-4 h-4 text-emerald-300" />
            <span>Exportar CSV Auditado</span>
          </button>

          <button
            onClick={exportarRota}
            title="Baixar planilha de rota com coordenadas e ordem das plantas"
            className="hidden md:inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-emerald-800/80 hover:bg-emerald-700 text-emerald-100 text-xs font-semibold border border-emerald-700/60 transition shadow-sm"
          >
            <Download className="w-4 h-4 text-emerald-300" />
            <span>Baixar Rota</span>
          </button>

          {/* Usuário Logado */}
          <div className="flex items-center gap-2 pl-2 border-l border-emerald-800/80">
            <div className="text-right hidden sm:block">
              <div className="text-xs font-bold text-white leading-tight">
                {usuario.nomePesquisador}
              </div>
              <div className="text-[10px] text-emerald-300 font-medium">
                {usuario.role.replace('ROLE_', '')}
              </div>
            </div>
            <button
              onClick={onLogout}
              title="Sair do painel"
              className="p-2 rounded-xl text-emerald-200 hover:text-white hover:bg-emerald-800 transition"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>
    </header>
  );
};
