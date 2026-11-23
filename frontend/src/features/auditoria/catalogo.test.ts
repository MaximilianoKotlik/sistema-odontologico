import { describe, expect, it } from 'vitest';
import type { EventoAuditoria } from './api';
import { actorDelEvento, cambiosDelEvento, detalleDeAcceso, GRUPOS_DE_FILTRO, muestraIp, textoDelEvento } from './catalogo';

// Tarea 5.1 de C-18 (spec pantalla-registro-cambios, design D6). Datos ficticios.
const BA = 'America/Argentina/Buenos_Aires';

/** Lista de la tarea 1.1: las acciones que publican los módulos del backend. */
const ACCIONES_PUBLICADAS = [
  'TURNO_CREADO', 'TURNO_REPROGRAMADO', 'TURNO_ESTADO_CAMBIADO', 'TURNO_DURACION_CAMBIADA', 'TURNO_NOTA_CAMBIADA',
  'BLOQUEO_CREADO', 'BLOQUEO_ELIMINADO',
  'CONSULTORIO_CONFIG_CAMBIADA', 'PRESTACION_CREADA', 'PRESTACION_CAMBIADA', 'PRESTACION_DESACTIVADA', 'PRESTACION_ACTIVADA', 'DISPONIBILIDAD_CAMBIADA',
  'PLAN_ACTIVADO', 'PLAN_VENCIDO',
  'PACIENTE_CREADO', 'PACIENTE_MARCADO_PARA_REVISION', 'PACIENTE_CONSENTIMIENTO_ACTUALIZADO', 'PACIENTE_REVISADO',
  'CUENTA_CREADA', 'LOGIN_EXITOSO', 'LOGIN_FALLIDO', 'PASSWORD_CAMBIADA', 'PASSWORD_RECUPERADA',
  'EXPORTACION_GENERADA',
];

const evento = (extra: Partial<EventoAuditoria> = {}): EventoAuditoria => ({
  id: 'e-1',
  ocurridoAt: '2026-11-20T14:42:00Z',
  actor: { tipo: 'ODONTOLOGO', id: 'u-1', nombre: 'Dra. Ejemplo', identidadEnRevision: false },
  accion: 'TURNO_REPROGRAMADO',
  entidad: 'turno',
  entidadId: 't-1',
  valorAnterior: null,
  valorNuevo: null,
  ip: null,
  contexto: null,
  ...extra,
});

const paciente = { tipo: 'PACIENTE', id: 'pa-1', nombre: 'Elena Demo', identidadEnRevision: false };

describe('textoDelEvento', () => {
  it('todas las acciones publicadas tienen texto y están en un filtro', () => {
    const enFiltros = GRUPOS_DE_FILTRO.flatMap((g) => g.opciones.flatMap((o) => o.acciones));
    for (const accion of ACCIONES_PUBLICADAS) {
      expect(textoDelEvento(evento({ accion }))).not.toBe(accion);
      expect(enFiltros).toContain(accion);
    }
  });

  it('el verbo depende del actor', () => {
    expect(textoDelEvento(evento())).toBe('Reprogramaste un turno');
    expect(textoDelEvento(evento({ actor: paciente }))).toBe('Reprogramó por el link');
    expect(textoDelEvento(evento({ accion: 'TURNO_CREADO', actor: paciente }))).toBe('Reservó por tu link');
  });

  it('un cambio de estado del paciente dice qué hizo', () => {
    const cancelado = evento({ accion: 'TURNO_ESTADO_CAMBIADO', actor: paciente, valorAnterior: { estado: 'RESERVADO' }, valorNuevo: { estado: 'CANCELADO', cancelado_por: 'PACIENTE' } });
    expect(textoDelEvento(cancelado)).toBe('Canceló por el link');
    expect(cambiosDelEvento(cancelado, BA)).toEqual([{ etiqueta: 'Estado', antes: 'Reservado', despues: 'Cancelado', codigo: false }]);
  });

  it('una acción desconocida se muestra con su código', () => {
    expect(textoDelEvento(evento({ accion: 'ACCION_NUEVA' }))).toBe('ACCION_NUEVA');
  });

  it('el login fallido no se atribuye a nadie', () => {
    expect(textoDelEvento(evento({ accion: 'LOGIN_FALLIDO', actor: { tipo: 'ODONTOLOGO', id: null, nombre: null, identidadEnRevision: false } }))).toBe('Intento fallido de iniciar sesión con tu email');
  });
});

describe('actorDelEvento', () => {
  it('sin id es "Sin sesión", nunca "Sistema"', () => {
    expect(actorDelEvento({ tipo: 'ODONTOLOGO', id: null, nombre: null, identidadEnRevision: false })).toEqual({ etiqueta: 'Sin sesión', nombre: null });
    expect(actorDelEvento({ tipo: 'PACIENTE', id: null, nombre: null, identidadEnRevision: false }).etiqueta).toBe('Sin sesión');
    expect(actorDelEvento({ tipo: 'SISTEMA', id: null, nombre: null, identidadEnRevision: false }).etiqueta).toBe('Sistema');
  });

  it('con la revisión pendiente no muestra el nombre', () => {
    expect(actorDelEvento({ tipo: 'PACIENTE', id: 'pa-1', nombre: 'Hernán Muestra', identidadEnRevision: true })).toEqual({ etiqueta: 'Reserva con este DNI', nombre: null });
  });

  it('muestra quién fue', () => {
    expect(actorDelEvento(evento().actor)).toEqual({ etiqueta: 'Vos', nombre: 'Dra. Ejemplo' });
    expect(actorDelEvento(paciente)).toEqual({ etiqueta: 'Paciente', nombre: 'Elena Demo' });
  });
});

describe('cambiosDelEvento', () => {
  it('muestra instantes en la zona del consultorio y omite el fin derivado', () => {
    const cambios = cambiosDelEvento(
      evento({ valorAnterior: { inicio: '2026-11-23T13:00:00Z', fin: '2026-11-23T13:30:00Z' }, valorNuevo: { inicio: '2026-11-23T14:00:00Z', fin: '2026-11-23T14:30:00Z' } }),
      BA,
    );
    expect(cambios).toEqual([{ etiqueta: 'Inicio', antes: 'lun 23/11 10:00', despues: 'lun 23/11 11:00', codigo: false }]);
  });

  it('un turno creado no muestra ids internos', () => {
    const cambios = cambiosDelEvento(
      evento({
        accion: 'TURNO_CREADO',
        valorNuevo: { inicio: '2026-11-23T13:00:00Z', fin: '2026-11-23T13:30:00Z', duracion_min: 30, tipo_prestacion_id: '018f0000-0000-7000-8000-000000000001', paciente_id: '018f0000-0000-7000-8000-000000000002', estado: 'RESERVADO', origen: 'LINK', es_sobreturno: false },
      }),
      BA,
    );
    expect(cambios.map((c) => c.etiqueta)).toEqual(['Inicio', 'Duración', 'Estado', 'Origen', 'Sobreturno']);
    expect(cambios.find((c) => c.etiqueta === 'Origen')?.despues).toBe('Por tu link');
    expect(cambios.find((c) => c.etiqueta === 'Sobreturno')?.despues).toBe('No');
    expect(JSON.stringify(cambios)).not.toContain('018f');
  });

  it('ordena los campos aunque el JSON llegue en otro orden, y los desconocidos van al final', () => {
    const cambios = cambiosDelEvento(evento({ accion: 'TURNO_CREADO', valorNuevo: { campo_nuevo: 1, estado: 'RESERVADO', origen: 'LINK', inicio: '2026-11-23T13:00:00Z', duracion_min: 30 } }), BA);
    expect(cambios.map((c) => c.etiqueta)).toEqual(['Inicio', 'Duración', 'Estado', 'Origen', 'campo_nuevo']);
  });

  it('solo incluye lo que cambió', () => {
    const cambios = cambiosDelEvento(evento({ accion: 'CONSULTORIO_CONFIG_CAMBIADA', entidad: 'consultorio', valorAnterior: { anticipacion_cancelacion_min: 1440, nombre: 'Demo' }, valorNuevo: { anticipacion_cancelacion_min: 2880, nombre: 'Demo' } }), BA);
    expect(cambios).toEqual([{ etiqueta: 'Anticipación para cancelar', antes: '24 h', despues: '48 h', codigo: false }]);
  });

  it('la revisión de un paciente nombra sus campos (C-17, PACIENTE_REVISADO)', () => {
    const cambios = cambiosDelEvento(
      evento({
        accion: 'PACIENTE_REVISADO',
        entidad: 'paciente',
        valorAnterior: { email: 'ana@example.com', nombre: 'Ana', apellido: 'Ejemplo', requiere_revision: true },
        valorNuevo: { email: null, nombre: 'Anna', apellido: 'Otro', requiere_revision: false },
      }),
      BA,
    );
    expect(cambios.map((c) => c.etiqueta)).toEqual(['Para revisar', 'Nombre', 'Apellido', 'Email']);
    expect(cambios.every((c) => !c.codigo)).toBe(true);
  });

  it('un campo desconocido se muestra con su nombre técnico', () => {
    const cambios = cambiosDelEvento(evento({ accion: 'TURNO_NOTA_CAMBIADA', valorAnterior: { campo_nuevo: 'a' }, valorNuevo: { campo_nuevo: { b: 1 } } }), BA);
    expect(cambios).toEqual([{ etiqueta: 'campo_nuevo', antes: 'a', despues: '{"b":1}', codigo: true }]);
  });

  it('los datos del intento de acceso no van como cambio', () => {
    const fallido = evento({ accion: 'LOGIN_FALLIDO', valorNuevo: { intentos_fallidos: 1, bloqueado_hasta: null }, ip: '203.0.113.24' });
    expect(cambiosDelEvento(fallido, BA)).toEqual([]);
    expect(detalleDeAcceso(fallido)).toBe('Contraseña incorrecta · 1 de 5 intentos');
  });

  it('la IP se muestra solo en los accesos', () => {
    expect(muestraIp('LOGIN_FALLIDO')).toBe(true);
    expect(muestraIp('PASSWORD_CAMBIADA')).toBe(true);
    expect(muestraIp('TURNO_CREADO')).toBe(false);
  });
});
