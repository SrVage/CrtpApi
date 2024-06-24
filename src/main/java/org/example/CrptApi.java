package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.*;

public class CrptApi {
    public static class Document {
        public Description description;
        public String doc_id;
        public String doc_status;
        public String doc_type;
        public boolean importRequest;
        public String owner_inn;
        public String participant_inn;
        public String producer_inn;
        public String production_date;
        public String production_type;
        public Product[] products;
        public String reg_date;
        public String reg_number;

        public Document() {
            this.description = new Description();
            this.doc_id = "string";
            this.doc_status = "string";
            this.doc_type = "LP_INTRODUCE_GOODS";
            this.importRequest = true;
            this.owner_inn = "string";
            this.participant_inn = "string";
            this.producer_inn = "string";
            this.production_date = "2020-01-23";
            this.production_type = "string";
            this.products = new Product[]{
                    new Product()
            };
            this.reg_date = "2020-01-23";
            this.reg_number = "string";
        }

        public static class Description {
            public String participantInn;

            public Description() {
                this.participantInn = "string";
            }
        }

        public static class Product {
            public String certificate_document;
            public String certificate_document_date;
            public String certificate_document_number;
            public String owner_inn;
            public String producer_inn;
            public String production_date;
            public String tnved_code;
            public String uit_code;
            public String uitu_code;

            public Product() {
                this.certificate_document = "string";
                this.certificate_document_date = "2020-01-23";
                this.certificate_document_number = "string";
                this.owner_inn = "string";
                this.producer_inn = "string";
                this.production_date = "2020-01-23";
                this.tnved_code = "string";
                this.uit_code = "string";
                this.uitu_code = "string";
            }
        }
    }

    public enum Messages {
        CREATE_ERROR("Ошибка в создании документа:");

        private final String message;

        Messages(String message) {
            this.message = message;
        }

        @Override
        public String toString() {
            return message;
        }
    }

    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private final BlockingQueue<Runnable> taskQueue;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        executor = Executors.newFixedThreadPool(requestLimit);
        taskQueue = new LinkedBlockingQueue<>();
        var intervalMillis = timeUnit.toMillis(1);
        for (int i = 0; i < requestLimit; i++) {
            executor.submit(() -> processTasks(intervalMillis));
        }
    }

    private void processTasks(long interval) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Runnable task = taskQueue.take();
                task.run();
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void send(Document document, String sign) {
        try {
            taskQueue.put(() -> {
                try {
                    String json = objectMapper.writeValueAsString(document);
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(new URI(API_URL))
                            .header("Content-Type", "application/json")
                            .header("Signature", sign)
                            .timeout(Duration.ofSeconds(30))
                            .POST(HttpRequest.BodyPublishers.ofString(json))
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() != 200) {
                        throw new RuntimeException(Messages.CREATE_ERROR + response.body());
                    }
                } catch (URISyntaxException | IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        }
    }
}
