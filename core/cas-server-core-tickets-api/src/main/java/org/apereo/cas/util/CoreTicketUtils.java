package org.apereo.cas.util;

import org.apache.commons.lang3.StringUtils;
import org.apereo.cas.CipherExecutor;
import org.apereo.cas.configuration.model.core.util.EncryptionRandomizedSigningJwtCryptographyProperties;
import org.apereo.cas.util.cipher.DefaultTicketCipherExecutor;
import org.apereo.cas.util.cipher.NoOpCipherExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is {@link CoreTicketUtils}.
 *
 * @author Misagh Moayyed
 * @since 5.2.0
 */
public final class CoreTicketUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoreTicketUtils.class);

    private CoreTicketUtils() {}
    
    /**
     * New ticket registry cipher executor cipher executor.
     *
     * @param registry     the registry
     * @param registryName the registry name
     * @return the cipher executor
     */
    public static CipherExecutor newTicketRegistryCipherExecutor(final EncryptionRandomizedSigningJwtCryptographyProperties registry,
                                                                 final String registryName) {
        return newTicketRegistryCipherExecutor(registry, false, registryName);
    }

    /**
     * New ticket registry cipher executor cipher executor.
     *
     * @param registry         the registry
     * @param forceIfBlankKeys the force if blank keys
     * @param registryName     the registry name
     * @return the cipher executor
     */
    public static CipherExecutor newTicketRegistryCipherExecutor(final EncryptionRandomizedSigningJwtCryptographyProperties registry,
                                                                 final boolean forceIfBlankKeys,
                                                                 final String registryName) {

        boolean enabled = registry.isEnabled();
        if (!enabled && (StringUtils.isNotBlank(registry.getEncryption().getKey())) && StringUtils.isNotBlank(registry.getSigning().getKey())) {
            LOGGER.warn("Ticket registry encryption/signing for [{}] is not enabled explicitly in the configuration, yet signing/encryption keys "
                    + "are defined for ticket operations. CAS will proceed to enable the ticket registry encryption/signing functionality. "
                    + "If you intend to turn off this behavior, consider removing/disabling the signing/encryption keys defined in settings", registryName);
            enabled = true;
        }

        if (enabled || forceIfBlankKeys) {
            LOGGER.debug("Ticket registry encryption/signing is enabled for [{}]", registryName);
            return new DefaultTicketCipherExecutor(
                    registry.getEncryption().getKey(),
                    registry.getSigning().getKey(),
                    registry.getAlg(),
                    registry.getSigning().getKeySize(),
                    registry.getEncryption().getKeySize(),
                    registryName);
        }
        LOGGER.info("Ticket registry encryption/signing is turned off. This MAY NOT be safe in a clustered production environment. "
                + "Consider using other choices to handle encryption, signing and verification of "
                + "ticket registry tickets, and verify the chosen ticket registry does support this behavior.");
        return NoOpCipherExecutor.getInstance();
    }
}
