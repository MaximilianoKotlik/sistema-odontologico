/* Solo para el mockup C-07: elige el estado con ?estado=... y arma la franja de revisión.
   Script clásico para que funcione abriendo el HTML por file://. No es código de producción. */
(function () {
  function lista(v) { return (v || "").split(/\s+/).filter(Boolean); }
  function aplicar() {
    var body = document.body;
    var estados = (body.getAttribute("data-estados") || "").split("|").map(function (p) {
      var i = p.indexOf(":"); return { id: p.slice(0, i).trim(), label: p.slice(i + 1).trim() };
    }).filter(function (e) { return e.id; });
    var pedido = new URLSearchParams(location.search).get("estado");
    var estado = estados.some(function (e) { return e.id === pedido; }) ? pedido : (estados[0] && estados[0].id);
    body.setAttribute("data-estado", estado);
    document.querySelectorAll("[data-solo]").forEach(function (el) { el.hidden = lista(el.getAttribute("data-solo")).indexOf(estado) < 0; });
    document.querySelectorAll("[data-no]").forEach(function (el) { if (lista(el.getAttribute("data-no")).indexOf(estado) >= 0) el.hidden = true; });
    document.querySelectorAll("[data-invalido]").forEach(function (el) {
      if (lista(el.getAttribute("data-invalido")).indexOf(estado) >= 0) el.setAttribute("aria-invalid", "true"); else el.removeAttribute("aria-invalid");
    });
    document.querySelectorAll("[data-desactivar]").forEach(function (el) { el.disabled = lista(el.getAttribute("data-desactivar")).indexOf(estado) >= 0; });
    document.querySelectorAll("[data-valor]").forEach(function (el) {
      var pares = el.getAttribute("data-valor").split("|");
      pares.forEach(function (p) { var i = p.indexOf("="); if (p.slice(0, i) === estado || p.slice(0, i) === "*") el.value = p.slice(i + 1); });
    });
    var nav = document.getElementById("estados");
    if (nav) {
      nav.innerHTML = "";
      estados.forEach(function (e) {
        var li = document.createElement("li"); var a = document.createElement("a");
        a.href = "?estado=" + e.id; a.textContent = e.label;
        if (e.id === estado) a.setAttribute("aria-current", "true");
        li.appendChild(a); nav.appendChild(li);
      });
    }
    /* Mostrar/ocultar contraseña: comportamiento esperado en la implementación */
    document.querySelectorAll(".pw .toggle").forEach(function (b) {
      b.addEventListener("click", function () {
        var input = b.parentNode.querySelector("input"); var ver = b.getAttribute("aria-pressed") !== "true";
        b.setAttribute("aria-pressed", String(ver)); input.type = ver ? "text" : "password";
      });
    });
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", aplicar); else aplicar();
})();
