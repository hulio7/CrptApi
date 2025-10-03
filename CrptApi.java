import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Класс для работы с API Честного знака с поддержкой ограничения запросов
 */
public class CrptApi {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final RequestLimiter requestLimiter;
    private final String apiUrl = "https://ismp.crpt.ru/api/v3/lk/documents/create";

    /**
     * Конструктор API клиента
     * @param timeUnit единица времени для ограничения запросов
     * @param requestLimit максимальное количество запросов в указанный промежуток времени
     */
    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("requestLimit должен быть положительным числом");
        }

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        this.objectMapper = new ObjectMapper();
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        this.requestLimiter = new RequestLimiter(timeUnit, requestLimit);
    }

    /**
     * Создание документа для ввода в оборот товара, произведенного в РФ
     * @param document объект документа
     * @param signature подпись документа в виде строки
     * @return результат выполнения запроса
     */
    public ApiResponse createDocument(Document document, String signature) {
        requestLimiter.acquire();

        try {
            String requestBody = objectMapper.writeValueAsString(document);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return new ApiResponse(response.statusCode(), response.body());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Ошибка сериализации документа", e);
        } catch (IOException e) {
            throw new RuntimeException("Ошибка сети при выполнении запроса", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Запрос был прерван", e);
        }
    }

    /**
     * Класс для ограничения количества запросов
     */
    private static class RequestLimiter {
        private final long timeWindowMillis;
        private final int requestLimit;
        private final ConcurrentLinkedQueue<Long> requestTimestamps;
        private final ReentrantLock lock;

        public RequestLimiter(TimeUnit timeUnit, int requestLimit) {
            this.timeWindowMillis = timeUnit.toMillis(1);
            this.requestLimit = requestLimit;
            this.requestTimestamps = new ConcurrentLinkedQueue<>();
            this.lock = new ReentrantLock();
        }

        public void acquire() {
            lock.lock();
            try {
                cleanExpiredRequests();

                while (requestTimestamps.size() >= requestLimit) {
                    try {
                        long oldestTimestamp = requestTimestamps.peek();
                        long timeToWait = timeWindowMillis - (System.currentTimeMillis() - oldestTimestamp);

                        if (timeToWait > 0) {
                            Thread.sleep(timeToWait);
                        }

                        cleanExpiredRequests();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Ожидание было прервано", e);
                    }
                }

                requestTimestamps.offer(System.currentTimeMillis());
            } finally {
                lock.unlock();
            }
        }

        private void cleanExpiredRequests() {
            long currentTime = System.currentTimeMillis();
            long cutoffTime = currentTime - timeWindowMillis;

            while (!requestTimestamps.isEmpty() && requestTimestamps.peek() <= cutoffTime) {
                requestTimestamps.poll();
            }
        }
    }

    /**
     * Модель документа для ввода в оборот товара
     */
    public static class Document {
        private Description description;
        private String docId;
        private String docStatus;
        private String docType;
        private boolean importRequest;
        private String ownerInn;
        private String participantInn;
        private String producerInn;
        private String productionDate;
        private String productionType;
        private Product[] products;
        private String regDate;
        private String regNumber;

        // Конструкторы, геттеры и сеттеры
        public Document() {}

        public Document(Description description, String docId, String docStatus, String docType,
                        boolean importRequest, String ownerInn, String participantInn,
                        String producerInn, String productionDate, String productionType,
                        Product[] products, String regDate, String regNumber) {
            this.description = description;
            this.docId = docId;
            this.docStatus = docStatus;
            this.docType = docType;
            this.importRequest = importRequest;
            this.ownerInn = ownerInn;
            this.participantInn = participantInn;
            this.producerInn = producerInn;
            this.productionDate = productionDate;
            this.productionType = productionType;
            this.products = products;
            this.regDate = regDate;
            this.regNumber = regNumber;
        }

        // Геттеры и сеттеры
        public Description getDescription() { return description; }
        public void setDescription(Description description) { this.description = description; }

        public String getDocId() { return docId; }
        public void setDocId(String docId) { this.docId = docId; }

        public String getDocStatus() { return docStatus; }
        public void setDocStatus(String docStatus) { this.docStatus = docStatus; }

        public String getDocType() { return docType; }
        public void setDocType(String docType) { this.docType = docType; }

        public boolean isImportRequest() { return importRequest; }
        public void setImportRequest(boolean importRequest) { this.importRequest = importRequest; }

        public String getOwnerInn() { return ownerInn; }
        public void setOwnerInn(String ownerInn) { this.ownerInn = ownerInn; }

        public String getParticipantInn() { return participantInn; }
        public void setParticipantInn(String participantInn) { this.participantInn = participantInn; }

        public String getProducerInn() { return producerInn; }
        public void setProducerInn(String producerInn) { this.producerInn = producerInn; }

        public String getProductionDate() { return productionDate; }
        public void setProductionDate(String productionDate) { this.productionDate = productionDate; }public String getProductionType() { return productionType; }
        public void setProductionType(String productionType) { this.productionType = productionType; }

        public Product[] getProducts() { return products; }
        public void setProducts(Product[] products) { this.products = products; }

        public String getRegDate() { return regDate; }
        public void setRegDate(String regDate) { this.regDate = regDate; }

        public String getRegNumber() { return regNumber; }
        public void setRegNumber(String regNumber) { this.regNumber = regNumber; }
    }

    /**
     * Описание документа
     */
    public static class Description {
        private String participantInn;

        public Description() {}
        public Description(String participantInn) {
            this.participantInn = participantInn;
        }

        public String getParticipantInn() { return participantInn; }
        public void setParticipantInn(String participantInn) { this.participantInn = participantInn; }
    }

    /**
     * Информация о продукте
     */
    public static class Product {
        private String certificateDocument;
        private String certificateDocumentDate;
        private String certificateDocumentNumber;
        private String ownerInn;
        private String producerInn;
        private String productionDate;
        private String tnvedCode;
        private String uitCode;
        private String uituCode;

        public Product() {}

        public Product(String certificateDocument, String certificateDocumentDate,
                       String certificateDocumentNumber, String ownerInn, String producerInn,
                       String productionDate, String tnvedCode, String uitCode, String uituCode) {
            this.certificateDocument = certificateDocument;
            this.certificateDocumentDate = certificateDocumentDate;
            this.certificateDocumentNumber = certificateDocumentNumber;
            this.ownerInn = ownerInn;
            this.producerInn = producerInn;
            this.productionDate = productionDate;
            this.tnvedCode = tnvedCode;
            this.uitCode = uitCode;
            this.uituCode = uituCode;
        }

        // Геттеры и сеттеры
        public String getCertificateDocument() { return certificateDocument; }
        public void setCertificateDocument(String certificateDocument) { this.certificateDocument = certificateDocument; }

        public String getCertificateDocumentDate() { return certificateDocumentDate; }
        public void setCertificateDocumentDate(String certificateDocumentDate) { this.certificateDocumentDate = certificateDocumentDate; }

        public String getCertificateDocumentNumber() { return certificateDocumentNumber; }
        public void setCertificateDocumentNumber(String certificateDocumentNumber) { this.certificateDocumentNumber = certificateDocumentNumber; }

        public String getOwnerInn() { return ownerInn; }
        public void setOwnerInn(String ownerInn) { this.ownerInn = ownerInn; }

        public String getProducerInn() { return producerInn; }
        public void setProducerInn(String producerInn) { this.producerInn = producerInn; }

        public String getProductionDate() { return productionDate; }
        public void setProductionDate(String productionDate) { this.productionDate = productionDate; }

        public String getTnvedCode() { return tnvedCode; }
        public void setTnvedCode(String tnvedCode) { this.tnvedCode = tnvedCode; }

        public String getUitCode() { return uitCode; }
        public void setUitCode(String uitCode) { this.uitCode = uitCode; }

        public String getUituCode() { return uituCode; }
        public void setUituCode(String uituCode) { this.uituCode = uituCode; }
    }

    /**
     * Результат выполнения API запроса
     */
    public static class ApiResponse {
        private final int statusCode;private final String body;

        public ApiResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        public int getStatusCode() { return statusCode; }
        public String getBody() { return body; }

        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}