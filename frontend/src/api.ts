import {
  DeteccaoBox,
  EnsaioResumo,
  ItemRota,
  LeituraColeta,
  PaginaLeituras,
  RelatorioProdutividade,
  ResultadoRotaResponse,
  StatusValidacao,
  UsuarioToken,
} from './types';

let authToken = localStorage.getItem('cacausync_jwt') || '';
let onUnauthorizedCallback: (() => void) | null = null;

export function setAuthToken(token: string) {
  authToken = token;
  if (token) {
    localStorage.setItem('cacausync_jwt', token);
  } else {
    localStorage.removeItem('cacausync_jwt');
  }
}

export function getAuthToken(): string {
  return authToken;
}

export function setOnUnauthorized(cb: () => void) {
  onUnauthorizedCallback = cb;
}

async function apiFetch(path: string, options: RequestInit = {}): Promise<Response> {
  const headers: Record<string, string> = {
    ...(options.headers as Record<string, string> || {}),
  };
  if (authToken) {
    headers['Authorization'] = `Bearer ${authToken}`;
  }
  const resp = await fetch(path, { ...options, headers });
  if (resp.status === 401) {
    onUnauthorizedCallback?.();
    throw new Error('Sessão expirada. Faça login novamente.');
  }
  if (!resp.ok) {
    let msg = `Erro HTTP ${resp.status}`;
    try {
      const errJson = await resp.json();
      msg = errJson.detail || errJson.title || errJson.message || msg;
    } catch {
      // ignore
    }
    throw new Error(msg);
  }
  return resp;
}

export async function loginApi(email: string, senha: string): Promise<UsuarioToken> {
  const resp = await fetch('/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: email.trim(), senha }),
  });
  if (!resp.ok) {
    let msg = 'Credenciais inválidas. Verifique seu usuário/e-mail e senha.';
    try {
      const j = await resp.json();
      msg = j.detail || msg;
    } catch {
      // ignore
    }
    throw new Error(msg);
  }
  return resp.json();
}

export async function listarEnsaiosApi(): Promise<EnsaioResumo[]> {
  const r = await apiFetch('/api/v1/ensaios');
  return r.json();
}

export async function buscarRelatorioApi(ensaio: string): Promise<RelatorioProdutividade> {
  const r = await apiFetch(`/api/v1/ensaios/${encodeURIComponent(ensaio)}/relatorio-clones`);
  return r.json();
}

export async function listarRotaJsonApi(ensaio: string): Promise<ItemRota[]> {
  const r = await apiFetch(`/api/v1/ensaios/${encodeURIComponent(ensaio)}/rota-json`);
  return r.json();
}

export async function listarLeiturasApi(
  ensaio: string,
  page = 0,
  size = 200,
  status?: string
): Promise<PaginaLeituras> {
  const statusQuery = status ? `&status=${encodeURIComponent(status)}` : '';
  const r = await apiFetch(
    `/api/v1/ensaios/${encodeURIComponent(ensaio)}/leituras?page=${page}&size=${size}&sort=planta.ordemRota,asc&sort=id,asc${statusQuery}`
  );
  return r.json();
}

export async function buscarLeituraDetalheApi(id: number): Promise<LeituraColeta> {
  const r = await apiFetch(`/api/v1/leituras/${id}`);
  return r.json();
}

const fotoBlobCache = new Map<number, string>();

export async function carregarFotoUrlApi(id: number, forceReload = false): Promise<string | null> {
  if (!forceReload && fotoBlobCache.has(id)) {
    return fotoBlobCache.get(id)!;
  }
  try {
    const r = await apiFetch(`/api/v1/leituras/${id}/foto`);
    const blob = await r.blob();
    const prev = fotoBlobCache.get(id);
    if (prev) URL.revokeObjectURL(prev);
    const url = URL.createObjectURL(blob);
    fotoBlobCache.set(id, url);
    return url;
  } catch {
    return null;
  }
}

export function limparCacheFotos() {
  fotoBlobCache.forEach((u) => URL.revokeObjectURL(u));
  fotoBlobCache.clear();
}

export async function anexarFotoLeituraApi(id: number, file: File): Promise<LeituraColeta> {
  const fd = new FormData();
  fd.append('foto', file);
  const r = await apiFetch(`/api/v1/leituras/${id}/foto`, {
    method: 'POST',
    body: fd,
  });
  return r.json();
}

export async function validarLeituraApi(
  id: number,
  payload: {
    status: StatusValidacao;
    qtdFrutosValidada: number;
    observacao: string | null;
    qtdSadios: number;
    qtdVassoura: number;
    qtdPodridao: number;
    qtdNaoAvaliavel: number;
    deteccoes: DeteccaoBox[];
  }
): Promise<LeituraColeta> {
  const r = await apiFetch(`/api/v1/leituras/${id}/validacao`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  return r.json();
}

export async function importarPlanilhaRotaApi(
  ensaio: string,
  arquivo: File,
  enviarParaUsb = true
): Promise<ResultadoRotaResponse> {
  const fd = new FormData();
  fd.append('arquivo', arquivo, arquivo.name);
  fd.append('enviarParaUsb', String(enviarParaUsb));
  const r = await apiFetch(`/api/v1/ensaios/${encodeURIComponent(ensaio)}/importar-rota`, {
    method: 'POST',
    body: fd,
  });
  return r.json();
}

export async function enviarRotaUsbApi(ensaio: string): Promise<ResultadoRotaResponse> {
  const r = await apiFetch(`/api/v1/ensaios/${encodeURIComponent(ensaio)}/enviar-rota-usb`, {
    method: 'POST',
  });
  return r.json();
}

export async function carregarRotaTc1801Api(ensaio: string): Promise<ResultadoRotaResponse> {
  const r = await apiFetch(
    `/api/v1/ensaios/${encodeURIComponent(ensaio)}/carregar-rota-tc1801?enviarParaUsb=true`,
    { method: 'POST' }
  );
  return r.json();
}

export async function sincronizarViaUsbApi(ensaio: string): Promise<any> {
  const r = await apiFetch(
    `/api/v1/coletas/sincronizar-usb?codigoEnsaio=${encodeURIComponent(ensaio)}`,
    { method: 'POST' }
  );
  return r.json();
}

export async function sincronizarCsvManualApi(
  ensaio: string,
  csvFile: File | null,
  fotos: FileList | null
): Promise<{ msg: string }> {
  const partes: string[] = [];
  if (csvFile) {
    const fd = new FormData();
    fd.append('arquivo', csvFile, csvFile.name);
    const r = await apiFetch(
      `/api/v1/coletas/sincronizar-csv?codigoEnsaio=${encodeURIComponent(ensaio)}`,
      { method: 'POST', body: fd }
    );
    const s = await r.json();
    partes.push(
      `CSV: ${s.novasLeiturasInseridas} novas (${s.leiturasDuplicadasIgnoradas} duplicadas ignoradas)`
    );
  }
  if (fotos && fotos.length > 0) {
    const fd = new FormData();
    for (const f of Array.from(fotos)) {
      fd.append('fotos', f, f.name);
    }
    const r = await apiFetch(
      `/api/v1/coletas/upload-fotos?codigoEnsaio=${encodeURIComponent(ensaio)}`,
      { method: 'POST', body: fd }
    );
    const u = await r.json();
    partes.push(
      `Fotos: ${u.pareadasComPlanta + u.pareadasPorOrdem} associadas de ${u.totalRecebidas}`
    );
  }
  return { msg: partes.join(' · ') };
}

export async function baixarArquivoCsv(url: string, filename: string) {
  const r = await apiFetch(url);
  const blob = await r.blob();
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 2000);
}
