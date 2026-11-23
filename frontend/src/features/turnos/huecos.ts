import { franjasDelDia, type BloqueoAgenda, type FranjaDisponible, type TurnoAgenda } from '../agenda/modelo';
import { horaDeMinutos, instanteLocal } from '../agenda/tiempo';

/**
 * Inicios libres de un día para una duración (C-14, design D6), con la misma regla que `CalculadoraDeHuecos` de C-12
 * para el odontólogo: avanza de a `paso` desde el inicio de cada franja mientras el turno entra completo, y descarta lo
 * que pisa un bloqueo o un turno activo que no es sobreturno (el odontólogo puede poner un turno encima de un
 * sobreturno). No ofrece el pasado. Es una sugerencia: el backend decide.
 */
export interface EntradaDeHuecos {
  readonly dia: string;
  readonly zona: string;
  readonly turnos: readonly TurnoAgenda[];
  readonly bloqueos: readonly BloqueoAgenda[];
  readonly franjas: readonly FranjaDisponible[] | null;
  readonly duracionMin: number;
  readonly paso: number;
  readonly ahora: Date;
  /** El turno que se reprograma: no se ocupa a sí mismo. */
  readonly excluir?: string;
}

const activo = (t: TurnoAgenda) => t.estado === 'RESERVADO' || t.estado === 'CONFIRMADO';

export function iniciosLibres(entrada: EntradaDeHuecos): string[] {
  if (entrada.franjas === null || entrada.paso <= 0) return [];
  const ocupados = [
    ...entrada.turnos.filter((t) => activo(t) && !t.esSobreturno && t.id !== entrada.excluir),
    ...entrada.bloqueos,
  ].map((o) => ({ desde: Date.parse(o.inicio), hasta: Date.parse(o.fin) }));
  const duracionMs = entrada.duracionMin * 60000;

  return franjasDelDia(entrada.dia, entrada.franjas).flatMap((franja) => {
    const inicios: string[] = [];
    for (let minuto = franja.desde; minuto + entrada.duracionMin <= franja.hasta; minuto += entrada.paso) {
      const hora = horaDeMinutos(minuto);
      const desde = instanteLocal(entrada.dia, hora, entrada.zona).getTime();
      const hasta = desde + duracionMs;
      if (desde <= entrada.ahora.getTime()) continue;
      if (ocupados.some((o) => o.desde < hasta && desde < o.hasta)) continue;
      inicios.push(hora);
    }
    return inicios;
  });
}
