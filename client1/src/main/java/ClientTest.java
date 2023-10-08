import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.util.EntityUtils;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author xiaorui
 */
@Slf4j
public class ClientTest {
    private static String ipAddr;
    private static ApiHttpClient apiClient;
    private static AtomicInteger totalRequests = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        if (args.length < Constant.ARGS_NUM) {
            log.error("Usage: java ApiClient <threadGroupSize> <numThreadGroups> <delay> <IPAddr>");
            System.exit(1);
        }

        int threadGroupSize = Integer.parseInt(args[0]);
        int numThreadGroups = Integer.parseInt(args[1]);
        int delay = Integer.parseInt(args[2]);
        ipAddr = args[3];
        apiClient = new ApiHttpClient(ipAddr);

        // Initial phase
        ExecutorService initialExecutor = Executors.newFixedThreadPool(Constant.INITIAL_THREADS);
        log.info("Start Initial Phase!");
        for (int i = 0; i < Constant.INITIAL_THREADS; i++) {
            HttpGet getRequest = apiClient.createGetRequest();
            HttpPost postRequest = apiClient.createPostRequest();
            initialExecutor.submit(() -> {
                for (int j = 0; j < Constant.INITIAL_API_CALLS; j++) {
                    // Call POST
                    try (CloseableHttpResponse postResponse = apiClient.executeRequest(postRequest)) {
                        EntityUtils.consume(postResponse.getEntity());
                    } catch (Exception e) {
                        log.error("Exception occurred in initial phase: POST");
                        throw new RuntimeException(e);
                    }
                    // Call GET
                    try (CloseableHttpResponse getResponse = apiClient.executeRequest(getRequest)) {
                        EntityUtils.consume(getResponse.getEntity());
                    } catch (Exception e) {
                        log.error("Exception occurred in initial phase: GET");
                        throw new RuntimeException(e);
                    }
                }
            });
        }
        initialExecutor.shutdown();
        try {
            initialExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Main thread interrupted while waiting for task completion", e);
        }
        log.info("End Initial Phase!");

        // Loop phase
        ExecutorService loopExecutor = Executors.newFixedThreadPool(threadGroupSize * numThreadGroups);
        log.info("Start Loop Phase!");
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < numThreadGroups; i++) {
            for (int j = 0; j < threadGroupSize; j++) {
                HttpGet getRequest = apiClient.createGetRequest();
                HttpPost postRequest = apiClient.createPostRequest();
                loopExecutor.submit(() -> {
                    for (int k = 0; k < Constant.LOOP_API_CALLS; k++) {
                        executeApiCall(postRequest);
                        executeApiCall(getRequest);
                    }
                });
            }

            if (i < numThreadGroups - 1) {
                try {
                    Thread.sleep(delay * 1000L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        loopExecutor.shutdown();
        try {
            loopExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Main thread interrupted while waiting for task completion", e);
        }
        log.info("End Loop Phase!");

        long endTime = System.currentTimeMillis();

        // Time in ms
        long totalTime = endTime - startTime;
        // Time in s
        double wallTime = totalTime / 1000.0;
        log.info("Wall Time: " + wallTime + " seconds");
        log.info("Total Requests: " + totalRequests.get() + " times");
        double throughput = totalRequests.get() / wallTime;
        log.info("Throughput: " + throughput + " requests/second");
    }

    private static void executeApiCall(HttpUriRequest request) {
            int retries = Constant.MAX_RETRIES;

            while (retries > 0) {
                try (CloseableHttpResponse response = apiClient.executeRequest(request)) {
                    EntityUtils.consume(response.getEntity());
                    totalRequests.incrementAndGet();

                    int statusCode = response.getStatusLine().getStatusCode();

                    if (statusCode >= 200 && statusCode < 300) {
                        return;
                    } else if (statusCode >= 400 && statusCode < 600) {
                        retries--;
                        if (retries == 0) {
                            log.error("Failed to execute request after 5 retries. URL: " + request.getURI() + request.getMethod());
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    log.info("Exception in Loop phase: " + request.getMethod());
                }
            }
    }


}
