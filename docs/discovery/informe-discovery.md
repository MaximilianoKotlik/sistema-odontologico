# Informe competitivo: software de gestión odontológica y turnos de salud para Argentina

**Fecha:** 16 de noviembre de 2026
**Alcance:** 30 sistemas relevados en cuatro lotes: nativos argentinos (6), plataformas de turnos de salud con presencia en Argentina (6), proveedores latinoamericanos (7) e internacionales de referencia (11). El foco es un consultorio o una clínica odontológica pequeña o mediana que opera en Argentina.
**Insumos:** `docs/discovery/research/<slug>.md` (fichas detalladas por dimensión), `docs/discovery/sources/<slug>.md` (notas de esquema fijo) y `docs/discovery/tabla-rapida.md`. Este informe no incorpora datos externos a esas fichas.

## Metodología

1. **Fuentes.** Sitios oficiales de los proveedores (home, páginas de producto, de precios, FAQ y, en un caso, el help center), consultados con WebFetch/WebSearch. Si el sitio oficial no respondía, se usaron directorios (ComparaSoftware, AlternativeTo) o blogs de terceros, y se indica en cada caso. Fecha de consulta de todas las fuentes: **2026-11-16**.
2. **Niveles de evidencia.** El informe usa tres niveles:
   - **Comprobado (documental):** la función o el precio figuran con detalle en una página oficial funcional, de precios, de FAQ o del help center (marcas `[C]` o `[C-sitio]` de las fichas, o páginas de precios marcadas "comprobado"). **Ningún dato se verificó en uso real**: "comprobado" significa "documentado con detalle por el proveedor", no "probado".
   - **Afirmación comercial (AC):** texto de marketing, cifras autodeclaradas o descripciones genéricas.
   - **Baja confianza:** el sitio oficial no respondió o no existe; los datos vienen de snippets de buscador o de directorios de terceros.
3. **Limitaciones (importantes para leer las conclusiones):**
   - **No hubo demos ni cuentas de prueba.** No se pudo observar la interfaz, la calidad de los flujos ni el rendimiento.
   - **No hay reseñas verificadas.** Capterra y G2 devolvieron 403. Las calificaciones citadas (por ejemplo, "4,8 en Capterra") son autodeclaradas por los proveedores.
   - **WebFetch devuelve resúmenes automáticos**, no el texto íntegro: una función ausente del resumen puede existir en el producto. Por eso "No evidenciado" **no equivale a "no existe"**.
   - **Montos de fuentes secundarias:** Doctocliq y Doctoralia (blog de un competidor, Pabau), XIAgenda (directorio orientado a México). Clinic Cloud no publica precios.
   - **Sistemas de baja confianza** (DentalCore, ZYRA, XIAgenda, Doctoralia Argentina): van en la sub-tabla A.3 y quedan fuera de la matriz de puntuación y de los porcentajes de mercado.
4. **Criterio de orden por relevancia para Argentina (RA).** Un puntaje simple que respeta la jerarquía pedida:
   - +4 si la presencia o adaptación argentina está comprobada (empresa o dominio argentino con oferta local, o módulo argentino documentado); +2 si solo hay indicios.
   - +3 si publica precios en ARS.
   - +1 por cada elemento evidenciado entre obras sociales, ARCA y Mercado Pago.
   - +1 si es vertical dental (odontograma o ficha dental evidenciada).
   - Empates: se ordena por vertical dental, luego idioma español o portugués, luego precio publicado.

### Leyenda de abreviaturas (tablas A)

| Abreviatura | Significado |
|---|---|
| **Sí** | Documentado con detalle en una página oficial funcional, de precios o de ayuda (nivel "comprobado documental"). |
| **AC** | Mencionado solo como afirmación comercial (marketing, sin detalle verificable). |
| **No** | El propio proveedor indica que no lo tiene, que está "en desarrollo" o que no aplica a Argentina. |
| **Parcial** | Existe con alcance reducido (solo en un plan alto, solo manual, etc.). |
| **Add-on** | Disponible con cargo adicional. |
| **(D)** | Dato de directorio de terceros (baja confianza). |
| **WA / MP / OS / LE** | WhatsApp / Mercado Pago / obras sociales y prepagas / lista de espera. |

---

## A. Tabla comparativa

La tabla se divide en dos bloques con el mismo orden y numeración, para que sea legible: **A.1** (identificación, agenda, turnos y clínica) y **A.2** (administración, integraciones, seguridad, modelo comercial y evidencia). La sub-tabla **A.3** reúne los sistemas no verificables.

### A.1 Identificación, agenda, turnos digitales, automatización y clínica

Columnas compuestas:
- **Agenda:** multiprofesional / sillones / sobreturnos / prevención de solapamiento.
- **Turnos digitales:** reserva online / WhatsApp / lista de espera.
- **Clínica:** ficha o HC / odontograma / periodontograma / imágenes / presupuestos.

| # | RA | Sistema (empresa, país) | URL | Segmento | Modalidad | Agenda (MP / Sill. / Sobret. / Solap.) | Turnos digitales (Online / WA / LE) | Automatización | Clínica (Ficha / Odonto / Perio / Img / Presup.) |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 10 | **DentalSoft** (razón social No evidenciado; Argentina, La Plata y Rosario) | https://dentalsoft.com.ar/ | Consultorios y clínicas; profesional independiente | SaaS; PC, tablet, celular | Sí / No evidenciado / Sí / Sí (con detección de solapamiento; además bloqueos, urgencias, drag and drop, multisucursal) | AC (4 pasos, 24/7, reconocimiento por DNI en Pro; portal para ver y cancelar) / AC (asistente IA, Pro) / No evidenciado | Recordatorios por WA y email: manuales en Gestión, automáticos en Pro (Meta cobra el mensaje) | Sí / Sí (FDI, 18 estados, historial, PDF) / No evidenciado / Sí (estudios, escaneos 3D) / Sí (por link) |
| 2 | 10 | **MedicAI** (MEDICAI S.A.S., Argentina) | https://www.medicai.com.ar | Médicos y consultorios de 1 a 15 profesionales; clínicas (Enterprise). **No dental** | SaaS web + PWA | Sí (por médico y sede) / No evidenciado / No evidenciado / No evidenciado | Sí (turnera 24/7, seña al reservar) / Parcial (solo recordatorios, add-on pago) / No evidenciado | Recordatorios por email (incluidos) y por WA (pago) | Sí (HC genérica, registro inmutable, dictado IA) / No evidenciado / No evidenciado / No evidenciado / No evidenciado |
| 3 | 10 | **YoFacturo, módulo Clínicas** (razón social No evidenciado; Argentina) | https://yo-facturo.com/clinicas/ | Médicos, kinesiólogos, psicólogos, odontólogos. **No dental** | SaaS web | Sí (por profesional) / No evidenciado / No evidenciado / No evidenciado | Parcial ("turnero" solo en plan Completo) / No evidenciado / No evidenciado | Recordatorios por WA o email | Parcial (ficha con notas; **HC explícitamente no incluida**) / No evidenciado / No evidenciado / No evidenciado / No evidenciado |
| 4 | 9 | **AgendaPro Argentina** (AgendaPro, LATAM) | https://agendapro.com/ar/dental/software-odontologico | Odontólogo independiente, consultorio, clínica multisede; plataforma horizontal con página vertical dental | SaaS nube (AWS, AC) | Sí / Sí (por sillón) / No evidenciado / No evidenciado | Sí (24/7, Google Calendar) / Sí (IA en WA, Instagram y Messenger; eficacia AC) / No evidenciado | Recordatorios por WA de 1 a 3 días antes con reinsistencia; seguimiento de presupuestos pendientes (Sí); campañas (AC) | Sí (anamnesis, evoluciones, consentimiento, firma) / Sí / No evidenciado / No evidenciado / Sí (envío por WA) |
| 5 | 8 | **Turnito** (Grupo Vansur, Argentina) | https://turnito.app | Genérico de servicios y salud. **No dental** | SaaS web | Sí (3, 5 o ilimitadas según plan) / No evidenciado / No evidenciado / No evidenciado | Sí (página sin cuenta del paciente; reservas recurrentes) / Parcial (link compartible por WA; bot No evidenciado) / No evidenciado | Recordatorios por WA, email y Telegram desde el plan Plus | Parcial (ficha de cliente, plan Pro) / No evidenciado / No evidenciado / No evidenciado / No evidenciado |
| 6 | 7 | **ClinIA – OdontoClinIA** (Emprinet, Argentina) | https://www.clinia.com.ar/odontologia | Consultorios, clínicas PyME, centros públicos, cadenas | Web; instancia propia por cliente | AC / AC ("varias sillas") / No evidenciado / AC (control de edición simultánea) | AC (turnero web, app del paciente) / AC (IA que confirma, cancela y reprograma) / No evidenciado | Recordatorios por WA y correo (AC) | AC (HCE, CIE-10) / AC (FDI 52 piezas, versionado, comparación) / No evidenciado / AC (adjuntos) / AC (plan con importe por nomenclador) |
| 7 | 6 | **Bilog** (razón social No evidenciado; Argentina) | https://bilog.com.ar/ | Consultorios, clínicas, obras sociales y gerenciadoras | Web + app móvil + tablet | AC (multiprofesional, multiconsultorio) / No evidenciado / No evidenciado / No evidenciado | AC (reserva, cancelación y consulta; alta por QR o link) / AC (confirmación y reprogramación) / No evidenciado | Recordatorios y confirmaciones (AC); IA iAngela para la carga interna (AC) | AC / AC / No evidenciado / AC (apoyo IA en radiografías) / AC |
| 8 | 6 | **Dentalink** (Chile; módulo argentino) | https://www.softwaredentalink.com | Odontólogo independiente; clínicas con varios profesionales y sucursales | SaaS nube | Sí (multisucursal, página de planes) / No evidenciado / No evidenciado / No evidenciado | AC (24/7) / AC (Contact Center IA por WA y llamadas) / No evidenciado | Confirmaciones automáticas, email marketing, NPS en plan Titanium (AC) | Sí (página de planes) / Sí / AC / AC (Rx con IA) / Sí |
| 9 | 4 | **OdontoGRAMA** (Solsoftware; Argentina por indicios: .com.ar, +54) | https://odontograma.com.ar/ | Consultorios | SaaS nube | AC (agendas personalizables con control de asistencia) / No evidenciado / No evidenciado / No evidenciado | No evidenciado / No evidenciado / No evidenciado | No evidenciado | AC (ficha con imágenes, diagnósticos) / No evidenciado (gráfico) / No evidenciado / AC / No evidenciado |
| 10 | 2 | **Klinicos** (Uruguay) | https://klinicos.app | Profesional independiente; clínicas dentales, estéticas, médicas, veterinarias | SaaS nube | AC (filtros por profesional) / No evidenciado / No evidenciado / No evidenciado | AC (link público) / AC (confirmación y recordatorio D-1 vía Meta Cloud API) / No evidenciado | Recordatorios por WA incluidos desde el plan básico (AC) | AC / AC (fechado, con histórico y comparación) / AC / No evidenciado / No evidenciado |
| 11 | 1 | **Doctocliq** (Perú) | https://doctocliq.com | Profesionales independientes, clínicas, centros; multiespecialidad | SaaS web + apps iOS y Android | AC (por consultorio y doctor) / No evidenciado / No evidenciado / No evidenciado | AC (IA "Soyla" agenda 24/7) / AC / No evidenciado | Recordatorios, cumpleaños, seguimiento de citas y pagos, campañas por WA (AC) | AC / AC / No evidenciado / AC / AC (y planes de tratamiento) |
| 12 | 1 | **Dentidesk** (Chile y Rep. Dominicana) | https://dentidesk.com | Clínicas dentales, odontólogos, centros, ámbito académico, sindicatos | SaaS + apps | AC (multisucursal) / No evidenciado / No evidenciado / No evidenciado | AC / No ("en desarrollo") / No evidenciado | No evidenciado (WA automático en desarrollo) | AC / AC (nomenclatura chilena, internacional, universal) / AC (ficha de periodoncia) / No evidenciado / No evidenciado |
| 13 | 1 | **Clinicorp** (Brasil) | https://www.clinicorp.com | Dentistas, consultorios, clínicas, redes, estética | SaaS + app del paciente + app del dentista | AC / No evidenciado / No evidenciado / No evidenciado | No evidenciado / AC (confirmaciones; agentes IA en combo) / No evidenciado | Alertas de retorno, confirmaciones por WA, check-in por QR (AC) | AC / No evidenciado / No evidenciado / AC (IA radiológica, cámara intraoral) / AC (planes de tratamiento) |
| 14 | 1 | **Simples Dental** (Brasil) | https://www.simplesdental.com | Dentistas, secretarias, administradores | SaaS nube | AC / No evidenciado / No evidenciado / AC ("sin conflictos de horario") | AC (enlace 24/7) / AC (secretaria virtual IA, "nuevo") / No evidenciado | Confirmación por WA, campañas por WA, programa de referidos (AC) | AC / AC / No evidenciado / No evidenciado / No evidenciado |
| 15 | 1 | **Open Dental** (país No evidenciado) | https://www.opendental.com/ | Consultorios de cualquier tamaño (tarifa por sede) | Escritorio por sede + apps ODMobile y ODTouch | AC (mejoras de bloqueos en notas de versión) / No evidenciado / No evidenciado / No evidenciado | Sí (Web Sched, add-on pago) / No evidenciado (SMS solo EEUU, Puerto Rico y Canadá) / Parcial (Web Sched ASAP, add-on) | eConfirmations, mensajería automática, email masivo, recall (Sí, como add-ons) | AC / AC (gráfico 3D) / No evidenciado / Sí (captura incluida; IA de imágenes add-on) / No evidenciado |
| 16 | 1 | **tab32** (tab32, Inc., EEUU) | http://tab32.com/ | Consultorio individual (Alpine) y multisede (Summit) | SaaS en navegador y móvil | No evidenciado / AC (columnas por operatorio) / No evidenciado / No evidenciado | AC / No evidenciado / AC | Recordatorios y recall por texto; seguimiento de ausentes (AC) | AC / AC / AC (por voz) / AC / AC (plan de tratamiento) |
| 17 | 1 | **Dentally** (Henry Schein One UK, Reino Unido) | https://www.dentally.com/en-gb/ | Consultorios y multisede | SaaS cloud | AC / No evidenciado / No evidenciado / No evidenciado | Sí (portal; solo planes Essentials y Pro) / No evidenciado / AC | Recordatorios y confirmaciones (AC) | AC / No evidenciado / No evidenciado / AC (Vision IA) / No evidenciado |
| 18 | 1 | **Ascend by Dentrix** (Henry Schein One, EEUU) | https://www.dentrixascend.com/ | Consultorios y grupos | SaaS nube | No evidenciado / No evidenciado / No evidenciado / No evidenciado | AC / No evidenciado / No evidenciado | Recall, confirmaciones, encuestas, mensajería bidireccional (AC) | AC / AC (charting con IA) / No evidenciado / AC (IA de radiografías) / No evidenciado |
| 19 | 1 | **Curve Dental** (CD Newco LLC, EEUU) | https://www.curvedental.com/ | Consultorios de EEUU y Canadá | SaaS en navegador | AC / No evidenciado / No evidenciado / No evidenciado | AC / No evidenciado / No evidenciado | Recordatorios (AC) | AC / AC / AC / AC / No evidenciado |
| 20 | 1 | **Denticon** (Planet DDS, EEUU) | https://www.planetdds.com/denticon/ | DSO y multisede; también consultorios | SaaS cloud | AC ("scheduler avanzado") / No evidenciado / No evidenciado / No evidenciado | AC (MyTooth) / No evidenciado / No evidenciado | Confirmaciones, recordatorios de saldo, reagenda de pacientes vencidos con agentes IA de llamadas (AC) | AC / AC (por voz) / AC (voice perio) / AC / No evidenciado |
| 21 | 1 | **CareStack** (Good Methods Global Inc., EEUU; sitios UK y AU) | https://www.carestack.com/ | Consultorios y DSO | SaaS cloud | AC (multisede) / No evidenciado / No evidenciado / No evidenciado | AC (y Reserve with Google) / No evidenciado / No evidenciado | Recordatorios (AC) | AC / AC / AC / AC / No evidenciado |
| 22 | 1 | **Oryx Dental** (EEUU y Canadá) | https://www.oryxdental.com/ | Consultorios | SaaS en Google Cloud | AC ("smart scheduling") / No evidenciado / No evidenciado / No evidenciado | AC (opcional) / No evidenciado / No evidenciado | Recordatorios y mensajería bidireccional (AC) | AC / AC / AC (por voz) / AC / No evidenciado |
| 23 | 0 | **Clinic Cloud** (Doctoralia España SL, España) | https://www.clinic-cloud.com/ | Consultas y clínicas. **No dental** | No evidenciado | AC (multiprofesional, multisede) / No evidenciado / No evidenciado / No evidenciado | AC (vía Doctoralia) / AC (WA y SMS) / No evidenciado | Recordatorios y confirmaciones (AC) | No evidenciado / No evidenciado / No evidenciado / No evidenciado / No evidenciado |
| 24 | 0 | **NexHealth** (EEUU) | https://www.nexhealth.com/ | Consultorios dentales y médicos; **capa sobre un PMS** | SaaS | Sincroniza con el PMS | AC / No evidenciado / AC | Recordatorios, campañas, recall (AC) | No aplica (no es PMS) |
| 25 | 0 | **Weave** (Weave Communications, EEUU) | https://www.getweave.com/ | Consultorios; **comunicación** | SaaS + app móvil | No (no es agenda) | AC (solicitud web y SMS) / No evidenciado / No evidenciado | Recordatorios, reactivación (AC) | No aplica |
| 26 | 0 | **Adit** (Adit Communications; país No evidenciado) | https://www.adit.com/ | Consultorios; **engagement** | SaaS | AC (online scheduling) | AC (y agente IA) / No evidenciado / No evidenciado | Recordatorios y mensajería de texto (AC) | No aplica |

### A.2 Administración, integraciones, seguridad, modelo comercial y evidencia

Columna compuesta **Administración:** caja / Mercado Pago / obras sociales / ARCA.

| # | Sistema | Administración (Caja / MP / OS / ARCA) | Integraciones clave | Seguridad y normativa declarada | Modelo comercial (precio, moneda, prueba) | Evidencia de adopción | Nivel de evidencia |
|---|---|---|---|---|---|---|---|
| 1 | DentalSoft | Sí (cierre diario, cuenta corriente) / Sí (links, señas) / Sí (coberturas; liquidaciones por OS y por profesional; comisiones) / No evidenciado | Google Calendar, WA, MP, email; importación Excel/CSV; exportación PDF/Excel; API No evidenciado | Backup y exportación desde el plan Gestión (AC); roles configurables; historial de cambios del odontograma. Leyes 25.326/26.529: No evidenciado (hay páginas de términos y privacidad, no leídas) | Gratis $0 (1 profesional, 50 turnos/mes, 3 meses); Gestión $30.000/mes; Pro $60.000/mes, **por profesional**; moneda no explícita; WA cobrado por Meta | 300+ clínicas (AC); Google 5,0 con 11 reseñas | **Comprobado** (documental) |
| 2 | MedicAI | Parcial (caja a cargo de la secretaria) / Sí (total o seña) / Sí (OSDE, Galeno, IOMA, Medicus, etc.; seguimiento de reclamos) / Sí (CAE) | Google Calendar, MP, ARCA, WA; API con enlace a documentación (no leída); receta firmada con 2.º factor (ReNaPDiS N.º 292) | AES-256-GCM, aislamiento por organización, anonimización antes de IA (AC); **auditoría inmutable por 15 años y exportación completa en FHIR (Sí)**; declara Leyes 25.326, 26.529 y 27.553 (AC, sin auditoría externa) | Premium ARS 25.900 + IVA por profesional/mes; WA add-on ARS 19.900 o 29.900 + IVA (200 o 400 mensajes); prueba de 30 días sin tarjeta; sin permanencia | No evidenciado (Capterra con 0 reseñas según buscador) | **Comprobado** |
| 3 | YoFacturo | No evidenciado / Sí (link de pago del producto general) / Sí (cuenta corriente de OS y prepagas, copagos; **no calcula aranceles ni se integra con financiadores**) / Sí (A, B y C con CAE) | ARCA, MP, WA y email; API en plan Premium | Cifrado (AC); backups diarios; auditoría de accesos y cambios, MFA y exportación solo en Premium; cita GDPR; 25.326/26.529: No evidenciado | ARS 20.000 / 50.000 / 75.000 / 150.000 por mes ("sin IVA adicional" según el sitio); add-ons de ARS 8.000 a 50.000; prueba de 15 + 15 días (el sitio también dice "30 días") | 200+ empresas, 150.000+ facturas (AC); sin casos de salud | **Comprobado** |
| 4 | AgendaPro AR | Sí (caja multisede, POS, honorarios por profesional, reporte de ocupación de sillones) / Sí (seña o total, cuotas) / No evidenciado / No evidenciado (solo un enlace sin detalle) | Google Calendar, MP, Google Reserve/Maps, WA; página de API sin contenido legible | Permisos por profesional (Sí); cifrado, backups y cumplimiento de las Leyes 25.326 y 26.529 declarados (AC, sin certificación) | Desde ARS 13.900/mes (FAQ), según funciones y cantidad de odontólogos; prueba de 14 días sin tarjeta; migración gratis en 24 h (AC) | +30.000 clínicas LATAM, 4,8 con +1.200 reseñas (AC); testimonios de otros países y mención a FONASA | **Comprobado** (parcial) |
| 5 | Turnito | No evidenciado / Sí (seña o total; también PayPal, Talo, transferencia) / No evidenciado / No evidenciado | MP, Google Calendar/Meet, WordPress, Telegram, WA, Píxel/Analytics | No evidenciado (nada declarado sobre datos de salud) | Gratis permanente; ARS 9.900 / 20.300 / 34.800 sin IVA; comisión por cobro de 5% / 3,5% / 1% / 0% | 4,8 con 164 reseñas en Google (AC, sin URL) | **Comprobado** |
| 6 | ClinIA | AC (arqueo; cobros en ARS, USD y EUR; recibos PDF por WA) / No evidenciado / AC (verificación PUCO, OS y prepagas) / AC | RENAPER, PUCO, ARCA, WA, recetario HL7 FHIR ReNaPDiS N.º 248 (cubre el recetario, no todo el software), módulos PAMI y SUMAR+ | Backups cada 10 minutos (AC); páginas de seguridad y privacidad no leídas; 25.326: No evidenciado | Abono por institución sin montos publicados; $0 por usuario y por migración; demo de 45 min | No evidenciado | **Comercial** |
| 7 | Bilog | No evidenciado / No evidenciado / AC (liquidaciones de OS; auditoría prestacional) / No evidenciado | WA | "Trato cuidadoso de los datos", sin detalle; normativa y backup: No evidenciado | Sin permanencia, alta gratis, pago por mes usado (AC); montos y prueba: No evidenciado | 1.500+ clínicas, 5.000+ usuarios, 150.000 turnos/mes (AC); testimonios nominales | **Comercial** |
| 8 | Dentalink | AC (caja, pagos, liquidaciones a profesionales, laboratorios) / No evidenciado / **Sí** (help center: crear obra social, recaudar con OS, conciliaciones) / No evidenciado | API (complemento), WA, videoconsulta, receta electrónica argentina (título del help center) | ISO 27001 con auditoría anual, AWS, cifrado, backups, "buenas prácticas" HIPAA y GDPR (AC); normativa argentina: No evidenciado | Sin precios ("solicitá tu cotización"); Capterra indica US$29 (fuente secundaria); planes Esencial, Pro y Titanium; sin permanencia | +15.000 clínicas, +100.000 profesionales (AC); clientes chilenos citados | **Comprobado** (parcial: módulo AR y planes) |
| 9 | OdontoGRAMA | AC (caja, cuentas corrientes, egresos) / No evidenciado / AC (facturación a prepagas y OS) / No evidenciado | No evidenciado | "Datos protegidos 24x7", backup a cargo del proveedor (AC) | Cuenta gratuita "con todas las funciones" (AC); sin precios | No evidenciado | **Comercial** |
| 10 | Klinicos | AC (imputación FIFO, saldo a favor, multimoneda) / AC (se configura tras el trial) / No evidenciado / No aplica (Uruguay; DGI: No evidenciado) | Meta Cloud API (WA), MP | No evidenciado | UYU: Básico $890 (~USD 22), Pro $1.690, Business $2.290; Enterprise a medida; prueba de 14 días; altas limitadas por lista de espera | No evidenciado (producto en lanzamiento) | **Comercial** (precios: Sí) |
| 11 | Doctocliq | AC (ingresos y egresos, comisiones, inventario) / No evidenciado / No evidenciado / No (fiscal solo en PE, MX, EC y CO) | WA, Meta Business Partner, Google, Kommo, API de agenda | Roles y permisos (AC); resto No evidenciado | Plan gratis y prueba de 7 días (sitio); montos de US$0/19/29/49 **solo por fuente secundaria competidora (Pabau)**, en conflicto con ComparaSoftware | +6.500 profesionales (AC); premios Forbes, Endeavor Perú | **Comercial** |
| 12 | Dentidesk | AC (insumos, +20 reportes) / No evidenciado / No evidenciado / No (SII Chile; Alegra/DGII Rep. Dominicana) | SII, Alegra | Ley de protección de datos de Chile (AC) | Sin precios; prueba de 15 días; Capterra US$50 (fuente secundaria) | Clientes citados en Chile y Cabo Verde | **Comercial** |
| 13 | Clinicorp | AC (Clinipay: conciliación, Pix, boletos) / No evidenciado / No evidenciado / No aplica (Brasil) | Cloudia, Image2Doc, Idoc, Cfaz, cámara intraoral, Google Meu Negócio | No evidenciado (niveles de acceso: AC) | Standard R$149,90/mes; Premium R$369,90/mes; Enterprise y combos de IA sin precio | +32.000 clínicas (AC) | **Comercial** (precios: Sí) |
| 14 | Simples Dental | AC (cobros, flujo de caja, comisiones) / No evidenciado / No evidenciado / No aplica (Brasil) | Asaas, Saúde Service, Meta, Alexa | No evidenciado | Precios no visibles en la landing consultada; prueba de 7 días sin tarjeta | Testimonios nominales | **Comercial** |
| 15 | Open Dental | No evidenciado / No evidenciado / No evidenciado / No evidenciado (reclamos vía clearinghouses de EEUU) | Clearinghouses de EEUU, imaging; API: No evidenciado | No evidenciado (solo política de privacidad) | **Sí:** USD 199/mes por sede en EEUU; **USD 89/mes en "otros países"**; gratis con soporte limitado en países en desarrollo (no verificado si aplica a Argentina); contrato de 12 meses; garantía de 90 días; add-ons en USD | No evidenciado | **Comprobado** (precios); funciones: Comercial |
| 16 | tab32 | No evidenciado / No evidenciado / No evidenciado / No evidenciado (claims de EEUU) | No evidenciado (API: No evidenciado) | HIPAA, SOC 2, BAA, cifrado, log de auditoría de toda acción (AC) | **Sí:** desde USD 95/mes, usuarios ilimitados; prueba de 14 días con tarjeta de EEUU | No evidenciado | **Comercial** (precio: Sí) |
| 17 | Dentally | No evidenciado / No evidenciado / No evidenciado / No evidenciado (NHS) | Marketplace, Developer Hub (contenido No evidenciado) | Sección de seguridad sin detalle | **Sí:** GBP/mes sin IVA, según sillones (1 a 9): Starter 134-618; Essentials 238-778; Pro 346-1.021 | 3 testimonios | **Comercial** (precios: Sí) |
| 18 | Ascend by Dentrix | No evidenciado / No evidenciado / No evidenciado / No evidenciado (claims y elegibilidad de EEUU) | Ascend API Exchange (AC) | SOC 2, HIPAA, backups cifrados, roles (AC) | **Sí:** Starter USD 199/mes; resto a medida; prueba: No evidenciado | 80.000+ usuarios, 66M+ recordatorios (AC) | **Comercial** |
| 19 | Curve Dental | No evidenciado / No evidenciado / No evidenciado / No evidenciado (Curve Pay, seguros de EEUU) | Página de partners (lista no extraída) | "HIPAA-compliant" (AC) | Cotización a medida | 100.000+ usuarios, 6.000+ sedes (AC) | **Comercial** |
| 20 | Denticon | No evidenciado / No evidenciado / No evidenciado / No evidenciado (eClaims, Planet DDS Pay) | "25+ integraciones" | HIPAA, SOC 2 Type 2, SIEM, auditoría (AC) | No publicado; implementación de ~60 días | 13.000+ consultorios (AC) | **Comercial** |
| 21 | CareStack | No evidenciado / No evidenciado / No evidenciado / No evidenciado (claims, débito automático, text-to-pay) | Telefonía, imaging, financiación; sección Developers | HIPAA, SOC 2 Type 2, ISO 27001:2022, GDPR (AC) | No publicado | 3.000+ consultorios (AC) | **Comercial** |
| 22 | Oryx Dental | No evidenciado / No evidenciado / No evidenciado / No evidenciado (SmilePay, claims) | Pearl, Overjet | HIPAA, PIPEDA, 2FA, backups, uptime 99,9%, roles, auditoría (AC) | No publicado | ~2.000 consultorios (AC) | **Comercial** |
| 23 | Clinic Cloud | AC (facturas, bonos, mutuas; Verifactu/TicketBAI de España) / No evidenciado / No aplica / No aplica | Doctoralia | RGPD "certificado", cifrado, trazabilidad (AC) | No publicado | "+12.000 doctores" (los contadores de la página figuran en 0) | **Comercial** |
| 24 | NexHealth | No evidenciado / No evidenciado / No evidenciado / No evidenciado (pagos, verificación de seguros) | **API pública con documentación y alta gratuita** (AC); Open Dental, Dentrix, Eaglesoft | HIPAA no declarado en la home | No publicado | 20.000+ consultorios (AC) | **Comercial** |
| 25 | Weave | No evidenciado (pagos, CareCredit) | PMS compatibles | HIPAA no publicado | No publicado | Un caso de éxito | **Comercial** |
| 26 | Adit | No evidenciado (Adit Pay) | PMS (formulario) | No evidenciado | No publicado | 5.000+ consultorios (AC) | **Comercial** |

### A.3 Sub-tabla de sistemas no verificables (baja confianza)

Estos sistemas quedan fuera de la matriz y de los porcentajes. Todo lo que figura acá proviene de buscadores o directorios y no está verificado.

| # | Sistema | URL | Por qué no es verificable | Qué dicen los snippets o directorios | Precio | Qué haría falta |
|---|---|---|---|---|---|---|
| 27 | **DentalCore** (Argentina, según el título) | https://dentalcore.app/ | Sitio oficial con ECONNREFUSED en dos intentos; Capterra devolvió 403 | HC con odontograma, recordatorios por WA, Google Calendar, facturación electrónica, OS, MP, inventario (AC) | No evidenciado | Revisión manual con navegador. Si se confirma, sería **competidor directo**: combina OS, MP y facturación en un vertical dental. |
| 28 | **ZYRA** (Argentina, por el dominio .ar) | https://www.appzyra.ar/ | SPA: WebFetch solo devolvió el título en tres intentos | Agenda inteligente, recordatorios por WA, HC con odontograma, facturación ARCA/AFIP (AC) | No evidenciado | Revisión manual; parece un producto nuevo. |
| 29 | **XIAgenda** (Argentina, según un directorio; proveedor "Xima" sin confirmar) | Sitio oficial no localizado (xiagenda.com: ENOTFOUND). Ficha: https://www.comparasoftware.com/xiagenda | Solo hay datos de directorios | Agente IA por WA, web y email que agenda, reprograma y cancela; **lista de espera con oferta automática de turnos liberados**; señas por MP (D) | Esencial USD 15/mes por miembro (directorio orientado a México) | Confirmar que el producto y el proveedor existen. |
| 30 | **Doctoralia** (Docplanner; Argentina: dominio .com.ar) | https://www.doctoralia.com.ar | .com.ar, /para-profesionales y /pro con ECONNREFUSED; los planes son de España y vienen de un blog competidor | Reserva online (Starter), recordatorios por SMS/WA (Plus), lista de espera (VIP), campañas (todo de España) | España: 89 / 109 / 129 €/mes sin IVA, facturación anual, permanencia de 12 meses. Argentina: No evidenciado | Confirmar oferta, moneda y planes para Argentina. Es el único actor con **marketplace de pacientes** (potencial no verificado en Argentina). |

---

## B. Matriz de puntuación (0 a 5)

### B.1 Rúbrica

**Reglas generales:**
- Se puntúan los **16 sistemas más relevantes para Argentina**: los 15 primeros por RA más tab32, como referencia internacional de bajo precio. Los de baja confianza quedan fuera.
- **Tope por evidencia:** si todo lo que sustenta un eje es afirmación comercial (AC), el eje puntúa como máximo **3**. Si todo el eje es "No evidenciado", puntúa **0 o 1** (1 cuando hay algún indicio parcial).
- Solo los elementos documentados con detalle (nivel "Sí") permiten llegar a 4 o 5.
- Se puntúa la **aplicabilidad en Argentina**: una función que solo opera en otro país (Pix, SII, claims de EEUU) no suma en los ejes de integraciones ni de administración.

| Eje (peso) | 0 | 3 | 5 |
|---|---|---|---|
| **Gestión de turnos y automatización (25%)** | Sin agenda evidenciada, o solo registro manual. | Agenda multiprofesional, reserva online y recordatorios automáticos, aunque sea como AC; sin evidencia de sobreturnos, sillones ni lista de espera. | Todo documentado: agenda por profesional **y** por sillón, sobreturnos con control de solapamiento, bloqueos, reserva online, recordatorios por WA con confirmación, reprogramación por el paciente y lista de espera o recall automático. |
| **Funcionalidad odontológica clínica (20%)** | Sin ficha clínica, o HC excluida explícitamente. | Ficha/HC, odontograma y presupuestos (aunque sea como AC). | Todo documentado: odontograma con historial o versionado, periodontograma, imágenes, plan de tratamiento con presupuesto vinculado y consentimientos. |
| **Integraciones locales y WhatsApp (15%)** | Ninguna integración aplicable a Argentina. | WA más una integración local (MP o Google Calendar), o varias locales solo como AC. | Todo documentado: WA (recordatorios y conversación), MP y ARCA, más padrones (PUCO o RENAPER), receta electrónica o API pública. |
| **Administración, cobros y facturación (15%)** | Sin módulo de cobros. | Caja o cobros más un elemento local (MP u OS), o varios elementos como AC. | Todo documentado: caja con cierre, cuenta corriente, MP, OS con liquidación y facturación ARCA desde el cobro. |
| **Experiencia del paciente (10%)** | Sin canal digital para el paciente. | Reserva online o por link y recordatorios, sin autogestión completa. | Todo documentado: reserva sin registro; confirmación, cancelación y reprogramación autónomas; pago o seña; presupuesto por link; comunicación por WA. |
| **Seguridad, exportación y trazabilidad (10%)** | Nada declarado. | Backups, roles y cifrado declarados, o cumplimiento normativo como AC. | Todo documentado: roles, auditoría de cambios, exportación completa en formato estándar, segundo factor y referencia explícita a las Leyes 25.326/26.529. |
| **Precio y facilidad de adopción (5%)** | Sin precio (solo cotización), contrato largo, sin prueba. | Precio parcial ("desde"), prueba sin montos publicados, o precio en moneda extranjera. | Precios completos en ARS, plan gratuito o prueba sin tarjeta, sin permanencia. |

Los puntajes 1, 2 y 4 quedan entre esos anclajes.

### B.2 Puntuación y total ponderado (ordenada por total)

Ejes: T = turnos y automatización (0,25); Cl = clínica (0,20); I = integraciones (0,15); A = administración (0,15); P = paciente (0,10); S = seguridad (0,10); $ = precio y adopción (0,05). Total = Σ(puntaje × peso), sobre 5.

| Pos. | Sistema | T (25%) | Cl (20%) | I (15%) | A (15%) | P (10%) | S (10%) | $ (5%) | **Total** | Nivel de evidencia dominante |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | DentalSoft | 4 | 4 | 3 | 4 | 4 | 2 | 4 | **3,65** | Comprobado |
| 2 | AgendaPro AR | 4 | 3 | 3 | 3 | 4 | 3 | 4 | **3,40** | Comprobado (parcial) |
| 3 | MedicAI | 3 | 1 | 4 | 4 | 3 | 4 | 4 | **3,05** | Comprobado |
| 4 | Dentalink | 3 | 4 | 2 | 3 | 3 | 3 | 1 | **2,95** | Comprobado (parcial) |
| 5 | ClinIA | 3 | 3 | 3 | 3 | 3 | 2 | 2 | **2,85** | Comercial |
| 6 | Doctocliq | 3 | 3 | 2 | 2 | 3 | 1 | 3 | **2,50** | Comercial |
| 7 | Klinicos | 3 | 3 | 2 | 2 | 3 | 0 | 4 | **2,45** | Comercial |
| 8 | Bilog | 3 | 3 | 1 | 2 | 3 | 1 | 2 | **2,30** | Comercial |
| 8 | Clinicorp | 3 | 3 | 1 | 2 | 3 | 1 | 2 | **2,30** | Comercial |
| 10 | Turnito | 3 | 1 | 3 | 2 | 3 | 0 | 5 | **2,25** | Comprobado |
| 11 | tab32 | 3 | 3 | 0 | 1 | 3 | 3 | 2 | **2,20** | Comercial |
| 12 | YoFacturo | 2 | 0 | 3 | 4 | 1 | 3 | 4 | **2,15** | Comprobado |
| 12 | Simples Dental | 3 | 3 | 1 | 2 | 3 | 0 | 1 | **2,15** | Comercial |
| 14 | Open Dental | 3 | 3 | 0 | 1 | 3 | 0 | 3 | **1,95** | Comprobado (precios) |
| 15 | Dentidesk | 1 | 3 | 1 | 2 | 2 | 1 | 2 | **1,70** | Comercial |
| 16 | OdontoGRAMA | 1 | 1 | 0 | 2 | 0 | 1 | 3 | **1,00** | Comercial |

### B.3 Justificación resumida por sistema

- **DentalSoft (3,65).** Es el único que documenta sobreturnos con detección de solapamiento. También documenta caja con cierre diario, liquidaciones por OS y por profesional, Mercado Pago y odontograma FDI con historial. Resta puntos por periodontograma, ARCA y lista de espera no evidenciados, seguridad solo como AC, y un precio por profesional (ARS 30.000 a 60.000) relativamente alto para clínicas con varios odontólogos.
- **AgendaPro AR (3,40).** Es el mejor documentado en turnos (agenda por sillón, recordatorio con reinsistencia, IA multicanal, seguimiento de presupuestos), con MP integrado y precio de entrada bajo. Le faltan obras sociales y ARCA, y la página no está bien localizada (menciona FONASA y usa testimonios de otros países). El cumplimiento de las Leyes 25.326/26.529 es AC.
- **MedicAI (3,05).** Es el referente local en cumplimiento y administración (ARCA con CAE, OS con reclamos, MP, auditoría inmutable, exportación FHIR), pero **no tiene vertical odontológica** (Cl = 1). Su total lo sostienen los ejes no clínicos. Es un competidor indirecto y un modelo a imitar en trazabilidad.
- **Dentalink (2,95).** Es el único extranjero con **adaptación argentina comprobada** (help center con obras sociales, conciliaciones y receta electrónica) y declara periodontograma (AC). Lo penalizan los precios opacos y la falta de evidencia de ARCA y MP.
- **ClinIA (2,85).** Declara el set de integraciones argentinas más amplio (RENAPER, PUCO, ARCA, ReNaPDiS, PAMI, SUMAR+) y odontograma versionado, pero **todo es AC**, así que no supera 3 en ningún eje. Merece verificación prioritaria por demo.
- **Doctocliq (2,50), Klinicos (2,45), Clinicorp (2,30) y Simples Dental (2,15).** Muestran buena amplitud funcional según su marketing, pero ninguna integración fiscal ni de pago aplicable a Argentina. Klinicos se destaca por precio bajo con WA incluido.
- **Bilog (2,30).** Por trayectoria y foco en auditoría de OS debería ser un actor fuerte, pero su sitio no evidencia MP, ARCA, caja, precios ni seguridad. Es la mayor brecha entre posicionamiento y evidencia del lote argentino.
- **Turnito (2,25).** Es el más fácil de adoptar (plan gratis permanente, precios en ARS) y su reserva tiene poca fricción, pero no tiene función clínica ni declara nada sobre la seguridad de datos de salud.
- **YoFacturo (2,15).** Es fuerte en facturación ARCA y cuenta corriente de OS, pero excluye explícitamente la historia clínica.
- **tab32 (2,20) y Open Dental (1,95).** Son referencias de producto y de precios sin aplicabilidad local: sin WA, sin MP y con claims de EEUU.
- **OdontoGRAMA (1,00).** La información pública es mínima y no evidencia turnos digitales.

**Lectura crítica:** ningún sistema supera 3,7/5. El techo bajo refleja en parte la pobreza de la evidencia (sin demos, todo AC en varios casos), no solo las limitaciones de los productos. Aun así, **las diferencias en los ejes de integraciones y administración local son reales**: casi ningún vertical dental combina MP, obras sociales y ARCA documentados.

---

## C. Análisis competitivo

### C.1 Funcionalidades estándar de mercado

Base: **26 sistemas verificables** (se excluyen los 4 de baja confianza). Cuenta como "evidenciado" tanto Sí como AC. Cuando es relevante, se separan dos subconjuntos: los **9 con presencia argentina** (DentalSoft, MedicAI, YoFacturo, AgendaPro, Turnito, ClinIA, Bilog, Dentalink, OdontoGRAMA) y los **19 con vertical dental**.

| Funcionalidad | Sistemas con evidencia | % (n/26) | Lectura |
|---|---|---|---|
| Recordatorios automáticos (cualquier canal) | Todos salvo OdontoGRAMA y Dentidesk | **92% (24)** | Estándar absoluto; no diferencia a nadie. |
| Reserva online por el paciente | Todos salvo YoFacturo (parcial), OdontoGRAMA y Clinicorp | **88% (23)** | Estándar. La diferencia está en la fricción (sin cuenta, por DNI) y en la autogestión posterior. |
| Algún tipo de IA (conversacional, clínica o de imágenes) | AgendaPro, Bilog, ClinIA, DentalSoft, Dentalink, Doctocliq, Clinicorp, Simples, MedicAI, YoFacturo (add-on), Klinicos (importación), Open Dental (add-on), Dentrix, Denticon, CareStack, Oryx, tab32, Dentally, Weave, Adit | **77% (20)** | Argumento de marketing ya generalizado; en ningún caso se verificó su eficacia. |
| Odontograma o charting dental | DentalSoft, AgendaPro, ClinIA, Bilog, Dentalink, Klinicos, Doctocliq, Dentidesk, Open Dental, Simples, tab32, Dentally, Dentrix, Curve, Denticon, CareStack, Oryx | **65% (17)**; 89% de los verticales dentales (17/19) | Requisito de entrada en el vertical dental, no diferencial. |
| Multisede / multisucursal | DentalSoft, MedicAI, AgendaPro, ClinIA, Bilog, Dentalink, Klinicos, Dentidesk, Doctocliq, Open Dental, Denticon, CareStack, Dentally, tab32, Clinic Cloud | **58% (15)** | Estándar en los planes medios y altos. |
| Imágenes y radiografías en la ficha | DentalSoft, Bilog, Dentalink, OdontoGRAMA, Doctocliq, Clinicorp, Open Dental, Dentrix, Curve, Denticon, CareStack, Oryx, tab32, Dentally | **54% (14)** | Frecuente. La IA sobre radiografías aparece en 7 sistemas, siempre como AC. |
| WhatsApp (recordatorio o conversación) | DentalSoft, MedicAI, YoFacturo, AgendaPro, Turnito, ClinIA, Bilog, Dentalink, Klinicos, Doctocliq, Clinicorp, Simples, Clinic Cloud | **50% (13)**; **87% del lote AR + LATAM (13/15)**; **0% del lote internacional (0/11)** | Estándar en la región; afuera domina el SMS. En Argentina, **no tener WA descalifica**. |
| Precio publicado con montos | DentalSoft, MedicAI, YoFacturo, AgendaPro ("desde"), Turnito, Klinicos, Clinicorp, Open Dental, tab32, Dentally, Dentrix (solo Starter) | **42% (11)**; en ARS: 5 | La mayoría oculta el precio. Entre los **verticales dentales argentinos**, solo DentalSoft publica todos sus planes. |
| Prueba gratuita o plan gratis | DentalSoft, MedicAI, YoFacturo, AgendaPro, Turnito, Klinicos, Doctocliq, Dentidesk, Simples, tab32, OdontoGRAMA (AC) | **42% (11)** | Frecuente en LATAM; poco frecuente entre los internacionales, que trabajan por cotización. |
| Caja / ingresos y egresos | DentalSoft, AgendaPro, ClinIA, Dentalink, OdontoGRAMA, Simples, MedicAI (parcial), Doctocliq | **31% (8)** | Menos estándar de lo esperable; solo DentalSoft documenta **caja con cierre diario**. |
| Presupuestos / plan de tratamiento | DentalSoft, AgendaPro, ClinIA, Bilog, Dentalink, Doctocliq, Clinicorp (plan), tab32 (plan) | **31% (8)** | Solo AgendaPro documenta el presupuesto **con seguimiento**. |
| Periodontograma | Dentalink, Klinicos, Dentidesk, Curve, Denticon, CareStack, Oryx, tab32 | **31% (8)**; **ningún nativo argentino** (solo Dentalink, a través de su producto chileno) | Brecha clara de la oferta argentina. |
| API declarada | Dentalink, MedicAI, YoFacturo, Doctocliq, Dentrix, NexHealth, CareStack, Dentally | **31% (8)** | No se verificó documentación pública en ningún caso (NexHealth la declara abierta). |
| Firma electrónica / consentimientos | DentalSoft, AgendaPro, Dentalink, Clinicorp, Simples, Dentidesk, Clinic Cloud | **27% (7)** | Semiestándar. |
| Obras sociales / prepagas | DentalSoft, MedicAI, YoFacturo, ClinIA, Bilog, Dentalink, OdontoGRAMA | **27% (7)**; **78% de los argentinos (7/9)** | Esperable en Argentina, pero con alcances muy distintos (ver C.3). |
| Auditoría / trazabilidad de cambios | MedicAI (Sí), YoFacturo (Premium), ClinIA, tab32, Denticon, Oryx, Clinic Cloud (AC); DentalSoft solo para el odontograma | **27% (7)** | Solo MedicAI la documenta con detalle. |
| Seguimiento de ausentes / recall | Open Dental, Dentrix, tab32, NexHealth, Weave (reactivación), Clinicorp (alertas de retorno), Denticon | **27% (7)**; **ningún argentino** | Lo resuelven los internacionales; en Argentina no aparece. |
| Mercado Pago | DentalSoft, MedicAI, YoFacturo, AgendaPro, Turnito, Klinicos | **23% (6)**; solo 2 verticales dentales argentinos (DentalSoft, AgendaPro) | Poco frecuente en el vertical dental. |
| Lista de espera | tab32, Dentally, NexHealth, Open Dental (ASAP, add-on) | **15% (4)**; **ningún argentino verificable** | En Argentina solo la mencionan XIAgenda y Doctoralia, ambos de baja confianza. |
| Agenda por sillón / box / operatorio | AgendaPro, ClinIA (AC), tab32 (AC) | **12% (3)** | Escasa, pese a que el sillón es el recurso escaso real del consultorio odontológico. |
| Facturación ARCA | MedicAI, YoFacturo, ClinIA (AC) | **12% (3)**; en verticales dentales: 1 (ClinIA, AC) | **Vacío principal.** |
| Exportación de datos | MedicAI (FHIR), YoFacturo (Premium), DentalSoft (Excel/PDF, backup) | **12% (3)** | Escasa: en el resto hay un lock-in implícito. |
| Reprogramación o cancelación por el paciente | DentalSoft (cancelación por portal), Bilog (AC), ClinIA (AC) | **12% (3)** | Escasa y casi siempre AC. |
| Normativa argentina declarada (Leyes 25.326 / 26.529) | AgendaPro (AC), MedicAI (AC) | **8% (2)** | Casi nadie la menciona y nadie la acredita. |
| Sobreturnos con control de solapamiento | DentalSoft | **4% (1)** | Simples Dental declara "sin conflictos de horario" (AC). |

### C.2 Funcionalidades diferenciadoras reales (poco frecuentes)

Se consideran diferenciadoras las funciones presentes en 3 sistemas o menos que tienen valor para el usuario argentino. Se indica el nivel de evidencia de cada una.

| Diferenciador | Quién lo tiene | Evidencia | Por qué importa |
|---|---|---|---|
| Sobreturnos con detección de solapamiento | DentalSoft | Sí | Refleja la operación real del consultorio argentino (urgencias, sobreturnos). |
| Agenda por sillón con reporte de ocupación de sillones | AgendaPro | Sí | Mide la productividad del activo más caro. |
| Recordatorio con reinsistencia si el paciente no confirma | AgendaPro | Sí | Ataca el ausentismo sin trabajo manual. |
| Seguimiento automático de presupuestos pendientes | AgendaPro | Sí | Convierte presupuestos en tratamientos, es decir, en ingreso directo. |
| Auditoría inmutable (15 años) y exportación completa en FHIR | MedicAI | Sí | Cumplimiento de la Ley 26.529 (historia clínica) y portabilidad; argumento de confianza. |
| Anonimización antes de enviar datos a la IA | MedicAI | AC | Diferencial de privacidad en un mercado que usa IA sin explicar qué hace con los datos. |
| Reclamos a obras sociales y estadística de ingresos por OS | MedicAI | Sí | Gestión de la cobranza a financiadores. |
| Facturación ARCA nativa y cuenta corriente de OS desde la agenda | YoFacturo | Sí | Une turno, prestación y factura. |
| Integraciones estatales: RENAPER, PUCO, ReNaPDiS (recetario), PAMI, SUMAR+ | ClinIA | AC | Valida identidad y cobertura en el alta, lo que reduce rechazos de las OS. |
| Odontograma versionado con comparación entre fechas | ClinIA (AC), Klinicos (AC); DentalSoft (historial, Sí) | Mixta | Hace visible la evolución clínica; respaldo médico-legal. |
| Auditoría prestacional para obras sociales y gerenciadoras | Bilog | AC | Abre un segundo segmento de clientes: los financiadores. |
| Módulo argentino de OS con conciliaciones y receta electrónica dentro de un vertical dental regional | Dentalink | Sí (help center) | Muestra que un actor regional invierte en localización argentina. |
| Plan gratis permanente con comisión decreciente por cobro | Turnito | Sí | Modelo de adopción sin barrera de entrada. |
| Precio publicado por sillón | Dentally | Sí | Precio alineado con el tamaño real de la clínica, no con la cantidad de profesionales. |
| Tarifa diferenciada por país ("otros países", "en desarrollo") | Open Dental | Sí | Precio adaptado al poder de compra local. |
| Periodontograma por voz | tab32, Oryx, Denticon | AC | Carga manos libres durante la consulta. |
| Agentes IA de llamadas que reagendan pacientes vencidos | Denticon | AC | Recall automatizado de punta a punta. |
| Lista de espera con oferta automática de turnos liberados | XIAgenda (baja confianza); lista de espera simple en tab32, Dentally y NexHealth | D / AC | Recupera huecos que dejan las cancelaciones. |
| Check-in por QR y app del paciente | Clinicorp | AC | Reduce la carga de la recepción. |
| Programa de referidos y marketing integrado | Simples Dental | AC | Captación de pacientes desde el mismo sistema. |
| Certificaciones publicadas (SOC 2 Type 2, ISO 27001:2022) | CareStack (y Dentalink con ISO 27001, AC) | AC | Señal de madurez en seguridad que ningún argentino ofrece. |
| API pública documentada con alta gratuita | NexHealth | AC | Habilita un ecosistema de integradores. |

### C.3 Vacíos frecuentes del mercado argentino

Ordenados por gravedad (impacto en el consultorio × rareza en la oferta):

1. **Facturación ARCA integrada en un vertical dental.** Ningún vertical dental argentino la documenta (ClinIA la menciona solo como AC). Los que la documentan, MedicAI y YoFacturo, no son dentales. Hoy el odontólogo debe elegir entre lo clínico y lo fiscal, o usar dos sistemas.
2. **Obras sociales con liquidación real.** El 78% de los argentinos menciona obras sociales, pero con alcances muy distintos. YoFacturo aclara que **no calcula aranceles ni se integra con financiadores**; MedicAI liquida solo en forma parcial; Bilog y ClinIA lo afirman sin detalle. Solo DentalSoft documenta liquidaciones por OS y por profesional. Ningún sistema evidencia un nomenclador odontológico parametrizable por OS con validación de cobertura antes de la prestación (ClinIA declara PUCO como AC).
3. **Periodontograma.** Ningún nativo argentino lo evidencia; en la región solo lo declaran Dentalink (Chile) y Klinicos (Uruguay).
4. **Lista de espera y recuperación de huecos.** Ningún sistema argentino verificable la tiene (solo XIAgenda y Doctoralia, de baja confianza). Tampoco aparecen en ningún argentino el seguimiento de ausentes ni el recall de controles periódicos.
5. **Sobreturnos, sillones y solapamientos.** Solo DentalSoft documenta sobreturnos con control de solapamiento y solo AgendaPro documenta agenda por sillón. Ninguno documenta ambas cosas.
6. **Autogestión del paciente (reprogramar o cancelar).** Solo hay casos parciales (portal de cancelación de DentalSoft) o AC (Bilog, ClinIA). La reserva online es estándar; lo que pasa después de reservar no lo es.
7. **Trazabilidad, auditoría y exportación.** Solo MedicAI, que no es dental, documenta auditoría inmutable y exportación completa. En el vertical dental argentino, solo DentalSoft ofrece exportación (Excel/PDF): el resto implica un **lock-in** de hecho.
8. **Normativa argentina explícita.** Solo AgendaPro y MedicAI mencionan las Leyes 25.326 y 26.529, ambos como AC y sin auditoría externa. Ningún vertical dental argentino publica una política de seguridad detallada.
9. **Precios transparentes en el vertical dental.** Bilog, ClinIA y Dentalink trabajan por cotización y AgendaPro publica solo un "desde". DentalSoft es el único que publica todos sus planes, y cobra **por profesional**, lo que penaliza a las clínicas con varios odontólogos.
10. **Mercado Pago en el vertical dental.** Solo DentalSoft y AgendaPro lo documentan; Bilog, ClinIA y Dentalink no lo evidencian.
11. **Costo de WhatsApp opaco o adicional.** DentalSoft traslada el costo de Meta, MedicAI lo vende como add-on (ARS 19.900 a 29.900 + IVA) y Turnito lo limita por plan. Solo Klinicos (Uruguay) lo incluye desde el plan básico.
12. **Evidencia de adopción verificable.** No hay reseñas independientes accesibles. Las cifras de clientes son autodeclaradas (Bilog 1.500+, DentalSoft 300+) o no existen (MedicAI, ClinIA, Turnito). Como dato de contexto **no verificado** (citado por buscador en la ficha de Doctoralia), una tesis de UdeSA estimaría unos 70.000 odontólogos en Argentina, de los cuales ~3% usaría software de gestión. Si se confirma, el mercado está muy poco penetrado.

> **Nota de marco regulatorio (2026-11-20).** Fuera del alcance de la investigación de mercado, al armar la Knowledge Base se relevó el marco normativo de referencia y se verificó su existencia en fuentes oficiales: Ley 25.326 y su Decreto reglamentario 1558/2001; Decreto 746/2017 (la Agencia de Acceso a la Información Pública pasa a ser el órgano de control de la ley de datos personales); Resolución AAIP 47/2018 (medidas de seguridad recomendadas); Ley 27.483 (aprueba el Convenio 108 del Consejo de Europa) y Ley 26.529 (derechos del paciente e historia clínica). No hay revisión profesional de su aplicación al producto.

### C.4 Oportunidades de innovación para un nuevo sistema

Cada oportunidad indica qué vacío de C.3 la justifica.

1. **"Del turno a la factura" en un solo flujo dental (vacíos 1, 2 y 10).** Turno → prestación cargada en el odontograma → cálculo del copago o arancel según la OS → cobro por MP o caja → factura ARCA con CAE. Hoy nadie combina odontograma, OS, MP y ARCA documentados. Es la propuesta de valor más defendible.
2. **Motor de obras sociales con nomenclador parametrizable (vacío 2).** Nomenclador por financiador, topes y autorizaciones, verificación de cobertura (PUCO) al dar el turno y liquidación mensual exportable. Bilog (AC) y Dentalink (Sí) muestran que hay demanda, pero ninguno documenta el ciclo completo.
3. **Agenda odontológica completa (vacío 5).** Agenda por profesional **y** por sillón, con sobreturnos visibles, detección de solapamiento, bloqueos y duración por práctica. Junta lo que hoy está repartido entre DentalSoft y AgendaPro.
4. **Recuperación de huecos y recall (vacío 4).** Lista de espera que ofrece automáticamente por WhatsApp el turno liberado, recall de controles (6 o 12 meses, profilaxis, ortodoncia) y seguimiento de ausentes. Son funciones estándar en EEUU (tab32, Dentrix, Open Dental) y ausentes en Argentina.
5. **Autogestión completa del paciente por WhatsApp (vacío 6).** Confirmar, cancelar o reprogramar con un mensaje o un link, sin crear cuenta (modelo Turnito), con identificación por DNI (modelo DentalSoft) y con seña por MP para los turnos largos.
6. **Periodontograma y odontograma versionado de serie (vacío 3).** Incluidos en el plan base, con comparación entre fechas y PDF para el paciente o la OS.
7. **La confianza como parte del producto (vacíos 7 y 8).** Log de auditoría inmutable, exportación completa (FHIR o CSV) en todos los planes, política explícita sobre las Leyes 25.326/26.529, 2FA y una página pública de seguridad. Es la estrategia de MedicAI, que nadie aplica en el vertical dental.
8. **Precio transparente por sillón o por clínica, con WhatsApp incluido (vacíos 9 y 11).** Precios en ARS publicados, sin costo por usuario, un cupo de mensajes de WA incluido y un plan de entrada gratuito o barato. Hoy los dentales cobran por profesional (DentalSoft) o no publican precios.
9. **Embudo de presupuestos (lleva más lejos el diferenciador de AgendaPro).** Presupuesto por link con aceptación online, seña por MP y seguimiento automático, más un indicador de conversión de presupuesto a tratamiento por profesional.
10. **IA con privacidad explícita.** La IA ya es un argumento estándar (77% de los sistemas) y nadie verificó su eficacia. La oportunidad no es "tener IA" sino aplicarla a tareas medibles (cargar turnos por WhatsApp, resumir la evolución clínica) con anonimización declarada, como hace MedicAI.

---

## D. Recomendación final

### D.1 Cinco competidores prioritarios para analizar mediante demo

1. **DentalSoft.** Primero en la matriz (3,65) y el vertical dental argentino mejor documentado: sobreturnos, liquidaciones por OS, MP y precios públicos. Es el benchmark directo de funcionalidad y precio. En la demo, verificar periodontograma, ARCA, el flujo real de liquidación por OS, la calidad del odontograma y la reserva por DNI.
2. **AgendaPro Argentina.** Segundo en la matriz (3,40), con el mejor flujo de turnos documentado (sillón, reinsistencia, seguimiento de presupuestos, MP) y la mayor escala declarada entre los actores regionales. En la demo, verificar obras sociales, ARCA, sobreturnos y el precio real de cada plan más allá del "desde ARS 13.900".
3. **ClinIA – OdontoClinIA.** Declara el set de integraciones argentinas más completo (RENAPER, PUCO, ARCA, ReNaPDiS, PAMI) y odontograma versionado, pero **todo es AC**. La demo de 45 minutos que ofrece el proveedor es indispensable para separar realidad de marketing. Si las afirmaciones se confirman, es el competidor más peligroso en el segmento institucional.
4. **Bilog.** Es el actor con mayor trayectoria declarada (20+ años, 1.500+ clínicas) y el único con foco explícito en auditoría para obras sociales y gerenciadoras. Verificar caja, MP, ARCA, precios y seguridad, que su sitio no evidencia. Es clave para entender qué espera el cliente que ya usa software.
5. **Dentalink.** Es el único actor regional con **localización argentina comprobada** (help center con OS, conciliaciones y receta), y declara periodontograma e ISO 27001. Representa la amenaza de un jugador regional con escala que se adapta al mercado local. Verificar precio para Argentina, ARCA y MP.

*Fuera del top 5, pero recomendable revisar rápido:* **MedicAI**, con su cuenta de prueba de 30 días sin tarjeta, para estudiar su modelo de auditoría, ARCA y obras sociales; y **DentalCore** (baja confianza), para confirmar si existe un competidor directo que combine OS, MP y facturación.

### D.2 Tres productos de referencia para experiencia de usuario

> Advertencia: sin demos, estas referencias se basan en los flujos que describen los proveedores, no en la interfaz observada. Conviene validarlas en la etapa de demo o prueba.

1. **Turnito, para una reserva sin fricción.** Copiar la página de reserva propia, compartible por WhatsApp, Instagram o web, **sin que el paciente cree una cuenta**; las reservas recurrentes; el cobro de la seña por MP en el mismo paso; y la forma de mostrar precios en ARS, con plan gratuito y escalones claros.
2. **AgendaPro, para la operación diaria de la recepción.** Copiar la agenda por profesional y por sillón; el recordatorio por WhatsApp de 1 a 3 días antes **con reinsistencia** si no hay confirmación; el envío del presupuesto por WhatsApp con seguimiento; y los reportes de ocupación de sillones.
3. **MedicAI, para la confianza y el control del dato.** Copiar la trazabilidad visible (registro inmutable y auditoría de quién hizo qué), la exportación completa de los datos por parte del profesional (FHIR), la firma de recetas con segundo factor y la explicación clara de cómo se tratan los datos que pasan por la IA. Es la referencia para que el producto "se sienta seguro" ante el odontólogo.

*Referencias secundarias:* Dentally (precio por sillón; portal del paciente según plan), ClinIA y Klinicos (odontograma versionado con comparación entre fechas) y tab32 (lista de espera y columnas por operatorio).

### D.3 MVP sugerido

> **Nota de alcance confirmado (2026-11-20).** El equipo confirmó que la v1 del producto es deliberadamente más modesta que el MVP que sugería este informe: solo la agenda de un odontólogo (con reserva pública por link, auditoría, exportación y precios públicos), sin multi-profesional, sin secretaria/recepción y sin roles. Es una decisión intencional, no un descuido. El 2026-11-20 se movió a "Etapas posteriores" todo imprescindible que la v1 no cubre (agenda por sillón y multiprofesional, WhatsApp, historia clínica, presupuestos, caja con Mercado Pago, obras sociales, roles), conservando su evidencia; también quedan fuera de la v1 por decisión del usuario el botón de compartir por WhatsApp y el 2FA. El análisis competitivo y los puntajes (secciones A a C, D.1 y D.2) no se modifican. Nota honesta: el mercado sigue considerando estándar lo que se difiere (riesgo SU-02).

#### Imprescindibles (sin esto no se compite)

*Cubiertos por la v1.*

- **Agenda de un odontólogo, con bloqueos, duración por práctica, sobreturnos y detección de solapamiento.** Es la operación central del consultorio y hoy está repartida entre DentalSoft (sobreturnos) y AgendaPro (sillones). La agenda por sillón y multiprofesional pasa a la primera etapa posterior.
- **Reserva online por link, sin cuenta del paciente (reservar, reprogramar y cancelar).** Es estándar (88%); la diferencia está en minimizar la fricción. El botón para compartir el link por WhatsApp pasa a la primera etapa posterior.
- **Auditoría de cambios en turnos y configuración, visible para el profesional.** Es un vacío del mercado (vacío 7) y la base de la transparencia. El log de auditoría de la HC y el odontograma, y los roles y permisos, pasan a la primera etapa posterior.
- **Exportación completa de datos (JSON, CSV y PDF) en todos los planes.** Elimina el lock-in que hoy impone el mercado (solo el 12% exporta).
- **Precios públicos en ARS, con plan de entrada y prueba sin tarjeta.** En el vertical dental solo DentalSoft lo hace; reduce la fricción de venta.

#### Diferenciadores del MVP (para ganarle a DentalSoft y AgendaPro)

- **Facturación ARCA (CAE) emitida desde el cobro del turno.** Es el vacío principal: ningún vertical dental lo documenta.
- **Lista de espera que ofrece automáticamente por WhatsApp los turnos liberados.** Ningún argentino verificable la tiene, y recupera ingresos que hoy se pierden por cancelaciones.
- **Autogestión del paciente: confirmar, cancelar y reprogramar desde WhatsApp o un link.** En Argentina solo existe en forma parcial o como AC.
- **Periodontograma incluido en el plan base.** Ningún nativo argentino lo evidencia.
- **Seguimiento automático de presupuestos pendientes, con indicador de conversión.** Solo AgendaPro lo documenta, y tiene impacto directo en la facturación.
- **Página pública de seguridad y cumplimiento (Leyes 25.326/26.529), con auditoría inmutable y 2FA (2FA diferido, DD-18).** Ningún vertical dental argentino lo comunica; convierte la confianza en argumento de venta.
- **Precio por sillón o por clínica, sin costo por usuario.** Contrasta con el precio por profesional de DentalSoft y MedicAI.

#### Etapas posteriores

##### Primera etapa posterior a la v1 (imprescindibles del mercado diferidos por alcance)

Son imprescindibles según este informe que la v1 no cubre, por decisión confirmada del equipo (2026-11-20). Se listan por prioridad sugerida.

- **Recordatorios y confirmación automática por WhatsApp, con reinsistencia y un cupo de mensajes incluido.** WA está en el 87% de la oferta regional, e incluir el cupo resuelve el costo opaco (vacío 11). Incluye el botón para compartir por WhatsApp el link de reserva pública (diferido por el usuario).
- **Ficha clínica, historia clínica y odontograma FDI con historial.** Es requisito de entrada en el vertical dental (89% de los dentales lo tiene). Incluye el log de auditoría de cambios en la HC y el odontograma (Ley 26.529, vacío 7).
- **Presupuestos y plan de tratamiento vinculados al odontograma y enviados por link.** Es la base del ingreso, y solo el 31% lo tiene.
- **Caja con cierre diario, cuenta corriente del paciente y cobro por Mercado Pago (seña o total).** Solo 2 verticales dentales argentinos documentan MP.
- **Obras sociales en nivel base: padrón de coberturas por paciente, registro de prestaciones por OS y reporte mensual de liquidación exportable.** El 78% de los argentinos lo menciona; no tenerlo descalifica.
- **Agenda multiprofesional y por sillón, y roles y permisos (odontólogo, recepción, administrador).** La agenda por sillón es la operación central de clínicas con varios boxes (AgendaPro); los roles los exige la Ley 26.529 cuando hay más de un usuario.

##### Etapas posteriores restantes

- **Recall de controles periódicos y seguimiento de ausentes.** Estándar en EEUU y ausente en Argentina, pero necesita historial de turnos acumulado.
- **Motor avanzado de obras sociales: nomenclador parametrizable, autorizaciones, verificación por PUCO y conciliación.** De alto valor, pero complejo y distinto para cada financiador; conviene validarlo con clientes.
- **Receta electrónica (registro en ReNaPDiS).** Requiere un trámite regulatorio propio; ClinIA y MedicAI tienen su recetario registrado.
- **Asistente conversacional con IA en WhatsApp para dar turnos.** Ya es un argumento común (AgendaPro, Bilog, ClinIA, Dentalink, Doctocliq) sin eficacia verificada; conviene sumarlo con una política de privacidad explícita.
- **Imágenes y radiografías con visor, y más adelante IA de apoyo.** Es frecuente (54%), pero el almacenamiento es costoso.
- **Multisede avanzada con tablero consolidado.** La necesitan clínicas y cadenas, no el consultorio inicial.
- **App nativa del paciente, check-in por QR (modelo Clinicorp) y portal propio.** Mejora la experiencia, pero WhatsApp cubre la necesidad inicial.
- **API pública documentada e integraciones (Google Calendar, laboratorios, radiología).** Construye ecosistema; NexHealth es la referencia.
- **Integración con RENAPER, módulos PAMI y SUMAR+, y auditoría prestacional para gerenciadoras.** Abren los segmentos institucional y de financiadores (Bilog, ClinIA).
- **Marketing: referidos, campañas, Google Reserve.** Sirve para captar pacientes (Simples Dental, AgendaPro), pero es secundario frente a la operación.

---

## Anexo 1. Fuentes consultadas

Todas se consultaron el **2026-11-16**, salvo indicación. Se aclara el estado cuando la fuente no fue accesible o no se leyó.

**Argentina, nativos**
- Bilog: https://bilog.com.ar/ ; https://bilog.com.ar/soluciones/consultorios ; citadas pero no abiertas: https://bilog.com.ar/solicitar-demo , https://bilog.com.ar/soluciones/auditoria-odontologica
- DentalSoft: https://dentalsoft.com.ar/ ; https://www.dentalsoft.com.ar/funcionalidades ; reseñas de Google: https://search.google.com/local/reviews?placeid=ChIJdQaKm4DsuA8RpY_BWaWdZuw ; GetApp (no abierto, posible homónimo): https://www.getapp.com/all-software/a/dentalsoft/
- OdontoGRAMA: https://odontograma.com.ar/
- ClinIA: https://www.clinia.com.ar/odontologia ; https://www.clinia.com.ar/recursos/mejor-software-gestion-clinicas-argentina (fuente de parte interesada)
- DentalCore: https://dentalcore.app/ (inaccesible, ECONNREFUSED) ; https://www.capterra.com/p/10047210/Dentalcore/ (403) ; búsqueda web
- ZYRA: https://www.appzyra.ar/ (SPA; solo devolvió el título) ; búsqueda web

**Plataformas de turnos de salud con presencia en Argentina**
- AgendaPro: https://agendapro.com/ar/dental/software-odontologico ; https://agendapro.com/ar/agenda-medica ; existente pero no leída: https://agendapro.com/ar/centro-medico/software-para-centro-medico
- Turnito: https://turnito.app
- MedicAI: https://www.medicai.com.ar ; https://alternativeto.net/software/medicai-argentina/about/ ; https://www.capterra.es/software/1098851/MedicAI (403, solo snippet)
- YoFacturo: https://yo-facturo.com/clinicas/ ; https://yo-facturo.com/pricing/ ; https://yo-facturo.com/blog/sistema-gestion-clinica-medica/
- XIAgenda: https://www.comparasoftware.com/xiagenda ; https://www.capterra.co.il/software/1109063/XIAgenda (403)
- Doctoralia: https://www.doctoralia.com.ar (inaccesible) ; https://pabau.com/es/blog/precios-de-doctoralia-pro/ (tercero competidor; datos de España) ; App Store (no leída): https://apps.apple.com/es/app/doctoralia-para-especialistas/id1237598188

**Latinoamérica**
- Dentalink: https://www.softwaredentalink.com ; https://web-es.softwaredentalink.com/en/planes ; https://ayuda.softwaredentalink.com/es/collections/19196578-modulo-exclusivo-para-argentina ; https://www.capterra.com/p/196361/Dentalink/ (403)
- Klinicos: https://klinicos.app ; búsqueda: publicación de LinkedIn (uy.linkedin.com/in/iara-montero-778a151b4)
- Clinicorp: https://www.clinicorp.com ; https://www.comparasoftware.com/clinicorp
- Dentidesk: https://dentidesk.com ; https://www.capterra.es/software/184029/dentidesk (solo listado en búsqueda)
- Simples Dental: https://www.simplesdental.com
- Doctocliq: https://doctocliq.com ; https://www.comparasoftware.com/doctocliq ; https://pabau.com/es/blog/precios-de-doctocliq/ (tercero competidor)
- Clinic Cloud: https://www.clinic-cloud.com/

**Internacionales**
- Open Dental: https://www.opendental.com/ ; https://opendental.com/site/order.html ; https://opendental.com/site/fees.html ; Software Advice (aparecido en búsqueda, no verificado): https://www.softwareadvice.com/dental/open-dental-profile/
- Ascend by Dentrix: https://www.dentrixascend.com/
- Curve Dental: https://www.curvedental.com/
- Denticon (Planet DDS): https://www.planetdds.com/denticon/
- CareStack: https://www.carestack.com/
- tab32: http://tab32.com/
- Oryx Dental: https://www.oryxdental.com/
- Dentally: https://www.dentally.com/en-gb/ ; https://www.dentally.com/en-gb/pricing
- NexHealth: https://www.nexhealth.com/
- Weave: https://www.getweave.com/
- Adit: https://www.adit.com/

## Anexo 2. Sistemas buscados y descartados

Los investigadores buscaron y descartaron los siguientes sistemas por al menos una de estas causas: no se halló sitio oficial; estaban fuera del alcance (otro país sin presencia argentina, otro rubro o producto discontinuado); o no eran verificables. Las fichas no documentan la causa individual de cada descarte, así que no se atribuye una causa específica a cada uno.

| Sistema | Sistema | Sistema |
|---|---|---|
| Alfa Gestión | GO | Dentalware |
| OdontoCloud | ConsultorioDental | Dentis |
| OdontoSoft | ODONTSIS | Medilink |
| Saludtools | Medesk | Reservo |
| Wedoc | Alephoo | MediCloud |
| Clinicsoft | SoloTurnos | Pedí Tu Turno |
| Órbita | MrTurno | Gesden |
| Dentalplus | Kolibri | CloudDent 360 |
| Dendoo | XDental | Apolonia |
| Odontonet | Dently | Odeen |

Total de descartados: 30. Además, cuatro sistemas se **incluyeron con baja confianza** en lugar de descartarse (DentalCore, ZYRA, XIAgenda y Doctoralia Argentina; ver A.3). Conviene verificarlos manualmente con un navegador antes de cerrar el mapa competitivo.
