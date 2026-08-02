package totah.lab.http.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EsmHttpClientConfig {

        @Value("${application.http_clients.API_URL}")
        private String apiUrl;

        @Value("${application.http_clients.ESM_API_KEY}")
        private String apiKey;

        public String getApiUrl() {
            return apiUrl;
        }

        public String getApiKey() {
            return apiKey;
        }
    }