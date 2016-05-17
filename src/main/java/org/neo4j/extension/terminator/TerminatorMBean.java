package org.neo4j.extension.terminator;

import org.neo4j.jmx.Description;
import org.neo4j.jmx.ManagementInterface;

import java.util.Collection;

/**
 * @author Stefan Armbruster
 */
@ManagementInterface( name = TerminatorMBean.NAME )
@Description( "Terminate running transactions" )
public interface TerminatorMBean {
    String NAME = "Terminator";

    @Description("number of currently running transactions")
    int getCurrentTransactionCount();

    @Description("kill all running transactions")
    void terminateAll();

    @Description("map with valid transaction ids")
    Collection<Long> getCurrentTransactionIds();

    @Description("terminate a specific transaction")
    void terminate(long id);

}
