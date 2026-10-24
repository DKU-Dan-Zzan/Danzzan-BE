package com.danzzan.infra.image.json;

import com.danzzan.infra.azure.AzureBlobStorage;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.IOException;

/** Signs image fields at the HTTP boundary without changing persisted references. */
public class ImageUrlSerializer extends JsonSerializer<String> {
    @Autowired(required = false)
    private AzureBlobStorage storage;

    @Override
    public void serialize(String value, JsonGenerator generator, SerializerProvider provider) throws IOException {
        generator.writeString(storage == null ? value : storage.readUrl(value));
    }
}
