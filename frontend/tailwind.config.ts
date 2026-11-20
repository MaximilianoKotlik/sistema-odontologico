import type { Config } from 'tailwindcss';

/**
 * Tailwind leyendo los tokens de design-system/tokens.css (C-06, design D5; regla dura 10).
 *
 * - Colores, espaciado, tipografía, radios, sombras y movimiento REEMPLAZAN el tema por defecto:
 *   una clase como `bg-red-500` o `p-8` no genera CSS y se nota en revisión.
 * - Las medidas de layout que el sistema de diseño no nombra se derivan de tokens con `calc()`.
 * - Las escalas estructurales de Tailwind (anchos máximos, bordes, z-index) quedan por defecto.
 */
const estado = (nombre: string) => ({
  fg: `var(--state-${nombre}-fg)`,
  bg: `var(--state-${nombre}-bg)`,
  bd: `var(--state-${nombre}-bd)`,
});

const espacio = {
  0: '0',
  1: 'var(--space-1)',
  2: 'var(--space-2)',
  3: 'var(--space-3)',
  4: 'var(--space-4)',
  5: 'var(--space-5)',
  6: 'var(--space-6)',
  7: 'var(--space-7)',
  touch: 'var(--touch-min)',
  /** Indicador de sección activa (3 px con base 4). */
  indicador: 'calc(var(--space-1) * 0.75)',
  /** Píldora del ícono en la navegación inferior (56 × 32). */
  'pildora-x': 'calc(var(--space-7) + var(--space-2))',
  /** Destino de la navegación inferior (64 px de alto). */
  'destino-nav': 'calc(var(--space-7) + var(--space-4))',
  /** Ancho de la barra lateral (248 px, como el mockup aprobado). */
  lateral: 'calc(var(--space-7) * 5 + var(--space-2))',
  /** Aire inferior del contenido para no quedar bajo la navegación fija en mobile. */
  'bajo-nav': 'calc(var(--space-7) + var(--space-4) + var(--space-5))',
} as const;

export default {
  content: ['./index.html', './src/**/*.ts'],
  theme: {
    colors: {
      transparent: 'transparent',
      current: 'currentColor',
      inherit: 'inherit',
      primario: {
        DEFAULT: 'var(--color-primary)',
        hover: 'var(--color-primary-hover)',
        activo: 'var(--color-primary-active)',
        tinte: 'var(--color-primary-tint)',
      },
      'sobre-primario': 'var(--color-on-primary)',
      fondo: 'var(--color-bg)',
      superficie: { DEFAULT: 'var(--color-surface)', tenue: 'var(--color-surface-muted)' },
      borde: { DEFAULT: 'var(--color-border)', fuerte: 'var(--color-border-strong)' },
      texto: { DEFAULT: 'var(--color-text)', tenue: 'var(--color-text-muted)', sutil: 'var(--color-text-subtle)' },
      peligro: { DEFAULT: 'var(--color-danger)', fondo: 'var(--color-danger-bg)' },
      exito: 'var(--color-success)',
      info: 'var(--color-info)',
      velo: 'color-mix(in srgb, var(--color-text) 45%, transparent)',
      reservado: estado('reservado'),
      confirmado: estado('confirmado'),
      cancelado: estado('cancelado'),
      sobreturno: estado('sobreturno'),
      bloqueo: estado('bloqueo'),
    },
    spacing: espacio,
    fontFamily: {
      sans: 'var(--font-sans)',
      mono: 'var(--font-mono)',
    },
    fontSize: {
      xs: ['var(--text-xs)', { lineHeight: 'var(--lh-xs)' }],
      sm: ['var(--text-sm)', { lineHeight: 'var(--lh-sm)' }],
      base: ['var(--text-base)', { lineHeight: 'var(--lh-base)' }],
      lg: ['var(--text-lg)', { lineHeight: 'var(--lh-lg)' }],
      xl: ['var(--text-xl)', { lineHeight: 'var(--lh-xl)' }],
      '2xl': ['var(--text-2xl)', { lineHeight: 'var(--lh-2xl)' }],
    },
    fontWeight: {
      normal: 'var(--weight-regular)',
      medium: 'var(--weight-medium)',
      semibold: 'var(--weight-semibold)',
      bold: 'var(--weight-bold)',
    },
    borderRadius: {
      none: '0',
      sm: 'var(--radius-sm)',
      DEFAULT: 'var(--radius-md)',
      md: 'var(--radius-md)',
      lg: 'var(--radius-lg)',
      full: 'var(--radius-full)',
    },
    boxShadow: {
      none: 'none',
      sm: 'var(--shadow-sm)',
      DEFAULT: 'var(--shadow-md)',
      md: 'var(--shadow-md)',
      lg: 'var(--shadow-lg)',
      foco: 'var(--focus-ring)',
    },
    transitionDuration: {
      DEFAULT: 'var(--duration-fast)',
      rapida: 'var(--duration-fast)',
      base: 'var(--duration-base)',
    },
    transitionTimingFunction: {
      DEFAULT: 'var(--ease-out)',
      salida: 'var(--ease-out)',
    },
    extend: {
      width: { icono: 'var(--space-5)', 'icono-sm': 'calc(var(--space-5) * 0.75)' },
      height: { icono: 'var(--space-5)', 'icono-sm': 'calc(var(--space-5) * 0.75)' },
      /** barra: alto de la barra superior en desktop (botón táctil + aire, 68 px). */
      minHeight: { touch: 'var(--touch-min)', barra: 'calc(var(--touch-min) + var(--space-5))' },
      minWidth: { touch: 'var(--touch-min)' },
      // Valores de design-system/components.html que no tienen variable propia en tokens.css.
      borderWidth: { control: '1.5px' },
      maxWidth: { lectura: '36ch' },
      scale: { presion: '.97' },
      keyframes: {
        brillo: { from: { backgroundPosition: '100% 0' }, to: { backgroundPosition: '0 0' } },
        subir: { from: { transform: 'translateY(var(--space-5))', opacity: '0' }, to: { transform: 'none', opacity: '1' } },
      },
      animation: {
        brillo: 'brillo calc(var(--duration-base) * 7) var(--ease-out) infinite',
        subir: 'subir var(--duration-base) var(--ease-out)',
      },
    },
  },
  plugins: [],
} satisfies Config;
