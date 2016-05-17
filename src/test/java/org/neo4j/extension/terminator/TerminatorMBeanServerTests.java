package org.neo4j.extension.terminator;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.neo4j.harness.internal.InProcessServerControls;
import org.neo4j.harness.junit.Neo4jRule;
import org.neo4j.jmx.JmxUtils;
import org.neo4j.kernel.GraphDatabaseAPI;
import org.neo4j.server.AbstractNeoServer;
import org.neo4j.server.configuration.ServerSettings;
import org.neo4j.test.server.HTTP;

import javax.management.ObjectName;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.lang.System.out;
import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.neo4j.helpers.collection.MapUtil.map;

public class TerminatorMBeanServerTests {

    @Rule
    public Neo4jRule neo4j = new Neo4jRule()
            .withConfig(ServerSettings.webserver_max_threads, "4")
            .withExtension("/dummy", "org.neo4j.extension.terminator");
    private GraphDatabaseAPI graphDatabaseAPI;
    private ObjectName objectName;

    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        Field controlsField = Neo4jRule.class.getDeclaredField("controls");
        controlsField.setAccessible(true);
        Field serverField = InProcessServerControls.class.getDeclaredField("server");
        serverField.setAccessible(true);

        InProcessServerControls controls = (InProcessServerControls) controlsField.get(neo4j);
        AbstractNeoServer server = (AbstractNeoServer) serverField.get(controls);
        graphDatabaseAPI = server.getDatabase().getGraph();
        objectName = JmxUtils.getObjectName( graphDatabaseAPI, "Terminator" );

    }

    @Test
    public void shouldTransactionalEndpointTransactionShowUpInJmx() throws InterruptedException {

        // given
        graphDatabaseAPI.execute("CREATE (:Person{name: 'John Doe'})");

        // when
        HTTP.Response response = HTTP.POST(neo4j.httpURI().toString() + "db/data/transaction", map("statements", asList(map("statement", "MATCH (n) RETURN n"))));
        String location = response.location();
        String[] parts = location.split("/");
        String transactionId = parts[parts.length-1];

        // then
        assertEquals(1, JmxUtils.getAttribute( objectName, "CurrentTransactionCount" ));
        assertEquals(1, ((Collection)JmxUtils.getAttribute( objectName, "CurrentTransactionIds" )).size());

        // when
        JmxUtils.invoke(objectName, "terminate", new Object[]{1l}, new String[]{"long"});

        response = HTTP.POST(neo4j.httpURI().toString() + "db/data/transaction/" + transactionId + "/commit", map("statements", asList(map("statement", "MATCH (n) RETURN n"))));
        assertEquals(200, response.status());
        Map contentMap = response.content();
        List<Map<String,Object>> errors = (List<Map<String, Object>>) contentMap.get("errors");
        assertEquals(1, errors.size());
        assertEquals("The transaction has been terminated.", errors.get(0).get("message"));

        // then
        assertEquals(0, JmxUtils.getAttribute( objectName, "CurrentTransactionCount" ));
        assertEquals(0, ((Collection)JmxUtils.getAttribute( objectName, "CurrentTransactionIds" )).size());
    }


    // expected to fail until kernel checks for transaction terminated while waiting for locks
    @Ignore
    @Test
    public void testJmxKillWithSaturatedJetty() throws InterruptedException {
        // given
        graphDatabaseAPI.execute("CREATE (:Person{name: 'John Doe'})");
        long started = System.currentTimeMillis();

        Collection<Thread> threads = new ConcurrentLinkedQueue<>();
        final Collection<Long> transactionIds = new ConcurrentLinkedQueue<>();
        for (int i=0; i < 10; i++) {
            threads.add(new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        out.printf("%d %s started\n", System.currentTimeMillis(), Thread.currentThread().getName());
                        HTTP.Response response = HTTP.POST(neo4j.httpURI().toString() + "db/data/transaction", map("statements", asList(map("statement", "MATCH (p:Person{name:'John Doe'}) SET p.accessed = p.accessed+1 RETURN p"))));
                        String location = response.location();
                        String[] parts = location.split("/");
                        String transactionId = parts[parts.length-1];
                        transactionIds.add(Long.parseLong(transactionId));

                        Thread.sleep(1000);

                        response = HTTP.POST(neo4j.httpURI().toString() + "db/data/transaction/" + transactionId + "/commit", map("statements", Collections.EMPTY_LIST));
                        out.printf("%d %s finished %d\n", System.currentTimeMillis(), Thread.currentThread().getName(), response.status());
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }));
        }
        for (Thread t: threads) {
            t.start();
        }

        // wait until we have all transactionIds
        while (transactionIds.size()!=threads.size()) {
            Thread.sleep(5);
        }

        // killall
        JmxUtils.invoke(objectName, "terminateAll", new Object[0], new String[0] );

        // wait
        for (Thread t: threads) {
            t.join();
        }

        assertThat(10_000l, greaterThan(System.currentTimeMillis()-started));

        graphDatabaseAPI.execute("MATCH (p:Person{name:'John Doe'}) RETURN p").writeAsStringTo(new PrintWriter(System.out));
    }

}


