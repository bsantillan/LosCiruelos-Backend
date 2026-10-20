package com.LosCiruelos.padel_club_api.Services.Email;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.extern.slf4j.Slf4j;

/**
 * Envía un email cuando la transacción actual se confirma.
 * - Si la transacción hace rollback, el email no se manda (no llegan códigos que no existen).
 * - Si el envío falla, se loguea y no afecta a la operación que ya se guardó.
 */
@Slf4j
public final class EnvioPostCommit {

    private EnvioPostCommit() {
    }

    public static void ejecutar(String descripcion, Runnable envio) {
        Runnable seguro = () -> {
            try {
                envio.run();
            } catch (Exception e) {
                log.error("No se pudo enviar el email ({}): {}", descripcion, e.getMessage());
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    seguro.run();
                }
            });
        } else {
            seguro.run();
        }
    }
}
