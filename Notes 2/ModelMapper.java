package com.neobank.onboarding.common.restclients.domainapi.mapper;


import com.neobank.ods.schema.business.document.BusinessDocument;
import com.neobank.ods.schema.card.Card;
import com.neobank.ods.schema.card.CardDetails;
import com.neobank.ods.schema.common.DocumentFile;
import com.neobank.ods.schema.common.OCRInformation;
import com.neobank.ods.schema.enums.*;
import com.neobank.ods.schema.individual.IndividualDetails;
import com.neobank.ods.schema.individual.IndividualPayload;
import com.neobank.ods.schema.individual.document.IndividualDocument;
import com.neobank.ods.schema.individual.document.IndividualDocumentDetails;
import com.neobank.ods.schema.individual.screening.ScreeningProfile;
import com.neobank.ods.schema.individual.screening.ScreeningProfileDetails;
import com.neobank.onboarding.common.restclients.domainapi.model.business.response.BusinessResponse;
import com.neobank.onboarding.common.restclients.domainapi.model.individual.response.Individual;
import com.neobank.onboarding.common.restclients.domainapi.model.individual.response.PrivacyTermsInfo;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.nonNull;

@Component
public class ModelMapper {

/*
    public BusinessPayload mapToBusinessPayload(BusinessResponse businessResponse) {
        return BusinessPayload.builder()
                //.businessDocumentDetails()
                .accounts(mapAccountDetails(businessResponse.getAccountsInfo()))
                .businessDetails(mapBusinessDetails(businessResponse))
                .build();
    }

 */

    public List<BusinessDocument> mapToBusinessDocument(BusinessResponse businessResponse) {
        return nonNull(businessResponse) ? businessResponse.getKybDocumentsInfo().stream().map(kybDocumentsInfo -> {
            return BusinessDocument.builder()
                    .businessId(kybDocumentsInfo.getBusinessId())
                    .documentType(kybDocumentsInfo.getDocumentType())
                    .documentId(kybDocumentsInfo.getDocumentId())
                    .documentFiles(kybDocumentsInfo.getDocumentFiles().stream().map(documentFile -> {
                        return DocumentFile.builder()
                                .documentName(documentFile.getDocumentName())
                                .adlsLink(documentFile.getADLSLink())
                                .fintechDocumentId(documentFile.getFintechDocumentId())
                                .mimeType(MimeType.getEnum(documentFile.getMimeType()))
                                .build();
                    }).collect(Collectors.toList()))
                    .source(Source.getEnum(kybDocumentsInfo.source))
                    .build();
        }).collect(Collectors.toList()) : Collections.emptyList();
    }


    public IndividualPayload mapToIndividualPayload(Individual individual) {
        return IndividualPayload.builder()
                .individualDetails(IndividualDetails.builder()
                        .individual(mapIndividual(individual)).build())
                .screeningDetails(ScreeningProfileDetails.builder()
                        .screeningProfile(mapScreeningProfile(individual)).build())
                .cards(mapCardDetails(individual))
                .documentDetails(mapIndividualDocuments(individual))
                .build();
    }

    private com.neobank.ods.schema.individual.Individual mapIndividual(Individual individual) {
        com.neobank.ods.schema.individual.Individual individualToReturn =  com.neobank.ods.schema.individual.Individual.builder()
                .individualId(individual.getIndividualId())
                .businessId(individual.getBusinessId())
                .firstName(individual.getBasicInfo().getFirstName())
                .middleName(individual.getBasicInfo().getMiddleName())
                .lastName(individual.getBasicInfo().getLastName())
                .fullName(individual.getBasicInfo().getFullName())
                .dateOfBirth(individual.getDateOfBirth())
                .nationality(Country.valueOf(individual.getNationality()))
                .gender(Gender.getEnumByPascalCaseValue(individual.getGender()))
                .role(Role.getEnum(individual.getBasicInfo().getRole()))
                .emailId(individual.getEmail().getEmailId())
                .emailIdVerified(individual.getEmail().getEmailIdVerified())
                .mobile(individual.getMobile().getMobileNumber())
                .mobileVerified(individual.getMobile().getMobileNumberVerified())
                //.twoFAContent()
                .deviceId(individual.getDeviceId())
                //.deviceRegistered()

                .termsAndConditionsAccepted(String.valueOf(individual.getTermsAndConditionsInfo().getTermsAndConditionsAccepted()))
                .termsAndConditionsAcceptedDateTime(individual.getTermsAndConditionsInfo().getTermsAndConditionsAcceptedDateTime())
                .termsAndConditionsVersion(individual.getTermsAndConditionsInfo().getTermsAndConditionsVersion())
                .status(IndividualStatus.getEnum(individual.getState()))
                .placeOfBirth(individual.getPlaceOfBirth())
                .passportIdNumber(individual.getPassportIdNumber())
                .passportExpiryDate(individual.getPassportExpiryDate())
                //.issuingCountry()
                .emiratesIdNumber(individual.getEmiratesIdNumber())
                .emiratesIdExpiryDate(individual.getEmiratesIdExpiryDate())
                //.visaIdNumber()
                .countryOfResidence(individual.getCountryOfResidence())
                .build();
        setPrivacyTermsInfo(individualToReturn, individual.getPrivacyTermsInfo());
        return individualToReturn;
    }

    private void setPrivacyTermsInfo(com.neobank.ods.schema.individual.Individual individual,
                                                                             PrivacyTermsInfo privacyTermsInfo) {
        if (privacyTermsInfo != null) {
            individual.setPrivacyPolicyAccepted(String.valueOf(privacyTermsInfo.isPrivacyPolicyAccepted()));
            individual.setPrivacyPolicyAcceptedDateTime(privacyTermsInfo.getPrivacyPolicyAcceptedDateTime());
            individual.setPrivacyPolicyVersion(privacyTermsInfo.getPrivacyPolicyVersion());
        }
    }

    private ScreeningProfile mapScreeningProfile(Individual individual) {
        return ScreeningProfile.builder()
                .status(ScreeningStatus.getEnum(individual.getScreeningInfo().getScreeningHitDetail()))
                .build();
    }

    private List<CardDetails> mapCardDetails(Individual individual) {
        return individual.getCards().stream().map(cardInfo -> {
            return CardDetails.builder()
                    .card(Card.builder()
                            .individualId(individual.getIndividualId())
                            .token(cardInfo.getCardToken())
                            .statusNetwork(Integer.parseInt(cardInfo.getCardStatus()))
                            .build())
                    .build();
        }).collect(Collectors.toList());
    }

    private List<IndividualDocumentDetails> mapIndividualDocuments(Individual individual) {
        return individual.getKycDocuments().stream().map(kycDocument -> {
            return IndividualDocumentDetails.builder()
                    .individualDocument(IndividualDocument.builder()
                            .documentType(kycDocument.getDocumentType())
                            .documentFiles(kycDocument.getDocumentFiles())
                            .ocrInformation(OCRInformation.builder()
                                    .idNumber(kycDocument.getIdNumber())
                                    .dateOfExpiry(kycDocument.getExpiryDate())
                                    .build())
                            .build())
                    .build();
        }).collect(Collectors.toList());
    }

    /*
    private List<AccountDetails> mapAccountDetails(List<AccountInfo> accountsInfo) {
        return accountsInfo.stream().map(accountInfo -> {
            return AccountDetails.builder()
                    .account(Account.builder()
                            .accountType(accountInfo.getAccountType())
                            .accountBalance(new BigDecimal(accountInfo.getBalance()))
                            .accountId(accountInfo.getAccountId())
                            .iban(accountInfo.getIban())
                            .accountCreationDate(accountInfo.getOpeningDate())
                            .build())
                    .build();
        }).collect(Collectors.toList());
    }

    private BusinessDetails mapBusinessDetails(BusinessResponse businessResponse) {
        return BusinessDetails.builder()
                .business(Business.builder()
                        .businessId(businessResponse.getBusinessId())
                        .awbReferenceNo(businessResponse.getBusinessBasicInfo().getAWBReferenceNo())
                        .annualTurnover(Double.valueOf(businessResponse.getLicenseInfo().getAnnualTurnOver()))
                        .companyName(businessResponse.getBusinessBasicInfo().getName())
                        .countryOfIncorporation(Country.getEnum(businessResponse.getLicenseInfo().getCountryOfIncorporation()))
                        .legalType(businessResponse.getBusinessBasicInfo().getLegalType())
                        .status(BusinessStatus.getEnum(businessResponse.getBusinessBasicInfo().getState()))
                        .journeyStatus(JourneyStatus.getEnum(businessResponse.getBusinessBasicInfo().getJourneyStatus()))
                        .physicalAddressPresent(businessResponse.getBusinessBasicInfo().getPhysicalAddressPresent())
                        .taxRegistrationDate(businessResponse.getTradeInfo().getTaxRegistrationDate())
                        .taxRegistrationNumber(businessResponse.getTradeInfo().getTaxRegistrationNumber())
                        .operatingAddressArea(businessResponse.getAddressInfo().getArea())
                        .operatingAddressApartmentNumber(businessResponse.getAddressInfo().getOfficeNumber())
                        .operatingAddressStreet(businessResponse.getAddressInfo().getStreet())
                        .operatingAddressEmirate(Emirate.getEnum(businessResponse.getAddressInfo().getEmirate()))
                        .operatingAddressArea(businessResponse.getAddressInfo().getArea())
                        .operatingAddressCountry(Country.valueOf(businessResponse.getAddressInfo().getCountry()))
                        .operatingAddressPOBox(businessResponse.getAddressInfo().getPoBox())
                        .build())
                .build();
    }
     */
}
