package org.neo4j.extension.terminator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.jmx.JmxUtils;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TerminatorMBeanTests {

    private GraphDatabaseService graphDatabaseService;

    @Before
    public void setUp() {
        graphDatabaseService = new TestGraphDatabaseFactory().newImpermanentDatabase();
    }

    @After
    public void tearDown() {
        graphDatabaseService.shutdown();
    }

    @Test
    public void shouldTerminatorMBeanBeAvailable() throws MalformedObjectNameException {
        ObjectName objectName = JmxUtils.getObjectName( graphDatabaseService, "Terminator" );
        assertNotNull(objectName);
        assertEquals(0, JmxUtils.getAttribute( objectName, "CurrentTransactionCount" ));
    }

}
