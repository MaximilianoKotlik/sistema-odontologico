/* Nombre provisorio del producto (TBD). Cambiar SOLO esta constante.
   Script clasico (no modulo) para que funcione abriendo el HTML por file://. */
(function () {
  var BRAND_NAME = "Turnos Odonto (TBD)";
  function bind() {
    document.querySelectorAll("[data-brand-name]").forEach(function (el) { el.textContent = BRAND_NAME; });
    document.title = document.title.replace("{brand}", BRAND_NAME);
  }
  window.BRAND_NAME = BRAND_NAME;
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", bind); else bind();
})();
