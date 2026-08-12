package com.itee.producer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Future;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Replays sample-data exception fixtures into Kafka.
 *
 * Usage: gradle :producer:run --args="exceptions.json 200"
 *   arg0 = JSON file under sample-data/ (or path)
 *   arg1 = delay between messages in ms (0 = max speed). Default 200.
 */
public final class ExceptionFeedProducer {

    private static final String TOPIC_NAME = "raw-trade-exceptions";
    private static final String BOOTSTRAP_SERVERS = "localhost:9092";

    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;

    public ExceptionFeedProducer() {
        this.producer = createKafkaProducer();
        this.objectMapper = createObjectMapper();
    }

    private KafkaProducer<String, String> createKafkaProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        return new KafkaProducer<>(props);
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    public void publishFile(String fileArg, long delayMs) throws Exception {
        Path path = resolvePath(fileArg);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("File not found: " + path.toAbsolutePath());
        }

        List<RawTradeExceptionEvent> events =
                objectMapper.readValue(path.toFile(), new TypeReference<>() {});

        System.out.println("ITETE Exception Feed Producer");
        System.out.println("File : " + path.toAbsolutePath());
        System.out.println("Count: " + events.size());
        System.out.println("Delay: " + (delayMs == 0 ? "MAX" : delayMs + " ms"));
        System.out.println("Topic: " + TOPIC_NAME);
        System.out.println("Kafka: " + BOOTSTRAP_SERVERS);
        System.out.println();

        int sent = 0;
        try {
            for (RawTradeExceptionEvent event : events) {
                String json = objectMapper.writeValueAsString(event);
                ProducerRecord<String, String> record =
                        new ProducerRecord<>(TOPIC_NAME, event.getTradeId(), json);
                Future<RecordMetadata> future = producer.send(record);
                RecordMetadata meta = future.get();
                sent++;
                System.out.printf(
                        "Sent #%d tradeId=%s type=%s partition=%d offset=%d%n",
                        sent,
                        event.getTradeId(),
                        event.getDiscrepancyType(),
                        meta.partition(),
                        meta.offset());
                if (delayMs > 0 && sent < events.size()) {
                    Thread.sleep(delayMs);
                }
            }
        } finally {
            producer.flush();
            producer.close();
        }

        System.out.println();
        System.out.println("Done. Published " + sent + " exceptions to " + TOPIC_NAME);
    }

    private Path resolvePath(String fileArg) {
        Path asGiven = Paths.get(fileArg);
        if (asGiven.isAbsolute() || Files.exists(asGiven)) {
            return asGiven;
        }
        return Paths.get("sample-data", fileArg);
    }

    public static void main(String[] args) throws Exception {
        String file = args.length > 0 ? args[0] : "exceptions.json";
        long delayMs = args.length > 1 ? Long.parseLong(args[1]) : 200L;

        ExceptionFeedProducer feed = new ExceptionFeedProducer();
        feed.publishFile(file, delayMs);
    }
}
