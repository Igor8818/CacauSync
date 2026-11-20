export interface UsuarioToken {
  token: string;
  tipo: string;
  nomePesquisador: string;
  email: string;
  role: string;
  expiraEmSegundos: number;
}

export interface EnsaioResumo {
  id: number;
  codigo: string;
  nome: string;
  estacaoExperimental: string;
  status: string;
  totalPlantas: number;
  totalLeituras: number;
}

export interface ItemRota {
  ordem: number;
  plantId: string;
  clone: string;
  parcela: string;
  planta: number;
  bloco: number;
  situacao: string;
  coletado: boolean;
  qtdFrutos: string;
  leituraId: number | null;
  statusValidacao: string | null;
}

export interface DeteccaoBox {
  classe: string;
  confianca: number;
  x: number;
  y: number;
  largura: number;
  altura: number;
}

export type StatusValidacao = 'PENDENTE' | 'VALIDADA' | 'AJUSTADA' | 'REJEITADA';

export interface LeituraColeta {
  id: number;
  codigoEnsaio: string;
  campanha: string;
  plantCode: string;
  codigoClone: string;
  numeroParcela: string;
  numeroPlanta: number;
  qtdFrutos: number;
  qtdSadios: number;
  qtdVassoura: number;
  qtdPodridao: number;
  qtdNaoAvaliavel: number;
  coletavel: boolean;
  diagnosticoBruto: string;
  dataColeta: string;
  latitude: number;
  longitude: number;
  alertaGps: boolean;
  pesquisadorEmail: string;
  importadoEm: string;
  temFoto: boolean;
  statusValidacao: StatusValidacao;
  qtdFrutosValidada: number | null;
  qtdEfetiva: number;
  observacaoValidacao: string | null;
  validadoPor: string | null;
  validadoEm: string | null;
  deteccoes: DeteccaoBox[];
  ordemRota: number | null;
}

export interface PaginaLeituras {
  content: LeituraColeta[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  last?: boolean;
}

export interface ResultadoRotaResponse {
  codigoEnsaio: string;
  origem: string;
  totalPlantasRota: number;
  plantasColetadas: number;
  enviadoParaCelularUsb: boolean;
  dispositivoUsb: string | null;
  mensagem: string;
  processadoEm: string;
  itens: ItemRota[];
}

export interface ResultadoImportacaoCsvResponse {
  codigoEnsaio: string;
  nomeArquivo: string;
  totalLinhasProcessadas: number;
  leiturasNovasInseridas: number;
  leiturasDuplicadasIgnoradas: number;
  plantasMarcadasNaoColetaveis: number;
  alertasGpsZerado: number;
  totalFrutosImportadosLote: number;
  fotosAnexadas: number;
  fotosIgnoradas: number;
  processadoEm: string;
  avisos: string[];
}

export interface ParametrosAgronomicos {
  indiceFruto: number;
  plantasPorHa: number;
  campanha: string | null;
}

export interface EstimativaAgronomica {
  mediaSadiosPorPlantaAvaliada: number;
  potencialKgHa: number;
  produtividadeRealKgHa: number;
  produtividadeRealArrobasHa: number;
  perdaDoencasKgHa: number;
  perdaDoencasArrobasHa: number;
}

export interface ProdutividadeClone {
  codigoClone: string;
  descricaoClone: string;
  totalPlantasCadastradas: number;
  plantasVivasColetaveis: number;
  plantasNaoColetaveisOuMortas: number;
  taxaSobrevivenciaPercentual: number;
  totalLeiturasRealizadas: number;
  totalFrutosContados: number;
  totalFrutosSadios: number;
  totalFrutosVassoura: number;
  totalFrutosPodridao: number;
  totalFrutosNaoAvaliavel: number;
  taxaInfeccaoPercentual: number;
  mediaFrutosPorPlantaViva: number;
  plantasAvaliadas: number;
  coberturaPercentual: number;
  estimativa: EstimativaAgronomica;
}

export interface RelatorioProdutividade {
  codigoEnsaio: string;
  nomeEnsaio: string;
  estacaoExperimental: string;
  totalPlantasEnsaio: number;
  totalPlantasVivas: number;
  totalPlantasNaoColetaveis: number;
  totalLeiturasRegistradas: number;
  totalFrutosAcumulados: number;
  totalFrutosSadios: number;
  totalFrutosVassoura: number;
  totalFrutosPodridao: number;
  totalFrutosNaoAvaliavel: number;
  taxaInfeccaoEnsaioPercentual: number;
  leiturasComAlertaGps: number;
  leiturasPendentesValidacao: number;
  leiturasValidadas: number;
  leiturasRejeitadas: number;
  geradoEm: string;
  rankingClones: ProdutividadeClone[];
  totalPlantasAvaliadas: number;
  coberturaPercentual: number;
  estimativa: EstimativaAgronomica;
  parametros: ParametrosAgronomicos;
  campanhasDisponiveis: string[];
}

export interface UsbStatusResponse {
  adbDisponivel: boolean;
  dispositivoConectado: boolean;
  serialDispositivo: string | null;
  appInstalado: boolean;
  registrosNoCelular: number;
  rotaNoCelular: boolean;
  mensagem: string;
}
