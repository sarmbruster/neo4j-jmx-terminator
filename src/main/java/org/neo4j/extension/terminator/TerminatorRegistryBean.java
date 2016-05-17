package org.neo4j.extension.terminator;

import org.apache.commons.configuration.Configuration;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.helpers.Service;
import org.neo4j.jmx.impl.ManagementBeanProvider;
import org.neo4j.jmx.impl.ManagementData;
import org.neo4j.jmx.impl.Neo4jMBean;
import org.neo4j.kernel.NeoStoreDataSource;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.impl.api.KernelTransactions;
import org.neo4j.kernel.impl.transaction.state.DataSourceManager;
import org.neo4j.kernel.lifecycle.LifeSupport;
import org.neo4j.kernel.lifecycle.Lifecycle;
import org.neo4j.server.NeoServer;
import org.neo4j.server.plugins.Injectable;
import org.neo4j.server.plugins.SPIPluginLifecycle;
import org.neo4j.server.rest.transactional.TransactionHandle;
import org.neo4j.server.rest.transactional.TransactionHandleRegistry;
import org.neo4j.server.rest.transactional.TransactionRegistry;
import org.neo4j.server.rest.transactional.error.TransactionLifecycleException;

import javax.management.NotCompliantMBeanException;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * @author Stefan Armbruster
 */
@Service.Implementation(ManagementBeanProvider.class)
public class TerminatorRegistryBean extends ManagementBeanProvider implements SPIPluginLifecycle {

    public static NeoServer server = null;

    public TerminatorRegistryBean() {
        super(TerminatorMBean.class);
    }

    @Override
    protected Neo4jMBean createMBean(ManagementData management) throws NotCompliantMBeanException {
        return new TerminatorMBeanImpl(management);
    }

    @Override
    public Collection<Injectable<?>> start(NeoServer neoServer) {
        server = neoServer;
        return Collections.EMPTY_LIST;
    }

    @Override
    public Collection<Injectable<?>> start(GraphDatabaseService graphDatabaseService, Configuration config) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void stop() {
    }

    private static class TerminatorMBeanImpl extends Neo4jMBean implements TerminatorMBean {

        private final DataSourceManager dataSourceManager;
        private Field registryField;
        private Field lifeField;

        protected TerminatorMBeanImpl(ManagementData management) throws NotCompliantMBeanException {
            super(management);
            dataSourceManager = management.resolveDependency(DataSourceManager.class);
            unlockPrivateFields();
        }

        @Override
        public int getCurrentTransactionCount() {
            KernelTransactions kernelTransactions = getKernelTransactions();
            return kernelTransactions.activeTransactions().size();
        }

        @Override
        public void terminateAll() {
            for (KernelTransaction transaction: getKernelTransactions ().activeTransactions()) {
                transaction.markForTermination();
            }
        }

        @Override
        public Collection<Long> getCurrentTransactionIds() {
            assertServer();
            TransactionHandleRegistry transactionRegistry = (TransactionHandleRegistry) server.getTransactionRegistry();
            try {
                Map<Long, Object> registry = (Map<Long, Object>) registryField.get(transactionRegistry);
                return registry.keySet();
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void terminate(long id) {
            assertServer();
            TransactionRegistry transactionRegistry = server.getTransactionRegistry();
            try {
                TransactionHandle handle = transactionRegistry.terminate(id);
                transactionRegistry.release(id, handle);
            } catch (TransactionLifecycleException e) {
                throw new RuntimeException(e);
            }
        }

        private void unlockPrivateFields() {
            try {
                lifeField = NeoStoreDataSource.class.getDeclaredField("life");
                lifeField.setAccessible(true);

                registryField = TransactionHandleRegistry.class.getDeclaredField("registry");
                registryField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }

        private void assertServer() {
            if (server==null) {
                throw new RuntimeException("found no `server` instance, maybe you've forgotton to register the unmanaged extension?");
            }
        }

        private KernelTransactions getKernelTransactions() {
            NeoStoreDataSource dataSource = dataSourceManager.getDataSource();
            try {
                LifeSupport l = (LifeSupport) lifeField.get(dataSource);
                for (Lifecycle lc : l.getLifecycleInstances()) {
                    if (lc instanceof KernelTransactions) {
                        return (KernelTransactions) lc;
                    }
                }
                return null;
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

    }
}
