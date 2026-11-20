/**
 * Monta el contenedor raíz de la SPA. El router, las features y el shell llegan en C-06.
 */
export function montarAplicacion(documento: Document): HTMLElement {
  const anfitrion = documento.querySelector<HTMLElement>('#app');
  if (anfitrion === null) {
    throw new Error('No se encontró el elemento #app');
  }

  const raiz = documento.createElement('main');
  raiz.dataset['appRoot'] = '';
  anfitrion.replaceChildren(raiz);
  return raiz;
}
