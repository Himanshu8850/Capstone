package com.bookygeek.contract;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Checks real HTTP responses against the approved {@code api/openapi.yaml} (hand-written contract assertions, as the
 * plan chose; no extra library). For an {@code operationId} it verifies that the status code is declared and that the
 * JSON body satisfies the declared schema: types, {@code required}, {@code enum}, string length and pattern, array
 * items, {@code format} {@code email} and {@code date-time}, and, because the contract is closed, that no undeclared
 * property is present (so a leaked field such as a password hash fails the test).
 */
final class ContractSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Map<String, Object> SPEC = load();

    private ContractSupport() {
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load() {
        Path path = locate();
        try (InputStream in = Files.newInputStream(path)) {
            return (Map<String, Object>) new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read " + path, ex);
        }
    }

    /** Surefire/Failsafe run from {@code backend/}; the contract is in {@code ../api/}. */
    private static Path locate() {
        for (String candidate : List.of("../api/openapi.yaml", "api/openapi.yaml")) {
            Path path = Path.of(candidate);
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        throw new IllegalStateException("api/openapi.yaml not found from " + Path.of("").toAbsolutePath());
    }

    // ---- operations -----------------------------------------------------------------------------------------------

    /** The responses object of the operation with this {@code operationId}. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> responsesOf(String operationId) {
        Map<String, Object> paths = (Map<String, Object>) SPEC.get("paths");
        for (Object pathItem : paths.values()) {
            for (Object operation : ((Map<String, Object>) pathItem).values()) {
                if (operation instanceof Map<?, ?> op && operationId.equals(op.get("operationId"))) {
                    return (Map<String, Object>) op.get("responses");
                }
            }
        }
        throw new AssertionError("operationId not in api/openapi.yaml: " + operationId);
    }

    /** Fails unless the status is declared for the operation and (when it has a body schema) the body conforms. */
    static void assertResponse(String operationId, int status, String body) {
        Map<String, Object> responses = responsesOf(operationId);
        Object declared = responses.get(Integer.toString(status));
        if (declared == null) {
            throw new AssertionError(operationId + " does not declare status " + status + "; declared: "
                    + responses.keySet());
        }
        Object schema = jsonSchemaOf(resolve(declared));
        if (schema == null) {
            if (body != null && !body.isBlank()) {
                throw new AssertionError(operationId + " " + status + " declares no body but returned: " + body);
            }
            return;
        }
        JsonNode node;
        try {
            node = JSON.readTree(body);
        } catch (RuntimeException ex) {
            throw new AssertionError(operationId + " " + status + " returned a non-JSON body", ex);
        }
        List<String> problems = new ArrayList<>();
        validate(schema, node, "$", problems);
        if (!problems.isEmpty()) {
            throw new AssertionError(operationId + " " + status + " violates the contract:\n  "
                    + String.join("\n  ", problems) + "\nbody: " + body);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object jsonSchemaOf(Object response) {
        Object content = ((Map<String, Object>) response).get("content");
        if (content == null) {
            return null;
        }
        Object json = ((Map<String, Object>) content).get("application/json");
        return json == null ? null : ((Map<String, Object>) json).get("schema");
    }

    // ---- schema validation ----------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Object resolve(Object node) {
        Object current = node;
        while (current instanceof Map<?, ?> map && map.containsKey("$ref")) {
            current = lookup((String) map.get("$ref"));
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private static Object lookup(String ref) {
        if (!ref.startsWith("#/")) {
            throw new IllegalStateException("Only local references are supported: " + ref);
        }
        Object current = SPEC;
        for (String part : ref.substring(2).split("/")) {
            current = ((Map<String, Object>) current).get(part);
            if (current == null) {
                throw new IllegalStateException("Dangling reference " + ref);
            }
        }
        return current;
    }

    /**
     * Merges the {@code allOf} members of a schema (recursively) into one closed object schema: the union of their
     * {@code properties} and {@code required}. {@code oneOf} members are not merged; they are checked separately.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> flatten(Object rawSchema) {
        Map<String, Object> schema = (Map<String, Object>) resolve(rawSchema);
        if (!(schema.get("allOf") instanceof List<?> parts)) {
            return schema;
        }
        Map<String, Object> merged = new java.util.LinkedHashMap<>(schema);
        merged.remove("allOf");
        Map<String, Object> properties = new java.util.LinkedHashMap<>(
                (Map<String, Object>) schema.getOrDefault("properties", Map.of()));
        List<Object> required = new ArrayList<>((List<Object>) schema.getOrDefault("required", List.of()));
        for (Object part : parts) {
            Map<String, Object> flat = flatten(part);
            properties.putAll((Map<String, Object>) flat.getOrDefault("properties", Map.of()));
            required.addAll((List<Object>) flat.getOrDefault("required", List.of()));
            if (flat.get("type") != null) {
                merged.put("type", flat.get("type"));
            }
            if (flat.get("oneOf") != null) {
                merged.put("oneOf", flat.get("oneOf"));
            }
        }
        merged.put("properties", properties);
        merged.put("required", required);
        return merged;
    }

    /** A {@code oneOf} option constrains only the properties it names; it is not a closed object on its own. */
    @SuppressWarnings("unchecked")
    private static void validateOption(Map<String, Object> option, JsonNode value, String at, List<String> problems) {
        if (option.get("required") instanceof List<?> required) {
            for (Object name : required) {
                if (!value.has((String) name)) {
                    problems.add(at + ": missing required property '" + name + "'");
                }
            }
        }
        Map<String, Object> properties = (Map<String, Object>) option.getOrDefault("properties", Map.of());
        properties.forEach((name, schema) -> {
            if (value.has(name)) {
                validate(schema, value.get(name), at + "." + name, problems);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static void validate(Object rawSchema, JsonNode value, String at, List<String> problems) {
        Map<String, Object> schema = flatten(rawSchema);
        if (schema.get("allOf") == null && schema.get("type") == null && schema.get("properties") != null) {
            schema = new java.util.LinkedHashMap<>(schema);
            schema.put("type", "object");
        }
        if (value == null || value.isNull()) {
            if (!Boolean.TRUE.equals(schema.get("nullable"))) {
                problems.add(at + ": null is not allowed");
            }
            return;
        }
        String type = (String) schema.get("type");
        if (type != null && !typeMatches(type, value)) {
            problems.add(at + ": expected " + type + " but was " + value.getNodeType());
            return;
        }
        if (schema.get("enum") instanceof List<?> allowed
                && allowed.stream().noneMatch(a -> String.valueOf(a).equals(value.asString()))) {
            problems.add(at + ": '" + value.asString() + "' is not one of " + allowed);
        }
        if (value.isNumber() && schema.get("minimum") instanceof Number min
                && value.asDouble() < min.doubleValue()) {
            problems.add(at + ": " + value.asString() + " is below the minimum " + min);
        }
        if ("string".equals(type)) {
            validateString(schema, value.asString(), at, problems);
        }
        if ("object".equals(type)) {
            validateObject(schema, value, at, problems);
            if (schema.get("oneOf") instanceof List<?> options) {
                validateOneOf(options, value, at, problems);
            }
        }
        if ("array".equals(type)) {
            if (schema.get("maxItems") instanceof Number max && value.size() > max.intValue()) {
                problems.add(at + ": more than " + max + " items");
            }
            if (schema.get("items") != null) {
                for (int i = 0; i < value.size(); i++) {
                    validate(schema.get("items"), value.get(i), at + "[" + i + "]", problems);
                }
            }
        }
    }

    /** Exactly one {@code oneOf} option must accept the value; when none does, every option's problems are listed. */
    @SuppressWarnings("unchecked")
    private static void validateOneOf(List<?> options, JsonNode value, String at, List<String> problems) {
        int matching = 0;
        List<String> rejected = new ArrayList<>();
        for (Object option : options) {
            List<String> own = new ArrayList<>();
            validateOption((Map<String, Object>) resolve(option), value, at, own);
            if (own.isEmpty()) {
                matching++;
            } else {
                rejected.addAll(own);
            }
        }
        if (matching != 1) {
            problems.add(at + ": must match exactly one of " + options.size() + " options but matched " + matching
                    + " (" + String.join("; ", rejected) + ")");
        }
    }

    private static boolean typeMatches(String type, JsonNode value) {
        return switch (type) {
            case "string" -> value.isString();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            default -> true;
        };
    }

    private static void validateString(Map<String, Object> schema, String text, String at, List<String> problems) {
        if (schema.get("minLength") instanceof Number min && text.length() < min.intValue()) {
            problems.add(at + ": shorter than " + min);
        }
        if (schema.get("maxLength") instanceof Number max && text.length() > max.intValue()) {
            problems.add(at + ": longer than " + max);
        }
        if (schema.get("pattern") instanceof String regex && !Pattern.compile(regex).matcher(text).find()) {
            problems.add(at + ": does not match " + regex);
        }
        String format = (String) schema.get("format");
        if ("email".equals(format) && !text.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            problems.add(at + ": not an e-mail address");
        }
        if ("date-time".equals(format)) {
            try {
                Instant.parse(text);
            } catch (RuntimeException ex) {
                problems.add(at + ": not an RFC 3339 date-time: " + text);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateObject(Map<String, Object> schema, JsonNode value, String at, List<String> problems) {
        Map<String, Object> properties = (Map<String, Object>) schema.getOrDefault("properties", Map.of());
        if (schema.get("required") instanceof List<?> required) {
            for (Object name : required) {
                if (!value.has((String) name)) {
                    problems.add(at + ": missing required property '" + name + "'");
                }
            }
        }
        value.propertyNames().forEach(name -> {
            if (!properties.containsKey(name)) {
                problems.add(at + "." + name + ": property is not declared in the contract");
            } else {
                validate(properties.get(name), value.get(name), at + "." + name, problems);
            }
        });
    }
}
