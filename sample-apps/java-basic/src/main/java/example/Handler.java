package example;

import static glide.api.logging.Logger.Level.ERROR;
import static glide.api.logging.Logger.Level.INFO;
import static glide.api.logging.Logger.Level.WARN;
import static glide.api.logging.Logger.log;
import static glide.api.models.configuration.RequestRoutingConfiguration.ByAddressRoute;

import glide.api.GlideClusterClient;
import glide.api.logging.Logger;
import glide.api.models.ClusterValue;
import glide.api.models.commands.InfoOptions.Section;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.ConnectionException;
import glide.api.models.exceptions.TimeoutException;

import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import java.util.Map;

// Handler value: example.Handler
public class Handler implements RequestHandler<Map<String,String>, Void>{

public static GlideClusterClient createClient(List<NodeAddress> nodeList)
            throws CancellationException, ExecutionException, InterruptedException {
        // Check `GlideClusterClientConfiguration` for additional options.
        GlideClusterClientConfiguration config =
                GlideClusterClientConfiguration.builder()
                        .addresses(nodeList)
                        // Enable this field if the servers are configured with TLS.
                        // .useTLS(true);
                        .build();

        GlideClusterClient client = GlideClusterClient.createClient(config).get();
        return client;
    }

    /**
     * Executes the main logic of the application, performing basic operations such as SET, GET, PING,
     * and INFO REPLICATION using the provided <code>GlideClusterClient</code>.
     *
     * @param client An instance of <code>GlideClusterClient</code>.
     * @throws ExecutionException if an execution error occurs during operations.
     * @throws InterruptedException if the operation is interrupted.
     */
    public static void appLogic(GlideClusterClient client)
            throws ExecutionException, InterruptedException {

        // Send SET and GET
        CompletableFuture<String> setResponse = client.set("foo", "bar");
        log(INFO, "app", "Set response is " + setResponse.get());

        CompletableFuture<String> getResponse = client.get("foo");
        log(INFO, "app", "Get response is " + getResponse.get());

        // Send PING to all primaries (according to Valkey's PING request_policy)
        Object[] shardsResponse = (Object[]) client.customCommand(new String[] {"CLUSTER", "SHARDS"}).get().getSingleValue();
        HashMap<Object, Object> foo = (HashMap<Object, Object>) shardsResponse[0];
        Object[] bar = (Object[]) foo.get("nodes");
        HashMap<Object, Object> baz = (HashMap<Object, Object>) bar[0];
        log(INFO, "app", "shards response: " + baz.get("port"));

        // Send INFO REPLICATION with routing option to all nodes
        ClusterValue<String> infoResponse =
                client
                        .info(new ByAddressRoute("127.0.0.1", 7004))
                        .get();
        log(
                INFO,
                "app",
                "INFO REPLICATION responses from all nodes are " + infoResponse.getSingleValue());
    }

    /**
     * Executes the application logic with exception handling.
     *
     * @throws ExecutionException if an execution error occurs during operations.
     */
    private static void execAppLogic() throws ExecutionException {

        // Define list of nodes
        List<NodeAddress> nodeList =
                Collections.singletonList(NodeAddress.builder().host("localhost").port(7000).build());

        while (true) {
            try (GlideClusterClient client = createClient(nodeList)) {
                appLogic(client);
                return;
            } catch (CancellationException e) {
                log(ERROR, "glide", "Request cancelled: " + e.getMessage());
                throw e;
            } catch (InterruptedException e) {
                log(ERROR, "glide", "Client interrupted: " + e.getMessage());
                Thread.currentThread().interrupt(); // Restore interrupt status
                throw new CancellationException("Client was interrupted.");
            } catch (ExecutionException e) {
                // All Glide errors will be handled as ExecutionException
                if (e.getCause() instanceof ClosingException) {
                    // If the error message contains "NOAUTH", raise the exception
                    // because it indicates a critical authentication issue.
                    if (e.getMessage().contains("NOAUTH")) {
                        log(ERROR, "glide", "Authentication error encountered: " + e.getMessage());
                        throw e;
                    } else {
                        log(WARN, "glide", "Client has closed and needs to be re-created: " + e.getMessage());
                    }
                } else if (e.getCause() instanceof ConnectionException) {
                    // The client wasn't able to reestablish the connection within the given retries
                    log(ERROR, "glide", "Connection error encountered: " + e.getMessage());
                    throw e;
                } else if (e.getCause() instanceof TimeoutException) {
                    // A request timed out. You may choose to retry the execution based on your application's
                    // logic
                    log(ERROR, "glide", "Timeout encountered: " + e.getMessage());
                    throw e;
                } else {
                    log(ERROR, "glide", "Execution error encountered: " + e.getCause());
                    throw e;
                }
            }
        }
    }

  @Override
  public Void handleRequest(Map<String,String> event, Context context)
  {
    try {
    Logger.setLoggerConfig(INFO);
    execAppLogic();

    LambdaLogger logger = context.getLogger();
    logger.log("EVENT TYPE: " + event.getClass());
    } catch (ExecutionException e) {}
    return null;
  }
}
