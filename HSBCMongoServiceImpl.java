package com.hsbc.mongo.services.impl;

import com.hsbc.mongo.exceptions.HSBCMongoException;
import com.hsbc.mongo.exceptions.model.ErrorMessage;
import com.hsbc.mongo.models.*;
import com.hsbc.mongo.models.balance.Balance;
import com.hsbc.mongo.models.balance.BalanceGrouping;
import com.hsbc.mongo.models.balance.BalancesWrapper;
import com.hsbc.mongo.models.balance.IdModel;
import com.hsbc.mongo.models.masterPayment.MasterPayment;
import com.hsbc.mongo.models.masterPayment.MasterPaymentResponse;
import com.hsbc.mongo.models.payment.Payment;
import com.hsbc.mongo.models.payment.PaymentWrapper;
import com.hsbc.mongo.models.transaction.SearchCriteria;
import com.hsbc.mongo.models.transaction.Transaction;
import com.hsbc.mongo.models.transaction.TransactionsWrapper;
import com.hsbc.mongo.repositories.BalanceRepository;
import com.hsbc.mongo.repositories.MasterPaymentRepository;
import com.hsbc.mongo.repositories.PaymentRepository;
import com.hsbc.mongo.repositories.TransactionRepository;

import com.hsbc.mongo.services.HSBCMongoService;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static com.hsbc.mongo.constants.HSBCMongoConstants.*;
import static com.hsbc.mongo.util.ServiceUtil.*;
import static org.apache.commons.lang3.StringUtils.substringBetween;
import static org.apache.logging.log4j.util.Strings.isNotBlank;

@Service
@RequiredArgsConstructor
@Slf4j
public class HSBCMongoServiceImpl implements HSBCMongoService {

    private final BalanceRepository balanceRepository;
    private final TransactionRepository transactionRepository;
    private final MasterPaymentRepository masterPaymentRepository;
    private final PaymentRepository paymentRepository;
    private final MongoTemplate mongoTemplate;

    @Transactional
    public void crossCollectionsThroughTransaction(List<Payment> payments, List<Balance> balances, boolean rollBack) {
        log.info("started crossCollectionsThroughTransaction");
        crossCollections(payments, balances, rollBack);
    }

    public void crossCollections(List<Payment> payments, List<Balance> balances, boolean breakTransaction) {
        log.info("started crossCollections");
        paymentRepository.insert(payments);
        balances.stream().forEach(balance -> {
            balanceRepository.save(balance);
        });
        if (breakTransaction) {
            throw new HSBCMongoException(ErrorMessage.builder()
                    .errorMessage("Transaction has been broken through program")
                    .build(), HttpStatus.SEE_OTHER.value());
        }
        log.info("Finished crossCollections");
    }

    @Transactional
    public void insertBalance(List<Balance> balances, boolean bulkInsert, boolean breakTransaction) {
        log.info("Started saveBalance with bulkInsert = {} and breakTransaction = {}", bulkInsert, breakTransaction);
        if (bulkInsert) {
            balanceRepository.insert(balances);
        } else {
            AtomicInteger totalCount = new AtomicInteger();
            try {
                balances.stream().forEach(balance -> {
                    balanceRepository.insert(balance);
                    totalCount.getAndAdd(1);
                });
            } catch (Exception exception) {
                throw new HSBCMongoException(ErrorMessage.builder()
                        .insertedRecords(totalCount.get())
                        .errorMessage(exception.getLocalizedMessage())
                        .build(), HttpStatus.SEE_OTHER.value());
            }
        }
        if (breakTransaction) {
            throw new HSBCMongoException(ErrorMessage.builder()
                    .insertedRecords(balances.size())
                    .errorMessage("Transaction has been broken through program")
                    .build(), HttpStatus.SEE_OTHER.value());
        }
    }

    @Timed(value = HSBC_MONGO_RETRIEVE_BALANCES_TIME, description = "Time taken to retrieve balance records from mongo db")
    public BalancesWrapper retrieveBalances(int page, int limit) {
        log.info("Retrieve Balances records with the limit {}, page {}", limit, page);
        long startTime = System.nanoTime();
        Page<Balance> balances = balanceRepository.findAll(PageRequest.of(page, limit,
                Sort.Direction.DESC, "balances.date"));
        return BalancesWrapper.builder()
                .timeTaken(buildResponseModel(System.nanoTime() - startTime, limit))
                .balances(balances.getContent())
                .build();
    }

    @Timed(value = HSBC_MONGO_RETRIEVE_TRANSACTIONS_TIME, description = "Time taken to retrieve transaction records from mongo db")
    public TransactionsWrapper retrieveTransactions(int page, int limit) {
        log.info("Retrieve Transaction records with the limit {}, page {}", limit, page);
        long startTime = System.nanoTime();
        Page<Transaction> transactions = transactionRepository.findAll(PageRequest.of(page, limit,
                Sort.Direction.DESC, "transactions.valDt"));
        return TransactionsWrapper.builder()
                .timeTaken(buildResponseModel(System.nanoTime() - startTime, limit))
                .transactions(transactions.getContent())
                .build();
    }

    @Timed(value = HSBC_MONGO_RETRIEVE_PAYMENTS_TIME, description = "Time taken to retrieve payment records from mongo db")
    public PaymentWrapper retrievePayments(int page, int limit) {
        log.info("Retrieve Payment records with the limit {}, page {}", limit, page);
        long startTime = System.nanoTime();
        Page<Payment> payments = paymentRepository.findAll(PageRequest.of(page, limit,
                Sort.Direction.DESC, "capturedTimestamp"));
        return PaymentWrapper.builder()
                .timeTaken(buildResponseModel(System.nanoTime() - startTime, limit))
                .transactions(payments.getContent())
                .build();
    }

    public Balance retrieveBalanceById(IdModel idModel) {
        log.info("Retrieve Balance by id");
        return balanceRepository.findById(idModel).orElseThrow();
    }

    public Transaction retrieveTransactionById(String id) {
        log.info("Retrieve Transaction by id {}", id);
        return transactionRepository.findById(new ObjectId(id)).orElseThrow();
    }

    public TransactionsWrapper retrieveTransactionsByAn(String accountNumber, int page, Integer limit, boolean addPayload) {
        log.info("Retrieve Transaction by accountNumber {} with page {} and limit {}", accountNumber, page, limit);
        long startTime = System.nanoTime();
        List<Transaction> transactions;
        if (limit != null) {
            transactions = transactionRepository.findByaN(accountNumber, PageRequest.of(page, limit,
                    Sort.Direction.DESC, "transactions.valDt")).toList();
        } else {
            transactions = transactionRepository.findByaN(accountNumber);
        }
        return TransactionsWrapper.builder()
                .timeTaken(buildResponseModel(System.nanoTime() - startTime, transactions.size()))
                .transactions(addPayload ? transactions : null)
                .build();
    }

    public TransactionsWrapper transactionBySearchCriteria(SearchCriteria sc, int page, Integer limit, boolean addPayload) {
        log.info("Retrieve Transaction by SearchCriteria {} with page {} and limit {}", sc, page, limit);
        long startTime = System.nanoTime();
        List<Transaction> transactions = getTransaction(sc, limit);
        return TransactionsWrapper.builder()
                .timeTaken(buildResponseModel(System.nanoTime() - startTime, transactions.size()))
                .transactions(addPayload ? transactions : null)
                .build();
    }

    public BalancesWrapper balanceBySearchCriteria(SearchCriteria sc, int page, Integer limit, boolean addPayload) {
        log.info("Retrieve Transaction by SearchCriteria {} with page {} and limit {}", sc, page, limit);
        long startTime = System.nanoTime();
        List<Balance> balances = new ArrayList();
        List<BalanceGrouping> groupings = new ArrayList();
        if (sc.isGroupingNeeded()) {
            groupings = getBalance(sc, limit, BalanceGrouping.class);
        } else {
            balances = getBalance(sc, limit, Balance.class);
        }

        return BalancesWrapper.builder()
                .timeTaken(buildResponseModel(System.nanoTime() - startTime,
                        balances.isEmpty()? groupings.size() : balances.size()))
                .balances(addPayload && !balances.isEmpty() ? balances : null)
                .groupings(addPayload && !groupings.isEmpty() ? groupings : null)
                .build();
    }

    @Async
    public CompletableFuture<Long> retrieveBalanceCount() {
        log.info("Retrieve Balance count");
        return CompletableFuture.completedFuture(balanceRepository.count());
    }

    @Async
    public CompletableFuture<Long> retrieveTransactionCount() {
        log.info("Retrieve Transaction count");
        return CompletableFuture.completedFuture(transactionRepository.count());
    }

    @Timed(value = HSBC_MONGO_RETRIEVE_PAYMENTS_COUNT_TIME, description = "Time taken to retrieve payment count from mongo db")
    public CollectionsCount retrievePaymentCount() {
        log.info("Retrieve Payment count");
        return CollectionsCount.builder()
                .transactionCount(paymentRepository.count())
                .build();
    }

    public List<Balance> saveBalance(List<Balance> request) {
        log.info("Started inserting {} Balance records", request.size());
        return balanceRepository.saveAll(request);
    }

    @Async
    public CompletableFuture<List<Balance>> insertBalanceAsync(List<Balance> request) {
        log.info("Started inserting {} Balance records", request.size());
        List<Balance> response = StreamSupport.stream(insertBalance(request).spliterator(), false)
                .collect(Collectors.toList());
        return CompletableFuture.completedFuture(response);
    }

    public List<Transaction> insertTransaction(List<Transaction> request) {
        log.info("Started inserting {} Transaction records", request.size());
        return transactionRepository.insert(request);
    }

    @Async
    public CompletableFuture<List<Transaction>> insertTransactionAsync(List<Transaction> request, boolean bulkInsert) {
        log.info("Started inserting {} Transaction records", request.size());
        List<Transaction> response;
        if (bulkInsert) {
            response = StreamSupport.stream(transactionRepository.insert(request).spliterator(), false)
                    .collect(Collectors.toList());
        } else {
            response = new ArrayList<>();
            request.stream().forEach(transaction -> response.add(transactionRepository.insert(transaction)));
        }

        return CompletableFuture.completedFuture(response);
    }

    @Async
    public CompletableFuture<List<Payment>> insertPayment(List<Payment> request) {
        log.info("Started inserting {} Payment records", request.size());
        List<Payment> response = StreamSupport.stream(paymentRepository.insert(request).spliterator(), false)
                .collect(Collectors.toList());
        return CompletableFuture.completedFuture(response);
    }

    public ResponseModel createMasterPayment(List<MasterPayment> masterPayments) {
        log.info("Started inserting {} MasterPayment records", masterPayments.size());
        long startTime = System.nanoTime();
        masterPaymentRepository.insert(masterPayments);
        return buildResponseModel(System.nanoTime() - startTime, masterPayments.size());
    }

    public MasterPaymentResponse retrieveMasterPaymentById(String id, boolean addPayload) {
        log.info("Retrieve MasterPayment by id {}", id);
        long startTime = System.nanoTime();
        MasterPayment masterPayment = masterPaymentRepository.findById(id).orElseThrow();
        return MasterPaymentResponse.builder()
                .timeTakeInNanoSeconds(System.nanoTime() - startTime)
                .id(masterPayment.getId())
                .numberOfBeneficiaries(masterPayment.getBeneficiaries().size())
                .payment(addPayload ? masterPayment : null)
                .build();
    }

    private List<Transaction> getTransaction(SearchCriteria sc, Integer limit) {
        List<Criteria> criteria = new ArrayList<>();
        if (isNotBlank(sc.getCountry())) {
            criteria.add(createCriteria("aC", sc.getCountry()));
        }
        if (isNotBlank(sc.getInstitution())) {
            criteria.add(createCriteria("aI", sc.getInstitution()));
        }
        if (isNotBlank(sc.getAccountNumber())) {
            criteria.add(createCriteria("aN", sc.getAccountNumber()));
        }
        return getSearchResult(sc, limit, criteria, "transaction", Transaction.class);
    }

    private <T> List<T> getBalance(SearchCriteria sc, Integer limit, Class<T> classType) {
        List<Criteria> criteria = new ArrayList<>();
        if (isNotBlank(sc.getCountry())) {
            criteria.add(createCriteria("_id.aC", sc.getCountry()));
        }
        if (isNotBlank(sc.getInstitution())) {
            criteria.add(createCriteria("_id.aI", sc.getInstitution()));
        }
        if (isNotBlank(sc.getAccountNumber())) {
            criteria.add(createCriteria("_id.aN", sc.getAccountNumber()));
        }
        return getSearchResult(sc, limit, criteria, "balance", classType);
    }

    private <T> List<T> getSearchResult(SearchCriteria sc, Integer limit,
                                        List<Criteria> criteria, String collectionName, Class<T> classType) {
        List<AggregationOperation> aggregationOperations = new ArrayList<>();
        aggregationOperations.add(Aggregation.match(new Criteria().andOperator(criteria.toArray(new Criteria[criteria.size()]))));

        if (sc.isSortingNeeded()) {
            aggregationOperations.add(Aggregation.sort(Sort.by(sc.getOrder(), sc.getSortingProperty())));
        }
        if (limit != null) {
            aggregationOperations.add(Aggregation.limit(limit));
        }
        if (sc.isGroupingNeeded()) {
            aggregationOperations.add(Aggregation.group(sc.getGroupingProperty()));
        }

        Aggregation aggregation = Aggregation.newAggregation(aggregationOperations);
        if (sc.getAllowDiskUse() != null && sc.getAllowDiskUse()) {
            aggregation = aggregation.withOptions(AggregationOptions.builder().allowDiskUse(true).build());
        }
        log.info("Final aggregation query {}", aggregation);
        return mongoTemplate.aggregate(aggregation, collectionName, classType).getMappedResults();
    }

    private Criteria createCriteria(String propertyName, String propertyValue) {
        if (propertyValue.contains(",")) {
            return Criteria.where(propertyName).in(propertyValue.split(","));
        }
        return Criteria.where(propertyName).is(propertyValue);
    }

    private List<Balance> insertBalance(List<Balance> request) {
        log.info("Started inserting {} Balance records", request.size());
        try {
            return balanceRepository.insert(request);
        } catch (DuplicateKeyException exception) {
            log.error(exception.getLocalizedMessage());
            IdModel idModel = extractIdModel(exception.getLocalizedMessage());
            int idIndex = getBalanceIndex(request, idModel);
            List<Balance> insertedList = new CopyOnWriteArrayList<>(request.subList(0, idIndex));
            List<Balance> toBeInserted = new CopyOnWriteArrayList<>(request.subList(idIndex, request.size()));
            toBeInserted.get(0).getId().setAN(RandomStringUtils.randomNumeric(8));
            List<Balance> response = insertBalance(toBeInserted);
            insertedList.addAll(response);
            return insertedList;
        }
    }

    private int getBalanceIndex(List<Balance> balances, IdModel idModel) {
        return IntStream.range(0, balances.size())
                .filter(i -> Objects.equals(balances.get(i).getId(), idModel))
                .findFirst().getAsInt();
    }

    private IdModel extractIdModel(String errorMessage) {
        return convertStringToObject(substringBetween(errorMessage, "_id:", "}',"));
    }
}
