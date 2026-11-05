package com.carddemo.online.common;

import java.util.List;

/**
 * One screenful of a browse (STARTBR/READNEXT/READPREV) list screen.
 * {@code firstKey}/{@code lastKey} mirror the CDEMO-*-FIRST/LAST keys kept in the COMMAREA and are
 * passed back by the client to page forward (PF8) or backward (PF7).
 */
public record KeysetPage<T>(
        List<T> items,
        int pageNumber,
        String firstKey,
        String lastKey,
        boolean hasPrevPage,
        boolean hasNextPage) {
}
