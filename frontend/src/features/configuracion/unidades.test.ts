import { describe, expect, it } from 'vitest';
import { editableAMinutos, formularioDesdeRespuesta, horizonteEnHoras, minutosAEditable, solicitudDesdeFormulario, type FormularioConsultorio } from './unidades';

// Tarea 2.1 de C-09 (design D4): horas y días ⇄ minutos, sin redondear en silencio. Datos ficticios.

describe('minutosAEditable', () => {
  it('muestra en horas los minutos que son horas enteras', () => {
    expect(minutosAEditable(1440)).toEqual({ valor: 24, unidad: 'horas' });
    expect(minutosAEditable(120)).toEqual({ valor: 2, unidad: 'horas' });
    expect(minutosAEditable(0)).toEqual({ valor: 0, unidad: 'horas' });
  });

  it('muestra en minutos lo que no es una hora entera, para no redondear (D4)', () => {
    expect(minutosAEditable(90)).toEqual({ valor: 90, unidad: 'minutos' });
  });
});

describe('editableAMinutos', () => {
  it('convierte horas y respeta los minutos', () => {
    expect(editableAMinutos({ valor: 48, unidad: 'horas' })).toBe(2880);
    expect(editableAMinutos({ valor: 90, unidad: 'minutos' })).toBe(90);
  });
});

describe('horizonteEnHoras', () => {
  it('días × 24', () => {
    expect(horizonteEnHoras(60)).toBe(1440);
  });
});

const respuesta = {
  nombre: 'Consultorio Demo Sur',
  slug: 'dra-ejemplo',
  direccion: 'Calle Falsa 123, CABA',
  telefonoContacto: '+5491100000001',
  zonaHoraria: 'America/Argentina/Buenos_Aires',
  anticipacionCancelacionMin: 1440,
  anticipacionReservaMin: 120,
  horizonteReservaDias: 60,
  pasoHuecosMin: 15,
  reservaPublicaActiva: true,
  plan: 'PRUEBA',
  pruebaHasta: '2026-12-21',
};

describe('formulario ⇄ API', () => {
  it('arma el formulario con unidades legibles desde la respuesta', () => {
    const formulario = formularioDesdeRespuesta(respuesta);

    expect(formulario.anticipacionCancelacion).toEqual({ valor: 24, unidad: 'horas' });
    expect(formulario.anticipacionReserva).toEqual({ valor: 2, unidad: 'horas' });
    expect(formulario.horizonteReservaDias).toBe(60);
    expect(formulario.direccion).toBe('Calle Falsa 123, CABA');
  });

  it('una respuesta sin dirección deja el campo vacío', () => {
    const { direccion: _sinDireccion, ...sinDireccion } = respuesta;
    expect(formularioDesdeRespuesta(sinDireccion).direccion).toBe('');
  });

  it('envía 48 horas como 2880 minutos y solo los campos del contrato', () => {
    const formulario: FormularioConsultorio = { ...formularioDesdeRespuesta(respuesta), anticipacionCancelacion: { valor: 48, unidad: 'horas' } };

    expect(solicitudDesdeFormulario(formulario)).toEqual({
      nombre: 'Consultorio Demo Sur',
      slug: 'dra-ejemplo',
      direccion: 'Calle Falsa 123, CABA',
      telefonoContacto: '+5491100000001',
      zonaHoraria: 'America/Argentina/Buenos_Aires',
      anticipacionCancelacionMin: 2880,
      anticipacionReservaMin: 120,
      horizonteReservaDias: 60,
      pasoHuecosMin: 15,
      reservaPublicaActiva: true,
    });
  });

  it('recorta espacios y no manda una dirección vacía', () => {
    const formulario: FormularioConsultorio = { ...formularioDesdeRespuesta(respuesta), nombre: '  Consultorio Demo Sur  ', direccion: '   ' };

    const solicitud = solicitudDesdeFormulario(formulario);

    expect(solicitud.nombre).toBe('Consultorio Demo Sur');
    expect('direccion' in solicitud).toBe(false);
  });
});
