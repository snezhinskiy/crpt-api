package org.sugrob.crptapiproject;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {
    private final static Logger logger = LoggerFactory.getLogger(CrptApi.class);
    private static final String CREATE_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private static final String REMOTE_SERVICE_ENCODING = "UTF-8";
    private ObjectMapper objectMapper = new ObjectMapper();
    private FairRateLimiter rateLimiter;
    private HttpClient httpClient;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.rateLimiter = new FairRateLimiter(timeUnit, requestLimit);
        this.httpClient = CrptApiHttpClientBuilder.build();
    }

    public <T> T CreateDocument(CrptDocument document, Class<T> responseClass) {
        rateLimiter.acquire();

        try {
            HttpPost httpPost = new HttpPost(CREATE_URL);
            httpPost.setEntity(new StringEntity(objectMapper.writeValueAsString(document), REMOTE_SERVICE_ENCODING));

            HttpResponse response = httpClient.execute(httpPost);
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode == HttpStatus.SC_OK) {
                String content = EntityUtils.toString(response.getEntity());
                if (StringUtils.hasText(content)) {
                    objectMapper.readValue(content, responseClass);
                }
            } else {
                logger.debug("Unexpected status code, {}", statusCode);
            }
        } catch (IOException e) {
            logger.error("Got an error", e);
        }

        return null;
    }

    public static class CrptApiHttpClientBuilder {
        private static final int CONNECTIONS_POOL_SIZE = 10;
        private static final int TIMEOUT = 5000;

        public static HttpClient build() {
            PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
            connManager.setMaxTotal(CONNECTIONS_POOL_SIZE);

            HttpClientBuilder clientBuilder = HttpClients.custom()
                .setConnectionManager(connManager)
                .setDefaultRequestConfig(
                    RequestConfig.custom()
                        .setConnectTimeout(TIMEOUT)
                        .setConnectionRequestTimeout(TIMEOUT)
                        .setSocketTimeout(TIMEOUT).build()
                );

            return clientBuilder.build();
        }
    }

    public static class FairRateLimiter {
        private final long interval;
        private final long limit;
        private final LinkedList<Long> queue;
        private ReentrantLock lock;

        public FairRateLimiter(TimeUnit timeUnit, int requestLimit) {
            interval = timeUnit.toNanos(1);
            limit = requestLimit;
            queue = new LinkedList<>();
            lock = new ReentrantLock(true);
        }

        public void acquire() {
            try {
                lock.lock();

                if (queue.size() >= limit) {
                    long availableAtNanos = queue.pollLast();

                    if (availableAtNanos > System.nanoTime()) {
                        /**
                         * Если мы попали сюда, это значит что частота запросов превышена - ждем в блокировке...
                         */
                        TimeUnit.NANOSECONDS.sleep(availableAtNanos - System.nanoTime());
                    }
                }

                queue.addFirst(System.nanoTime() + interval);

            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                lock.unlock();
            }
        }
    }

    @Getter
    @Setter
    public static class CrptDocument {
        /**
         * формате JSON документ:
         * {
         *  "description": { "participantInn": "string" },
         *  "doc_id": "string",
         *  "doc_status": "string",
         *  "doc_type": "LP_INTRODUCE_GOODS",
         *  "importRequest": true,
         *  "owner_inn": "string",
         *  "participant_inn": "string",
         *  "producer_inn": "string",
         *  "production_date": "2020-01-23",
         *  "production_type": "string",
         *  "products": [
         *      {
         *          "certificate_document": "string",
         *          "certificate_document_date": "2020-01-23",
         *          "certificate_document_number": "string",
         *          "owner_inn": "string",
         *          "producer_inn": "string",
         *          "production_date": "2020-01-23",
         *          "tnved_code": "string",
         *          "uit_code": "string",
         *          "uitu_code": "string"
         *      }
         *  ],
         * "reg_date": "2020-01-23",
         * "reg_number": "string"
         * }
         */
            private Description description;
            private String docId;
            private String docStatus;
            /**
             * Может быть enum но не в проекте где все в одном файле
             */
            private String docType;
            private boolean importRequest;
            private String ownerInn;
            private String participantInn;
            private String producerInn;
            private String productionDate;
            private String production_type;
            private List<CrptProduct> products;
            private String regDate;
            private String regNumber;

            @Getter
            @Setter
            public static class Description {
                private String participantInn;
            }

            @Getter
            @Setter
            public static class CrptProduct {
                public String certificateDocument;
                private String certificateDocumentDate;
                private String certificateDocumentNumber;
                private String ownerInn;
                private String producerInn;
                private String productionDate;
                private String tnvedCode;
                private String uitCode;
                private String uituCode;
            }
    }
}
