export interface UsuarioToken {
  token: string;
  tipo: string;
  nomePesquisador: string;
  email: string;
  role: string;
  expiracaoSegundos: number;
}

export interface EnsaioResumo {
  id: number;
  codigo: string;
  nome: string;
  estacaoExperimental: string;
  status: string;
}

export interface ItemRota {
  ordem: number;
  plantId: string;
  clone: string;
  parcela: string;
  planta: number;
  bloco: number;
  situacao: string;
  coletada: boolean;
  temFoto: boolean;
}

export interface DeteccaoBox {
  id?: number;
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
}

export interface ResultadoRotaResponse {
  codigoEnsaio: string;
  totalPlantasRota: number;
  novasPlantasCriadas: number;
  plantasAtualizadas: number;
  enviadoParaUsb: boolean;
  mensagem: string;
  itens: ItemRota[];
}

export interface RelatorioProdutividade {
  totalPlantasEnsaio: number;
  totalPlantasVivas: number;
  totalPlantasNaoColetaveis: number;
  totalLeiturasRegistradas: number;
  leiturasPendentesValidacao: number;
  leiturasValidadas: number;
  leiturasRejeitadas?: number;
  totalFrutosAcumulados?: number;
  totalFrutosSadios?: number;
  totalFrutosVassoura?: number;
  totalFrutosPodridao?: number;
  totalFrutosNaoAvaliavel?: number;
  totalFrutosContabilizados?: number;
  totalSadios?: number;
  totalVassoura?: number;
  totalPodridao?: number;
  totalNaoAvaliavel?: number;
}
