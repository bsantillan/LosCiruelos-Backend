package com.LosCiruelos.padel_club_api.Services.Avisos;

/**
 * Avisos que manda el sistema. Cada uno tiene su plantilla de WhatsApp (se crea con este mismo
 * nombre y texto en el WhatsApp Manager, categoría Utility, idioma es_AR) y el asunto del mail.
 * El texto se usa igual en el mail, reemplazando {{1}}, {{2}}, ….
 */
public enum TipoAviso {

    // ── Para los ADMIN (según lo que elija cada uno) ──
    RESERVA_CONFIRMADA(true, "aviso_reserva_confirmada", "Nueva reserva",
            "Nueva reserva: {{1}} reservó la cancha {{2}} para el {{3}}, de {{4}}. Pagó {{5}} de {{6}}."),
    SALDO_COBRADO(true, "aviso_saldo_cobrado", "Saldo cobrado",
            "Saldo cobrado: la reserva de {{1}} (cancha {{2}}, {{3}}, de {{4}}) quedó paga. Total: {{5}}."),
    RESERVA_CANCELADA(true, "aviso_reserva_cancelada", "Reserva cancelada",
            "Reserva cancelada: {{1}}, cancha {{2}}, {{3}}, de {{4}}. {{5}}"),
    REEMBOLSO_MANUAL(true, "aviso_reembolso_manual", "Hay que devolver dinero",
            "Atención: hay que devolver {{1}} a mano. Reserva #{{2}} de {{3}}. Motivo: {{4}}"),
    REEMBOLSO_REALIZADO(true, "aviso_reembolso_realizado", "Reembolso hecho",
            "Reembolso hecho: Mercado Pago devolvió {{1}} de la reserva cancelada #{{2}} de {{3}}, que había quedado pendiente."),

    // ── Para el cliente ──
    RECORDATORIO(false, "recordatorio_turno", "Recordatorio de tu turno",
            "Hola {{1}}, te recordamos tu turno en Los Ciruelos: cancha {{2}}, {{3}}, de {{4}}. {{5}}"),
    CANCELADA_POR_CLUB(false, "reserva_cancelada_por_club", "Tu reserva fue cancelada",
            "Hola {{1}}, el club canceló tu reserva de la cancha {{2}} del {{3}}, de {{4}}. {{5}}");

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
