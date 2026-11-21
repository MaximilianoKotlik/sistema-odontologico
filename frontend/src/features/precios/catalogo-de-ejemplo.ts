import type { CatalogoPublico } from './catalogo';

/** Catálogo ficticio para los tests (mismo contenido que planes.yaml del backend). */
export const catalogoDeEjemplo: CatalogoPublico = {
  planes: [
    {
      id: 'consultorio',
      nombre: 'Plan consultorio',
      descripcion: 'Un odontólogo · un consultorio.',
      incluye: ['Agenda diaria y semanal', 'Bloqueos y sobreturnos marcados'],
      importeARS: '14900.00',
      moneda: 'ARS',
      impuestosIncluidos: true,
    },
  ],
  prueba: { dias: 30, requiereMedioDePago: false },
  actualizadoEn: '2026-11-20T03:00:00Z',
};
