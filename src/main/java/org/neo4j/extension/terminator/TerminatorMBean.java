package org.neo4j.extension.terminator;

import org.neo4j.jmx.Description;
import org.neo4j.jmx.ManagementInterface;

import java.util.Collection;
import java.util.Map;

/**
 * @author Stefan Armbruster
 */
@ManagementInterface( name = TerminatorMBean.NAME )
@Description( "Terminate running transactions" )
public interface TerminatorMBean {
    String NAME = "Terminator";

    @Description("number of currently running transactions")
    int getCurrentTransactionCount();

    /*@Description("list of currently running queries")
    Collection<Map<String, Object>> getRunningQueries();

    @Description("terminate a query by id")
    void terminate(String id);

    @Description("query statistics")
    Map<String, Map<String, Object>> getStatistics();

    @Description("clear statistics")
    void clearStatistics();*/

}
