package com.carddemo.online.card;

import com.carddemo.online.common.CardDemoException;
import com.carddemo.online.common.ScreenResponse;
import com.carddemo.online.domain.Card;
import com.carddemo.online.domain.CardRepository;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** COCRDSLC (card detail) and the search half of COCRDUPC (9000-READ-DATA). */
@Service
public class CardDetailService {

    private final CardRepository cards;

    public CardDetailService(CardRepository cards) {
        this.cards = cards;
    }

    /**
     * @param forUpdate true for the COCRDUPC search: name is upper-cased (9000-READ-DATA) and the
     *                  INFOMSG reads 'Details of selected card shown above'
     */
    public ScreenResponse<CardDetailView> find(String acctId, String cardNum, boolean forUpdate) {
        CardDetailView keysOnly = CardDetailView.keysOnly(trim(acctId), trim(cardNum), CardMessages.PROMPT_FOR_INPUT);
        CardKeys.Keys keys = CardKeys.validate(acctId, cardNum, keysOnly);
        // 9100-GETCARD-BYACCTCARD: READ CARDDAT by card number; NOTFND flags both keys
        Card card = cards.findById(keys.cardNum()).orElseThrow(() -> CardDemoException
                .notFound(CardMessages.DID_NOT_FIND_ACCTCARD_COMBO, CardKeys.ACCT_ID).withData(keysOnly));
        String info = forUpdate ? CardMessages.FOUND_CARD_FOR_UPDATE : CardMessages.DISPLAYING_DETAILS;
        return ScreenResponse.of(view(keys.acctId(), card, forUpdate, info), info);
    }

    static CardDetailView view(String acctId, Card card, boolean upperName, String info) {
        String name = card.getEmbossedName() == null ? "" : card.getEmbossedName();
        LocalDate exp = card.getExpirationDate();
        return new CardDetailView(acctId, card.getCardNum(), upperName ? name.toUpperCase(Locale.ROOT) : name,
                card.getActiveStatus(),
                exp == null ? "" : String.format("%02d", exp.getMonthValue()),
                exp == null ? "" : String.format("%04d", exp.getYear()),
                exp == null ? "" : String.format("%02d", exp.getDayOfMonth()),
                info);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
