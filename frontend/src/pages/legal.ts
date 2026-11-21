import privacidadMd from '../../../docs/legal/politica-de-privacidad.md?raw';
import terminosMd from '../../../docs/legal/terminos-y-condiciones.md?raw';
import type { PaginaAuth } from '../features/auth/contexto';
import { renderizarMarkdown } from '../features/auth/markdown';
import { enlace } from '../features/auth/ui';
import { el } from '../shared/dom';

/**
 * `/terminos` y `/privacidad` (C-07, design D9): el borrador de `docs/legal/` (#24), importado en el build y
 * convertido a DOM. El aviso de "pendiente de revisión legal" es parte del texto (DD-28).
 */

function paginaLegal(titulo: string, markdown: string): PaginaAuth {
  return {
    titulo,
    montar({ contenedor }) {
      contenedor.classList.replace('max-w-md', 'max-w-2xl');
      contenedor.replaceChildren(
        el('article', { class: 'text-base' }, renderizarMarkdown(markdown)),
        el('p', { class: 'mt-6 mb-0' }, enlace('/registro', 'Volver a crear mi consultorio')),
      );
      return null;
    },
  };
}

export const terminos = paginaLegal('Términos y condiciones', terminosMd);
export const privacidad = paginaLegal('Política de privacidad', privacidadMd);
