// Solo para tests. Según la versión de Node, `Response.blob()` devuelve el Blob de Node o el de jsdom,
// y el de jsdom no implementa arrayBuffer(). Los navegadores soportados sí lo tienen.
if (!Reflect.has(Blob.prototype, 'arrayBuffer')) {
  Object.defineProperty(Blob.prototype, 'arrayBuffer', {
    configurable: true,
    value(this: Blob): Promise<ArrayBuffer> {
      return new Promise((resolver, rechazar) => {
        const lector = new FileReader();
        lector.onload = () => resolver(lector.result as ArrayBuffer);
        lector.onerror = () => rechazar(new Error('No se pudo leer el archivo'));
        lector.readAsArrayBuffer(this);
      });
    },
  });
}
