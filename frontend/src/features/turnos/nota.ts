import { el } from '../../shared/dom';
import { aviso } from '../../shared/ui/aviso';
import type { DetalleTurno } from '../agenda/api';
import type { EntornoDeAccion } from '../agenda/puntos-de-entrada';
import { CLASE_CONTROL } from '../configuracion/ui';
import { editarNota } from './api';
import { traducirErrorDeTurno } from './errores';
import { abrirFormulario } from './formulario';
import { avisoDeError, diaDelTurno, referenciaDelTurno } from './ui';
import { LARGO_NOTA, estadoDelContador } from './validaciones';

/** Nota interna (C-14, US-025; design D9): contador sobre 500 que se anuncia cada 50 caracteres y al pasarse. */
export function abrirNota(turno: DetalleTurno, entorno: EntornoDeAccion): void {
  const original = turno.notaInterna ?? '';
  const area = el('textarea', {
    id: 'nota-interna',
    name: 'nota',
    rows: 6,
    'aria-describedby': 'nota-ayuda nota-contador',
    class: `${CLASE_CONTROL} min-h-[calc(var(--space-7)*3)] py-2 resize-y`,
  });
  area.value = original;
  const mensaje = el('span', {});
  const cuenta = el('span', { class: 'tabular' });
  const contador = el('p', { id: 'nota-contador', class: 'flex justify-between gap-2 m-0 mt-1 text-sm text-texto-tenue' }, mensaje, cuenta);
  const anuncio = el('p', { 'aria-live': 'polite', class: 'sr-only' });

  const formulario = abrirFormulario({
    titulo: 'Nota interna',
    contenido: el(
      'div',
      {},
      referenciaDelTurno(turno, entorno.zona),
      el('div', { class: 'grid gap-1' }, el('label', { for: 'nota-interna', class: 'font-medium text-sm' }, 'Nota interna'), area, contador),
      el('p', { id: 'nota-ayuda', class: 'm-0 mt-2 text-sm text-texto-tenue' }, 'El paciente no la ve. Solo la ves vos.'),
      anuncio,
    ),
    textoGuardar: 'Guardar nota',
    textoCargando: 'Guardando…',
    conCambios: () => area.value !== original,
    validar: () => !estadoDelContador(area.value.length, LARGO_NOTA).excedido,
    ejecutar: async () => {
      await editarNota(entorno.http, turno.id, { nota: area.value, version: turno.version ?? 0 });
      aviso(area.value.trim() === '' ? 'Borramos la nota' : 'Guardamos la nota', { tono: 'exito' });
      entorno.alTerminar();
    },
    alFallar: (error, { zonaDeAviso, ventana, reintentar }) => {
      const traducido = traducirErrorDeTurno(error, { operacion: 'nota', zona: entorno.zona, dia: diaDelTurno(turno, entorno.zona), turnos: [], bloqueos: [], franjas: null });
      zonaDeAviso.replaceChildren(
        avisoDeError(traducido.campos.length > 0 ? { ...traducido, titulo: 'La nota puede tener hasta 500 caracteres', texto: 'Acortala y volvé a guardar.' } : traducido, {
          alReintentar: reintentar,
          alVerActualizado: () => {
            ventana.cerrar();
            entorno.alTerminar();
          },
        }),
      );
    },
  });

  function actualizar(): void {
    const estado = estadoDelContador(area.value.length, LARGO_NOTA);
    cuenta.textContent = estado.texto;
    mensaje.textContent = estado.excedido ? (estado.anunciar ?? '') : '';
    contador.classList.toggle('text-peligro', estado.excedido);
    contador.classList.toggle('font-semibold', estado.excedido);
    area.setAttribute('aria-invalid', String(estado.excedido));
    formulario.guardar.disabled = estado.excedido;
    if (estado.anunciar !== null) anuncio.textContent = estado.anunciar;
  }
  area.addEventListener('input', actualizar);
  actualizar();
}
