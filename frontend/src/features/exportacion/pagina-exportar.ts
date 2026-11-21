import type { ContextoPagina } from '../../pages/pagina';
import { el, idUnico, type Hijo } from '../../shared/dom';
import { ApiError, type ClienteHttp } from '../../shared/http';
import { nombreDeMarca } from '../../shared/marca';
import { boton } from '../../shared/ui/boton';
import { icono, type NombreIcono } from '../../shared/ui/iconos';
import { avisoAuth, spinner, type OpcionesAviso } from '../auth/ui';
import { tarjeta } from '../configuracion/ui';

/** Sección "Exportar mis datos" (C-19, spec pantalla-exportacion; mockup `exportar.html`). */

const RUTA = '/api/v1/exportacion';
const TEXTO_PROGRESO = 'Preparando tu archivo…';
/** Tamaño del fin de un ZIP sin comentario, que es lo que genera el backend. */
const BYTES_FIN_DE_ZIP = 22;
const FIRMA_FIN_DE_ZIP = [0x50, 0x4b, 0x05, 0x06];

/** `detalle` es una función para crear nodos nuevos en cada montaje. */
const ARCHIVOS: readonly { readonly icono: NombreIcono; readonly titulo: string; readonly detalle: () => readonly Hijo[] }[] = [
  { icono: 'tabla', titulo: 'Planillas CSV y archivos JSON', detalle: () => ['Uno por cada tipo de dato. Los CSV se abren con Excel o Google Sheets.'] },
  { icono: 'documento', titulo: 'Un resumen en PDF', detalle: () => ['Para leer o imprimir: datos del consultorio, prestaciones, pacientes, turnos y un resumen de los cambios.'] },
  { icono: 'lista', titulo: 'Un índice del contenido', detalle: () => [codigo('manifest.json'), ', con cuántos registros hay de cada tipo, para comprobar que no falta nada.'] },
];

const DATOS: readonly { readonly icono: NombreIcono; readonly texto: string; readonly clase: string }[] = [
  { icono: 'candado', texto: 'No incluye tu contraseña ni los links privados de tus pacientes.', clase: 'text-texto-tenue' },
  { icono: 'check', texto: 'Podés exportar siempre, también si tu plan venció.', clase: 'text-exito' },
  { icono: 'reloj', texto: 'Podés pedir una exportación cada 5 minutos.', clase: 'text-exito' },
];

function codigo(texto: string): HTMLElement {
  return el('code', { class: 'font-mono text-sm break-all' }, texto);
}

function aviso(opciones: Omit<OpcionesAviso, 'rol'>): HTMLDivElement {
  const elemento = avisoAuth({ ...opciones, rol: null });
  elemento.classList.replace('mb-5', 'mt-4');
  return elemento;
}

function minutosParaReintentar(retryAfter: string | null): number {
  const segundos = Number(retryAfter);
  return Number.isFinite(segundos) && segundos > 0 ? Math.ceil(segundos / 60) : 5;
}

function avisoDeError(error: unknown): HTMLDivElement {
  if (error instanceof ApiError && error.codigo === 'EXPORTACION_RECIENTE') {
    const minutos = minutosParaReintentar(error.retryAfter);
    return aviso({
      tono: 'advertencia',
      icono: 'reloj',
      titulo: 'Ya generaste una exportación hace poco.',
      cuerpo: [`Vas a poder pedir otra en ${minutos} ${minutos === 1 ? 'minuto' : 'minutos'}.`],
    });
  }
  if (error instanceof ApiError && error.codigo === 'SIN_CONEXION') {
    return aviso({
      tono: 'peligro',
      icono: 'sin-conexion',
      titulo: 'No tenés conexión',
      cuerpo: ['Revisá tu internet y volvé a tocar "Exportar mis datos".'],
    });
  }
  return aviso({
    tono: 'peligro',
    icono: 'alerta',
    titulo: 'No pudimos completar la exportación',
    cuerpo: ['No se guardó ningún archivo incompleto. Probá de nuevo.'],
  });
}

function leerBytes(contenido: Blob): Promise<Uint8Array> {
  return new Promise((resolver, rechazar) => {
    const lector = new FileReader();
    lector.onload = () => resolver(new Uint8Array(lector.result as ArrayBuffer));
    lector.onerror = () => rechazar(new Error('No se pudo leer el archivo'));
    lector.readAsArrayBuffer(contenido);
  });
}

/** Un streaming que falla no llama a `finish()`: sin el cierre del ZIP, el archivo está incompleto (design D8). */
async function zipCompleto(contenido: Blob): Promise<boolean> {
  if (contenido.size < BYTES_FIN_DE_ZIP) return false;
  const fin = await leerBytes(contenido.slice(contenido.size - BYTES_FIN_DE_ZIP));
  return FIRMA_FIN_DE_ZIP.every((byte, i) => fin[i] === byte);
}

function guardar(contenido: Blob, nombre: string): void {
  const url = URL.createObjectURL(contenido);
  const enlace = el('a', { href: url, download: nombre, hidden: true });
  document.body.append(enlace);
  enlace.click();
  enlace.remove();
  URL.revokeObjectURL(url);
}

function esPlanVencido(valor: unknown): boolean {
  return typeof valor === 'object' && valor !== null && Reflect.get(valor, 'plan') === 'VENCIDO';
}

/** El aviso es informativo: si no se puede leer el plan, la exportación sigue disponible (RN-EX-03). */
async function avisarSiVencio(http: ClienteHttp, columna: HTMLElement): Promise<void> {
  const consultorio = await http.get<unknown>('/api/v1/consultorio').catch(() => null);
  if (!esPlanVencido(consultorio)) return;
  const elemento = avisoAuth({
    tono: 'info',
    icono: 'info',
    titulo: 'Tu prueba terminó, pero tus datos siguen siendo tuyos',
    cuerpo: ['La agenda quedó en solo lectura. La exportación funciona igual que siempre.'],
    rol: 'status',
  });
  elemento.classList.remove('mb-5');
  columna.prepend(elemento);
}

function tarjetaDeAccion(http: ClienteHttp): HTMLElement {
  const idAyuda = idUnico('exportar-ayuda');
  const resultado = el('div', { role: 'status', 'aria-live': 'polite' });
  let enVuelo = false;

  const accion = boton({ texto: 'Exportar mis datos', icono: 'descargar', alHacerClic: () => void exportar() });
  const contenidoInicial = [...accion.childNodes];
  accion.setAttribute('data-exportar', '');
  accion.setAttribute('aria-describedby', idAyuda);
  accion.classList.add('w-full', 'min-[520px]:w-auto');

  function indicarProgreso(activo: boolean, teniaFoco: boolean): void {
    accion.disabled = activo;
    accion.replaceChildren(...(activo ? [spinner(), TEXTO_PROGRESO] : contenidoInicial));
    // Un botón deshabilitado pierde el foco: se devuelve para que el teclado siga donde estaba.
    if (!activo && teniaFoco) accion.focus();
  }

  async function exportar(): Promise<void> {
    if (enVuelo) return;
    enVuelo = true;
    const teniaFoco = document.activeElement === accion;
    indicarProgreso(true, teniaFoco);
    resultado.replaceChildren(el('p', { class: 'sr-only' }, TEXTO_PROGRESO));
    try {
      const { contenido, nombre } = await http.descargar(RUTA, { metodo: 'POST' });
      if (!(await zipCompleto(contenido))) throw new ApiError(200, 'DESCARGA_INTERRUMPIDA', 'El archivo llegó incompleto.');
      guardar(contenido, nombre);
      resultado.replaceChildren(
        aviso({
          tono: 'exito',
          icono: 'check',
          titulo: 'Listo, descargamos tu archivo',
          cuerpo: [codigo(nombre), ' está en tu carpeta de descargas.'],
        }),
      );
    } catch (error) {
      resultado.replaceChildren(avisoDeError(error));
    } finally {
      enVuelo = false;
      indicarProgreso(false, teniaFoco);
    }
  }

  return tarjeta(
    'Descargá todos tus datos',
    `Todo lo que cargaste en ${nombreDeMarca()}, en un archivo para guardarlo, revisarlo o llevarlo a otro sistema.`,
    el(
      'div',
      { class: 'grid gap-3 min-[520px]:grid-cols-[auto_1fr] min-[520px]:items-center' },
      accion,
      el('p', { id: idAyuda, class: 'm-0 text-sm text-texto-tenue' }, 'Puede tardar unos segundos. El archivo tiene datos de tus pacientes: guardalo en un lugar seguro.'),
    ),
    resultado,
  );
}

function tarjetaDeContenido(): HTMLElement {
  return tarjeta(
    'Qué incluye el archivo',
    'Un ZIP con tu consultorio, prestaciones, horarios, bloqueos, pacientes, turnos y registro de cambios.',
    el(
      'ul',
      { class: 'list-none m-0 p-0 grid gap-3' },
      ...ARCHIVOS.map((archivo) =>
        el(
          'li',
          { class: 'flex gap-3 items-start' },
          el('span', { 'aria-hidden': 'true', class: 'grid place-items-center flex-none min-w-touch min-h-touch rounded-md bg-primario-tinte text-primario' }, icono(archivo.icono)),
          el('div', { class: 'min-w-0' }, el('strong', { class: 'block font-semibold' }, archivo.titulo), el('span', { class: 'text-sm text-texto-tenue' }, ...archivo.detalle())),
        ),
      ),
    ),
    el(
      'ul',
      { class: 'list-none mt-4 mb-0 p-0 pt-4 border-t border-borde grid gap-2' },
      ...DATOS.map((dato) => el('li', { class: 'flex gap-2 items-start text-sm' }, icono(dato.icono, `w-icono-sm h-icono-sm flex-none ${dato.clase}`), el('span', {}, dato.texto))),
    ),
  );
}

export function montarExportar({ contenedor, http }: Pick<ContextoPagina, 'contenedor' | 'http'>): void {
  const columna = el('div', { class: 'max-w-3xl grid gap-5 pb-7' }, tarjetaDeAccion(http), tarjetaDeContenido());
  contenedor.replaceChildren(columna);
  void avisarSiVencio(http, columna);
}
