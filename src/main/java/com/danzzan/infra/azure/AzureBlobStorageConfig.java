package com.danzzan.infra.azure;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.AzureCliCredentialBuilder;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;

@Configuration
@ConditionalOnProperty(prefix = "image.storage", name = "provider", havingValue = "azure")
public class AzureBlobStorageConfig {

    @Bean
    AzureBlobStorageProperties azureBlobStorageProperties(
            @Value("${azure.storage.endpoint}") String endpoint,
            @Value("${azure.storage.container:images}") String container,
            @Value("${azure.storage.credential:managed-identity}") String credential,
            @Value("${azure.storage.read-sas-expiration-seconds:300}") long readSasExpirationSeconds) {
        return new AzureBlobStorageProperties(URI.create(endpoint), container,
                AzureBlobStorageProperties.Credential.from(credential),
                Duration.ofSeconds(readSasExpirationSeconds));
    }

    @Bean
    TokenCredential azureStorageCredential(AzureBlobStorageProperties properties) {
        return switch (properties.credential()) {
            case AZURE_CLI -> new AzureCliCredentialBuilder().build();
            case MANAGED_IDENTITY -> new ManagedIdentityCredentialBuilder().build();
        };
    }

    @Bean
    BlobServiceClient azureBlobServiceClient(AzureBlobStorageProperties properties, TokenCredential azureStorageCredential) {
        return new BlobServiceClientBuilder()
                .endpoint(properties.endpoint().toString())
                .credential(azureStorageCredential)
                .buildClient();
    }

    @Bean
    AzureBlobStorage azureBlobStorage(BlobServiceClient azureBlobServiceClient,
                                      AzureBlobStorageProperties properties) {
        return new AzureBlobStorage(azureBlobServiceClient, properties, Clock.systemUTC());
    }
}
