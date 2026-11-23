/* Solo para el mockup C-18 (copiado de C-09): elige el estado con ?estado=... y arma la franja de revisión.
   Basado en el mockup.js de C-07. Script clásico para que funcione abriendo el HTML por file://.
   No es código de producción.
   Atributos:
     data-estados="id:Etiqueta|id2:Etiqueta 2"  (en <body>; el primero es el de por defecto)
     data-solo="a b"        visible solo en esos estados
     data-no="a b"          oculto en esos estados
     data-invalido="a b"    aria-invalid=true en esos estados
     data-error-en="a b"    agrega el atributo data-error en esos estados
     data-desactivar="a b"  disabled en esos estados
     data-valor="*=x|a=y"   value por estado (* = todos)
     data-texto="*=x|a=y"   textContent por estado */
(function () {
  function lista(v) { return (v || "").split(/\s+/).filter(Boolean); }
  function porEstado(attr, estado, fn) {
    document.querySelectorAll("[" + attr + "]").forEach(function (el) {
      el.getAttribute(attr).split("|").forEach(function (p) {
        var i = p.indexOf("="); var k = p.slice(0, i);
        if (k === "*" || lista(k).indexOf(estado) >= 0) fn(el, p.slice(i + 1));
      });
    });
  }
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
    document.querySelectorAll("[data-error-en]").forEach(function (el) {
      if (lista(el.getAttribute("data-error-en")).indexOf(estado) >= 0) el.setAttribute("data-error", ""); else el.removeAttribute("data-error");
    });
    document.querySelectorAll("[data-desactivar]").forEach(function (el) { el.disabled = lista(el.getAttribute("data-desactivar")).indexOf(estado) >= 0; });
    porEstado("data-valor", estado, function (el, v) { el.value = v; });
    porEstado("data-texto", estado, function (el, v) { el.textContent = v; });
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
    /* Interruptores: comportamiento esperado en la implementación */
    document.querySelectorAll(".switch").forEach(function (b) {
      b.addEventListener("click", function () {
        var on = b.getAttribute("aria-checked") !== "true";
        b.setAttribute("aria-checked", String(on));
        var t = b.querySelector(".state"); if (t) t.textContent = on ? "Activa" : "Pausada";
      });
    });
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", aplicar); else aplicar();
})();
