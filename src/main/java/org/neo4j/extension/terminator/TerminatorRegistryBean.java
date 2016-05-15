package org.neo4j.extension.terminator;

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

import javax.management.NotCompliantMBeanException;
import java.lang.reflect.Field;

/**
 * @author Stefan Armbruster
 */
@Service.Implementation(ManagementBeanProvider.class)
public class TerminatorRegistryBean extends ManagementBeanProvider {

    public TerminatorRegistryBean() {
        super(TerminatorMBean.class);
    }

    @Override
    protected Neo4jMBean createMBean(ManagementData management) throws NotCompliantMBeanException {
        return new TerminatorMBeanImpl(management);
    }

    private static class TerminatorMBeanImpl extends Neo4jMBean implements TerminatorMBean {

        private final DataSourceManager dataSourceManager;
        private Field lifeField;

        protected TerminatorMBeanImpl(ManagementData management) throws NotCompliantMBeanException {
            super(management);

            dataSourceManager = management.resolveDependency(DataSourceManager.class);
            try {
                lifeField = NeoStoreDataSource.class.getDeclaredField("life");
                lifeField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
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

        /*@Override
        public int getRunningQueriesCount() {
            return queryRegistryExtension.getTransactionEntryMap().size();
        }

        @Override
        public Collection<Map<String, Object>> getRunningQueries() {
            Collection<Map<String,Object>> retVal = new ArrayList<>();
            for (TransactionEntry entry: queryRegistryExtension.getTransactionEntryMap()) {
                try {
                    retVal.add(BeanUtils.describe(entry));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return retVal;
        }

        @Override
        public void terminate(String id) {
            queryRegistryExtension.abortQuery(id);
        }

        @Override
        public Map<String, Map<String, Object>> getStatistics() {
            Map<String, Map<String,Object>> retVal = new LinkedHashMap<>();

            for (Map.Entry<String, QueryStat> entry : statisticsExtension.getSortedStatistics().entrySet()) {
                try {
                    retVal.put(entry.getKey(), BeanUtils.describe(entry.getValue()));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return retVal;
        }

        @Override
        public void clearStatistics() {
            statisticsExtension.clear();
        }*/

    }
}
