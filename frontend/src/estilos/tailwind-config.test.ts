import { describe, expect, it } from 'vitest';
import resolveConfig from 'tailwindcss/resolveConfig';
import config from '../../tailwind.config';

type Arbol = string | readonly unknown[] | { readonly [clave: string]: unknown };

function hojas(valor: unknown): string[] {
  if (typeof valor === 'string') return [valor];
  if (Array.isArray(valor)) return valor.flatMap(hojas);
  if (typeof valor === 'object' && valor !== null) return Object.values(valor as Arbol).flatMap(hojas);
  return [];
}

const tema = resolveConfig(config).theme;

describe('tailwind.config: tokens del sistema de diseño (D5)', () => {
  it('no incluye la paleta de colores por defecto de Tailwind', () => {
    expect(tema.colors).not.toHaveProperty('red');
    expect(tema.colors).not.toHaveProperty('gray');
    expect(tema.colors).not.toHaveProperty('white');
  });

  it('todos los colores salen de variables de tokens.css', () => {
    const permitidos = new Set(['transparent', 'currentColor', 'inherit']);
    const sueltos = hojas(tema.colors).filter((v) => !permitidos.has(v) && !v.includes('var(--'));
    expect(sueltos).toEqual([]);
  });

  it('mapea los colores de marca y de estados de turno', () => {
    expect(tema.colors).toMatchObject({
      primario: { DEFAULT: 'var(--color-primary)', tinte: 'var(--color-primary-tint)' },
      fondo: 'var(--color-bg)',
      reservado: { fg: 'var(--state-reservado-fg)', bg: 'var(--state-reservado-bg)', bd: 'var(--state-reservado-bd)' },
    });
  });

  it('espaciado, radios, sombras y tipografía vienen de tokens', () => {
    const valores = [
      ...hojas(tema.spacing).filter((v) => v !== '0'),
      ...hojas(tema.borderRadius).filter((v) => v !== '0'),
      ...hojas(tema.boxShadow).filter((v) => v !== 'none'),
      ...hojas(tema.fontSize),
      ...hojas(tema.fontFamily),
    ];
    expect(valores.filter((v) => !v.includes('var(--'))).toEqual([]);
    expect(tema.spacing).toMatchObject({ 4: 'var(--space-4)', touch: 'var(--touch-min)' });
  });
});
