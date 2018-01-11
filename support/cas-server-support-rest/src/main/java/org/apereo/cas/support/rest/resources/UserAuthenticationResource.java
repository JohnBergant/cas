package org.apereo.cas.support.rest.resources;

import org.apereo.cas.authentication.AuthenticationException;
import org.apereo.cas.authentication.AuthenticationResult;
import org.apereo.cas.authentication.AuthenticationSystemSupport;
import org.apereo.cas.authentication.Credential;
import org.apereo.cas.authentication.principal.Service;
import org.apereo.cas.authentication.principal.ServiceFactory;
import org.apereo.cas.rest.BadRestRequestException;
import org.apereo.cas.rest.RestHttpRequestCredentialFactory;
import org.apereo.cas.support.rest.factory.UserAuthenticationResourceEntityResponseFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;

/**
 * {@link RestController} implementation of CAS' REST API.
 * <p>
 * This class implements main CAS RESTful resource for vending/deleting TGTs and vending STs:
 * </p>
 * <ul>
 * <li>{@code POST /v1/tickets}</li>
 * <li>{@code POST /v1/tickets/{TGT-id}}</li>
 * <li>{@code GET /v1/tickets/{TGT-id}}</li>
 * <li>{@code DELETE /v1/tickets/{TGT-id}}</li>
 * </ul>
 *
 * @author Dmitriy Kopylenko
 * @since 4.1.0
 */
@RestController("userAuthenticationResource")
public class UserAuthenticationResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserAuthenticationResource.class);

    private final AuthenticationSystemSupport authenticationSystemSupport;
    private final ServiceFactory serviceFactory;
    private final RestHttpRequestCredentialFactory credentialFactory;
    private final UserAuthenticationResourceEntityResponseFactory userAuthenticationResourceEntityResponseFactory;

    public UserAuthenticationResource(final AuthenticationSystemSupport authenticationSystemSupport,
                                      final RestHttpRequestCredentialFactory credentialFactory,
                                      final ServiceFactory serviceFactory,
                                      final UserAuthenticationResourceEntityResponseFactory userAuthenticationResourceEntityResponseFactory) {
        this.authenticationSystemSupport = authenticationSystemSupport;
        this.credentialFactory = credentialFactory;
        this.serviceFactory = serviceFactory;
        this.userAuthenticationResourceEntityResponseFactory = userAuthenticationResourceEntityResponseFactory;
    }

    /**
     * Create new ticket granting ticket.
     *
     * @param requestBody username and password application/x-www-form-urlencoded values
     * @param request     raw HttpServletRequest used to call this method
     * @return ResponseEntity representing RESTful response
     */
    @PostMapping(value = "/v1/users", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> createTicketGrantingTicket(@RequestBody final MultiValueMap<String, String> requestBody,
                                                             final HttpServletRequest request) {
        try {
            final Collection<Credential> credential = this.credentialFactory.fromRequestBody(requestBody);
            if (credential == null || credential.isEmpty()) {
                throw new BadRestRequestException("No credentials are provided or extracted to authenticate the REST request");
            }
            final Service service = this.serviceFactory.createService(request);
            final AuthenticationResult authenticationResult =
                authenticationSystemSupport.handleAndFinalizeSingleAuthenticationTransaction(service, credential);
            return this.userAuthenticationResourceEntityResponseFactory.build(authenticationResult, request);
        } catch (final AuthenticationException e) {
            return RestResourceUtils.createResponseEntityForAuthnFailure(e);
        } catch (final BadRestRequestException e) {
            LOGGER.error(e.getMessage(), e);
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
