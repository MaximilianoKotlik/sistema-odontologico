import './styles.css';
import { montarAplicacion } from './app';
import { esRutaPublica, montarPaginaPublica } from './publico';

// Las páginas para visitantes (p. ej. /precios, C-21) se montan sin el shell ni la sesión del odontólogo.
if (esRutaPublica(location.pathname)) {
  void montarPaginaPublica(document, location.pathname);
} else {
  montarAplicacion(document);
}
