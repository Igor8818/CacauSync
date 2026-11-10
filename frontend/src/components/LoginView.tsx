import React, { useState } from 'react';
import {
  ShieldCheck,
  Smartphone,
  Cpu,
  CheckCircle2,
  Lock,
  User,
  Eye,
  EyeOff,
  ArrowRight,
  Sparkles,
} from 'lucide-react';
import { loginApi } from '../api';
import { UsuarioToken } from '../types';

interface LoginViewProps {
  onLoginSuccess: (user: UsuarioToken) => void;
}

export const LoginView: React.FC<LoginViewProps> = ({ onLoginSuccess }) => {
  const [email, setEmail] = useState('uilson lopes');
  const [senha, setSenha] = useState('ceplac123');
  const [mostrarSenha, setMostrarSenha] = useState(false);
  const [carregando, setCarregando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErro(null);
    setCarregando(true);
    try {
      const data = await loginApi(email, senha);
      onLoginSuccess(data);
    } catch (err: any) {
      setErro(err.message || 'Erro ao autenticar.');
    } finally {
      setCarregando(false);
    }
  };

  const preencherPerfil = (u: string, s: string) => {
    setEmail(u);
    setSenha(s);
    setErro(null);
  };

  return (
    <div className="min-h-screen flex flex-col lg:flex-row bg-stone-950">
      {/* LADO ESQUERDO: Hero Agronômico e Identidade CEPEC/CEPLAC */}
      <div className="relative lg:w-7/12 bg-gradient-to-br from-emerald-950 via-emerald-900 to-stone-950 text-white p-8 lg:p-14 flex flex-col justify-between overflow-hidden">
        {/* Padrão decorativo sutil de fundo */}
        <div
          className="absolute inset-0 opacity-10 pointer-events-none"
          style={{
            backgroundImage:
              'radial-gradient(circle at 20% 20%, #34d399 0%, transparent 45%), radial-gradient(circle at 80% 75%, #f59e0b 0%, transparent 45%)',
          }}
        />

        {/* Topo Institucional */}
        <div className="relative z-10 flex items-center justify-between border-b border-emerald-800/60 pb-5">
          <div className="flex items-center gap-3.5">
            <div className="w-12 h-12 rounded-2xl bg-emerald-800/70 border border-emerald-600/50 flex items-center justify-center shadow-lg">
              <svg width="30" height="30" viewBox="0 0 48 48" fill="none">
                <path
                  d="M24 6C20 10 18 16 22 20C26 24 32 22 36 18C36 12 30 6 24 6Z"
                  fill="#4ADE80"
                />
                <path
                  d="M24 16C15 16 10 23 11 32C12 39 19 44 24 44C29 44 36 39 37 32C38 23 33 16 24 16Z"
                  fill="#F59E0B"
                />
                <path d="M24 16C21 24 21 36 24 44" stroke="#78350F" strokeWidth="2.2" />
              </svg>
            </div>
            <div>
              <div className="text-xs font-bold uppercase tracking-widest text-emerald-300">
                Ministério da Agricultura e Pecuária
              </div>
              <div className="text-lg font-extrabold tracking-tight text-white">
                CEPLAC • CEPEC — Centro de Pesquisas do Cacau
              </div>
            </div>
          </div>
          <span className="hidden sm:inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-emerald-800/60 border border-emerald-600/40 text-emerald-200">
            <Sparkles className="w-3.5 h-3.5 text-amber-400" />
            Ecossistema v2.0 React
          </span>
        </div>

        {/* Centro: Proposta de Valor e Fluxo Operacional */}
        <div className="relative z-10 my-10 lg:my-auto max-w-2xl">
          <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-amber-500/15 border border-amber-400/30 text-amber-300 text-xs font-semibold mb-5">
            <span>🌱 Melhoramento Genético do Cacaueiro • Ilhéus / Itabuna - BA</span>
          </div>

          <h1 className="text-3xl lg:text-4xl font-extrabold tracking-tight leading-tight text-white mb-4">
            Logística de Rota em Campo &amp; Auditoria Fitossanitária com IA
          </h1>
          <p className="text-emerald-100/85 text-base leading-relaxed mb-8">
            Plataforma integrada ao aplicativo Android <strong>CacauApp (YOLOv8 Offline)</strong> para
            planejamento de quadras experimentais, sincronização em 1 clique e conferência científica
            de frutos na sequência exata da planilha.
          </p>

          {/* 3 Cards do Fluxo Operacional */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="p-4 rounded-2xl bg-white/5 border border-white/10 backdrop-blur-sm">
              <div className="w-9 h-9 rounded-xl bg-emerald-500/20 text-emerald-300 flex items-center justify-center mb-3 font-bold">
                <Smartphone className="w-5 h-5" />
              </div>
              <div className="text-xs font-bold uppercase tracking-wider text-emerald-300 mb-1">
                Passo 1 • Pré-Campo
              </div>
              <div className="text-sm font-bold text-white mb-1">Rota 1-Clique (USB/Wi-Fi)</div>
              <p className="text-xs text-emerald-100/70 leading-relaxed">
                Importe a planilha Excel/CSV do ensaio e envie direto para o celular do trabalhador.
              </p>
            </div>

            <div className="p-4 rounded-2xl bg-white/5 border border-white/10 backdrop-blur-sm">
              <div className="w-9 h-9 rounded-xl bg-amber-500/20 text-amber-300 flex items-center justify-center mb-3 font-bold">
                <Cpu className="w-5 h-5" />
              </div>
              <div className="text-xs font-bold uppercase tracking-wider text-amber-300 mb-1">
                Passo 2 • Em Campo
              </div>
              <div className="text-sm font-bold text-white mb-1">Detecção Edge AI YOLOv8</div>
              <p className="text-xs text-emerald-100/70 leading-relaxed">
                Contagem offline de frutos e retorno automático de fotos e caixas delimitadoras.
              </p>
            </div>

            <div className="p-4 rounded-2xl bg-white/5 border border-white/10 backdrop-blur-sm">
              <div className="w-9 h-9 rounded-xl bg-sky-500/20 text-sky-300 flex items-center justify-center mb-3 font-bold">
                <CheckCircle2 className="w-5 h-5" />
              </div>
              <div className="text-xs font-bold uppercase tracking-wider text-sky-300 mb-1">
                Passo 3 • Laboratório
              </div>
              <div className="text-sm font-bold text-white mb-1">Auditoria em Tela Dividida</div>
              <p className="text-xs text-emerald-100/70 leading-relaxed">
                Confira foto por foto na ordem #1 → #N e visualize o mapa fitossanitário da quadra.
              </p>
            </div>
          </div>
        </div>

        {/* Rodapé Esquerdo */}
        <div className="relative z-10 flex flex-wrap items-center justify-between gap-4 pt-5 border-t border-emerald-800/60 text-xs text-emerald-200/75">
          <div className="flex items-center gap-2">
            <ShieldCheck className="w-4 h-4 text-emerald-400" />
            <span>Autenticação Institucional JWT • Controle de Acesso RBAC</span>
          </div>
          <div>Ensaio Ativo: TC-1801 (940 Plantas • 104 Clones CEPEC)</div>
        </div>
      </div>

      {/* LADO DIREITO: Card de Autenticação */}
      <div className="lg:w-5/12 bg-stone-50 flex items-center justify-center p-6 sm:p-12">
        <div className="w-full max-w-md bg-white rounded-3xl shadow-xl border border-stone-200/80 p-8 sm:p-10">
          <div className="mb-7">
            <span className="inline-block px-3 py-1 rounded-full text-xs font-bold uppercase tracking-wider bg-emerald-100 text-emerald-800 mb-3">
              Acesso Restrito ao Laboratório
            </span>
            <h2 className="text-2xl font-extrabold text-stone-900 tracking-tight">
              Entrar no Painel CacauSync
            </h2>
            <p className="text-sm text-stone-500 mt-1">
              Informe seu usuário ou e-mail institucional da CEPLAC/CEPEC.
            </p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label
                htmlFor="loginInput"
                className="block text-xs font-bold uppercase tracking-wider text-stone-700 mb-2"
              >
                Usuário ou E-mail Institucional
              </label>
              <div className="relative">
                <User className="w-5 h-5 text-stone-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
                <input
                  id="loginInput"
                  type="text"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="Ex: uilson lopes ou pesquisador@cepec.gov.br"
                  className="w-full pl-11 pr-4 py-3 rounded-xl border border-stone-300 bg-stone-50/60 text-stone-900 font-medium text-sm focus:bg-white focus:outline-none focus:ring-2 focus:ring-emerald-600 focus:border-emerald-600 transition"
                />
              </div>
            </div>

            <div>
              <label
                htmlFor="senhaInput"
                className="block text-xs font-bold uppercase tracking-wider text-stone-700 mb-2"
              >
                Senha de Acesso
              </label>
              <div className="relative">
                <Lock className="w-5 h-5 text-stone-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
                <input
                  id="senhaInput"
                  type={mostrarSenha ? 'text' : 'password'}
                  required
                  value={senha}
                  onChange={(e) => setSenha(e.target.value)}
                  className="w-full pl-11 pr-11 py-3 rounded-xl border border-stone-300 bg-stone-50/60 text-stone-900 font-medium text-sm focus:bg-white focus:outline-none focus:ring-2 focus:ring-emerald-600 focus:border-emerald-600 transition"
                />
                <button
                  type="button"
                  onClick={() => setMostrarSenha(!mostrarSenha)}
                  className="absolute right-3.5 top-1/2 -translate-y-1/2 text-stone-400 hover:text-stone-600"
                  title={mostrarSenha ? 'Ocultar senha' : 'Mostrar senha'}
                >
                  {mostrarSenha ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            {erro && (
              <div className="p-3.5 rounded-xl bg-red-50 border border-red-200 text-red-700 text-xs font-semibold">
                ❌ {erro}
              </div>
            )}

            <button
              type="submit"
              disabled={carregando}
              className="w-full py-3.5 px-5 rounded-xl bg-emerald-700 hover:bg-emerald-800 active:bg-emerald-900 text-white font-bold text-sm shadow-md shadow-emerald-900/15 flex items-center justify-center gap-2 transition disabled:opacity-60 cursor-pointer"
            >
              <span>{carregando ? 'Autenticando no servidor...' : 'Acessar Painel Central'}</span>
              <ArrowRight className="w-4 h-4" />
            </button>
          </form>

          {/* Perfis rápidos para agilizar uso em bancada */}
          <div className="mt-7 pt-6 border-t border-stone-200/80">
            <div className="text-[11px] font-bold uppercase tracking-wider text-stone-400 mb-3">
              Preenchimento Rápido de Bancada (1 Clique):
            </div>
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                onClick={() => preencherPerfil('uilson lopes', 'ceplac123')}
                className={`px-3 py-1.5 rounded-lg text-xs font-semibold border transition cursor-pointer ${
                  email.toLowerCase().includes('uilson')
                    ? 'bg-emerald-50 border-emerald-600 text-emerald-800'
                    : 'bg-stone-100 border-stone-200 text-stone-700 hover:bg-stone-200/70'
                }`}
              >
                👨‍🔬 Uilson Lopes
              </button>
              <button
                type="button"
                onClick={() => preencherPerfil('pesquisador@cepec.gov.br', 'cepec123')}
                className={`px-3 py-1.5 rounded-lg text-xs font-semibold border transition cursor-pointer ${
                  email === 'pesquisador@cepec.gov.br'
                    ? 'bg-emerald-50 border-emerald-600 text-emerald-800'
                    : 'bg-stone-100 border-stone-200 text-stone-700 hover:bg-stone-200/70'
                }`}
              >
                🌱 Pesquisador de Campo
              </button>
              <button
                type="button"
                onClick={() => preencherPerfil('coordenador@cepec.gov.br', 'cepec123')}
                className={`px-3 py-1.5 rounded-lg text-xs font-semibold border transition cursor-pointer ${
                  email === 'coordenador@cepec.gov.br'
                    ? 'bg-emerald-50 border-emerald-600 text-emerald-800'
                    : 'bg-stone-100 border-stone-200 text-stone-700 hover:bg-stone-200/70'
                }`}
              >
                🔬 Coordenador Genética
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
