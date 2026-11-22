/**
 * ARCHIVO GENERADO: no editar a mano.
 * Fuente: contrato OpenAPI del backend (/v3/api-docs). Regenerar con `npm run api:types`.
 */
export interface paths {
    "/api/v1/prestaciones/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["consultar"];
        put: operations["editar"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/disponibilidad": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["consultar_1"];
        put: operations["reemplazar"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/consultorio": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["consultar_2"];
        put: operations["editar_1"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/turnos": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["crear"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/turnos/{id}/visto": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["visto"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/turnos/{id}/reprogramar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["reprogramar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/public/turnos/{token}/reprogramacion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Mover el turno a otro hueco */
        post: operations["reprogramar_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/public/turnos/{token}/cancelacion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Cancelar el turno */
        post: operations["cancelar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/public/consultorios/{slug}/turnos": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Reservar un turno sin cuenta; el link de gestión se devuelve una sola vez */
        post: operations["reservar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/prestaciones": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["listar"];
        put?: never;
        post: operations["crear_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/prestaciones/{id}/desactivar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["desactivar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/prestaciones/{id}/activar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["activar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/exportacion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Descarga todos los datos del consultorio en un ZIP */
        post: operations["exportar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/bloqueos": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["listar_1"];
        put?: never;
        post: operations["crear_2"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/auth/registro": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["registrar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/auth/refresh": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["refresh"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/auth/recuperacion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["solicitar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/auth/recuperacion/confirmar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["confirmar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/auth/logout": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["logout"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/auth/login": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["login"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/auth/cambiar-password": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["cambiar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/turnos/{id}/nota": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch: operations["nota"];
        trace?: never;
    };
    "/api/v1/turnos/{id}/estado": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch: operations["cambiarEstado"];
        trace?: never;
    };
    "/api/v1/turnos/{id}/duracion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch: operations["duracion"];
        trace?: never;
    };
    "/api/v1/turnos/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["detalle"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/turnos/{id}/historial": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Eventos de un turno, en orden cronológico */
        get: operations["historial"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/turnos/fuera-de-disponibilidad": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["fueraDeDisponibilidad"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/public/turnos/{token}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Ver el turno */
        get: operations["ver"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/public/turnos/{token}/huecos": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Huecos para reprogramar, sin contar el propio turno */
        get: operations["huecos"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/public/planes": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["planes"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/public/consultorios/{slug}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Página pública del consultorio */
        get: operations["pagina"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/public/consultorios/{slug}/huecos": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Huecos ofrecibles para una prestación, hasta 31 días */
        get: operations["huecos_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/plan": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["consultar_3"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/pacientes": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["buscar"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/auditoria": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Registro de cambios del consultorio, del más nuevo al más viejo */
        get: operations["registro"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/agenda": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["agenda"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/bloqueos/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete: operations["eliminar"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        PrestacionRequest: {
            nombre: string;
            /** Format: int32 */
            duracionMin: number;
            visiblePublica?: boolean;
            /** Format: int32 */
            orden?: number;
        };
        PrestacionResponse: {
            /** Format: uuid */
            id?: string;
            nombre?: string;
            /** Format: int32 */
            duracionMin?: number;
            activa?: boolean;
            visiblePublica?: boolean;
            /** Format: int32 */
            orden?: number;
        };
        FranjaDto: {
            /** Format: int32 */
            diaSemana: number;
            horaInicio: string;
            horaFin: string;
        };
        GrillaRequest: {
            franjas: components["schemas"]["FranjaDto"][];
        };
        FranjaResponse: {
            /** Format: int32 */
            diaSemana?: number;
            horaInicio?: string;
            horaFin?: string;
        };
        GrillaResponse: {
            franjas?: components["schemas"]["FranjaResponse"][];
        };
        ConfiguracionRequest: {
            nombre: string;
            slug: string;
            direccion?: string;
            telefonoContacto: string;
            zonaHoraria: string;
            /** Format: int32 */
            anticipacionCancelacionMin: number;
            /** Format: int32 */
            anticipacionReservaMin: number;
            /** Format: int32 */
            horizonteReservaDias: number;
            /** Format: int32 */
            pasoHuecosMin: number;
            reservaPublicaActiva: boolean;
            /** Format: int64 */
            version: number;
        };
        ConfiguracionResponse: {
            nombre?: string;
            slug?: string;
            direccion?: string;
            telefonoContacto?: string;
            zonaHoraria?: string;
            /** Format: int32 */
            anticipacionCancelacionMin?: number;
            /** Format: int32 */
            anticipacionReservaMin?: number;
            /** Format: int32 */
            horizonteReservaDias?: number;
            /** Format: int32 */
            pasoHuecosMin?: number;
            reservaPublicaActiva?: boolean;
            plan?: string;
            /** Format: date */
            pruebaHasta?: string;
            /** Format: int64 */
            version?: number;
        };
        CrearTurnoRequest: {
            /** Format: uuid */
            pacienteId?: string;
            pacienteNuevo?: components["schemas"]["PacienteNuevoRequest"];
            /** Format: uuid */
            prestacionId: string;
            /** Format: date-time */
            inicio: string;
            /** Format: int32 */
            duracionMin?: number;
            esSobreturno?: boolean;
        };
        PacienteNuevoRequest: {
            nombre: string;
            apellido: string;
            dni: string;
            telefono: string;
            email?: string;
        };
        DetalleResponse: {
            /** Format: uuid */
            id?: string;
            /** Format: date-time */
            inicio?: string;
            /** Format: date-time */
            fin?: string;
            /** Format: int32 */
            duracionMin?: number;
            /** @enum {string} */
            estado?: "RESERVADO" | "CONFIRMADO" | "CANCELADO" | "AUSENTE";
            esSobreturno?: boolean;
            /** @enum {string} */
            origen?: "LINK" | "MANUAL";
            prestacion?: components["schemas"]["PrestacionResumen"];
            paciente?: components["schemas"]["PacienteDelDetalle"];
            tieneNota?: boolean;
            notaInterna?: string;
            /** @enum {string} */
            canceladoPor?: "ODONTOLOGO" | "PACIENTE";
            motivoCancelacion?: string;
            /** @enum {string} */
            cambioPaciente?: "RESERVO" | "REPROGRAMO" | "CANCELO";
            /** Format: date-time */
            novedadPacienteAt?: string;
            horarioAnterior?: components["schemas"]["Horario"];
            /** Format: int64 */
            version?: number;
        };
        Horario: {
            /** Format: date-time */
            inicio?: string;
            /** Format: date-time */
            fin?: string;
        };
        PacienteDelDetalle: {
            /** Format: uuid */
            id?: string;
            nombre?: string;
            apellido?: string;
            dni?: string;
            telefono?: string;
            email?: string;
            requiereRevision?: boolean;
        };
        PrestacionResumen: {
            /** Format: uuid */
            id?: string;
            nombre?: string;
        };
        TurnoCreadoResponse: {
            turno?: components["schemas"]["DetalleResponse"];
            pacienteReutilizado?: boolean;
        };
        VistoRequest: {
            /** Format: date-time */
            novedadAt: string;
        };
        ReprogramarRequest: {
            /** Format: date-time */
            inicio: string;
            /** Format: int64 */
            version: number;
        };
        PedidoDeReprogramacion: {
            /** Format: date-time */
            nuevoInicio: string;
        };
        ConsultorioVisible: {
            nombre?: string;
            direccion?: string;
            telefono?: string;
            zonaHoraria?: string;
        };
        PacienteVisible: {
            nombre?: string;
            apellido?: string;
        };
        TurnoVisible: {
            /** Format: date-time */
            inicio?: string;
            /** Format: date-time */
            fin?: string;
            prestacion?: string;
            estado?: string;
            canceladoPor?: string;
        };
        VistaDeTurno: {
            turno?: components["schemas"]["TurnoVisible"];
            paciente?: components["schemas"]["PacienteVisible"];
            consultorio?: components["schemas"]["ConsultorioVisible"];
            puedeModificar?: boolean;
            puedeReprogramar?: boolean;
            /** Format: date-time */
            modificableHasta?: string;
        };
        PedidoDeReservaDto: {
            /** Format: uuid */
            prestacionId: string;
            /** Format: date-time */
            inicio: string;
            nombre: string;
            apellido: string;
            dni: string;
            telefono: string;
            email?: string;
            aceptaPrivacidad?: boolean;
        };
        ReservaCreada: {
            turno?: components["schemas"]["TurnoReservado"];
            linkGestion?: string;
        };
        TurnoReservado: {
            /** Format: uuid */
            id?: string;
            /** Format: date-time */
            inicio?: string;
            /** Format: date-time */
            fin?: string;
            prestacion?: string;
            estado?: string;
        };
        ProblemDetail: {
            /** Format: uri */
            type?: string;
            title?: string | null;
            /** Format: int32 */
            status?: number;
            detail?: string | null;
            /** Format: uri */
            instance?: string | null;
            properties?: {
                [key: string]: unknown;
            } | null;
        };
        CrearBloqueoRequest: {
            /** Format: date-time */
            inicio: string;
            /** Format: date-time */
            fin: string;
            motivo?: string;
        };
        BloqueoResponse: {
            /** Format: uuid */
            id?: string;
            /** Format: date-time */
            inicio?: string;
            /** Format: date-time */
            fin?: string;
            motivo?: string;
        };
        RegistroRequest: {
            nombre: string;
            apellido: string;
            /** Format: email */
            email: string;
            password?: string;
            nombreConsultorio: string;
            aceptaTerminos?: boolean;
        };
        RegistroResponse: {
            /** Format: uuid */
            usuarioId?: string;
            /** Format: uuid */
            consultorioId?: string;
            slug?: string;
        };
        SolicitudRequest: {
            email?: string;
        };
        ConfirmacionRequest: {
            token?: string;
            password?: string;
        };
        LoginRequest: {
            email?: string;
            password?: string;
        };
        SesionResponse: {
            accessToken?: string;
            /** Format: int64 */
            expiraEn?: number;
        };
        CambioRequest: {
            passwordActual?: string;
            passwordNueva?: string;
        };
        NotaRequest: {
            nota?: string;
            /** Format: int64 */
            version: number;
        };
        CambiarEstadoRequest: {
            /** @enum {string} */
            accion: "CONFIRMAR" | "CANCELAR" | "MARCAR_AUSENTE";
            motivo?: string;
            /** Format: int64 */
            version: number;
        };
        DuracionRequest: {
            /** Format: int32 */
            duracionMin: number;
            /** Format: int64 */
            version: number;
        };
        ActorResponse: {
            tipo?: string;
            /** Format: uuid */
            id?: string;
            nombre?: string;
            identidadEnRevision?: boolean;
        };
        ContextoResponse: {
            /** Format: date-time */
            inicio?: string;
            paciente?: string;
        };
        EventoResponse: {
            /** Format: uuid */
            id?: string;
            /** Format: date-time */
            ocurridoAt?: string;
            actor?: components["schemas"]["ActorResponse"];
            accion?: string;
            entidad?: string;
            /** Format: uuid */
            entidadId?: string;
            valorAnterior?: {
                [key: string]: unknown;
            };
            valorNuevo?: {
                [key: string]: unknown;
            };
            ip?: string;
            contexto?: components["schemas"]["ContextoResponse"];
        };
        PacienteResumen: {
            /** Format: uuid */
            id?: string;
            nombre?: string;
            apellido?: string;
        };
        TurnoResponse: {
            /** Format: uuid */
            id?: string;
            /** Format: date-time */
            inicio?: string;
            /** Format: date-time */
            fin?: string;
            /** Format: int32 */
            duracionMin?: number;
            /** @enum {string} */
            estado?: "RESERVADO" | "CONFIRMADO" | "CANCELADO" | "AUSENTE";
            esSobreturno?: boolean;
            /** @enum {string} */
            origen?: "LINK" | "MANUAL";
            prestacion?: components["schemas"]["PrestacionResumen"];
            paciente?: components["schemas"]["PacienteResumen"];
            tieneNota?: boolean;
            /** @enum {string} */
            canceladoPor?: "ODONTOLOGO" | "PACIENTE";
            /** @enum {string} */
            cambioPaciente?: "RESERVO" | "REPROGRAMO" | "CANCELO";
            /** Format: date-time */
            novedadPacienteAt?: string;
            /** Format: int64 */
            version?: number;
        };
        Dia: {
            /** Format: date */
            fecha?: string;
            atiende?: boolean;
            huecos?: string[];
        };
        HuecosPublicos: {
            zonaHoraria?: string;
            dias?: components["schemas"]["Dia"][];
        };
        PlanPublicoResponse: {
            id: string;
            nombre: string;
            descripcion?: string;
            incluye: string[];
            importeARS: string;
            moneda: string;
            impuestosIncluidos: boolean;
        };
        PlanesPublicosResponse: {
            planes: components["schemas"]["PlanPublicoResponse"][];
            prueba: components["schemas"]["PruebaResponse"];
            /** Format: date-time */
            actualizadoEn: string;
        };
        PruebaResponse: {
            /** Format: int32 */
            dias: number;
            requiereMedioDePago: boolean;
        };
        PaginaPublica: {
            nombre?: string;
            direccion?: string;
            telefono?: string;
            zonaHoraria?: string;
            tomaTurnosOnline?: boolean;
            /** Format: date */
            reservableHasta?: string;
            prestaciones?: components["schemas"]["PrestacionPublica"][];
        };
        PrestacionPublica: {
            /** Format: uuid */
            id?: string;
            nombre?: string;
            /** Format: int32 */
            duracionMin?: number;
        };
        PlanResponse: {
            plan?: string;
            /** Format: date */
            pruebaHasta?: string;
            /** Format: int32 */
            diasDePruebaRestantes?: number;
            soloLectura?: boolean;
        };
        PacienteResponse: {
            /** Format: uuid */
            id?: string;
            nombre?: string;
            apellido?: string;
            dni?: string;
            telefono?: string;
            requiereRevision?: boolean;
        };
        RegistroDeCambiosResponse: {
            eventos?: components["schemas"]["EventoResponse"][];
            siguienteCursor?: string;
        };
        AgendaResponse: {
            turnos?: components["schemas"]["TurnoResponse"][];
            bloqueos?: components["schemas"]["BloqueoResponse"][];
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    consultar: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PrestacionResponse"];
                };
            };
        };
    };
    editar: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PrestacionRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PrestacionResponse"];
                };
            };
            /** @description PLAN_VENCIDO: el plan del consultorio está vencido y la agenda está en solo lectura (RN-PL-03) */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    consultar_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["GrillaResponse"];
                };
            };
        };
    };
    reemplazar: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["GrillaRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["GrillaResponse"];
                };
            };
            /** @description PLAN_VENCIDO: el plan del consultorio está vencido y la agenda está en solo lectura (RN-PL-03) */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    consultar_2: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ConfiguracionResponse"];
                };
            };
        };
    };
    editar_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ConfiguracionRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ConfiguracionResponse"];
                };
            };
            /** @description PLAN_VENCIDO: el plan del consultorio está vencido y la agenda está en solo lectura (RN-PL-03) */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    crear: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CrearTurnoRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TurnoCreadoResponse"];
                };
            };
            /** @description PLAN_VENCIDO: el plan del consultorio está vencido y la agenda está en solo lectura (RN-PL-03) */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    visto: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["VistoRequest"];
            };
        };
        responses: {
            /** @description No Content */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    reprogramar: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ReprogramarRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["DetalleResponse"];
                };
            };
            /** @description PLAN_VENCIDO: el plan del consultorio está vencido y la agenda está en solo lectura (RN-PL-03) */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    reprogramar_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                token: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PedidoDeReprogramacion"];
            };
        };
        responses: {
            /** @description Turno con el horario nuevo */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["VistaDeTurno"];
                };
            };
            /** @description VALIDACION */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["VistaDeTurno"];
                };
            };
            /** @description RECURSO_NO_ENCONTRADO */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["VistaDeTurno"];
                };
            };
            /** @description FUERA_DE_PLAZO o RESERVA_NO_DISPONIBLE (con telefonoContacto), HUECO_NO_DISPONIBLE (con huecos) o CONFLICTO_CONCURRENCIA */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["VistaDeTurno"];
                };
            };
            /** @description CUERPO_DEMASIADO_GRANDE */
            413: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["VistaDeTurno"];
                };
            };
            /** @description DEMASIADAS_SOLICITUDES */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["VistaDeTurno"];
                };
            };
        };
    };
    cancelar: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                token: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Turno CANCELADO */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["VistaDeTurno"];
                };
            };
            /** @description RECURSO_NO_ENCONTRADO */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["VistaDeTurno"];
                };
            };
            /** @description FUERA_DE_PLAZO (con telefonoContacto) o CONFLICTO_CONCURRENCIA */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["VistaDeTurno"];
                };
            };
            /** @description DEMASIADAS_SOLICITUDES */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["VistaDeTurno"];
                };
            };
        };
    };
    reservar: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                slug: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PedidoDeReservaDto"];
            };
        };
        responses: {
            /** @description Turno RESERVADO con linkGestion */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ReservaCreada"];
                };
            };
            /** @description VALIDACION, DNI_INVALIDO o TELEFONO_INVALIDO */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ReservaCreada"];
                };
            };
            /** @description RECURSO_NO_ENCONTRADO */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ReservaCreada"];
                };
            };
            /** @description HUECO_NO_DISPONIBLE (con huecos), LIMITE_TURNOS o RESERVA_NO_DISPONIBLE (con telefonoContacto) */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ReservaCreada"];
                };
            };
            /** @description CUERPO_DEMASIADO_GRANDE */
            413: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ReservaCreada"];
                };
            };
            /** @description DEMASIADAS_SOLICITUDES */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ReservaCreada"];
                };
            };
        };
    };
    listar: {
        parameters: {
            query?: {
                incluirInactivas?: boolean;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PrestacionResponse"][];
                };
            };
        };
    };
    crear_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PrestacionRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PrestacionResponse"];
                };
            };
            /** @description PLAN_VENCIDO: el plan del consultorio está vencido y la agenda está en solo lectura (RN-PL-03) */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    desactivar: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PrestacionResponse"];
                };
            };
            /** @description PLAN_VENCIDO: el plan del consultorio está vencido y la agenda está en solo lectura (RN-PL-03) */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    activar: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PrestacionResponse"];
                };
            };
            /** @description PLAN_VENCIDO: el plan del consultorio está vencido y la agenda está en solo lectura (RN-PL-03) */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    exportar: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description ZIP con JSON, CSV, PDF y manifest */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/zip": string;
                };
            };
            /** @description Sin sesión */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Exportación reciente o en curso; ver Retry-After */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    listar_1: {
        parameters: {
            query?: {
                desde?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["BloqueoResponse"][];
                };
            };
        };
    };
    crear_2: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CrearBloqueoRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["BloqueoResponse"];
                };
            };
            /** @description PLAN_VENCIDO: el plan del consultorio está vencido y la agenda está en solo lectura (RN-PL-03) */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    registrar: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RegistroRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["RegistroResponse"];
                };
            };
        };
    };
    refresh: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: {
                refresh?: string;
            };
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": Record<string, never>;
                };
            };
        };
    };
    solicitar: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SolicitudRequest"];
            };
        };
        responses: {
            /** @description Accepted */
            202: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    confirmar: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ConfirmacionRequest"];
            };
        };
        responses: {
            /** @description No Content */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    logout: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: {
                refresh?: string;
            };
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    login: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["LoginRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SesionResponse"];
                };
            };
        };
    };
    cambiar: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CambioRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    nota: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["NotaRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["DetalleResponse"];
                };
            };
            /** @description PLAN_VENCIDO: el plan del consultorio está vencido y la agenda está en solo lectura (RN-PL-03) */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    cambiarEstado: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CambiarEstadoRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["DetalleResponse"];
                };
            };
            /** @description PLAN_VENCIDO: el plan del consultorio está vencido y la agenda está en solo lectura (RN-PL-03) */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    duracion: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DuracionRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["DetalleResponse"];
                };
            };
            /** @description PLAN_VENCIDO: el plan del consultorio está vencido y la agenda está en solo lectura (RN-PL-03) */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    detalle: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["DetalleResponse"];
                };
            };
        };
    };
    historial: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["EventoResponse"][];
                };
            };
        };
    };
    fueraDeDisponibilidad: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TurnoResponse"][];
                };
            };
        };
    };
    ver: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                token: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Turno, paciente, consultorio y acciones disponibles */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["VistaDeTurno"];
                };
            };
            /** @description RECURSO_NO_ENCONTRADO */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["VistaDeTurno"];
                };
            };
            /** @description DEMASIADAS_SOLICITUDES */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["VistaDeTurno"];
                };
            };
        };
    };
    huecos: {
        parameters: {
            query?: {
                desde?: string;
                hasta?: string;
            };
            header?: never;
            path: {
                token: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Días con sus huecos en UTC */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["HuecosPublicos"];
                };
            };
            /** @description VALIDACION */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["HuecosPublicos"];
                };
            };
            /** @description RECURSO_NO_ENCONTRADO */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["HuecosPublicos"];
                };
            };
            /** @description RESERVA_NO_DISPONIBLE, con telefonoContacto */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["HuecosPublicos"];
                };
            };
            /** @description DEMASIADAS_SOLICITUDES */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["HuecosPublicos"];
                };
            };
        };
    };
    planes: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PlanesPublicosResponse"];
                };
            };
        };
    };
    pagina: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                slug: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Datos públicos; sin reserva online, sin prestaciones */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PaginaPublica"];
                };
            };
            /** @description RECURSO_NO_ENCONTRADO */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PaginaPublica"];
                };
            };
            /** @description DEMASIADAS_SOLICITUDES */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PaginaPublica"];
                };
            };
        };
    };
    huecos_1: {
        parameters: {
            query: {
                prestacionId: string;
                desde?: string;
                hasta?: string;
            };
            header?: never;
            path: {
                slug: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Días con sus huecos en UTC */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["HuecosPublicos"];
                };
            };
            /** @description VALIDACION */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["HuecosPublicos"];
                };
            };
            /** @description RECURSO_NO_ENCONTRADO */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["HuecosPublicos"];
                };
            };
            /** @description RESERVA_NO_DISPONIBLE, con telefonoContacto */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["HuecosPublicos"];
                };
            };
            /** @description DEMASIADAS_SOLICITUDES */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["HuecosPublicos"];
                };
            };
        };
    };
    consultar_3: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PlanResponse"];
                };
            };
        };
    };
    buscar: {
        parameters: {
            query?: {
                q?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PacienteResponse"][];
                };
            };
        };
    };
    registro: {
        parameters: {
            query?: {
                turnoId?: string;
                desde?: string;
                hasta?: string;
                accion?: string[];
                cursor?: string;
                tamano?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["RegistroDeCambiosResponse"];
                };
            };
        };
    };
    agenda: {
        parameters: {
            query: {
                desde: string;
                hasta: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AgendaResponse"];
                };
            };
        };
    };
    eliminar: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description No Content */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description PLAN_VENCIDO: el plan del consultorio está vencido y la agenda está en solo lectura (RN-PL-03) */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
}
