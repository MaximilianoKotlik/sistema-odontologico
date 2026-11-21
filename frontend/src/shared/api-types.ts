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
    "/api/v1/prestaciones": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["listar"];
        put?: never;
        post: operations["crear"];
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
    crear: {
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
}
