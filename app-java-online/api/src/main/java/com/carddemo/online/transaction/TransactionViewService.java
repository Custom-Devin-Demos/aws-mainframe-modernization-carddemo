package com.carddemo.online.transaction;

import com.carddemo.online.common.CardDemoException;
import com.carddemo.online.common.CobolText;
import com.carddemo.online.common.ScreenResponse;
import com.carddemo.online.domain.Transaction;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/** COTRN01C (CT01) - View Transaction. */
@Service
public class TransactionViewService {

    static final String MSG_EMPTY = "Tran ID can NOT be empty...";
    static final String MSG_NOT_FOUND = "Transaction ID NOT found...";
    static final String MSG_UNABLE_LOOKUP = "Unable to lookup Transaction...";
    static final String FIELD = "tranId";

    private final TransactionStore store;

    public TransactionViewService(TransactionStore store) {
        this.store = store;
    }

    /** PROCESS-ENTER-KEY + READ-TRANSACT-FILE. */
    public ScreenResponse<TransactionView> view(String tranId) {
        if (CobolText.isBlank(tranId)) {
            throw CardDemoException.validation(MSG_EMPTY, FIELD);
        }
        Transaction t;
        try {
            t = store.find(CobolText.rtrim(tranId)).orElse(null);
        } catch (DataAccessException e) {
            throw CardDemoException.failure(MSG_UNABLE_LOOKUP, FIELD);
        }
        if (t == null) {
            throw CardDemoException.notFound(MSG_NOT_FOUND, FIELD);
        }
        return ScreenResponse.of(toView(t)).withField(FIELD);
    }

    static TransactionView toView(Transaction t) {
        return new TransactionView(
                t.getTranId(),
                t.getCardNum(),
                t.getTranTypeCd(),
                TranFormat.zeroPad(t.getTranCatCd() == null ? 0 : t.getTranCatCd(), 4),
                t.getSource(),
                TranFormat.amount(t.getAmount()),
                TranFormat.fit(t.getDescription(), 60),
                TranFormat.fit(TranFormat.timestamp(t.getOrigTs()), 10),
                TranFormat.fit(TranFormat.timestamp(t.getProcTs()), 10),
                TranFormat.zeroPad(t.getMerchantId() == null ? 0 : t.getMerchantId(), 9),
                TranFormat.fit(t.getMerchantName(), 30),
                TranFormat.fit(t.getMerchantCity(), 25),
                TranFormat.fit(t.getMerchantZip(), 10));
    }
}
