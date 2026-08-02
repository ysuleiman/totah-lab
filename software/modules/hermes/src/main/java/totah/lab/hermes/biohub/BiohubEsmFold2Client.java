package totah.lab.hermes.biohub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import totah.lab.http.biohub.BiohubEsmFold2Config;
import totah.lab.http.biohub.JdkBiohubHttpTransport;
import totah.lab.http.biohub.model.AtomComplex;
import totah.lab.http.biohub.model.ComplexToken;
import totah.lab.http.biohub.model.MolecularComplexPrediction;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class BiohubEsmFold2Client {

    public static final String DEFAULT_MODEL = "esmfold2-fast-2026-05";
    private static final String PROVIDER = "BIOHUB_ESMFOLD2";

    private final BiohubClientConfig clientConfig;
    private final String model;
    private final BiohubHttpTransport transport;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public BiohubEsmFold2Client(BiohubClientConfig clientConfig) {
        this(
                clientConfig,
                DEFAULT_MODEL,
                new JdkBiohubHttpTransport(clientConfig.requestTimeout()),
                new ObjectMapper(),
                Clock.systemUTC()
        );
    }

    BiohubEsmFold2Client(
            BiohubClientConfig clientConfig,
            String model,
            BiohubHttpTransport transport,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.clientConfig = Objects.requireNonNull(
                clientConfig,
                "clientConfig"
        );
        this.model = requireText(model, "model");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper"
        );
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public MolecularComplexPrediction foldProteinLigand(
            String sequence,
            String ligandCcd,
            BiohubEsmFold2Config foldingConfig
    ) throws IOException, InterruptedException {
        String normalizedSequence = normalizeSequence(sequence);
        String normalizedCcd = requireText(ligandCcd, "ligandCcd")
                .toUpperCase(Locale.ROOT);
        return foldProteinLigand(
                normalizedSequence,
                normalizedCcd,
                null,
                foldingConfig
        );
    }

    public MolecularComplexPrediction foldProteinSmiles(
            String sequence,
            String ligandId,
            String smiles,
            BiohubEsmFold2Config foldingConfig
    ) throws IOException, InterruptedException {
        return foldProteinLigand(
                normalizeSequence(sequence),
                requireText(ligandId, "ligandId"),
                requireText(smiles, "smiles"),
                foldingConfig
        );
    }

    private MolecularComplexPrediction foldProteinLigand(
            String normalizedSequence,
            String ligandId,
            String smiles,
            BiohubEsmFold2Config foldingConfig
    ) throws IOException, InterruptedException {
        Objects.requireNonNull(foldingConfig, "foldingConfig");

        ObjectNode request = createRequest(
                normalizedSequence,
                ligandId,
                smiles,
                foldingConfig
        );
        URI endpoint = clientConfig.baseUri().resolve(
                "/api/v1/fold_all_atom"
        );
        BiohubHttpTransport.Response response = transport.post(
                endpoint,
                clientConfig.apiToken(),
                clientConfig.requestTimeout(),
                objectMapper.writeValueAsString(request)
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(
                    "BioHub fold_all_atom failed with HTTP "
                            + response.statusCode() + ": "
                            + abbreviate(response.body())
            );
        }
        JsonNode payload = objectMapper.readTree(response.body());
        if (!payload.has("complex") && payload.has("data")) {
            payload = payload.get("data");
        }
        return parsePrediction(payload, ligandId);
    }

    private ObjectNode createRequest(
            String sequence,
            String ligandId,
            String smiles,
            BiohubEsmFold2Config config
    ) {
        ObjectNode request = objectMapper.createObjectNode();
        ObjectNode allAtomInput = request.putObject("all_atom_input");
        ArrayNode sequences = allAtomInput.putArray("sequences");
        ObjectNode protein = sequences.addObject();
        protein.put("sequence", sequence);
        protein.put("id", "A");
        protein.put("type", "protein");
        protein.putNull("msa");
        ObjectNode ligand = sequences.addObject();
        ligand.put("id", "L");
        if (smiles == null) {
            ligand.putNull("smiles");
            ligand.putArray("ccd").add(ligandId);
        } else {
            ligand.put("smiles", smiles);
        }
        ligand.put("type", "ligand");

        request.put("model", model);
        request.put("include_distogram", false);
        request.put("include_pae", false);
        request.put("num_sampling_steps", config.numSamplingSteps());
        request.put("num_loops", config.numLoops());
        request.put("lm_dropout", config.lmDropout());
        request.put("lm_mask_pct", 0.1);
        request.put("msa_max_depth", 1024);
        request.put(
                "msa_column_mask_rate",
                config.msaColumnMaskRate()
        );
        request.put("include_embeddings", false);
        return request;
    }

    private MolecularComplexPrediction parsePrediction(
            JsonNode payload,
            String ligandCcd
    ) throws IOException {
        JsonNode complex = payload.path("complex");
        List<String> sequence = stringList(
                complex.path("sequence"),
                "complex.sequence"
        );
        List<JsonNode> positions = nodeList(
                complex.path("atom_positions"),
                "complex.atom_positions"
        );
        List<String> elements = stringList(
                complex.path("atom_elements"),
                "complex.atom_elements"
        );
        List<String> atomNames = stringList(
                complex.path("atom_names"),
                "complex.atom_names"
        );
        List<Boolean> hetero = booleanList(
                complex.path("atom_hetero"),
                "complex.atom_hetero"
        );
        List<JsonNode> tokenToAtoms = nodeList(
                complex.path("token_to_atoms"),
                "complex.token_to_atoms"
        );
        List<Integer> chainIds = integerList(
                complex.path("chain_id"),
                "complex.chain_id"
        );
        List<Double> confidence = doubleList(
                complex.path("plddt"),
                "complex.plddt"
        );
        validateLengths(
                sequence,
                positions,
                elements,
                atomNames,
                hetero,
                tokenToAtoms,
                chainIds,
                confidence
        );
        Map<Integer, String> chainLookup = chainLookup(
                complex.path("metadata").path("chain_lookup")
        );
        List<AtomComplex> atoms = parseAtoms(
                positions,
                elements,
                atomNames,
                hetero
        );
        List<ComplexToken> tokens = parseTokens(
                sequence,
                tokenToAtoms,
                chainIds,
                confidence,
                chainLookup,
                atoms
        );
        return new MolecularComplexPrediction(
                PROVIDER,
                model,
                ligandCcd,
                Instant.now(clock),
                nullableDouble(payload.get("ptm")),
                nullableDouble(payload.get("interface_ptm")),
                tokens
        );
    }

    private List<AtomComplex> parseAtoms(
            List<JsonNode> positions,
            List<String> elements,
            List<String> names,
            List<Boolean> hetero
    ) throws IOException {
        List<AtomComplex> atoms = new ArrayList<>(positions.size());
        for (int index = 0; index < positions.size(); index++) {
            JsonNode position = positions.get(index);
            if (!position.isArray() || position.size() != 3) {
                throw new IOException(
                        "BioHub atom position must contain x, y, and z"
                );
            }
            atoms.add(new AtomComplex(
                    Atom.builder()
                            .pdbSerial(index + 1)
                            .name(names.get(index))
                            .position(new Point3D(
                                    position.get(0).asDouble(),
                                    position.get(1).asDouble(),
                                    position.get(2).asDouble()
                            ))
                            .charge(0.0)
                            .occupancy(1.0)
                            .bFactor(0.0)
                            .element(Element.fromSymbol(
                                    elements.get(index)
                            ))
                            .build(),
                    hetero.get(index)
            ));
        }
        return atoms;
    }

    private List<ComplexToken> parseTokens(
            List<String> sequence,
            List<JsonNode> tokenToAtoms,
            List<Integer> chainIds,
            List<Double> confidence,
            Map<Integer, String> chainLookup,
            List<AtomComplex> atoms
    ) throws IOException {
        Map<Integer, Integer> positionsByChain = new HashMap<>();
        List<ComplexToken> tokens = new ArrayList<>(sequence.size());
        for (int index = 0; index < sequence.size(); index++) {
            int chainId = chainIds.get(index);
            int chainPosition = positionsByChain.merge(
                    chainId,
                    1,
                    Integer::sum
            );
            JsonNode range = tokenToAtoms.get(index);
            if (!range.isArray() || range.size() != 2) {
                throw new IOException(
                        "BioHub token-to-atoms range is invalid"
                );
            }
            int start = range.get(0).asInt();
            int end = range.get(1).asInt();
            if (start < 0 || end <= start || end > atoms.size()) {
                throw new IOException(
                        "BioHub token-to-atoms range is out of bounds"
                );
            }
            tokens.add(new ComplexToken(
                    index,
                    chainLookup.getOrDefault(
                            chainId,
                            String.valueOf((char) ('A' + chainId))
                    ),
                    chainPosition,
                    sequence.get(index),
                    confidence.get(index),
                    atoms.subList(start, end)
            ));
        }
        return tokens;
    }

    private Map<Integer, String> chainLookup(JsonNode node)
            throws IOException {
        if (!node.isObject()) {
            throw new IOException(
                    "BioHub complex metadata has no chain lookup"
            );
        }
        Map<Integer, String> result = new HashMap<>();
        var fields = node.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            result.put(Integer.parseInt(field.getKey()), field.getValue().asText());
        }
        return Map.copyOf(result);
    }

    private void validateLengths(
            List<String> sequence,
            List<JsonNode> positions,
            List<String> elements,
            List<String> atomNames,
            List<Boolean> hetero,
            List<JsonNode> tokenToAtoms,
            List<Integer> chainIds,
            List<Double> confidence
    ) throws IOException {
        int atomCount = positions.size();
        if (elements.size() != atomCount
                || atomNames.size() != atomCount
                || hetero.size() != atomCount) {
            throw new IOException("BioHub complex atom arrays do not align");
        }
        int tokenCount = sequence.size();
        if (tokenToAtoms.size() != tokenCount
                || chainIds.size() != tokenCount
                || confidence.size() != tokenCount) {
            throw new IOException(
                    "BioHub complex token arrays do not align"
            );
        }
    }

    private List<JsonNode> nodeList(JsonNode node, String name)
            throws IOException {
        requireArray(node, name);
        List<JsonNode> values = new ArrayList<>(node.size());
        node.forEach(values::add);
        return values;
    }

    private List<String> stringList(JsonNode node, String name)
            throws IOException {
        requireArray(node, name);
        List<String> values = new ArrayList<>(node.size());
        node.forEach(value -> values.add(value.asText()));
        return values;
    }

    private List<Integer> integerList(JsonNode node, String name)
            throws IOException {
        requireArray(node, name);
        List<Integer> values = new ArrayList<>(node.size());
        node.forEach(value -> values.add(value.asInt()));
        return values;
    }

    private List<Double> doubleList(JsonNode node, String name)
            throws IOException {
        requireArray(node, name);
        List<Double> values = new ArrayList<>(node.size());
        node.forEach(value -> values.add(value.asDouble()));
        return values;
    }

    private List<Boolean> booleanList(JsonNode node, String name)
            throws IOException {
        requireArray(node, name);
        List<Boolean> values = new ArrayList<>(node.size());
        node.forEach(value -> values.add(value.asBoolean()));
        return values;
    }

    private void requireArray(JsonNode node, String name)
            throws IOException {
        if (!node.isArray()) {
            throw new IOException("BioHub response has no " + name + " array");
        }
    }

    private Double nullableDouble(JsonNode node) {
        return node == null || node.isNull() ? null : node.asDouble();
    }

    private String normalizeSequence(String sequence) {
        String normalized = requireText(sequence, "sequence")
                .replaceAll("\\s+", "")
                .toUpperCase(Locale.ROOT);
        if (!normalized.matches("[ACDEFGHIKLMNPQRSTVWY]+")) {
            throw new IllegalArgumentException(
                    "sequence contains unsupported amino acids"
            );
        }
        return normalized;
    }

    private String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private String abbreviate(String body) {
        if (body == null) return "";
        return body.length() <= 500 ? body : body.substring(0, 500) + "…";
    }
}
