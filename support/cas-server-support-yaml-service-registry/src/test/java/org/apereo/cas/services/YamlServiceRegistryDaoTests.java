package org.apereo.cas.services;

import static org.mockito.Mockito.*;

import org.apereo.cas.services.replication.NoOpRegisteredServiceReplicationStrategy;
import org.junit.Before;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Test cases for {@link YamlServiceRegistryDao}.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
public class YamlServiceRegistryDaoTests extends AbstractResourceBasedServiceRegistryDaoTests {

    @Before
    public void setup() {
        try {
            this.dao = new YamlServiceRegistryDao(RESOURCE, false, 
                    mock(ApplicationEventPublisher.class), new NoOpRegisteredServiceReplicationStrategy());
        } catch (final Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

}
