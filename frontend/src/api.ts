import {
  EnsaioResumo,
  ItemRota,
  LeituraColeta,
  PaginaLeituras,
  RelatorioProdutividade,
  ResultadoImportacaoCsvResponse,
  ResultadoRotaResponse,
  StatusValidacao,
  UsbStatusResponse,
  UsuarioToken,
} from './types';

let authToken = localStorage.getItem('cacausync_token') || '';
const fotoObjectUrlCache = new Map<number, string>();
let onUnauthorizedCallback: (() => void) | null = null;

export function setAuthToken(token: string) {
  authToken = token;
  if (token) {
    localStorage.setItem('cacausync_token', token);
  } else {
    localStorage.removeItem('cacausync_token');
  }
}

export function getAuthToken(): string {
  return authToken;
}

export function setOnUnauthorized(cb: () => void) {
  onUnauthorizedCallback = cb;
}

async function request(url: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers || {});
  if (authToken) {
    headers.set('Authorization', `Bearer ${authToken}`);
  }
  const res = await fetch(url, { ...init, headers });
  if (res.status === 401 && onUnauthorizedCallback) {
    setAuthToken('');
    onUnauthorizedCallback();
  }
  return res;
}

async function extrairErroHttp(res: Response, fallback: string): Promise<string> {
  let msg = fallback;
  try {
    const data = await res.json();
    msg = data.detail || data.message || data.title || data.error || msg;
  } catch {
    // ignora corpo não-JSON
  }
  return msg;
}

async function parseJsonOrThrow<T>(res: Response): Promise<T> {
  if (!res.ok) {
    throw new Error(await extrairErroHttp(res, `Erro HTTP ${res.status}`));
  }
  return res.json() as Promise<T>;
}

export async function loginApi(email: string, senha: string): Promise<UsuarioToken> {
  const res = await fetch('/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, senha }),
  });
  const data = await parseJsonOrThrow<UsuarioToken>(res);
  setAuthToken(data.token);
  try {
    localStorage.setItem('cacausync_user', JSON.stringify(data));
  } catch {
    // ignore storage quota errors
  }
  return data;
}

export async function listarEnsaiosApi(): Promise<EnsaioResumo[]> {
  const res = await request('/api/v1/ensaios');
  return parseJsonOrThrow<EnsaioResumo[]>(res);
}

export async function buscarRelatorioApi(
  ensaio: string,
  params?: { indiceFruto?: number; plantasPorHa?: number; campanha?: string }
): Promise<RelatorioProdutividade> {
  const qs = new URLSearchParams();
  if (params?.indiceFruto && params.indiceFruto > 0) {
    qs.set('indiceFruto', String(params.indiceFruto));
  }
  if (params?.plantasPorHa && params.plantasPorHa > 0) {
    qs.set('plantasPorHa', String(params.plantasPorHa));
  }
  if (params?.campanha && params.campanha.trim()) {
    qs.set('campanha', params.campanha.trim());
  }
  const suffix = qs.toString() ? `?${qs.toString()}` : '';
  const res = await request(`/api/v1/ensaios/${encodeURIComponent(ensaio)}/relatorio-clones${suffix}`);
  return parseJsonOrThrow<RelatorioProdutividade>(res);
}

export async function listarRotaJsonApi(ensaio: string): Promise<ItemRota[]> {
  const res = await request(`/api/v1/ensaios/${encodeURIComponent(ensaio)}/rota-json`);
  return parseJsonOrThrow<ItemRota[]>(res);
}

export async function listarLeiturasApi(
  ensaio: string,
  page = 0,
  size = 200,
  status?: StatusValidacao,
  campanha?: string
): Promise<PaginaLeituras> {
  const qs = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) qs.set('status', status);
  if (campanha && campanha.trim()) qs.set('campanha', campanha.trim());
  const res = await request(`/api/v1/ensaios/${encodeURIComponent(ensaio)}/leituras?${qs.toString()}`);
  return parseJsonOrThrow<PaginaLeituras>(res);
}

/**
 * Carrega TODAS as páginas do ensaio (evita corte em 500 registros para ensaios grandes como TC-1801 com 940 plantas).
 */
export async function listarTodasLeiturasApi(
  ensaio: string,
  status?: StatusValidacao,
  campanha?: string
): Promise<LeituraColeta[]> {
  const todas: LeituraColeta[] = [];
  let page = 0;
  const size = 200;
  while (true) {
    const pg = await listarLeiturasApi(ensaio, page, size, status, campanha);
    if (Array.isArray(pg.content) && pg.content.length > 0) {
      todas.push(...pg.content);
    }
    const chegouFim =
      pg.last === true ||
      page + 1 >= (pg.totalPages ?? 1) ||
      !pg.content ||
      pg.content.length === 0;
    if (chegouFim) break;
    page += 1;
    if (page > 100) break;
  }
  return todas;
}

export async function buscarLeituraDetalheApi(id: number): Promise<LeituraColeta> {
  const res = await request(`/api/v1/leituras/${id}`);
  return parseJsonOrThrow<LeituraColeta>(res);
}

export async function carregarFotoUrlApi(id: number, forceReload = false): Promise<string | null> {
  if (!forceReload && fotoObjectUrlCache.has(id)) {
    return fotoObjectUrlCache.get(id)!;
  }
  const res = await request(`/api/v1/leituras/${id}/foto?t=${Date.now()}`);
  if (!res.ok) return null;
  const blob = await res.blob();
  const objUrl = URL.createObjectURL(blob);
  const old = fotoObjectUrlCache.get(id);
  if (old) URL.revokeObjectURL(old);
  fotoObjectUrlCache.set(id, objUrl);
  return objUrl;
}

export function limparCacheFotos() {
  fotoObjectUrlCache.forEach((url) => URL.revokeObjectURL(url));
  fotoObjectUrlCache.clear();
}

export async function anexarFotoLeituraApi(id: number, file: File): Promise<LeituraColeta> {
  const fd = new FormData();
  fd.append('foto', file);
  const res = await request(`/api/v1/leituras/${id}/foto`, {
    method: 'POST',
    body: fd,
  });
  return parseJsonOrThrow<LeituraColeta>(res);
}

export async function validarLeituraApi(
  id: number,
  payload: {
    status: StatusValidacao;
    qtdFrutosValidada?: number | null;
    observacao?: string | null;
    qtdSadios?: number;
    qtdVassoura?: number;
    qtdPodridao?: number;
    qtdNaoAvaliavel?: number;
    deteccoes?: Array<{
      classe: string;
      confianca: number;
      x: number;
      y: number;
      largura: number;
      altura: number;
    }>;
  }
): Promise<LeituraColeta> {
  const res = await request(`/api/v1/leituras/${id}/validacao`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  return parseJsonOrThrow<LeituraColeta>(res);
}

export async function excluirLeituraApi(id: number): Promise<void> {
  const res = await request(`/api/v1/leituras/${id}`, { method: 'DELETE' });
  if (!res.ok) {
    throw new Error(await extrairErroHttp(res, `Erro ao excluir leitura (${res.status})`));
  }
}

export async function importarPlanilhaRotaApi(
  ensaio: string,
  file: File,
  enviarParaUsb = true
): Promise<ResultadoRotaResponse> {
  const fd = new FormData();
  fd.append('arquivo', file);
  const res = await request(
    `/api/v1/ensaios/${encodeURIComponent(ensaio)}/importar-rota?enviarParaUsb=${enviarParaUsb}`,
    {
      method: 'POST',
      body: fd,
    }
  );
  return parseJsonOrThrow<ResultadoRotaResponse>(res);
}

export async function enviarRotaUsbApi(ensaio: string): Promise<ResultadoRotaResponse> {
  const res = await request(`/api/v1/ensaios/${encodeURIComponent(ensaio)}/enviar-rota-usb`, {
    method: 'POST',
  });
  return parseJsonOrThrow<ResultadoRotaResponse>(res);
}

export async function carregarRotaTc1801Api(ensaio: string): Promise<ResultadoRotaResponse> {
  const res = await request(
    `/api/v1/ensaios/${encodeURIComponent(ensaio)}/carregar-rota-tc1801?enviarParaUsb=true`,
    { method: 'POST' }
  );
  return parseJsonOrThrow<ResultadoRotaResponse>(res);
}

export async function buscarStatusUsbApi(): Promise<UsbStatusResponse> {
  const res = await request('/api/v1/coletas/usb-status');
  return parseJsonOrThrow<UsbStatusResponse>(res);
}

export async function sincronizarViaUsbApi(ensaio: string): Promise<ResultadoImportacaoCsvResponse> {
  const res = await request(
    `/api/v1/coletas/sincronizar-usb?codigoEnsaio=${encodeURIComponent(ensaio)}`,
    { method: 'POST' }
  );
  return parseJsonOrThrow<ResultadoImportacaoCsvResponse>(res);
}

export async function sincronizarCsvManualApi(
  ensaio: string,
  csvFile: File | null,
  fotos: FileList | File[] | null
): Promise<ResultadoImportacaoCsvResponse> {
  if (!csvFile) {
    throw new Error('Selecione o arquivo CSV exportado pelo CacauApp para importar.');
  }
  const fd = new FormData();
  fd.append('arquivo', csvFile);
  if (fotos) {
    const arr = Array.from(fotos);
    arr.forEach((f) => fd.append('fotos', f));
  }
  const res = await request(
    `/api/v1/coletas/importar-csv?codigoEnsaio=${encodeURIComponent(ensaio)}`,
    {
      method: 'POST',
      body: fd,
    }
  );
  return parseJsonOrThrow<ResultadoImportacaoCsvResponse>(res);
}

export async function baixarArquivoCsv(url: string, filename: string) {
  const res = await request(url);
  if (!res.ok) {
    throw new Error(await extrairErroHttp(res, 'Falha ao gerar CSV.'));
  }
  let texto = await res.text();
  if (!texto.startsWith('\uFEFF')) {
    texto = '\uFEFF' + texto;
  }
  const blob = new Blob([texto], { type: 'text/csv;charset=utf-8' });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = filename;
  link.click();
  URL.revokeObjectURL(link.href);
}
