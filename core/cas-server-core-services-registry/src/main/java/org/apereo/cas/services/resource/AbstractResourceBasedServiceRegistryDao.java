package org.apereo.cas.services.resource;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apereo.cas.services.AbstractRegisteredService;
import org.apereo.cas.services.AbstractServiceRegistryDao;
import org.apereo.cas.services.RegisteredService;
import org.apereo.cas.services.ResourceBasedServiceRegistryDao;
import org.apereo.cas.services.replication.NoOpRegisteredServiceReplicationStrategy;
import org.apereo.cas.services.replication.RegisteredServiceReplicationStrategy;
import org.apereo.cas.support.events.service.CasRegisteredServiceDeletedEvent;
import org.apereo.cas.support.events.service.CasRegisteredServiceLoadedEvent;
import org.apereo.cas.support.events.service.CasRegisteredServicePreDeleteEvent;
import org.apereo.cas.util.CollectionUtils;
import org.apereo.cas.util.RegexUtils;
import org.apereo.cas.util.ResourceUtils;
import org.apereo.cas.util.io.LockedOutputStream;
import org.apereo.cas.util.io.PathWatcherService;
import org.apereo.cas.util.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

import javax.annotation.PreDestroy;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * This is {@link AbstractResourceBasedServiceRegistryDao}.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
public abstract class AbstractResourceBasedServiceRegistryDao extends AbstractServiceRegistryDao implements ResourceBasedServiceRegistryDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractResourceBasedServiceRegistryDao.class);

    private static final String PATTERN_REGISTERED_SERVICE_FILE_NAME = "(\\w+)-(\\d+)\\.";

    private static final BinaryOperator<RegisteredService> LOG_DUPLICATE_AND_RETURN_FIRST_ONE = (s1, s2) -> {
        BaseResourceBasedRegisteredServiceWatcher.LOG_SERVICE_DUPLICATE.accept(s2);
        return s1;
    };

    /**
     * The Service registry directory.
     */
    protected Path serviceRegistryDirectory;

    /**
     * Map of service ID to registered service.
     */
    private Map<Long, RegisteredService> serviceMap = new ConcurrentHashMap<>();

    /**
     * The Registered service json serializers.
     */
    private Collection<StringSerializer<RegisteredService>> registeredServiceSerializers;

    private PathWatcherService serviceRegistryConfigWatcher;

    private Pattern serviceFileNamePattern;

    private RegisteredServiceReplicationStrategy registeredServiceReplicationStrategy;

    public AbstractResourceBasedServiceRegistryDao(final Resource configDirectory,
                                                   final Collection<StringSerializer<RegisteredService>> serializers,
                                                   final ApplicationEventPublisher eventPublisher) throws Exception {
        this(configDirectory, serializers, false, eventPublisher, new NoOpRegisteredServiceReplicationStrategy());
    }

    /**
     * Instantiates a new service registry dao.
     *
     * @param configDirectory                      the config directory
     * @param serializer                           the registered service json serializer
     * @param enableWatcher                        enable watcher thread
     * @param eventPublisher                       the event publisher
     * @param registeredServiceReplicationStrategy the registered service replication strategy
     */
    public AbstractResourceBasedServiceRegistryDao(final Path configDirectory,
                                                   final StringSerializer<RegisteredService> serializer,
                                                   final boolean enableWatcher,
                                                   final ApplicationEventPublisher eventPublisher,
                                                   final RegisteredServiceReplicationStrategy registeredServiceReplicationStrategy) {
        this(configDirectory, CollectionUtils.wrap(serializer), enableWatcher, eventPublisher, registeredServiceReplicationStrategy);
    }

    /**
     * Instantiates a new Abstract resource based service registry dao.
     *
     * @param configDirectory                      the config directory
     * @param serializers                          the serializers
     * @param enableWatcher                        the enable watcher
     * @param eventPublisher                       the event publisher
     * @param registeredServiceReplicationStrategy the registered service replication strategy
     */
    public AbstractResourceBasedServiceRegistryDao(final Path configDirectory,
                                                   final Collection<StringSerializer<RegisteredService>> serializers,
                                                   final boolean enableWatcher,
                                                   final ApplicationEventPublisher eventPublisher,
                                                   final RegisteredServiceReplicationStrategy registeredServiceReplicationStrategy) {
        initializeRegistry(configDirectory, serializers, enableWatcher, eventPublisher, registeredServiceReplicationStrategy);
    }

    /**
     * Instantiates a new Abstract resource based service registry dao.
     *
     * @param configDirectory                      the config directory
     * @param serializers                          the serializers
     * @param enableWatcher                        the enable watcher
     * @param eventPublisher                       the event publisher
     * @param registeredServiceReplicationStrategy the registered service replication strategy
     * @throws Exception the exception
     */
    public AbstractResourceBasedServiceRegistryDao(final Resource configDirectory,
                                                   final Collection<StringSerializer<RegisteredService>> serializers,
                                                   final boolean enableWatcher,
                                                   final ApplicationEventPublisher eventPublisher,
                                                   final RegisteredServiceReplicationStrategy registeredServiceReplicationStrategy) throws Exception {

        final Resource servicesDirectory = ResourceUtils.prepareClasspathResourceIfNeeded(configDirectory, true, getExtension());
        if (servicesDirectory == null) {
            throw new IllegalArgumentException("Could not determine the services configuration directory from " + configDirectory);
        }
        final File file = servicesDirectory.getFile();
        initializeRegistry(Paths.get(file.getCanonicalPath()), serializers, enableWatcher, eventPublisher, registeredServiceReplicationStrategy);
    }

    private void initializeRegistry(final Path configDirectory,
                                    final Collection<StringSerializer<RegisteredService>> serializers,
                                    final boolean enableWatcher,
                                    final ApplicationEventPublisher eventPublisher,
                                    final RegisteredServiceReplicationStrategy registeredServiceReplicationStrategy) {
        
        setEventPublisher(eventPublisher);
        this.registeredServiceReplicationStrategy = ObjectUtils.defaultIfNull(registeredServiceReplicationStrategy,
            new NoOpRegisteredServiceReplicationStrategy());
        this.registeredServiceSerializers = serializers;
        this.serviceFileNamePattern = RegexUtils.createPattern(PATTERN_REGISTERED_SERVICE_FILE_NAME + getExtension());

        this.serviceRegistryDirectory = configDirectory;
        Assert.isTrue(this.serviceRegistryDirectory.toFile().exists(), this.serviceRegistryDirectory + " does not exist");
        Assert.isTrue(this.serviceRegistryDirectory.toFile().isDirectory(), this.serviceRegistryDirectory + " is not a directory");
        
        if (enableWatcher) {
            enableServicesDirectoryPathWatcher();
        }
    }

    private void enableServicesDirectoryPathWatcher() {
        LOGGER.info("Watching service registry directory at [{}]", this.serviceRegistryDirectory);

        final Consumer<File> onCreate = new CreateResourceBasedRegisteredServiceWatcher(this);
        final Consumer<File> onDelete = new DeleteResourceBasedRegisteredServiceWatcher(this);
        final Consumer<File> onModify = new ModifyResourceBasedRegisteredServiceWatcher(this);

        this.serviceRegistryConfigWatcher = new PathWatcherService(this.serviceRegistryDirectory, onCreate, onModify, onDelete);
        this.serviceRegistryConfigWatcher.start(getClass().getSimpleName());
        LOGGER.debug("Started service registry watcher thread");
    }

    /**
     * Destroy the watch service thread.
     */
    @PreDestroy
    public void destroy() {
        if (this.serviceRegistryConfigWatcher != null) {
            this.serviceRegistryConfigWatcher.close();
        }
    }

    @Override
    public long size() {
        return this.serviceMap.size();
    }

    @Override
    public RegisteredService findServiceById(final long id) {
        final RegisteredService service = this.serviceMap.get(id);
        return this.registeredServiceReplicationStrategy.getRegisteredServiceFromCacheIfAny(service, id, this);
    }

    @Override
    public RegisteredService findServiceById(final String id) {
        final RegisteredService service = this.serviceMap.values()
            .stream()
            .filter(r -> r.matches(id))
            .findFirst()
            .orElse(null);
        return this.registeredServiceReplicationStrategy.getRegisteredServiceFromCacheIfAny(service, id, this);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    @Override
    public synchronized boolean delete(final RegisteredService service) {
        try {
            final File f = getRegisteredServiceFileName(service);
            publishEvent(new CasRegisteredServicePreDeleteEvent(this, service));
            final boolean result = f.exists() ? f.delete() : true;
            if (!result) {
                LOGGER.warn("Failed to delete service definition file [{}]", f.getCanonicalPath());
            } else {
                removeRegisteredService(service);
                LOGGER.debug("Successfully deleted service definition file [{}]", f.getCanonicalPath());
            }
            publishEvent(new CasRegisteredServiceDeletedEvent(this, service));
            return result;
        } catch (final IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Remove registered service.
     *
     * @param service the service
     */
    protected void removeRegisteredService(final RegisteredService service) {
        this.serviceMap.remove(service.getId());
    }

    @Override
    public synchronized List<RegisteredService> load() {
        final Collection<File> files = FileUtils.listFiles(this.serviceRegistryDirectory.toFile(), new String[]{getExtension()}, true);
        this.serviceMap = files.stream()
            .map(this::load)
            .filter(Objects::nonNull)
            .flatMap(Collection::stream)
            .sorted()
            .collect(Collectors.toMap(RegisteredService::getId, Function.identity(),
                LOG_DUPLICATE_AND_RETURN_FIRST_ONE, LinkedHashMap::new));
        final List<RegisteredService> services = new ArrayList<>(this.serviceMap.values());
        final List<RegisteredService> results =
            this.registeredServiceReplicationStrategy.updateLoadedRegisteredServicesFromCache(services, this);
        results.forEach(service -> publishEvent(new CasRegisteredServiceLoadedEvent(this, service)));
        return results;
    }

    /**
     * Load registered service from file.
     *
     * @param file the file
     * @return the registered service, or null if file cannot be read, is not found, is empty or parsing error occurs.
     */
    @Override
    public Collection<RegisteredService> load(final File file) {
        if (!file.canRead()) {
            LOGGER.warn("[{}] is not readable. Check file permissions", file.getName());
            return new ArrayList<>(0);
        }

        if (!file.exists()) {
            LOGGER.warn("[{}] is not found at the path specified", file.getName());
            return new ArrayList<>(0);
        }

        if (file.length() == 0) {
            LOGGER.debug("[{}] appears to be empty so no service definition will be loaded", file.getName());
            return new ArrayList<>(0);
        }

        if (!RegexUtils.matches(this.serviceFileNamePattern, file.getName())) {
            LOGGER.warn("[{}] does not match the recommended pattern [{}]. "
                    + "While CAS tries to be forgiving as much as possible, it's recommended "
                    + "that you rename the file to match the requested pattern to avoid issues with duplicate service loading. "
                    + "Future CAS versions may try to strictly force the naming syntax, refusing to load the file.",
                file.getName(), this.serviceFileNamePattern.pattern());
        }

        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
            return this.registeredServiceSerializers
                .stream()
                .filter(s -> s.supports(file))
                .map(s -> s.load(in))
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        } catch (final Exception e) {
            LOGGER.error("Error reading configuration file [{}]", file.getName(), e);
        }
        return new ArrayList<>(0);
    }

    @Override
    public RegisteredService save(final RegisteredService service) {
        if (service.getId() == RegisteredService.INITIAL_IDENTIFIER_VALUE && service instanceof AbstractRegisteredService) {
            LOGGER.debug("Service id not set. Calculating id based on system time...");
            ((AbstractRegisteredService) service).setId(System.currentTimeMillis());
        }
        final File f = getRegisteredServiceFileName(service);
        try (LockedOutputStream out = new LockedOutputStream(new FileOutputStream(f))) {
            final boolean result = this.registeredServiceSerializers
                .stream()
                .anyMatch(s -> {
                    try {
                        s.to(out, service);
                        return true;
                    } catch (final Exception e) {
                        LOGGER.debug(e.getMessage(), e);
                        return false;
                    }
                });

            if (!result) {
                throw new IOException("The service definition file could not be saved at " + f.getCanonicalPath());
            }
            if (this.serviceMap.containsKey(service.getId())) {
                LOGGER.debug("Found existing service definition by id [{}]. Saving...", service.getId());
            }
            this.serviceMap.put(service.getId(), service);
            LOGGER.debug("Saved service to [{}]", f.getCanonicalPath());
        } catch (final IOException e) {
            throw new IllegalArgumentException("IO error opening file stream.", e);
        }
        return findServiceById(service.getId());
    }


    @Override
    public void update(final RegisteredService service) {
        this.serviceMap.put(service.getId(), service);
    }

    /**
     * Gets registered service from file.
     *
     * @param file the file
     * @return the registered service from file
     */
    protected RegisteredService getRegisteredServiceFromFile(final File file) {
        final Matcher matcher = this.serviceFileNamePattern.matcher(file.getName());
        if (matcher.find()) {
            final String serviceId = matcher.group(2);
            if (NumberUtils.isCreatable(serviceId)) {
                final long id = Long.parseLong(serviceId);
                return findServiceById(id);
            }
            final String serviceName = matcher.group(1);
            return findServiceByExactServiceName(serviceName);
        }
        LOGGER.warn("Provided file [{}} does not match the recommended service definition file pattern [{}]",
            this.serviceFileNamePattern.pattern());
        return null;
    }

    /**
     * Creates a file for a registered service.
     * The file is named as {@code [SERVICE-NAME]-[SERVICE-ID]-.{@value #getExtension()}}
     *
     * @param service Registered service.
     * @return file in service registry directory.
     * @throws IllegalArgumentException if file name is invalid
     */
    protected File getRegisteredServiceFileName(final RegisteredService service) {
        final String fileName = StringUtils.remove(buildServiceDefinitionFileName(service), " ");
        try {
            final File svcFile = new File(this.serviceRegistryDirectory.toFile(), fileName);
            LOGGER.debug("Using [{}] as the service definition file", svcFile.getCanonicalPath());
            return svcFile;
        } catch (final Exception e) {
            LOGGER.warn("Service file name [{}] is invalid; Examine for illegal characters in the name.", fileName);
            throw new IllegalArgumentException(e);
        }
    }

    private String buildServiceDefinitionFileName(final RegisteredService service) {
        return service.getName() + '-' + service.getId() + '.' + getExtension();
    }

    /**
     * Gets extension associated with files in the given resource directory.
     *
     * @return the extension
     */
    protected abstract String getExtension();
}
