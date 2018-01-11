package org.apereo.cas.configuration.model.support.ldap;

import org.apache.commons.lang3.NotImplementedException;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.io.Serializable;

/**
 * This is {@link AbstractLdapProperties}.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
public abstract class AbstractLdapProperties implements Serializable {
    private static final long serialVersionUID = 2682743362616979324L;

    /**
     * The ldap type used to handle specific ops.
     */
    public enum LdapType {
        /**
         * Generic ldap type (OpenLDAP, 389ds, etc).
         */
        GENERIC,
        /**
         * Active directory.
         */
        AD,
        /**
         * FreeIPA directory.
         */
        FreeIPA,
        /**
         * EDirectory.
         */
        EDirectory
    }

    /**
     * The ldap connection pool passivator.
     */
    public enum LdapConnectionPoolPassivator {
        /**
         * No passivator.
         */
        NONE,
        /**
         * Close passivator.
         */
        CLOSE,
        /**
         * Bind passivator.
         */
        BIND
    }

    /**
     * Describe ldap connection strategies.
     */
    public enum LdapConnectionStrategy {
        /**
         * Default JNDI.
         */
        DEFAULT,
        /**
         * First ldap used until it fails.
         */
        ACTIVE_PASSIVE,
        /**
         * Navigate the ldap url list for new connections and circle back.
         */
        ROUND_ROBIN,
        /**
         * Randomly pick a url.
         */
        RANDOM,
        /**
         * ldap urls based on DNS SRV records.
         */
        DNS_SRV
    }

    /**
     * Path of the trust certificates to use for the SSL connection.
     * Ignores keystore-related settings when activated and used.
     */
    private String trustCertificates;

    /**
     * Path to the keystore used for SSL connections.
     * Typically contains SSL certificates for the LDAP server.
     */
    private String keystore;
    /**
     * Keystore password.
     */
    private String keystorePassword;
    /**
     * The type of keystore. {@code PKCS12} or {@code JKS}.
     * If left blank, defaults to the default keystore type indicated
     * by the underlying Java platform.
     */
    private String keystoreType;

    /**
     * Minimum LDAP connection pool size.
     * Size the pool should be initialized to and pruned to
     */
    private int minPoolSize = 3;

    /**
     * Maximum LDAP connection pool size which the pool can use to grow.
     */
    private int maxPoolSize = 10;

    /**
     * You may receive unexpected LDAP failures, when CAS is configured to authenticate
     * using DIRECT or AUTHENTICATED types and LDAP is locked down to not allow anonymous binds/searches.
     * Every second attempt with a given LDAP connection from the pool would fail if it was on
     * the same connection as a failed login attempt, and the regular connection validator would
     * similarly fail. When a connection is returned back to a pool,
     * it still may contain the principal and credentials from the previous attempt.
     * Before the next bind attempt using that connection, the validator tries to
     * validate the connection again but fails because it’s no longer trying with the
     * configured bind credentials but with whatever user DN was used in the previous step.
     * Given the validation failure, the connection is closed and CAS would deny access by default. Passivators attempt to reconnect
     * to LDAP with the configured bind credentials, effectively resetting the connection
     * to what it should be after each bind request.
     */
    private String poolPassivator = "BIND";

    /**
     * Whether connections should be validated when loaned out from the pool.
     */
    private boolean validateOnCheckout = true;
    /**
     * Whether connections should be validated periodically when the pool is idle.
     */
    private boolean validatePeriodically = true;

    /**
     * Period at which validation operations may time out.
     */
    private String validateTimeout = "PT5S";
    /**
     * Period at which pool should be validated.
     */
    private String validatePeriod = "PT5M";

    /**
     * Attempt to populate the connection pool early on startup
     * and fail quickly if something goes wrong.
     */
    private boolean failFast = true;

    /**
     * Removes connections from the pool based on how long they have been idle in the available queue.
     * Prunes connections that have been idle for more than the indicated amount.
     */
    private String idleTime = "PT10M";
    /**
     * Removes connections from the pool based on how long they have been idle in the available queue.
     * Run the pruning process at the indicated interval.
     */
    private String prunePeriod = "PT2H";
    /**
     * The length of time the pool will block.
     * By default the pool will block indefinitely and there is no guarantee that
     * waiting threads will be serviced in the order in which they made their request.
     * This option should be used with a blocking connection pool when you need to control the exact
     * number of connections that can be created
     */
    private String blockWaitTime = "PT3S";

    /**
     * If multiple URLs are provided as the ldapURL this describes how each URL will be processed.
     * <ul>
     * <li>{@code DEFAULT} The default JNDI provider behavior will be used. </li>
     * <li>{@code ACTIVE_PASSIVE} First LDAP will be used for every request unless it fails and then the next shall be used.</li>
     * <li>{@code ROUND_ROBIN} For each new connection the next url in the list will be used.</li>
     * <li>{@code RANDOM} For each new connection a random LDAP url will be selected.</li>
     * <li>{@code DNS_SRV} LDAP urls based on DNS SRV records of the configured/given LDAP url will be used. </li>
     * </ul>
     */
    private String connectionStrategy;

    /**
     * The LDAP url to the server. More than one may be specified, separated by space and/or comma.
     */
    private String ldapUrl = "ldap://localhost:389";
    /**
     * If the LDAP connection should be used with SSL enabled.
     */
    private boolean useSsl = true;
    /**
     * Whether TLS should be used and enabled when establishing the connection.
     */
    private boolean useStartTls;
    /**
     * Sets the maximum amount of time that connects will block.
     */
    private String connectTimeout = "PT5S";
    /**
     * Duration of time to wait for responses.
     */
    private String responseTimeout = "PT5S";

    /**
     * LDAP operations are delegated to what we call a provider. This allows developers and deployers to change the underlying library
     * that provides the LDAP implementation without modifying any code. By default the JNDI provider is used, though
     * it may be swapped out for {@code org.ldaptive.provider.unboundid.UnboundIDProvider}.
     */
    private String providerClass;
    /**
     * Whether search/query results are allowed to match on multiple DNs,
     * or whether a single unique DN is expected for the result.
     */
    private boolean allowMultipleDns;

    /**
     * The bind DN to use when connecting to LDAP.
     * LDAP connection configuration injected into the LDAP connection pool can be initialized with the following parameters:
     * <ul>
     * <li>{@code bindDn/bindCredential} provided - Use the provided credentials to bind when initializing connections.</li>
     * <li>{@code bindDn/bindCredential}  set to {@code *} - Use a fast-bind strategy to initialize the pool.</li>
     * <li>{@code bindDn/bindCredential}  set to blank - Skip connection initializing; perform operations anonymously. </li>
     * <li>SASL mechanism provided - Use the given SASL mechanism to bind when initializing connections. </li>
     * </ul>
     */
    private String bindDn;
    /**
     * The bind credential to use when connecting to LDAP.
     */
    private String bindCredential;

    /**
     * The SASL realm.
     */
    private String saslRealm;
    /**
     * The SASL mechanism.
     */
    private String saslMechanism;
    /**
     * SASL authorization id.
     */
    private String saslAuthorizationId;
    /**
     * SASL security strength.
     */
    private String saslSecurityStrength;
    /**
     * SASL mutual auth is enabled?
     */
    private Boolean saslMutualAuth;
    /**
     * SASL quality of protected.
     */
    private String saslQualityOfProtection;

    /**
     * LDAP connection validator settings.
     */
    @NestedConfigurationProperty
    private LdapValidatorProperties validator = new LdapValidatorProperties();

    /**
     * Name of the authentication handler.
     */
    private String name;

    public String getValidateTimeout() {
        return validateTimeout;
    }

    public void setValidateTimeout(final String validateTimeout) {
        this.validateTimeout = validateTimeout;
    }

    public String getPoolPassivator() {
        return poolPassivator;
    }

    public void setPoolPassivator(final String poolPassivator) {
        this.poolPassivator = poolPassivator;
    }

    public String getConnectionStrategy() {
        return connectionStrategy;
    }

    public void setConnectionStrategy(final String connectionStrategy) {
        this.connectionStrategy = connectionStrategy;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public LdapValidatorProperties getValidator() {
        return validator;
    }

    public void setValidator(final LdapValidatorProperties validator) {
        this.validator = validator;
    }

    public String getBindDn() {
        return bindDn;
    }

    public void setBindDn(final String bindDn) {
        this.bindDn = bindDn;
    }

    public String getBindCredential() {
        return bindCredential;
    }

    public void setBindCredential(final String bindCredential) {
        this.bindCredential = bindCredential;
    }

    public String getProviderClass() {
        return providerClass;
    }

    public void setProviderClass(final String providerClass) {
        this.providerClass = providerClass;
    }

    public boolean isAllowMultipleDns() {
        return allowMultipleDns;
    }

    public void setAllowMultipleDns(final boolean allowMultipleDns) {
        this.allowMultipleDns = allowMultipleDns;
    }

    public String getPrunePeriod() {
        return prunePeriod;
    }

    public void setPrunePeriod(final String prunePeriod) {
        this.prunePeriod = prunePeriod;
    }

    public String getTrustCertificates() {
        return trustCertificates;
    }

    public void setTrustCertificates(final String trustCertificates) {
        this.trustCertificates = trustCertificates;
    }

    public String getKeystore() {
        return keystore;
    }

    public void setKeystore(final String keystore) {
        this.keystore = keystore;
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    public void setKeystorePassword(final String keystorePassword) {
        this.keystorePassword = keystorePassword;
    }

    public String getKeystoreType() {
        return keystoreType;
    }

    public void setKeystoreType(final String keystoreType) {
        this.keystoreType = keystoreType;
    }

    public int getMinPoolSize() {
        return minPoolSize;
    }

    public void setMinPoolSize(final int minPoolSize) {
        this.minPoolSize = minPoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(final int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public boolean isValidateOnCheckout() {
        return validateOnCheckout;
    }

    public void setValidateOnCheckout(final boolean validateOnCheckout) {
        this.validateOnCheckout = validateOnCheckout;
    }

    public boolean isValidatePeriodically() {
        return validatePeriodically;
    }

    public void setValidatePeriodically(final boolean validatePeriodically) {
        this.validatePeriodically = validatePeriodically;
    }

    public String getValidatePeriod() {
        return validatePeriod;
    }

    public void setValidatePeriod(final String validatePeriod) {
        this.validatePeriod = validatePeriod;
    }

    public boolean isFailFast() {
        return failFast;
    }

    public void setFailFast(final boolean failFast) {
        this.failFast = failFast;
    }

    public String getIdleTime() {
        return idleTime;
    }

    public void setIdleTime(final String idleTime) {
        this.idleTime = idleTime;
    }

    public String getBlockWaitTime() {
        return blockWaitTime;
    }

    public void setBlockWaitTime(final String blockWaitTime) {
        this.blockWaitTime = blockWaitTime;
    }

    public String getLdapUrl() {
        return ldapUrl;
    }

    public void setLdapUrl(final String ldapUrl) {
        this.ldapUrl = ldapUrl;
    }

    public boolean isUseSsl() {
        return useSsl;
    }

    public void setUseSsl(final boolean useSsl) {
        this.useSsl = useSsl;
    }

    public boolean isUseStartTls() {
        return useStartTls;
    }

    public void setUseStartTls(final boolean useStartTls) {
        this.useStartTls = useStartTls;
    }

    public String getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(final String connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public String getSaslRealm() {
        return saslRealm;
    }

    public void setSaslRealm(final String saslRealm) {
        this.saslRealm = saslRealm;
    }

    public String getSaslMechanism() {
        return saslMechanism;
    }

    public void setSaslMechanism(final String saslMechanism) {
        this.saslMechanism = saslMechanism;
    }

    public String getSaslAuthorizationId() {
        return saslAuthorizationId;
    }

    public void setSaslAuthorizationId(final String saslAuthorizationId) {
        this.saslAuthorizationId = saslAuthorizationId;
    }

    public String getSaslSecurityStrength() {
        return saslSecurityStrength;
    }

    public void setSaslSecurityStrength(final String saslSecurityStrength) {
        this.saslSecurityStrength = saslSecurityStrength;
    }

    public String getSaslQualityOfProtection() {
        return saslQualityOfProtection;
    }

    public void setSaslQualityOfProtection(final String saslQualityOfProtection) {
        this.saslQualityOfProtection = saslQualityOfProtection;
    }

    public void setSaslMutualAuth(final Boolean saslMutualAuth) {
        this.saslMutualAuth = saslMutualAuth;
    }

    public Boolean getSaslMutualAuth() {
        return saslMutualAuth;
    }

    public String getResponseTimeout() {
        return responseTimeout;
    }

    public void setResponseTimeout(final String responseTimeout) {
        this.responseTimeout = responseTimeout;
    }

    /**
     * @deprecated Since 5.2. 
     * Sets user filter.
     *
     * @param filter the filter
     */
    @Deprecated
    @DeprecatedConfigurationProperty(reason = "userFilter is replaced with searchFilter instead.", replacement = "searchFilter")
    public void setUserFilter(final String filter) {
        throw new NotImplementedException("userFilter is no longer supported. Use searchFilter instead");
    }

    /**
     * @deprecated Since 5.2.
     * Gets user filter.
     *
     * @return the user filter
     */
    @Deprecated
    @DeprecatedConfigurationProperty(reason = "userFilter is replaced with searchFilter instead.", replacement = "searchFilter")
    public String getUserFilter() {
        throw new NotImplementedException("userFilter is no longer supported. Use searchFilter instead");
    }
}
