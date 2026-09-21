package com.carddemo.interest.batch;

import com.carddemo.interest.calc.InterestCalculator;
import com.carddemo.interest.domain.TranCatBalance;
import com.carddemo.interest.domain.Transaction;
import com.carddemo.interest.repository.InMemoryAccountRepository;
import com.carddemo.interest.repository.InMemoryCardXrefRepository;
import com.carddemo.interest.repository.InMemoryDisclosureGroupRepository;
import com.carddemo.interest.service.AccountPostingService;
import com.carddemo.interest.service.Db2TimestampSupplier;
import com.carddemo.interest.service.InterestRateService;
import com.carddemo.interest.service.TransactionFactory;
import com.carddemo.interest.trace.Trace;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Batch job "interestCalcJob" equivalent to JCL INTCALC STEP15 (app/jcl/INTCALC.jcl line 22).
 *
 * <p>Job parameters: {@code runDate} (PARM='2022071800'), input/output file locations for
 * TCATBALF, XREFFILE, DISCGRP, ACCTFILE, TRANSACT. Files are the ASCII fixed-width line
 * layouts from app/data/ASCII (one record per line).
 *
 * <p>Step {@code interestCalcStep}: TCATBAL FlatFileItemReader -> {@link InterestProcessor} ->
 * {@link InterestItemWriter} (SYSTRAN FlatFileItemWriter + ACCTFILE REWRITE). The keyed files (XREF, DISCGRP,
 * ACCT) are loaded into the in-memory repositories before the step and the account file is written
 * back after it, see {@link InterestStepListener}.
 */
@Configuration
@Trace(program = "CBACT04C", paragraph = "PROCEDURE DIVISION", lines = "180-232")
public class InterestJobConfig {

    public static final String JOB_NAME = "interestCalcJob";
    public static final String STEP_NAME = "interestCalcStep";
    public static final String PARAM_RUN_DATE = "runDate";
    public static final String PARAM_TCATBAL_FILE = "tcatbalFile";
    public static final String PARAM_XREF_FILE = "xrefFile";
    public static final String PARAM_DISCGRP_FILE = "discgrpFile";
    public static final String PARAM_ACCT_FILE = "acctFile";
    public static final String PARAM_TRAN_FILE = "tranFile";

    /** COBOL processes one record at a time; chunk size only affects transaction boundaries. */
    static final int CHUNK_SIZE = 100;

    @Bean
    public Job interestCalcJob(JobRepository jobRepository, Step interestCalcStep) {
        return new JobBuilder(JOB_NAME, jobRepository).start(interestCalcStep).build();
    }

    @Bean
    public Step interestCalcStep(JobRepository jobRepository,
                                 PlatformTransactionManager transactionManager,
                                 FlatFileItemReader<TranCatBalance> tcatbalReader,
                                 InterestProcessor interestProcessor,
                                 ItemWriter<InterestItemResult> interestItemWriter,
                                 InterestStepListener interestStepListener) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<TranCatBalance, InterestItemResult>chunk(CHUNK_SIZE, transactionManager)
                .reader(tcatbalReader)
                .processor(interestProcessor)
                .writer(interestItemWriter)
                .listener(interestStepListener)
                .build();
    }

    /** 0000-TCATBALF-OPEN / 1000-TCATBALF-GET-NEXT: sequential read of TCATBAL-FILE. */
    @Bean
    @StepScope
    @Trace(program = "CBACT04C", paragraph = "1000-TCATBALF-GET-NEXT", lines = "325-349")
    public FlatFileItemReader<TranCatBalance> tcatbalReader(
            @Value("#{jobParameters['" + PARAM_TCATBAL_FILE + "']}") String tcatbalFile) {
        return new FlatFileItemReaderBuilder<TranCatBalance>()
                .name("tcatbalReader")
                .resource(new FileSystemResource(tcatbalFile))
                .lineMapper((line, lineNumber) -> TranCatBalance.fromRecord(line))
                .build();
    }

    @Bean
    @StepScope
    public InterestProcessor interestProcessor(
            @Value("#{jobParameters['" + PARAM_RUN_DATE + "']}") String runDate,
            InterestCalculator calculator,
            InterestRateService rateService,
            TransactionFactory transactionFactory,
            AccountPostingService postingService,
            InMemoryAccountRepository accounts,
            InMemoryCardXrefRepository xrefs,
            Db2TimestampSupplier timestamps) {
        return new InterestProcessor(runDate, calculator, rateService, transactionFactory,
                postingService, accounts, xrefs, timestamps);
    }

    /** 0400-TRANFILE-OPEN / 1300-B-WRITE-TX WRITE: sequential SYSTRAN output, one 350-byte record per line. */
    @Bean
    @StepScope
    @Trace(program = "CBACT04C", paragraph = "1300-B-WRITE-TX", lines = "500-515")
    public FlatFileItemWriter<Transaction> tranWriter(
            @Value("#{jobParameters['" + PARAM_TRAN_FILE + "']}") String tranFile) {
        return new FlatFileItemWriterBuilder<Transaction>()
                .name("tranWriter")
                .resource(new FileSystemResource(tranFile))
                .lineAggregator(Transaction::toRecord)
                .shouldDeleteIfEmpty(false)
                .build();
    }

    @Bean
    @StepScope
    public InterestItemWriter interestItemWriter(FlatFileItemWriter<Transaction> tranWriter,
                                                             InMemoryAccountRepository accounts) {
        return new InterestItemWriter(tranWriter, accounts);
    }

    @Bean
    @StepScope
    public InterestStepListener interestStepListener(
            @Value("#{jobParameters['" + PARAM_XREF_FILE + "']}") String xrefFile,
            @Value("#{jobParameters['" + PARAM_DISCGRP_FILE + "']}") String discgrpFile,
            @Value("#{jobParameters['" + PARAM_ACCT_FILE + "']}") String acctFile,
            InMemoryCardXrefRepository xrefs,
            InMemoryDisclosureGroupRepository discgrps,
            InMemoryAccountRepository accounts,
            InterestProcessor interestProcessor,
            ItemWriter<InterestItemResult> interestItemWriter) {
        return new InterestStepListener(xrefFile, discgrpFile, acctFile, xrefs, discgrps, accounts,
                interestProcessor, interestItemWriter);
    }
}
