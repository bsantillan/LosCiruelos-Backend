package com.LosCiruelos.padel_club_api.Services.Avisos;

/**
 * Avisos que manda el sistema. Cada uno tiene su plantilla de WhatsApp (se crea con este mismo
 * nombre y texto en el WhatsApp Manager, categoría Utility, idioma es_AR, variables de tipo número)
 * y el asunto del mail. El texto se usa igual en el mail, reemplazando {{1}}, {{2}}, ….
 * Reglas de Meta: ninguna variable al principio ni al final, y bastante texto fijo por variable.
 */
public enum TipoAviso {

    // ── Para los ADMIN (según lo que elija cada uno) ──
    RESERVA_CONFIRMADA(true, "aviso_reserva_confirmada_v2", "Nueva reserva",
            "Nueva reserva en Los Ciruelos: {{1}} reservó la cancha {{2}} para el {{3}}, de {{4}}. Pagó {{5}} de un total de {{6}}. Podés ver el detalle en el panel del club."),
    SALDO_COBRADO(true, "aviso_saldo_cobrado_v2", "Saldo cobrado",
            "Saldo cobrado en Los Ciruelos: la reserva de {{1}} en la cancha {{2}} para el {{3}}, de {{4}}, quedó totalmente paga. Total cobrado: {{5}}. Podés ver el detalle en el panel del club."),
    RESERVA_CANCELADA(true, "aviso_reserva_cancelada_v2", "Reserva cancelada",
            "Reserva cancelada en Los Ciruelos: {{1}}, cancha {{2}}, el {{3}}, de {{4}}. Detalle: {{5}} Podés ver la reserva en el panel del club."),
    REEMBOLSO_MANUAL(true, "aviso_reembolso_manual_v2", "Hay que devolver dinero",
            "Atención: hay que devolver {{1}} a mano por la reserva #{{2}} de {{3}}. Motivo: {{4}} Revisalo en el panel de Mercado Pago o en el panel del club."),
    REEMBOLSO_REALIZADO(true, "aviso_reembolso_realizado_v2", "Reembolso hecho",
            "Reembolso hecho: Mercado Pago devolvió {{1}} de la reserva cancelada #{{2}} de {{3}}, que había quedado pendiente. No hace falta hacer nada más."),

    // ── Para el cliente ──
    RECORDATORIO(false, "recordatorio_turno_v2", "Recordatorio de tu turno",
            "Hola {{1}}, te recordamos tu turno en Los Ciruelos: cancha {{2}}, el {{3}}, de {{4}}. {{5}} Si no podés venir, cancelá la reserva desde la página. ¡Te esperamos!"),
    CANCELADA_POR_CLUB(false, "reserva_cancelada_por_club_v2", "Tu reserva fue cancelada",
            "Hola {{1}}, el club canceló tu reserva de la cancha {{2}} del {{3}}, de {{4}}. {{5}} Disculpá las molestias.");

    private final boolean paraAdmin;
    private final String plantilla;
    private final String asunto;
    private final String texto;

    TipoAviso(boolean paraAdmin, String plantilla, String asunto, String texto) {
        this.paraAdmin = paraAdmin;
        this.plantilla = plantilla;
        this.asunto = asunto;
        this.texto = texto;
    }

    public boolean esParaAdmin() {
        return paraAdmin;
    }

    public String plantilla() {
        return plantilla;
    }

    public String asunto() {
        return asunto;
    }

    /** Texto con los parámetros reemplazados (para el mail y los logs). */
    public String texto(java.util.List<String> parametros) {
        String resultado = texto;
        for (int i = 0; i < parametros.size(); i++) {
            resultado = resultado.replace("{{" + (i + 1) + "}}", parametros.get(i));
        }
        return resultado;
    }

    /** Texto de la plantilla tal como se carga en Meta. */
    public String textoPlantilla() {
        return texto;
    }
}
