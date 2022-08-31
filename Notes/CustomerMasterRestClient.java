package com.neobank.account.maintenance.individual.restclient;

import com.neobank.account.maintenance.individual.models.response.Business;
import com.neobank.account.maintenance.individual.models.response.BusinessSearchResponse;
import com.neobank.account.maintenance.individual.models.response.Individual;
import com.neobank.account.maintenance.individual.models.response.IndividualSearchResponse;
import com.neobank.common.restclient.RestClient;
import com.neobank.onboarding.common.restclients.SafeWrapperForRestClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.util.CollectionUtils.isEmpty;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class CustomerMasterRestClient {
    private static final Map<String, String> COMMON_HEADERS = Map.of("Content-Type", MediaType.APPLICATION_JSON.toString());
    public static final String BUSINESS_ID = "BusinessId";
    private final RestClient restClient;
    private final String individualSearchEndpoint;
    private final String businessSearchEndpoint;

    @Autowired
    public CustomerMasterRestClient(RestClient restClient,
                                    @Value("${customer.master.individual.search.endpoint}") String individualSearchEndpoint,
                                    @Value("${customer.master.business.search.endpoint}") String businessSearchEndpoint) {
        this.restClient = restClient;
        this.individualSearchEndpoint = individualSearchEndpoint;
        this.businessSearchEndpoint = businessSearchEndpoint;
    }

    public Optional<Individual> searchIndividualByBusinessId(String businessId) {
        return getIndividualBySearch(Map.of(BUSINESS_ID, businessId));
    }

    public Optional<Individual> searchIndividualById(String individualId) {
        log.info("Retrieve individual details by individual Id");
        var response = SafeWrapperForRestClient.eatClientError(() -> restClient.callApi(
                String.format("%s/%s", individualSearchEndpoint, individualId),
                HttpMethod.GET,
                COMMON_HEADERS,
                IndividualSearchResponse.class), NOT_FOUND);
        if (response.isPresent() && !isEmpty(response.get().getIndividuals())) {
            return Optional.of(response.get().getIndividuals().get(0));
        }
        return Optional.empty();
    }

    private Optional<Individual> getIndividualBySearch(Map request) {
        log.info("Retrieve individual details");
        var response = SafeWrapperForRestClient.eatClientError(() -> restClient.callApiWithPayload(
                String.format("%s/search", individualSearchEndpoint),
                HttpMethod.POST,
                request,
                COMMON_HEADERS,
                IndividualSearchResponse.class), NOT_FOUND);
        if (response.isPresent() && !isEmpty(response.get().getIndividuals())) {
            return Optional.of(response.get().getIndividuals().get(0));
        }
        return Optional.empty();
    }

    public Optional<Business> searchBusinessById(String businessId) {
        log.info("Retrieve business details by businessId");
        var response = SafeWrapperForRestClient.eatClientError(() -> restClient.callApi(
                String.format("%s/%s", businessSearchEndpoint, businessId),
                HttpMethod.GET,
                COMMON_HEADERS,
                BusinessSearchResponse.class), NOT_FOUND);
        if (response.isPresent() && !isEmpty(response.get().getBusinesses())) {
            return Optional.of(response.get().getBusinesses().get(0));
        }
        return Optional.empty();
    }
}
