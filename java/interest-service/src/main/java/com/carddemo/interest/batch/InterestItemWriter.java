package com.carddemo.interest.batch;

import com.carddemo.interest.domain.Account;
import com.carddemo.interest.domain.Transaction;
import com.carddemo.interest.repository.AccountRepository;
import com.carddemo.interest.trace.Trace;
import java.util.ArrayList;
import java.util.List;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemWriter;

/**
 * Writes the two outputs of one processed TCATBAL record: the interest {@link Transaction}s go to the
 * SYSTRAN file (1300-B-WRITE-TX WRITE, lines 500-515) and the posted {@link Account}s are rewritten
 * to the account file (1050-UPDATE-ACCOUNT REWRITE, lines 356-370).
 */
public class InterestItemWriter implements ItemWriter<InterestItemResult>, ItemStream {

    private final FlatFileItemWriter<Transaction> tranWriter;
    private final AccountRepository accounts;

    public InterestItemWriter(FlatFileItemWriter<Transaction> tranWriter, AccountRepository accounts) {
        this.tranWriter = tranWriter;
        this.accounts = accounts;
    }

    @Override
    @Trace(program = "CBACT04C", paragraph = "1300-B-WRITE-TX", lines = "500-515")
    @Trace(program = "CBACT04C", paragraph = "1050-UPDATE-ACCOUNT", lines = "356-370")
    public void write(Chunk<? extends InterestItemResult> chunk) throws Exception {
        List<Transaction> transactions = new ArrayList<>();
        for (InterestItemResult result : chunk) {
            transactions.addAll(result.transactions());
            result.accountUpdates().forEach(accounts::rewrite);
        }
        if (!transactions.isEmpty()) {
            tranWriter.write(new Chunk<>(transactions));
        }
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        tranWriter.open(executionContext);
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        tranWriter.update(executionContext);
    }

    @Override
    public void close() throws ItemStreamException {
        tranWriter.close();
    }
}
