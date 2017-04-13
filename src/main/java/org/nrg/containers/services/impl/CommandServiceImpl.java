package org.nrg.containers.services.impl;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import org.nrg.containers.exceptions.CommandValidationException;
import org.nrg.containers.model.command.auto.CommandSummaryForContext;
import org.nrg.containers.model.configuration.CommandConfiguration;
import org.nrg.containers.model.command.entity.CommandEntity;
import org.nrg.containers.model.command.entity.CommandWrapperEntity;
import org.nrg.containers.model.command.auto.Command;
import org.nrg.containers.model.command.auto.Command.CommandWrapper;
import org.nrg.containers.model.configuration.CommandConfigurationInternal;
import org.nrg.containers.services.CommandEntityService;
import org.nrg.containers.services.CommandService;
import org.nrg.containers.services.ContainerConfigService;
import org.nrg.containers.services.ContainerConfigService.CommandConfigurationException;
import org.nrg.framework.exceptions.NotFoundException;
import org.nrg.xdat.schema.SchemaElement;
import org.nrg.xft.exception.ElementNotFoundException;
import org.nrg.xft.exception.XFTInitException;
import org.nrg.xft.security.UserI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class CommandServiceImpl implements CommandService, InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(CommandServiceImpl.class);

    private final CommandEntityService commandEntityService;
    private final ContainerConfigService containerConfigService;

    @Autowired
    public CommandServiceImpl(final CommandEntityService commandEntityService,
                              final ContainerConfigService containerConfigService) {
        this.commandEntityService = commandEntityService;
        this.containerConfigService = containerConfigService;
    }

    @Override
    public void afterPropertiesSet() {
        // Set the default JayWay JSONPath configuration
        Configuration.setDefaults(new Configuration.Defaults() {

            private final JsonProvider jsonProvider = new JacksonJsonProvider();
            private final MappingProvider mappingProvider = new JacksonMappingProvider();

            @Override
            public JsonProvider jsonProvider() {
                return jsonProvider;
            }

            @Override
            public MappingProvider mappingProvider() {
                return mappingProvider;
            }

            @Override
            public Set<Option> options() {
                return Sets.newHashSet(Option.DEFAULT_PATH_LEAF_TO_NULL);
            }
        });
    }

    @Override
    @Nonnull
    @Transactional
    public Command create(@Nonnull final Command command) throws CommandValidationException {
        final List<String> errors = command.validate();
        if (!errors.isEmpty()) {
            throw new CommandValidationException(errors);
        }
        return toPojo(commandEntityService.create(fromPojo(command)));
    }

    @Override
    @Nonnull
    public List<Command> getAll() {
        return toPojo(commandEntityService.getAll());
    }

    @Override
    @Nullable
    public Command retrieve(final long id) {
        return toPojo(commandEntityService.retrieve(id));
    }

    @Override
    @Nonnull
    public Command get(final long id) throws NotFoundException {
        return toPojo(commandEntityService.get(id));
    }

    @Override
    @Nonnull
    public List<Command> findByProperties(final Map<String, Object> properties) {
        return toPojo(commandEntityService.findByProperties(properties));
    }

    @Override
    @Nonnull
    @Transactional
    public Command update(final @Nonnull Command toUpdate) throws NotFoundException, CommandValidationException {
        final List<String> errors = toUpdate.validate();
        if (!errors.isEmpty()) {
            throw new CommandValidationException(errors);
        }
        final CommandEntity updatableEntity = fromPojo(toUpdate);
        commandEntityService.update(updatableEntity);
        commandEntityService.refresh(updatableEntity);
        return toPojo(updatableEntity);
    }

    @Override
    public void delete(final long id) {
        final Command command = retrieve(id);
        if (command != null) {
            for (final CommandWrapper commandWrapper : command.xnatCommandWrappers()) {
                containerConfigService.deleteAllConfiguration(id, commandWrapper.name());
            }

            commandEntityService.delete(id);
        }

        containerConfigService.deleteAllConfiguration(id);
    }

    @Override
    @Nonnull
    @Transactional
    public List<Command> save(final List<Command> commands) {
        final List<Command> created = Lists.newArrayList();
        if (!(commands == null || commands.isEmpty())) {
            for (final Command command : commands) {
                try {
                    created.add(create(command));
                } catch (CommandValidationException e) {
                    // TODO: should I "update" instead of erroring out if command already exists?
                    log.error("Could not save command " + command.name(), e);
                }
            }
        }
        return created;
    }

    @Override
    @Nonnull
    @Transactional
    public CommandWrapper addWrapper(final long commandId, final @Nonnull CommandWrapper wrapperToAdd) throws CommandValidationException, NotFoundException {
        return addWrapper(get(commandId), wrapperToAdd);
    }

    @Override
    @Nonnull
    @Transactional
    public CommandWrapper addWrapper(final @Nonnull Command command, final @Nonnull CommandWrapper wrapperToAdd) throws CommandValidationException, NotFoundException {
        final CommandWrapper created = toPojo(commandEntityService.addWrapper(fromPojo(command), fromPojo(wrapperToAdd)));

        final List<String> errors = get(command.id()).validate();
        if (!errors.isEmpty()) {
            throw new CommandValidationException(errors);
        }
        return created;
    }

    @Override
    @Nullable
    public CommandWrapper retrieve(final long commandId, final long wrapperId) {
        return toPojo(commandEntityService.retrieve(commandId, wrapperId));
    }

    @Override
    @Nonnull
    public CommandWrapper get(final long commandId, final long wrapperId) throws NotFoundException {
        return toPojo(commandEntityService.get(commandId, wrapperId));
    }

    @Override
    @Nonnull
    @Transactional
    public CommandWrapper update(final long commandId, final @Nonnull CommandWrapper toUpdate) throws CommandValidationException, NotFoundException {
        final CommandEntity commandEntity = commandEntityService.get(commandId);
        final CommandWrapperEntity template = commandEntityService.get(commandEntity, toUpdate.id());
        final CommandWrapper updated = toPojo(commandEntityService.update(template.update(toUpdate)));

        final List<String> errors = toPojo(commandEntity).validate();
        if (!errors.isEmpty()) {
            throw new CommandValidationException(errors);
        }
        return updated;
    }

    @Override
    @Transactional
    public void delete(final long commandId, final long wrapperId) {
        commandEntityService.delete(commandId, wrapperId);
    }

    @Override
    public void configureForSite(final CommandConfiguration commandConfiguration, final long commandId, final String wrapperName, final boolean enable, final String username, final String reason)
            throws CommandConfigurationException, NotFoundException {
        assertPairExists(commandId, wrapperName);
        // If the "enable" param is true, we enable the configuration.
        // Otherwise, we leave the existing "enabled" setting alone (even if it is null).
        // We will never change "enabled" to "false" here.
        final Boolean enabledStatusToSet = enable ? Boolean.TRUE : isEnabledForSite(commandId, wrapperName);
        containerConfigService.configureForSite(
                CommandConfigurationInternal.create(enabledStatusToSet, commandConfiguration),
                commandId, wrapperName, username, reason);
    }

    @Override
    public void configureForProject(final CommandConfiguration commandConfiguration, final String project, final long commandId, final String wrapperName, final boolean enable, final String username, final String reason) throws CommandConfigurationException, NotFoundException {
        assertPairExists(commandId, wrapperName);

        // If the "enable" param is true, we enable the configuration.
        // Otherwise, we leave the existing "enabled" setting alone (even if it is null).
        // We will never change "enabled" to "false" here.
        final Boolean enabledStatusToSet = enable ? Boolean.TRUE : isEnabledForProject(project, commandId, wrapperName);
        containerConfigService.configureForProject(
                CommandConfigurationInternal.create(enabledStatusToSet, commandConfiguration),
                project, commandId, wrapperName, username, reason);
    }

    @Override
    @Nullable
    public CommandConfiguration getSiteConfiguration(final long commandId, final String wrapperName) throws NotFoundException {
        assertPairExists(commandId, wrapperName);
        final CommandConfigurationInternal commandConfigurationInternal = containerConfigService.getSiteConfiguration(commandId, wrapperName);
        return CommandConfiguration.create(get(commandId), commandConfigurationInternal, wrapperName);
    }

    @Override
    @Nullable
    public CommandConfiguration getProjectConfiguration(final String project, final long commandId, final String wrapperName) throws NotFoundException {
        assertPairExists(commandId, wrapperName);
        final CommandConfigurationInternal commandConfigurationInternal = containerConfigService.getProjectConfiguration(project, commandId, wrapperName);
        return CommandConfiguration.create(get(commandId), commandConfigurationInternal, wrapperName);
    }

    @Override
    public void deleteSiteConfiguration(final long commandId, final String wrapperName, final String username) throws CommandConfigurationException {
        containerConfigService.deleteSiteConfiguration(commandId, wrapperName, username);
    }

    @Override
    public void deleteProjectConfiguration(final String project, final long commandId, final String wrapperName, final String username) throws CommandConfigurationException {
        containerConfigService.deleteProjectConfiguration(project, commandId, wrapperName, username);
    }

    @Override
    public void deleteAllConfiguration(final long commandId, final String wrapperName) {
        containerConfigService.deleteAllConfiguration(commandId, wrapperName);
    }

    @Override
    public void deleteAllConfiguration(final long commandId) {
        containerConfigService.deleteAllConfiguration(commandId);
    }

    @Override
    public void enableForSite(final long commandId, final String wrapperName, final String username, final String reason) throws CommandConfigurationException, NotFoundException {
        assertPairExists(commandId, wrapperName);
        containerConfigService.enableForSite(commandId, wrapperName, username, reason);
    }

    @Override
    public void disableForSite(final long commandId, final String wrapperName, final String username, final String reason) throws CommandConfigurationException, NotFoundException {
        assertPairExists(commandId, wrapperName);
        containerConfigService.disableForSite(commandId, wrapperName, username, reason);
    }

    @Override
    public boolean isEnabledForSite(final long commandId, final String wrapperName) throws NotFoundException {
        assertPairExists(commandId, wrapperName);
        return containerConfigService.isEnabledForSite(commandId, wrapperName);
    }

    @Override
    public void enableForProject(final String project, final long commandId, final String wrapperName, final String username, final String reason) throws CommandConfigurationException, NotFoundException {
        assertPairExists(commandId, wrapperName);
        containerConfigService.enableForProject(project, commandId, wrapperName, username, reason);
    }

    @Override
    public void disableForProject(final String project, final long commandId, final String wrapperName, final String username, final String reason) throws CommandConfigurationException, NotFoundException {
        assertPairExists(commandId, wrapperName);
        containerConfigService.disableForProject(project, commandId, wrapperName, username, reason);
    }

    @Override
    public boolean isEnabledForProject(final String project, final long commandId, final String wrapperName) throws NotFoundException {
        assertPairExists(commandId, wrapperName);
        return containerConfigService.isEnabledForProject(project, commandId, wrapperName);
    }

    @Override
    @Nonnull
    public List<CommandSummaryForContext> available(final String project,
                                                    final String xsiType,
                                                    final UserI userI) throws ElementNotFoundException {
        final List<CommandSummaryForContext> available = new ArrayList<>();

        for (final Command command : getAll()) {
            for (final CommandWrapper wrapper : command.xnatCommandWrappers()) {
                if (!xsiTypesMatch(xsiType, wrapper.contexts())) {
                    continue;
                }

                if (!userCanLaunch(userI, project, wrapper)) {
                    continue;
                }

                available.add(CommandSummaryForContext.create(command, wrapper,
                        containerConfigService.isEnabledForProject(project, command.id(), wrapper.name())));
            }
        }

        return available;
    }

    // Cache the pairs of (parent, child) xsiType relationships.
    // If child is descended from parent, return true. Else return false.
    private Map<XsiTypePair, Boolean> xsiTypePairCache = new HashMap<>();

    /**
     * Check if the xsiType that the user gave us is equal to *or* *descended* *from*
     * one of the xsiTypes in the wrapper's contexts set.
     *
     * Example
     * If a wrapper can run on {"xnat:mrSessionData", "xnat:petSessionData"}, and
     * the user asks 'what can I run on an "xnat:mrSessionData"?' we return true.
     *
     * If a wrapper can run on {"xnat:imageSessionData", "xnat:imageAssessorData"}, and
     * the user asks 'what can I run on an "xnat:mrSessionData"?' we return true.

     * If a wrapper can run on {"xnat:mrSessionData"}, and
     * the user asks 'what can I run on an "xnat:imageSessionData"?' we return false.
     *
     * @param xsiType A user asked "what commands can run on this xsiType"?
     * @param wrapperXsiTypes For a particular command wrapper, there are the xsiTypes it can run on.
     *                        This may include "parent" xsiTypes. We want all the "child" types of that
     *                        "parent" type to match as well.
     * @return Can this wrapper run on this xsiType?
     */
    private boolean xsiTypesMatch(final @Nonnull String xsiType,
                                  final @Nonnull Set<String> wrapperXsiTypes) throws ElementNotFoundException {
        if (wrapperXsiTypes.contains(xsiType)) {
            return true;
        }

        for (final String wrapperXsiType : wrapperXsiTypes) {
            final XsiTypePair xsiTypeKey = new XsiTypePair(wrapperXsiType, xsiType);

            // Return a result from the cache if it exists.
            if (xsiTypePairCache.containsKey(xsiTypeKey)) {
                final Boolean cached = xsiTypePairCache.get(xsiTypeKey);
                return cached == null ? false : cached; // Should never be null. But let's check for safety.
            }

            // Compute new result
            boolean match = false;
            try {
                match = SchemaElement.GetElement(xsiType).getGenericXFTElement().instanceOf(wrapperXsiType);
            } catch (XFTInitException e) {
                log.error("XFT not initialized."); // If this happens, we have a lot of other problems.
            }

            // Add result to cache
            xsiTypePairCache.put(xsiTypeKey, match);

            // Shortcut loop if a result is true
            if (match) {
                return true;
            }
        }
        return false;
    }

    private boolean userCanLaunch(final UserI userI, final String project, final CommandWrapper wrapper) {
        // TODO How do we know if the user can launch this command wrapper in this project?
        return true;
    }


    @Nonnull
    private Command toPojo(@Nonnull final CommandEntity commandEntity) {
        return Command.create(commandEntity);
    }

    @Nonnull
    private List<Command> toPojo(final List<CommandEntity> commandEntityList) {
        final List<Command> returnList = Lists.newArrayList();
        if (commandEntityList != null) {
            returnList.addAll(Lists.transform(commandEntityList, new Function<CommandEntity, Command>() {
                @Nullable
                @Override
                public Command apply(@Nullable final CommandEntity commandEntity) {
                    return commandEntity == null ? null : toPojo(commandEntity);
                }
            }));
        }
        return returnList;
    }

    @Nonnull
    private CommandEntity fromPojo(@Nonnull final Command command) {
        final CommandEntity template = commandEntityService.retrieve(command.id());
        return template == null ? CommandEntity.fromPojo(command) : template.update(command);
    }

    @Nonnull
    private CommandWrapperEntity fromPojo(@Nonnull final CommandWrapper commandWrapper) {
        return CommandWrapperEntity.fromPojo(commandWrapper);
    }

    @Nonnull
    private CommandWrapper toPojo(@Nonnull final CommandWrapperEntity commandWrapperEntity) {
        return CommandWrapper.create(commandWrapperEntity);
    }

    private void assertPairExists(final long commandId, final String wrapperName) throws NotFoundException {
        commandEntityService.assertPairExists(commandId, wrapperName);
    }

    private static class XsiTypePair {
        private String wrapperXsiType;
        private String userRequestedXsiType;

        XsiTypePair(final String wrapperXsiType,
                    final String userRequestedXsiType) {
            this.wrapperXsiType = wrapperXsiType;
            this.userRequestedXsiType = userRequestedXsiType;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final XsiTypePair that = (XsiTypePair) o;
            return Objects.equals(this.wrapperXsiType, that.wrapperXsiType) &&
                    Objects.equals(this.userRequestedXsiType, that.userRequestedXsiType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(wrapperXsiType, userRequestedXsiType);
        }
    }
}
