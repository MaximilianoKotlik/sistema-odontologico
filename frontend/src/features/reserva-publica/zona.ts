/**
 * Nombre legible de la zona del consultorio para "Horarios en hora de …, la del consultorio" (C-16, design D4).
 * Cubre las zonas argentinas de IANA; una zona que no está en la tabla se muestra con su identificador.
 */
const NOMBRES: Readonly<Record<string, string>> = {
  'America/Argentina/Buenos_Aires': 'Buenos Aires',
  'America/Argentina/Catamarca': 'Catamarca',
  'America/Argentina/Cordoba': 'Córdoba',
  'America/Argentina/Jujuy': 'Jujuy',
  'America/Argentina/La_Rioja': 'La Rioja',
  'America/Argentina/Mendoza': 'Mendoza',
  'America/Argentina/Rio_Gallegos': 'Río Gallegos',
  'America/Argentina/Salta': 'Salta',
  'America/Argentina/San_Juan': 'San Juan',
  'America/Argentina/San_Luis': 'San Luis',
  'America/Argentina/Tucuman': 'Tucumán',
  'America/Argentina/Ushuaia': 'Ushuaia',
};

export function nombreDeZona(zona: string): string {
  return NOMBRES[zona] ?? zona;
}
