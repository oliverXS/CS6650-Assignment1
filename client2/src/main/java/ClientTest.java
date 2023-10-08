import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.util.EntityUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author xiaorui
 */
@Slf4j
public class ClientTest {
    private static String ipAddr;
    private static final AtomicInteger totalRequests = new AtomicInteger(0);
    private static final ConcurrentLinkedDeque<Long> allLatencies = new ConcurrentLinkedDeque<>();
    private static final CSVUtility csvUtility = new CSVUtility();
    private static ApiHttpClient apiClient;
    private static final ThreadUtility threadUtility = new ThreadUtility();

    public static void main(String[] args) throws InterruptedException {
        // Check arguments
        if (args.length < Constant.ARGS_NUM) {
            log.error("Usage: java ApiClient <threadGroupSize> <numThreadGroups> <delay> <IPAddr>");
            System.exit(1);
        }

        // Initialize arguments
        int threadGroupSize = Integer.parseInt(args[0]);
        int numThreadGroups = Integer.parseInt(args[1]);
        int delay = Integer.parseInt(args[2]);
        ipAddr = args[3];
        apiClient = new ApiHttpClient(ipAddr);

        // Initial Phase
        ThreadPoolExecutor initialExecutor = threadUtility.generateInitialExecutor(Constant.INITIAL_THREADS);
        log.info("Start Initial Phase!");
        for (int i = 0; i < Constant.INITIAL_THREADS; i++) {
            HttpPost postRequest = apiClient.createPostRequest();
            HttpGet getRequest = apiClient.createGetRequest();
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
        ThreadPoolExecutor loopExecutor = threadUtility.generateLoopExecutor(threadGroupSize, numThreadGroups);
        log.info("Start Loop Phase!");
        csvUtility.initializeCSVWriter("csv_api_record_Java_" + numThreadGroups + "_" + threadGroupSize);
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < numThreadGroups; i++) {
            for (int j = 0; j < threadGroupSize; j++) {
                HttpPost postRequest = apiClient.createPostRequest();
                HttpGet getRequest = apiClient.createGetRequest();
                loopExecutor.submit(() -> {
                    for (int k = 0; k < Constant.LOOP_API_CALLS; k++) {
                        executeApiCall(postRequest);
                        executeApiCall(getRequest);
                    }
                });
            }
            if (i < numThreadGroups - 1) {
                Thread.sleep(delay * 1000L);
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
        csvUtility.closeCSVWriter();

        // Time in s
        long totalTime = endTime - startTime;
        // Time in ms
        double wallTime = totalTime / 1000.0;
        log.info("Wall Time: " + wallTime + " seconds");
        log.info("Total Requests: " + totalRequests.get() + " times");
        double throughput = totalRequests.get() / wallTime;
        log.info("Throughput: " + throughput + " request/second");

        List<Long> combinedLatencies = new ArrayList<>(allLatencies);
        computeLatencyStats(combinedLatencies);
    }

    private static void executeApiCall(HttpUriRequest request) {
        int retries = Constant.MAX_RETRIES;

        while (retries > 0) {
            long startTime = System.currentTimeMillis();
            try (CloseableHttpResponse response = apiClient.executeRequest(request)) {
                EntityUtils.consume(response.getEntity());
                long endTime = System.currentTimeMillis();
                long latency = endTime - startTime;
                allLatencies.add(latency);

                totalRequests.incrementAndGet();
                int statusCode = response.getStatusLine().getStatusCode();

                ApiRecord apiRecord = new ApiRecord(startTime, request.getMethod(), latency, statusCode);

                csvUtility.writeRecord(apiRecord);

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

    private static void computeLatencyStats(List<Long> latencies) {
        Collections.sort(latencies);
        int size = latencies.size();
        long sum = 0;
        for (long latency : latencies) {
            sum += latency;
        }
        double mean = sum / (double) size;
        double median = size % 2 == 0 ? (latencies.get(size / 2 - 1) + latencies.get(size / 2)) / 2.0 : latencies.get(size / 2);
        double p99 = latencies.get((int) (size * 0.99));
        long min = latencies.get(0);
        long max = latencies.get(size - 1);

        log.info("Mean Latency: " + mean + "ms");
        log.info("Median Latency: " + median + "ms");
        log.info("99th Percentile Latency: " + p99 + "ms");
        log.info("Min Latency: " + min + "ms");
        log.info("Max Latency: " + max + "ms");
    }
}
