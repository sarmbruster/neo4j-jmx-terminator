package org.neo4j.extension.terminator;

import javax.ws.rs.POST;
import javax.ws.rs.Path;

/**
 * this unmanaged extension class is intentionally empty. Since {@link TerminatorRegistryBean}
 * is a {@link org.neo4j.server.plugins.SPIPluginLifecycle} this packages needs to have an
 * unmanaged extension class, otherwise we would get an error upon startup
 */
@Path("/")
public class FakeExtension {

    @POST
    public void doNothing() {
    }
}
