package com.danzzan.infra.image.json;

import com.danzzan.infra.azure.AzureBlobStorage;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.IOException;

/** Removes expiring SAS credentials before request values can reach persistence. */
public class ImageReferenceDeserializer extends JsonDeserializer<String> {
    @Autowired(required = false)
    private AzureBlobStorage storage;

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String value = parser.getValueAsString();
        if (value == null) throw JsonMappingException.from(parser, "Image reference must be a string");
        try {
            return storage == null ? value : storage.normalizeReference(value);
        } catch (IllegalArgumentException | org.springframework.web.server.ResponseStatusException exception) {
            throw JsonMappingException.from(parser, "Invalid image reference");
        }
    }
}
