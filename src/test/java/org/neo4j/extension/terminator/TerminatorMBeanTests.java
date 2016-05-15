package org.neo4j.extension.terminator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.DynamicLabel;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.jmx.JmxUtils;
import org.neo4j.test.TestGraphDatabaseFactory;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import static org.junit.Assert.*;

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

    @Test
    public void shouldTerminateAnEndlessTransaction() throws InterruptedException {
        // given
        graphDatabaseService.execute("create (:Person{name:'John'})");
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try (Transaction tx = graphDatabaseService.beginTx()) {
                    while (true) {
                        Thread.sleep(5);
                        Node n = graphDatabaseService.findNode(DynamicLabel.label("Person"), "name", "John");
                        if (n != null) {
                            String name = (String) n.getProperty("name");
                        }
                    }
//                    tx.success();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        thread.start();
        ObjectName objectName = JmxUtils.getObjectName( graphDatabaseService, "Terminator" );
        assertTrue(thread.isAlive());
        assertEquals(1, JmxUtils.getAttribute( objectName, "CurrentTransactionCount" ));

        // when
        JmxUtils.invoke(objectName, "terminateAll", new Object[0], new String[0] );

        // then
        Thread.sleep(20);
        assertFalse(thread.isAlive());

    }
}


