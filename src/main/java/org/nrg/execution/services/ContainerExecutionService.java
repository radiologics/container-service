package org.nrg.execution.services;

import org.nrg.execution.events.DockerContainerEvent;
import org.nrg.execution.model.ContainerExecution;
import org.nrg.execution.model.ResolvedCommand;
import org.nrg.framework.orm.hibernate.BaseHibernateService;
import org.nrg.xft.security.UserI;

public interface ContainerExecutionService extends BaseHibernateService<ContainerExecution> {
    void processEvent(final DockerContainerEvent event);
    void finalize(final Long containerExecutionId, final UserI userI);
    void finalize(final ContainerExecution containerExecution, final UserI userI);
    ContainerExecution save(final ResolvedCommand resolvedCommand,
                            final String containerId,
                            final String rootObjectId,
                            final String rootObjectXsiType,
                            final UserI userI);
}
